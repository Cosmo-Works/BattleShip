// Ported from Sakura (https://github.com/Samsuik/Sakura), licensed under the MIT License.
package me.samsuik.sakura.player.visibility;

import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class FilterOrModifyPackets {
    public static boolean filterExplosion(
        final Vec3 explosionPos,
        final ServerPlayer player,
        final long gameTime,
        final MinimalVisibility minimalVisibility
    ) {
        final VisibilitySettings settings = player.visibilitySettings;
        final VisibilityState state = settings.get(VisibilityTypes.EXPLOSIONS);

        if (state == VisibilityState.MINIMAL && minimalVisibility.filterExplosion(explosionPos, gameTime)) {
            return false;
        }

        return state != VisibilityState.ON;
    }

    public static boolean canSendEntityUpdates(
        final Entity entity,
        final ServerPlayer player,
        final MinimalVisibility minimalVisibility
    ) {
        final VisibilitySettings settings = player.visibilitySettings;
        if (!settings.playerModified() || !entity.cannonEntity) {
            return true;
        }

        final EntityType<?> entityType = entity.getType();
        final VisibilityType type;
        if (entityType == EntityTypes.TNT) {
            type = VisibilityTypes.TNT;
        } else if (entityType == EntityTypes.FALLING_BLOCK) {
            type = VisibilityTypes.SAND;
        } else if (entityType == EntityTypes.WIND_CHARGE || entityType == EntityTypes.BREEZE_WIND_CHARGE) {
            type = VisibilityTypes.WIND_CHARGE;
        } else if (entityType == EntityTypes.SULFUR_CUBE) {
            type = VisibilityTypes.SULFUR_CUBE;
        } else {
            return true;
        }

        final VisibilityState state = settings.get(type);
        if (state == VisibilityState.MINIMAL) {
            return !minimalVisibility.filterEntity(entity);
        }

        return state != VisibilityState.OFF;
    }

    public static boolean filterOrModifyPacket(final Packet<?> packet, final ServerPlayer player) {
        final VisibilitySettings settings = player.visibilitySettings;
        final List<Packet<?>> extraPackets = extraPackets(packet);
        if (extraPackets != null) {
            extraPackets.removeIf(extraPacket -> filterExtraPackets(extraPacket, player));
        }

        if (settings.isToggled(VisibilityTypes.SPAWNERS)) {
            ClientboundLevelChunkPacketData chunkData = null;
            if (packet instanceof final ClientboundLevelChunkWithLightPacket chunkPacket) {
                chunkData = chunkPacket.getChunkData();
            } else if (packet instanceof final ClientboundLevelChunkPacketData chunkPacket) {
                chunkData = chunkPacket;
            }

            if (chunkData != null) {
                chunkData.removeBlockEntitiesOfType(BlockEntityTypes.MOB_SPAWNER);
            }

            if (packet instanceof final ClientboundBlockEntityDataPacket bedPacket && bedPacket.getType() == BlockEntityTypes.MOB_SPAWNER) {
                return true;
            }
        }

        if (packet instanceof final ClientboundBlockEventPacket bePacket && settings.isToggled(VisibilityTypes.PISTONS)) {
            return bePacket.getBlock() instanceof PistonBaseBlock;
        }

        return false;
    }

    private static boolean filterExtraPackets(final Packet<?> packet, final ServerPlayer player) {
        final VisibilitySettings settings = player.visibilitySettings;
        if (settings.isToggled(VisibilityTypes.SPAWNERS) && packet instanceof final ClientboundBlockEntityDataPacket bedPacket) {
            return bedPacket.getType() == BlockEntityTypes.MOB_SPAWNER;
        }

        return false;
    }

    private static @Nullable List<Packet<?>> extraPackets(final Packet<?> packet) {
        if (packet instanceof final ClientboundLevelChunkWithLightPacket chunkPacket) {
            return chunkPacket.getChunkData().getExtraPackets();
        }
        if (packet instanceof final ClientboundLevelChunkPacketData chunkData) {
            return chunkData.getExtraPackets();
        }
        return null;
    }
}
