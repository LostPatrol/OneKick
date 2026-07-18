package net.lostpatrol.onekick.kick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class KickMathTest {
    @Test
    void baseKickIsStableAndStationaryMovementAddsNothing() {
        assertEquals(1.0D, KickMath.calculateFinalSpeed(1.0D, 0.0D, 0.0D, 0), 1.0E-9D);
    }

    @Test
    void movementChargeAndOverloadUseSeparateMultipliers() {
        double expected = (1.0D * 1.5D + 0.25D * 1.8D) * KickMath.chargeMultiplier(2.0D)
                * (1.0D + 0.55D);
        assertEquals(expected, KickMath.calculateFinalSpeed(1.5D, 0.25D, 2.0D, 1), 1.0E-9D);
    }

    @Test
    void chargePowerStartsSoftAndAcceleratesQuadratically() {
        assertEquals(1.0D, KickMath.chargeMultiplier(0.0D), 1.0E-9D);
        assertEquals(1.0D + 0.25D / 15.0D, KickMath.chargeMultiplier(0.5D), 1.0E-9D);
        assertEquals(1.6D, KickMath.chargeMultiplier(3.0D), 1.0E-9D);
        assertEquals(3.4D, KickMath.chargeMultiplier(6.0D), 1.0E-9D);
        assertTrue(KickMath.chargeMultiplier(6.0D) - KickMath.chargeMultiplier(5.0D)
                > KickMath.chargeMultiplier(1.0D) - KickMath.chargeMultiplier(0.0D));
    }

    @Test
    void debugValuesAreNotUpperClampedButInvalidNegativeInputsStaySafe() {
        assertTrue(KickMath.calculateFinalSpeed(1000.0D, 1000.0D, 1000.0D, 99) > 20.0D);
        assertEquals(1.0D,
                KickMath.calculateFinalSpeed(Double.NaN, -10.0D, -4.0D, -3), 1.0E-9D);
    }

    @Test
    void levelFiveChargeHasSpecifiedFullThresholdFoodCostAndFasterRate() {
        assertEquals(6.0F, KickMath.maxCharge(5, 0), 1.0E-6F);
        assertEquals(12.0F, KickMath.maxCharge(5, 1), 1.0E-6F);
        assertEquals(18.0F, KickMath.maxCharge(5, 2), 1.0E-6F);
        assertEquals(15.0F, KickMath.chargeFoodCost(KickMath.maxCharge(5, 0)), 1.0E-6F);
        assertEquals(0.2625F, KickMath.chargePerTick(5), 1.0E-6F);
    }

    @Test
    void disintegrationRadiusHasDiminishingReturns() {
        double earlyGain = KickMath.disintegrationRadius(2.0D, false)
                - KickMath.disintegrationRadius(1.0D, false);
        double lateGain = KickMath.disintegrationRadius(10.0D, false)
                - KickMath.disintegrationRadius(9.0D, false);
        assertTrue(earlyGain > lateGain);
        assertTrue(KickMath.disintegrationRadius(10.0D, false) > 4.5D);
    }

    @Test
    void kickedFlightUsesSnowballDragAndGravity() {
        Vec3 air = KickMath.nextFlightVelocity(new Vec3(10.0D, 2.0D, -5.0D), false, false);
        assertEquals(9.9D, air.x, 1.0E-9D);
        assertEquals(1.95D, air.y, 1.0E-9D);
        assertEquals(-4.95D, air.z, 1.0E-9D);

        Vec3 water = KickMath.nextFlightVelocity(new Vec3(10.0D, 2.0D, -5.0D), true, false);
        assertEquals(8.0D, water.x, 1.0E-9D);
        assertEquals(1.57D, water.y, 1.0E-9D);
        assertEquals(-4.0D, water.z, 1.0E-9D);
    }

    @Test
    void launchDirectionRespondsStronglyToUpwardView() {
        Vec3 flat = KickMath.launchDirection(new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 upward = KickMath.launchDirection(new Vec3(0.0D, 0.6D, 0.8D));
        assertTrue(upward.y > flat.y);
        assertTrue(upward.z > 0.0D);
    }

    @Test
    void healthAndKnockbackResistanceHaveOnlyMildLaunchPenalties() {
        assertTrue(KickMath.healthLaunchFactor(500.0D) > 0.6D);
        assertEquals(0.85D, KickMath.resistanceLaunchFactor(1.0D), 1.0E-9D);
    }

    @Test
    void impactEffectsRequireDirectionCloseToOriginalLaunch() {
        Vec3 initial = new Vec3(1.0D, 0.0D, 0.0D);
        assertTrue(KickMath.isAlignedImpact(initial, new Vec3(1.0D, 0.25D, 0.0D)));
        assertTrue(!KickMath.isAlignedImpact(initial, new Vec3(0.0D, -1.0D, 0.0D)));
        assertTrue(!KickMath.isAlignedImpact(initial, new Vec3(-1.0D, 0.0D, 0.0D)));
    }

    @Test
    void flightEffectTierUsesPersistentLaunchSpeedBands() {
        assertEquals(0, KickMath.flightEffectTier(0.5D));
        assertEquals(1, KickMath.flightEffectTier(0.9D));
        assertEquals(2, KickMath.flightEffectTier(1.8D));
        assertEquals(3, KickMath.flightEffectTier(2.8D));
        assertEquals(4, KickMath.flightEffectTier(4.0D));
    }

    @Test
    void traversalDistanceMatchesTheActiveDestructionTrack() {
        double kickSpeed = 6.0D;
        assertEquals(KickMath.disintegrationDepth(kickSpeed, 0),
                KickMath.impactTraversalDistance(kickSpeed, 1, 0, 0), 1.0E-9D);
        assertEquals(KickMath.explosionPower(kickSpeed, 2) * 1.35D,
                KickMath.impactTraversalDistance(kickSpeed, 1, 2, 0), 1.0E-6D);
        assertEquals(KickMath.disintegrationDepth(kickSpeed, 1),
                KickMath.impactTraversalDistance(kickSpeed, 1, 2, 1), 1.0E-9D);
    }
}
