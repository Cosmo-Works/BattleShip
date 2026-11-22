package dev.cosmoworks.battleship.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.ProfileResult;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class ProfileLookupGuardTest {
    @Test
    void failedLookupsAreNotRepeated() {
        final SessionService delegate = mock(SessionService.class);
        when(delegate.fetchProfile(any(), anyBoolean())).thenReturn(null);
        final SessionService guard = ProfileLookupGuard.wrap(delegate);
        final UUID id = UUID.randomUUID();
        for (int i = 0; i < 80; ++i) {
            assertNull(guard.fetchProfile(id, true));
        }
        verify(delegate, times(1)).fetchProfile(id, true);
    }

    @Test
    void successfulLookupsGoThrough() {
        final SessionService delegate = mock(SessionService.class);
        final UUID id = UUID.randomUUID();
        when(delegate.fetchProfile(id, true)).thenReturn(new ProfileResult(new GameProfile(id, "battleship")));
        final SessionService guard = ProfileLookupGuard.wrap(delegate);
        for (int i = 0; i < 3; ++i) {
            assertNotNull(guard.fetchProfile(id, true));
        }
        verify(delegate, times(3)).fetchProfile(id, true);
    }

    @Test
    void aRateLimitPausesAllLookups() {
        final SessionService delegate = mock(SessionService.class);
        final org.slf4j.Logger authlibLogger = org.slf4j.LoggerFactory.getLogger("com.mojang.authlib.services.MinecraftServicesSessionService");
        when(delegate.fetchProfile(any(), anyBoolean())).thenAnswer(invocation -> {
            authlibLogger.warn("Couldn't look up profile properties for {}", invocation.getArgument(0),
                new MinecraftClientHttpException(429, null, Duration.ofSeconds(60)));
            return null;
        });
        final SessionService guard = ProfileLookupGuard.wrap(delegate);
        assertNull(guard.fetchProfile(UUID.randomUUID(), true));
        for (int i = 0; i < 20; ++i) {
            assertNull(guard.fetchProfile(UUID.randomUUID(), true));
        }
        verify(delegate, times(1)).fetchProfile(any(), anyBoolean());
        assertEquals(20L, ((ProfileLookupGuard) guard).skipped.sum());
    }
}
