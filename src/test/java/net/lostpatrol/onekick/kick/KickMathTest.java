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
    void reactionAndAerodynamicsImpulsesMatchConfiguredCoefficients() {
        assertEquals(0.44D, KickMath.blockReactionImpulse(1.0D), 1.0E-9D);
        assertEquals(0.44D, KickMath.entityReactionImpulse(1.0D), 1.0E-9D);
        assertEquals(0.50D, KickMath.aerodynamicsImpulse(1.0D), 1.0E-9D);
        assertEquals(2.20D, KickMath.blockReactionImpulse(5.0D), 1.0E-9D);
        assertEquals(2.20D, KickMath.entityReactionImpulse(5.0D), 1.0E-9D);
        assertEquals(2.50D, KickMath.aerodynamicsImpulse(5.0D), 1.0E-9D);
        assertEquals(0.0D, KickMath.blockReactionImpulse(-2.0D), 1.0E-9D);
        assertEquals(0.0D, KickMath.entityReactionImpulse(Double.NaN), 1.0E-9D);
        assertEquals(0.0D, KickMath.aerodynamicsImpulse(-1.0D), 1.0E-9D);
    }

    @Test
    void reactionVelocityUsesTheFullLookDirectionIncludingVertical() {
        Vec3 downward = KickMath.reactionVelocity(Vec3.ZERO, new Vec3(0.0D, -1.0D, 0.0D), 2.0D);
        assertEquals(0.0D, downward.x, 1.0E-9D);
        assertEquals(2.0D, downward.y, 1.0E-9D);
        assertEquals(0.0D, downward.z, 1.0E-9D);

        Vec3 upward = KickMath.reactionVelocity(Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D), 2.0D);
        assertEquals(0.0D, upward.x, 1.0E-9D);
        assertEquals(-2.0D, upward.y, 1.0E-9D);
        assertEquals(0.0D, upward.z, 1.0E-9D);

        Vec3 diagonal = KickMath.reactionVelocity(
                Vec3.ZERO, new Vec3(0.0D, 1.0D, 1.0D), 2.0D);
        assertTrue(diagonal.y < 0.0D);
        assertTrue(diagonal.z < 0.0D);
        assertEquals(2.0D, diagonal.length(), 1.0E-9D);
        assertEquals(
                KickMath.entityReactionImpulse(1.0D) * -1.0D / Math.sqrt(2.0D),
                KickMath.reactionVelocity(
                        Vec3.ZERO, new Vec3(0.0D, 1.0D, 1.0D),
                        KickMath.entityReactionImpulse(1.0D)).y,
                1.0E-9D);
    }

    @Test
    void reactionVelocityKeepsImpulsesAboveTheVanillaMotionPacketCap() {
        Vec3 result = KickMath.reactionVelocity(
                Vec3.ZERO, new Vec3(0.0D, -1.0D, 0.0D), 18.0D);
        assertTrue(result.y > 3.9D);
        assertEquals(18.0D, result.y, 1.0E-9D);
    }

    @Test
    void aerodynamicsImpulseDoesNotScaleWithTheOldEnchantmentLevelCurve() {
        double kickSpeed = 3.0D;
        double oldLevelThree = kickSpeed * 0.16D * (1.0D + 3 * 0.15D);
        assertEquals(kickSpeed * 0.50D, KickMath.aerodynamicsImpulse(kickSpeed), 1.0E-9D);
        assertTrue(KickMath.aerodynamicsImpulse(kickSpeed) > oldLevelThree);
    }

    @Test
    void debugValuesAreNotUpperClampedButInvalidNegativeInputsStaySafe() {
        assertTrue(KickMath.calculateFinalSpeed(1000.0D, 1000.0D, 1000.0D, 99) > 20.0D);
        assertEquals(1.0D,
                KickMath.calculateFinalSpeed(Double.NaN, -10.0D, -4.0D, -3), 1.0E-9D);
    }

    @Test
    void chargingAndChargedKickUseSeparateOverchargeFoodCurves() {
        assertEquals(6.0F, KickMath.maxCharge(5, 0), 1.0E-6F);
        assertEquals(12.0F, KickMath.maxCharge(5, 1), 1.0E-6F);
        assertEquals(18.0F, KickMath.maxCharge(5, 2), 1.0E-6F);
        assertEquals(15.0F, KickMath.chargeFoodCost(KickMath.maxCharge(5, 0)), 1.0E-6F);
        assertEquals(30.0F, KickMath.chargeFoodCost(KickMath.maxCharge(5, 1)), 1.0E-6F);
        assertEquals(45.0F, KickMath.chargeFoodCost(KickMath.maxCharge(5, 2)), 1.0E-6F);
        assertEquals(15.0F, KickMath.chargedKickFoodCost(KickMath.maxCharge(5, 0)), 1.0E-6F);
        assertEquals(25.0F, KickMath.chargedKickFoodCost(KickMath.maxCharge(5, 1)), 1.0E-6F);
        assertEquals(35.0F, KickMath.chargedKickFoodCost(KickMath.maxCharge(5, 2)), 1.0E-6F);
        assertTrue(KickMath.chargedKickFoodCost(7.0F) - KickMath.chargedKickFoodCost(6.0F)
                < KickMath.chargedKickFoodCost(6.0F) - KickMath.chargedKickFoodCost(5.0F));
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
    void destructionCapsuleRoundsBothCylinderEnds() {
        double depth = 8.0D;
        assertEquals(9.0D,
                KickMath.destructionCapsuleDistanceSquared(4.0D, 3.0D, depth), 1.0E-9D);
        assertEquals(25.0D,
                KickMath.destructionCapsuleDistanceSquared(-3.0D, 4.0D, depth), 1.0E-9D);
        assertEquals(25.0D,
                KickMath.destructionCapsuleDistanceSquared(11.0D, 4.0D, depth), 1.0E-9D);
        assertTrue(KickMath.destructionCapsuleDistanceSquared(-3.0D, 4.1D, depth) > 25.0D);
        assertTrue(KickMath.destructionCapsuleDistanceSquared(11.0D, 4.1D, depth) > 25.0D);
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
        assertEquals(KickMath.MACH_RING_RENDER_LIMIT,
                KickMath.machRingCount(Double.MAX_VALUE));
    }

    @Test
    void machRingIntervalKeepsItsBaseAndGrowsByAtMostHalf() {
        assertEquals(21.375D,
                KickMath.machRingInterval(KickMath.MACH_RING_MIN_SPEED), 1.0E-9D);
        assertTrue(KickMath.machRingInterval(7.0D)
                > KickMath.machRingInterval(5.0D));
        assertTrue(KickMath.machRingInterval(20.0D)
                > KickMath.machRingInterval(7.0D));
        assertEquals(21.375D * 1.5D,
                KickMath.machRingInterval(Double.MAX_VALUE), 1.0E-9D);
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
    void machTrailDissipatesTwentyPercentSlowerThanMachRings() {
        double threshold = KickMath.MACH_RING_MIN_SPEED;
        assertEquals(75, KickMath.machTrailLifetimeTicks(threshold));
        assertEquals((int) Math.ceil(KickMath.machRingLifetimeTicks(20.0D) / 0.8D),
                KickMath.machTrailLifetimeTicks(20.0D));
        assertEquals(250, KickMath.machTrailLifetimeTicks(Double.MAX_VALUE));
    }

    @Test
    void particleFadeUsesSmoothEndpointsAndMidpoint() {
        assertEquals(1.0F, KickMath.smoothFadeScale(20, 20));
        assertEquals(0.5F, KickMath.smoothFadeScale(10, 20));
        assertEquals(0.0F, KickMath.smoothFadeScale(0, 20));
    }

    @Test
    void disintegrationSmokeScalesWithDestroyedBlocksAndImpactSpeed() {
        assertEquals(0, KickMath.disintegrationSmokeParticleCount(0, 6.0D));
        assertEquals(0, KickMath.disintegrationSmokeParticleCount(100, 0.0D));
        assertEquals(7, KickMath.disintegrationSmokeParticleCount(1, 1.0D));
        assertEquals(27, KickMath.disintegrationSmokeParticleCount(1, 2.0D));
        assertEquals(60, KickMath.disintegrationSmokeParticleCount(1, 3.0D));
        assertEquals(240, KickMath.disintegrationSmokeParticleCount(1, 6.0D));
        assertTrue(KickMath.disintegrationSmokeParticleCount(100, 1.0D)
                < KickMath.disintegrationSmokeParticleCount(100, 3.0D));
        assertTrue(KickMath.disintegrationSmokeParticleCount(100, 3.0D)
                < KickMath.disintegrationSmokeParticleCount(100, 6.0D));
        assertTrue(KickMath.disintegrationSmokeParticleCount(1000, 3.0D)
                > KickMath.disintegrationSmokeParticleCount(100, 3.0D));
        assertEquals(4096,
                KickMath.disintegrationSmokeParticleCount(Integer.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(32,
                KickMath.disintegrationSmokeParticleCount(1, 4, 1.0D));
        assertEquals(2048,
                KickMath.disintegrationSmokeParticleCount(1000, 256, 1.0D));
        assertEquals(4096,
                KickMath.disintegrationSmokeParticleCount(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(0.24D, KickMath.impactDebrisInitialSpeed(0.0D, 0.5D), 1.0E-9D);
        assertEquals(1.34D, KickMath.impactDebrisInitialSpeed(10.0D, 0.0D), 1.0E-9D);
        assertEquals(1.69D, KickMath.impactDebrisInitialSpeed(10.0D, 1.0D), 1.0E-9D);
        assertEquals(0.084D,
                KickMath.disintegrationSmokeInitialSpeed(0.0D, 0.5D), 1.0E-9D);
        assertEquals(0.469D,
                KickMath.disintegrationSmokeInitialSpeed(10.0D, 0.0D), 1.0E-9D);
        assertEquals(0.5915D,
                KickMath.disintegrationSmokeInitialSpeed(10.0D, 1.0D), 1.0E-9D);
        assertTrue(KickMath.shouldEmitDisintegrationSmoke(1, 0));
        assertTrue(!KickMath.shouldEmitDisintegrationSmoke(1, 1));
        assertTrue(!KickMath.shouldEmitDisintegrationSmoke(0, 0));
        assertTrue(KickMath.shouldApplyTraversalDamage(1, 0));
        assertTrue(!KickMath.shouldApplyTraversalDamage(1, 1));
        assertTrue(!KickMath.shouldApplyTraversalDamage(0, 0));
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
        assertEquals(1.0D, KickMath.unstableExplosionVisualScale(1.0D), 1.0E-9D);
        assertEquals(1.0D, KickMath.unstableExplosionVisualScale(4.0D), 1.0E-9D);
        assertEquals(4.0D, KickMath.unstableExplosionVisualScale(16.0D), 1.0E-9D);
    }

    @Test
    void kineticOverloadDropProtectionUsesOnlyEnableAndEnchantmentGates() {
        assertTrue(!KickMath.shouldSuppressKineticOverloadBlockDrops(false, 3));
        assertTrue(!KickMath.shouldSuppressKineticOverloadBlockDrops(true, 0));
        assertTrue(KickMath.shouldSuppressKineticOverloadBlockDrops(true, 1));
        assertTrue(KickMath.shouldSuppressKineticOverloadBlockDrops(true, 3));
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
