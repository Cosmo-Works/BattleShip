package me.samsuik.sakura.player.knockback;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KnockbackSyncState {
    private double ping = -1.0;
    private double previousPing = -1.0;
    private double verticalVelocity;
    private int lastDamageTicks;
    private int combatUntilTick = Integer.MIN_VALUE;

    public void rememberAttack(
        final double latencyMillis,
        final int tickCount,
        final int combatTimer,
        final int invulnerableTicks,
        final double attackVerticalVelocity
    ) {
        this.observePing(latencyMillis);
        this.verticalVelocity = attackVerticalVelocity;
        this.lastDamageTicks = invulnerableTicks;
        this.combatUntilTick = tickCount + combatTimer;
    }

    public void observePing(final double latencyMillis) {
        this.previousPing = this.ping < 0.0 ? latencyMillis : this.ping;
        this.ping = latencyMillis;
    }

    public boolean inCombat(final int tickCount) {
        return tickCount <= this.combatUntilTick;
    }

    public boolean pingBelowOffset(final int pingOffset) {
        return this.currentPing() < pingOffset;
    }

    public int lastDamageTicks() {
        return this.lastDamageTicks;
    }

    public double verticalVelocity() {
        return this.verticalVelocity;
    }

    public double compensatedPing(final int pingOffset, final int spikeThreshold) {
        return Math.max(1.0, this.spikeSafePing(spikeThreshold) - pingOffset);
    }

    public int rawTicks() {
        return (int) Math.ceil(this.currentPing() * 20.0 / 1000.0);
    }

    public boolean onGroundClientSide(
        final double verticalVelocity,
        final double distanceToGround,
        final double gravity,
        final int pingOffset,
        final int spikeThreshold
    ) {
        final int timeToApex = verticalVelocity > 0.0
            ? KnockbackSyncPhysics.ticksUntilApex(verticalVelocity, gravity)
            : 0;
        if (timeToApex == -1) {
            return false;
        }

        final double apexHeight = verticalVelocity > 0.0
            ? KnockbackSyncPhysics.distanceTravelled(verticalVelocity, timeToApex, gravity)
            : 0.0;
        final int fallTicks = KnockbackSyncPhysics.fallTicks(verticalVelocity, apexHeight + distanceToGround, gravity);
        if (fallTicks == -1) {
            return false;
        }

        final int compensatedTicks = (int) Math.ceil(this.compensatedPing(pingOffset, spikeThreshold) * 20.0 / 1000.0);
        return (timeToApex + fallTicks) - compensatedTicks <= 0 && distanceToGround <= 1.3;
    }

    private double spikeSafePing(final int spikeThreshold) {
        final double current = this.currentPing();
        final double previous = this.previousPing < 0.0 ? current : this.previousPing;
        return current - previous > spikeThreshold ? previous : current;
    }

    private double currentPing() {
        return this.ping < 0.0 ? 0.0 : this.ping;
    }
}
