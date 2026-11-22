package dev.cosmoworks.battleship.movement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.cosmoworks.battleship.BattleshipServerConfig;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AllFeatures
class MovePacketCollapseTest {
    private ServerGamePacketListenerImpl listener;
    private PacketProcessor processor;

    @BeforeEach
    void setUp() {
        BattleshipServerConfig.movement().collapseRedundantMovePackets = true;
        this.listener = mock(ServerGamePacketListenerImpl.class);
        when(this.listener.shouldHandleMessage(any())).thenReturn(true);
        when(this.listener.battleship$skipRedundantMove(any())).thenReturn(true);
        this.processor = new PacketProcessor(null);
    }

    @AfterEach
    void tearDown() {
        BattleshipServerConfig.movement().collapseRedundantMovePackets = false;
    }

    private void schedule(final ServerboundMovePlayerPacket packet) {
        this.processor.scheduleIfPossible(this.listener, packet);
    }

    @Test
    void rotationFollowedByRotationIsCollapsed() {
        final ServerboundMovePlayerPacket first = new ServerboundMovePlayerPacket.Rot(10.0F, 0.0F, true, false);
        final ServerboundMovePlayerPacket second = new ServerboundMovePlayerPacket.Rot(20.0F, 0.0F, true, false);
        this.schedule(first);
        this.schedule(second);
        this.processor.processQueuedPackets();
        verify(this.listener, times(1)).battleship$skipRedundantMove(first);
        verify(this.listener, never()).handleMovePlayer(first);
        verify(this.listener, times(1)).handleMovePlayer(second);
    }

    @Test
    void rotationBeforeAPositionOnlyPacketIsKept() {
        final ServerboundMovePlayerPacket rotation = new ServerboundMovePlayerPacket.Rot(10.0F, 0.0F, true, false);
        final ServerboundMovePlayerPacket position = new ServerboundMovePlayerPacket.Pos(1.0, 2.0, 3.0, true, false);
        this.schedule(rotation);
        this.schedule(position);
        this.processor.processQueuedPackets();
        verify(this.listener, times(1)).handleMovePlayer(rotation);
        verify(this.listener, times(1)).handleMovePlayer(position);
    }

    @Test
    void differentGroundFlagsAreKept() {
        final ServerboundMovePlayerPacket first = new ServerboundMovePlayerPacket.StatusOnly(false, false);
        final ServerboundMovePlayerPacket second = new ServerboundMovePlayerPacket.Rot(20.0F, 0.0F, true, false);
        this.schedule(first);
        this.schedule(second);
        this.processor.processQueuedPackets();
        verify(this.listener, times(1)).handleMovePlayer(first);
        verify(this.listener, times(1)).handleMovePlayer(second);
    }

    @Test
    void statusBeforeAnyMovePacketWithTheSameFlagsIsCollapsed() {
        final ServerboundMovePlayerPacket status = new ServerboundMovePlayerPacket.StatusOnly(true, false);
        final ServerboundMovePlayerPacket position = new ServerboundMovePlayerPacket.Pos(1.0, 2.0, 3.0, true, false);
        this.schedule(status);
        this.schedule(position);
        this.processor.processQueuedPackets();
        verify(this.listener, never()).handleMovePlayer(status);
        verify(this.listener, times(1)).handleMovePlayer(position);
    }

    @Test
    void positionPacketsAreNeverCollapsed() {
        final ServerboundMovePlayerPacket first = new ServerboundMovePlayerPacket.PosRot(1.0, 2.0, 3.0, 0.0F, 0.0F, true, false);
        final ServerboundMovePlayerPacket second = new ServerboundMovePlayerPacket.PosRot(1.0, 2.0, 3.0, 5.0F, 0.0F, true, false);
        this.schedule(first);
        this.schedule(second);
        this.processor.processQueuedPackets();
        verify(this.listener, times(1)).handleMovePlayer(first);
        verify(this.listener, times(1)).handleMovePlayer(second);
    }

    @Test
    void offByDefault() {
        BattleshipServerConfig.movement().collapseRedundantMovePackets = false;
        final ServerboundMovePlayerPacket first = new ServerboundMovePlayerPacket.Rot(10.0F, 0.0F, true, false);
        final ServerboundMovePlayerPacket second = new ServerboundMovePlayerPacket.Rot(20.0F, 0.0F, true, false);
        this.schedule(first);
        this.schedule(second);
        this.processor.processQueuedPackets();
        verify(this.listener, times(1)).handleMovePlayer(first);
        verify(this.listener, times(1)).handleMovePlayer(second);
    }
}
