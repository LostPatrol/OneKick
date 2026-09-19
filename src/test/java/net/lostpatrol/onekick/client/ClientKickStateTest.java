package net.lostpatrol.onekick.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ClientKickStateTest {
    private static final double EPSILON = 1.0E-8D;

    @Test
    void predictedMachTrailsConnectEveryAdjacentRingAlongTheBallisticPath() {
        Vec3 start = new Vec3(1.25D, 4.0D, -2.5D);
        int ringCount = 7;
        double ringInterval = 21.375D;
        List<ClientKickState.PredictedMachRing> rings = ClientKickState.predictMachRings(
                start, new Vec3(5.0D, 1.4D, 0.65D), false, ringCount, ringInterval);

        assertEquals(ringCount, rings.size());
        Vec3 previousCenter = start;
        boolean foundCurvedSegment = false;
        for (int i = 0; i < rings.size(); i++) {
            ClientKickState.PredictedMachRing ring = rings.get(i);
            List<Vec3> trail = ring.trailPath();
            assertVecEquals(previousCenter, trail.get(0));
            assertVecEquals(ring.center(), trail.get(trail.size() - 1));
            assertEquals(i == 0 ? 4.0D : ringInterval,
                    ClientKickState.machTrailLength(trail), EPSILON);
            assertVecEquals(trail.get(0),
                    ClientKickState.sampleMachTrail(trail, 0.0D).point());
            assertVecEquals(trail.get(trail.size() - 1),
                    ClientKickState.sampleMachTrail(
                            trail, ClientKickState.machTrailLength(trail)).point());
            foundCurvedSegment |= trail.size() > 2;
            previousCenter = ring.center();
        }
        assertTrue(foundCurvedSegment,
                "Regression setup did not cross any ballistic tick boundary");
    }

    @Test
    void machTrailSamplingFollowsPolylineCornersInsteadOfTheFinalTangent() {
        List<Vec3> trail = List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 3.0D, 0.0D),
                new Vec3(5.0D, 3.0D, 0.0D));

        assertEquals(8.0D, ClientKickState.machTrailLength(trail), EPSILON);
        assertVecEquals(new Vec3(1.0D, 0.0D, 0.0D),
                ClientKickState.sampleMachTrail(trail, 1.0D).point());
        assertVecEquals(new Vec3(2.0D, 2.0D, 0.0D),
                ClientKickState.sampleMachTrail(trail, 4.0D).point());
        assertVecEquals(new Vec3(4.0D, 3.0D, 0.0D),
                ClientKickState.sampleMachTrail(trail, 7.0D).point());
    }

    @Test
    void chargeSmokeScaleAndDensityUseTheChargeLimit() {
        assertEquals(1.0D, ClientKickState.chargeSmokeScale(2.0D), EPSILON);
        assertEquals(2.0D, ClientKickState.chargeSmokeScale(8.0D), EPSILON);
        assertEquals(3.0D, ClientKickState.chargeSmokeScale(18.0D), EPSILON);
        assertEquals(2.9D, ClientKickState.chargeSmokeMaximumReach(2.0D), EPSILON);
        assertEquals(5.5D, ClientKickState.chargeSmokeMaximumReach(18.0D), EPSILON);

        for (double maximum = 3.0D; maximum <= 18.0D; maximum += 1.0D) {
            assertTrue(ClientKickState.chargeSmokeMaximumReach(maximum)
                    > ClientKickState.chargeSmokeMaximumReach(maximum - 1.0D));
            assertTrue(ClientKickState.chargeSmokeMaximumConeRadius(maximum)
                    > ClientKickState.chargeSmokeMaximumConeRadius(maximum - 1.0D));
            assertTrue(ClientKickState.chargeSmokeConeParticleCount(maximum, 0.0D)
                    >= ClientKickState.chargeSmokeConeParticleCount(maximum - 1.0D, 0.0D));
        }
        assertEquals(21, ClientKickState.chargeSmokeConeParticleCount(6.0D, 0.0D));
        assertEquals(15, ClientKickState.chargeSmokeConeParticleCount(6.0D, 0.5D));
    }

    @Test
    void chargeSmokeFunnelStartsImmediatelyAndFinishesAtFullCharge() {
        assertEquals(1.0D, ClientKickState.chargeConeRemainingScale(0.0D), EPSILON);
        assertTrue(ClientKickState.chargeSmokeConeParticleCount(6.0D, 0.0D) > 0);
        assertTrue(ClientKickState.chargeConeRemainingScale(0.5D) > 0.0D);
        assertTrue(ClientKickState.chargeConeRemainingScale(0.999D) > 0.0D);
        assertEquals(0.0D, ClientKickState.chargeConeRemainingScale(1.0D), EPSILON);
        assertEquals(0, ClientKickState.chargeSmokeConeParticleCount(6.0D, 1.0D));
    }

    @Test
    void chargeSmokeFunnelUsesTheFullViewDirectionIncludingPitch() {
        Vec3 eye = new Vec3(2.0D, 5.0D, -3.0D);
        Vec3 foot = new Vec3(2.5D, 3.8D, -2.4D);
        Vec3 horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 upward = new Vec3(0.0D, 1.0D, 1.0D).normalize();
        Vec3 downward = new Vec3(0.0D, -1.0D, 1.0D).normalize();

        Vec3 horizontalCenter = ClientKickState.chargeSmokeConeCenter(
                eye, foot, horizontal, 6.0D, 0.0D);
        Vec3 upwardCenter = ClientKickState.chargeSmokeConeCenter(
                eye, foot, upward, 6.0D, 0.0D);
        Vec3 downwardCenter = ClientKickState.chargeSmokeConeCenter(
                eye, foot, downward, 6.0D, 0.0D);
        assertEquals(eye.y, horizontalCenter.y, EPSILON);
        assertTrue(upwardCenter.y > horizontalCenter.y);
        assertTrue(downwardCenter.y < horizontalCenter.y);

        ClientKickState.ChargeSmokeBasis basis = ClientKickState.chargeSmokeBasis(upward);
        assertEquals(0.0D, basis.forward().dot(basis.right()), EPSILON);
        assertEquals(0.0D, basis.forward().dot(basis.up()), EPSILON);
        assertVecEquals(foot, ClientKickState.chargeSmokeConeCenter(
                eye, foot, upward, 6.0D, 1.0D));
        assertVecEquals(foot, ClientKickState.chargeSmokeConeCenter(
                eye, foot, downward, 6.0D, 1.0D));
    }

    @Test
    void chargeSmokeParticlesTravelAtAConstantSpeedAndStopAtTheFoot() {
        Vec3 start = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 target = new Vec3(4.0D, 6.0D, 3.0D);
        Vec3 first = ChargeSmokeParticle.nextPositionAtConstantSpeed(start, target);
        Vec3 second = ChargeSmokeParticle.nextPositionAtConstantSpeed(first, target);

        assertEquals(ChargeSmokeParticle.TRAVEL_DISTANCE_PER_TICK,
                first.distanceTo(start), EPSILON);
        assertEquals(ChargeSmokeParticle.TRAVEL_DISTANCE_PER_TICK,
                second.distanceTo(first), EPSILON);
        assertTrue(first.distanceTo(target) < start.distanceTo(target));
        assertVecEquals(target, ChargeSmokeParticle.nextPositionAtConstantSpeed(
                target.add(0.1D, -0.1D, 0.0D), target));
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceToSqr(actual) <= EPSILON * EPSILON,
                () -> "Expected " + expected + " but got " + actual);
    }
}
