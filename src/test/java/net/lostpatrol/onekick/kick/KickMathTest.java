package net.lostpatrol.onekick.kick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class KickMathTest {
    @Test
    void kickCooldownIsShortEnoughForRapidFollowUps() {
        assertEquals(4, KickMath.COOLDOWN_TICKS);
    }

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
    void irregularDestructionKeepsTheAverageRadiusAndBoundsEdgeVariation() {
        assertEquals(KickMath.IRREGULAR_DESTRUCTION_MIN_SCALE,
                KickMath.irregularDestructionScale(0.0D), 1.0E-9D);
        assertEquals(1.0D, KickMath.irregularDestructionScale(0.5D), 1.0E-9D);
        assertEquals(KickMath.IRREGULAR_DESTRUCTION_MAX_SCALE,
                KickMath.irregularDestructionScale(1.0D), 1.0E-9D);
        assertEquals(KickMath.IRREGULAR_DESTRUCTION_MIN_SCALE,
                KickMath.irregularDestructionScale(-10.0D), 1.0E-9D);
        assertEquals(KickMath.IRREGULAR_DESTRUCTION_MAX_SCALE,
                KickMath.irregularDestructionScale(10.0D), 1.0E-9D);
    }

    @Test
    void kickedFlightUsesAConstantGravityBallisticArc() {
        Vec3 air = KickMath.nextBallisticVelocity(new Vec3(10.0D, 2.0D, -5.0D), false);
        assertEquals(10.0D, air.x, 1.0E-9D);
        assertEquals(1.94D, air.y, 1.0E-9D);
        assertEquals(-5.0D, air.z, 1.0E-9D);

        Vec3 water = KickMath.nextBallisticVelocity(new Vec3(10.0D, 2.0D, -5.0D), true);
        assertEquals(8.2D, water.x, 1.0E-9D);
        assertEquals(1.62D, water.y, 1.0E-9D);
        assertEquals(-4.1D, water.z, 1.0E-9D);
    }

    @Test
    void strongerKickKeepsItsArcFlatterAndTravelsFarther() {
        Vec3 direction = new Vec3(1.0D, 0.3D, 0.0D).normalize();
        Vec3 low = direction;
        Vec3 high = direction.scale(4.0D);
        Vec3 lowNext = KickMath.nextBallisticVelocity(low, false);
        Vec3 highNext = KickMath.nextBallisticVelocity(high, false);
        double initialSlope = direction.y / direction.x;
        assertTrue(Math.abs(highNext.y / highNext.x - initialSlope)
                < Math.abs(lowNext.y / lowNext.x - initialSlope));
        assertTrue(simulatedBallisticRange(high) > simulatedBallisticRange(low) * 8.0D);
    }

    @Test
    void collisionDamageRequiresActualSpeedLoss() {
        assertEquals(0.0F, KickMath.collisionDamage(3.0D, 3.0D, 3), 1.0E-6F);
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
        assertEquals(3, KickMath.flightEffectTier(4.0D));
        assertEquals(4, KickMath.flightEffectTier(KickMath.MACH_RING_MIN_SPEED));
    }

    @Test
    void machRingsGrowAcrossTheFirstThreeRingsAndWithLaunchSpeed() {
        double thresholdSpeed = KickMath.MACH_RING_MIN_SPEED;
        assertEquals(0.55D, KickMath.machRingRadiusScale(0, thresholdSpeed), 1.0E-9D);
        assertEquals(0.78D, KickMath.machRingRadiusScale(1, thresholdSpeed), 1.0E-9D);
        assertEquals(1.0D, KickMath.machRingRadiusScale(2, thresholdSpeed), 1.0E-9D);
        assertEquals(1.0D, KickMath.machRingRadiusScale(20, thresholdSpeed), 1.0E-9D);
        assertTrue(KickMath.machRingRadiusScale(2, 7.0D)
                > KickMath.machRingRadiusScale(2, 5.0D));
        assertTrue(KickMath.machRingRadiusScale(2, 20.0D)
                > KickMath.machRingRadiusScale(2, 6.0D));
        assertTrue(KickMath.machRingMaximumRadiusScale(20.0D) > 3.0D);
    }

    @Test
    void machRingCountGrowsWithLaunchSpeedAndStaysBounded() {
        assertEquals(3, KickMath.machRingCount(KickMath.MACH_RING_MIN_SPEED));
        assertTrue(KickMath.machRingCount(7.0D) > KickMath.machRingCount(5.0D));
        assertTrue(KickMath.machRingCount(20.0D) > KickMath.machRingCount(6.0D));
        assertEquals(24, KickMath.machRingCount(Double.MAX_VALUE));
    }

    @Test
    void machRingLifetimeStartsLongAndGrowsWithLaunchSpeed() {
        assertEquals(60, KickMath.machRingLifetimeTicks(KickMath.MACH_RING_MIN_SPEED));
        assertTrue(KickMath.machRingLifetimeTicks(7.0D)
                > KickMath.machRingLifetimeTicks(5.0D));
        assertTrue(KickMath.machRingLifetimeTicks(20.0D)
                > KickMath.machRingLifetimeTicks(6.0D));
        assertEquals(200, KickMath.machRingLifetimeTicks(Double.MAX_VALUE));
    }

    @Test
    void machTrailDimensionsAndLifetimeGrowWithLaunchSpeedAndStayBounded() {
        double threshold = KickMath.MACH_RING_MIN_SPEED;
        assertEquals(21.375D, KickMath.machTrailLength(threshold), 1.0E-9D);
        assertEquals(0.24D, KickMath.machTrailThicknessScale(threshold), 1.0E-9D);
        assertEquals(45, KickMath.machTrailLifetimeTicks(threshold));
        assertTrue(KickMath.machTrailLength(20.0D) > KickMath.machTrailLength(6.0D));
        assertTrue(KickMath.machTrailThicknessScale(20.0D)
                > KickMath.machTrailThicknessScale(6.0D));
        assertTrue(KickMath.machTrailLifetimeTicks(20.0D)
                > KickMath.machTrailLifetimeTicks(6.0D));
        assertEquals(34.0D, KickMath.machTrailLength(Double.MAX_VALUE), 1.0E-9D);
        assertEquals(0.42D, KickMath.machTrailThicknessScale(Double.MAX_VALUE), 1.0E-9D);
        assertEquals(160, KickMath.machTrailLifetimeTicks(Double.MAX_VALUE));
    }

    @Test
    void traversalDistanceMatchesTheActiveDestructionTrack() {
        double kickSpeed = 6.0D;
        double launchSpeed = 4.75D;
        assertEquals(KickMath.disintegrationDepth(kickSpeed, 0),
                KickMath.impactTraversalDistance(kickSpeed, launchSpeed, 1, 0, 0), 1.0E-9D);
        assertEquals(KickMath.unstableExplosionRadius(launchSpeed, 2),
                KickMath.impactTraversalDistance(kickSpeed, launchSpeed, 1, 2, 0), 1.0E-6D);
        assertEquals(KickMath.disintegrationDepth(kickSpeed, 1),
                KickMath.impactTraversalDistance(kickSpeed, launchSpeed, 1, 2, 1), 1.0E-9D);
    }

    @Test
    void unstableExplosionRadiusTracksActualLaunchSpeedAndReferenceLoadout() {
        double fullCharge = KickMath.maxCharge(5, 2);
        double kickSpeed = KickMath.calculateFinalSpeed(1.5D, 0.0D, fullCharge, 0);
        double normalMobLaunchSpeed = kickSpeed * 0.82D;
        double levelOneRadius = KickMath.unstableExplosionRadius(normalMobLaunchSpeed, 1);
        double levelTwoRadius = KickMath.unstableExplosionRadius(normalMobLaunchSpeed, 2);
        double levelThreeRadius = KickMath.unstableExplosionRadius(normalMobLaunchSpeed, 3);

        assertEquals(16.0D, levelThreeRadius, 0.1D);
        assertEquals(levelThreeRadius / 3.0D, levelOneRadius, 1.0E-9D);
        assertEquals(levelThreeRadius * 2.0D / 3.0D, levelTwoRadius, 1.0E-9D);
        assertTrue(KickMath.unstableExplosionRadius(10.0D, 3)
                > KickMath.unstableExplosionRadius(5.0D, 3));
        assertTrue(KickMath.unstableExplosionRadius(10.0D, 3)
                > KickMath.unstableExplosionRadius(10.0D, 1));
    }

    private static double simulatedBallisticRange(Vec3 initialVelocity) {
        Vec3 position = Vec3.ZERO;
        Vec3 velocity = initialVelocity;
        for (int tick = 0; tick < 500; tick++) {
            position = position.add(velocity);
            velocity = KickMath.nextBallisticVelocity(velocity, false);
            if (tick > 0 && position.y <= 0.0D) {
                return position.horizontalDistance();
            }
        }
        throw new AssertionError("Ballistic arc did not return to its launch height");
    }
}
