package gg.shrekmc.profile;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;
import gg.shrekmc.ShrekServerConfig;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class ProfileLookupGuard implements SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger("ShrekProfileLookup");
    private static final String AUTHLIB_LOGGER = "com.mojang.authlib.services.MinecraftServicesSessionService";
    private static final long MIN_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final ThreadLocal<Long> RATE_LIMITED_FOR = new ThreadLocal<>();
    private static volatile boolean filterInstalled;

    private final SessionService delegate;
    private final Map<UUID, Long> failedUntil = new ConcurrentHashMap<>();
    private final AtomicLong backoffUntil = new AtomicLong(System.nanoTime());
    public final LongAdder skipped = new LongAdder();

    private ProfileLookupGuard(final SessionService delegate) {
        this.delegate = delegate;
    }

    public static SessionService wrap(final SessionService delegate) {
        return new ProfileLookupGuard(delegate);
    }

    @Override
    public @Nullable ProfileResult fetchProfile(final UUID profileId, final boolean requireSecure) {
        final int cacheSeconds = ShrekServerConfig.network().profileLookupFailureCacheSeconds;
        if (cacheSeconds <= 0) {
            return this.delegate.fetchProfile(profileId, requireSecure);
        }
        installFilter();
        final long now = System.nanoTime();
        if (now - this.backoffUntil.get() < 0) {
            this.skipped.increment();
            return null;
        }
        final Long until = this.failedUntil.get(profileId);
        if (until != null) {
            if (now - until < 0) {
                this.skipped.increment();
                return null;
            }
            this.failedUntil.remove(profileId, until);
        }
        RATE_LIMITED_FOR.remove();
        final ProfileResult result = this.delegate.fetchProfile(profileId, requireSecure);
        if (result != null) {
            return result;
        }
        final long cacheNanos = TimeUnit.SECONDS.toNanos(cacheSeconds);
        this.failedUntil.put(profileId, now + cacheNanos);
        final Long retryAfter = RATE_LIMITED_FOR.get();
        if (retryAfter != null) {
            RATE_LIMITED_FOR.remove();
            final long backoff = Math.max(MIN_BACKOFF_NANOS, retryAfter);
            final long target = now + backoff;
            this.backoffUntil.accumulateAndGet(target, (current, next) -> current - next < 0 ? next : current);
            LOGGER.warn("Mojang rate limited profile lookups; pausing them for {} s", TimeUnit.NANOSECONDS.toSeconds(backoff));
        }
        if (this.failedUntil.size() > 100_000) {
            this.failedUntil.entrySet().removeIf(entry -> now - entry.getValue() >= 0);
        }
        return null;
    }

    private static void installFilter() {
        if (filterInstalled) {
            return;
        }
        synchronized (ProfileLookupGuard.class) {
            if (filterInstalled) {
                return;
            }
            try {
                final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(AUTHLIB_LOGGER);
                logger.addFilter(new AbstractFilter(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL) {
                    @Override
                    public Filter.Result filter(final LogEvent event) {
                        if (event.getLevel().isMoreSpecificThan(Level.WARN)
                            && event.getThrown() instanceof MinecraftClientHttpException http && http.getStatus() == 429) {
                            RATE_LIMITED_FOR.set(http.getRetryAfter().map(java.time.Duration::toNanos).orElse(MIN_BACKOFF_NANOS));
                        }
                        return Filter.Result.NEUTRAL;
                    }
                });
            } catch (final Throwable thrown) {
                LOGGER.warn("Couldn't watch authlib for rate limits; failed lookups are still not retried", thrown);
            }
            filterInstalled = true;
        }
    }

    @Override
    public void joinServer(final UUID profileId, final String authenticationToken, final String serverId) throws AuthenticationException {
        this.delegate.joinServer(profileId, authenticationToken, serverId);
    }

    @Override
    public @Nullable ProfileResult hasJoinedServer(final String profileName, final String serverId, final @Nullable InetAddress address) throws AuthenticationUnavailableException {
        return this.delegate.hasJoinedServer(profileName, serverId, address);
    }

    @Override
    public @Nullable Property getPackedTextures(final GameProfile profile) {
        return this.delegate.getPackedTextures(profile);
    }

    @Override
    public MinecraftProfileTextures unpackTextures(final Property packedTextures) {
        return this.delegate.unpackTextures(packedTextures);
    }

    @Override
    public MinecraftProfileTextures getTextures(final GameProfile profile) {
        return this.delegate.getTextures(profile);
    }

    @Override
    public @Nullable String getSecurePropertyValue(final Property property) throws InsecurePublicKeyException {
        return this.delegate.getSecurePropertyValue(property);
    }
}
