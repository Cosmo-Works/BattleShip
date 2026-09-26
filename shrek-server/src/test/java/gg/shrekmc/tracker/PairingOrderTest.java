package gg.shrekmc.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.UpdateInterval;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
class PairingOrderTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void equipmentResyncGoesOutAfterTheSpawnBundle() {
        final List<String> sent = new ArrayList<>();

        final ServerPlayer viewer = mock(ServerPlayer.class);
        final ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        viewer.connection = connection;
        doAnswer(invocation -> {
            final Object packet = invocation.getArgument(0);
            sent.add(packet instanceof ClientboundBundlePacket ? "spawn bundle" : packet.getClass().getSimpleName());
            return null;
        }).when(connection).send(any(Packet.class));

        final LivingEntity entity = mock(LivingEntity.class, RETURNS_DEEP_STUBS);
        when(entity.trackingPosition()).thenReturn(Vec3.ZERO);
        when(entity.getDeltaMovement()).thenReturn(Vec3.ZERO);
        when(entity.getEntityData().getNonDefaultValues()).thenReturn(null);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.getAddEntityPacket(any())).thenReturn((Packet) new ClientboundKeepAlivePacket(1));
        when(entity.getAttributes().getSyncableAttributes()).thenReturn(List.of());
        when(entity.getItemBySlot(any())).thenReturn(ItemStack.EMPTY);
        when(entity.getPassengers()).thenReturn((List) List.of());

        doAnswer(invocation -> {
            viewer.connection.send(new ClientboundKeepAlivePacket(2));
            sent.set(sent.size() - 1, "equipment broadcast");
            return null;
        }).when(entity).detectEquipmentUpdates();

        final ServerEntity serverEntity = new ServerEntity(
            mock(ServerLevel.class), entity, mock(UpdateInterval.class), true, mock(ServerEntity.Synchronizer.class), Set.of()
        );
        serverEntity.addPairing(viewer);

        assertEquals(List.of("spawn bundle", "equipment broadcast"), sent);
    }
}
