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
    void chargeSmokeShellGrowsWithTheChargeEnchantmentLevel() {
        assertEquals(1.12D, ClientKickState.chargeSmokeMaximumRadius(1), EPSILON);
        assertEquals(2.4D, ClientKickState.chargeSmokeMaximumRadius(5), EPSILON);
        for (int level = 2; level <= 5; level++) {
            assertTrue(ClientKickState.chargeSmokeMaximumRadius(level)
                    > ClientKickState.chargeSmokeMaximumRadius(level - 1));
        }
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceToSqr(actual) <= EPSILON * EPSILON,
                () -> "Expected " + expected + " but got " + actual);
    }
}
