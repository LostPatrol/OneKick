package net.lostpatrol.onekick.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KickAnimationTest {
    @Test
    void chargeCircleGrowsAndAcceleratesAtEveryEnchantmentLevel() {
        assertEquals(Math.toRadians(6.0D), KickAnimation.chargeRadius(1), 1.0E-9D);
        assertEquals(Math.toRadians(14.0D), KickAnimation.chargeRadius(5), 1.0E-9D);
        assertEquals(0.15D, KickAnimation.chargeAngularSpeed(1), 1.0E-9D);
        assertEquals(1.025D, KickAnimation.chargeAngularSpeed(5), 1.0E-9D);
        for (int level = 2; level <= 5; level++) {
            assertTrue(KickAnimation.chargeRadius(level)
                    > KickAnimation.chargeRadius(level - 1));
            assertTrue(KickAnimation.chargeAngularSpeed(level)
                    > KickAnimation.chargeAngularSpeed(level - 1));
        }
    }

    @Test
    void chargePoseTracesAConstantRadiusCircleAroundReadyDirection() {
        double expectedAngle = KickAnimation.chargeRadius(5);
        double[] center = direction(KickAnimation.READY_POSE);
        for (int sample = 0; sample < 360; sample++) {
            double time = sample * Math.PI * 2.0D
                    / (360.0D * KickAnimation.chargeAngularSpeed(5));
            double[] point = direction(KickAnimation.chargePose(5, time));
            assertEquals(Math.cos(expectedAngle), dot(center, point), 1.0E-6D);
            assertEquals(1.0D, Math.sqrt(dot(point, point)), 1.0E-6D);
        }
    }

    @Test
    void chargePoseKeepsTheLegCrossSectionFromRollingAroundItsAxis() {
        for (int level = 1; level <= 5; level++) {
            for (int sample = 0; sample < 360; sample++) {
                double time = sample * Math.PI * 2.0D
                        / (360.0D * KickAnimation.chargeAngularSpeed(level));
                assertNoRoll(KickAnimation.chargePose(level, time));
            }
        }
    }

    @Test
    void kineticOverloadReplacesTheChargeCircleWithRapidSmallJitter() {
        assertEquals(Math.toRadians(1.5D),
                KickAnimation.kineticOverloadJitterAmplitude(1), 1.0E-9D);
        assertEquals(Math.toRadians(3.0D),
                KickAnimation.kineticOverloadJitterAmplitude(5), 1.0E-9D);
        assertEquals(KickAnimation.chargePose(5, 3.25D),
                KickAnimation.chargePose(5, 0, 3.25D));

        double[] ready = direction(KickAnimation.READY_POSE);
        double[] previous = null;
        int visiblyDifferentSteps = 0;
        for (int tick = 0; tick < 40; tick++) {
            KickAnimation.Pose pose = KickAnimation.chargePose(5, 3, tick);
            double[] current = direction(pose);
            double angleFromReady = Math.acos(
                    Math.max(-1.0D, Math.min(1.0D, dot(ready, current))));
            assertTrue(angleFromReady < Math.toRadians(7.0D));
            assertNoRoll(pose);
            if (previous != null) {
                double step = Math.acos(
                        Math.max(-1.0D, Math.min(1.0D, dot(previous, current))));
                if (step > Math.toRadians(1.0D)) {
                    visiblyDifferentSteps++;
                }
            }
            previous = current;
        }
        assertTrue(visiblyDifferentSteps >= 20,
                "Kinetic Overload charge pose did not jitter on most ticks");
    }

    @Test
    void chargedKickStartsAtTheReleasePose() {
        KickAnimation.Pose release = KickAnimation.chargePose(5, 12.75D);
        assertEquals(release, KickAnimation.kickPose(release, 0.0F));
    }

    @Test
    void kickTransitionDoesNotAddAxialRoll() {
        KickAnimation.Pose release = KickAnimation.chargePose(5, 12.75D);
        for (int sample = 0; sample <= 90; sample++) {
            float elapsed = KickAnimation.KICK_DURATION_TICKS * sample / 90.0F;
            assertNoRoll(KickAnimation.kickPose(release, elapsed));
        }
    }

    private static void assertNoRoll(KickAnimation.Pose pose) {
        double[] leg = direction(pose);
        double[] expectedRight = normalize(new double[]{
                1.0D - leg[0] * leg[0],
                -leg[0] * leg[1],
                -leg[0] * leg[2]
        });
        double[] actualRight = right(pose);
        assertEquals(expectedRight[0], actualRight[0], 1.0E-6D);
        assertEquals(expectedRight[1], actualRight[1], 1.0E-6D);
        assertEquals(expectedRight[2], actualRight[2], 1.0E-6D);
    }

    private static double[] direction(KickAnimation.Pose pose) {
        double sinX = Math.sin(pose.xRot());
        double cosX = Math.cos(pose.xRot());
        double sinY = Math.sin(pose.yRot());
        double cosY = Math.cos(pose.yRot());
        double sinZ = Math.sin(pose.zRot());
        double cosZ = Math.cos(pose.zRot());
        return new double[]{
                cosZ * sinY * sinX - sinZ * cosX,
                sinZ * sinY * sinX + cosZ * cosX,
                cosY * sinX
        };
    }

    private static double[] right(KickAnimation.Pose pose) {
        double cosY = Math.cos(pose.yRot());
        return new double[]{
                Math.cos(pose.zRot()) * cosY,
                Math.sin(pose.zRot()) * cosY,
                -Math.sin(pose.yRot())
        };
    }

    private static double[] normalize(double[] vector) {
        double inverseLength = 1.0D / Math.sqrt(dot(vector, vector));
        return new double[]{
                vector[0] * inverseLength,
                vector[1] * inverseLength,
                vector[2] * inverseLength
        };
    }

    private static double dot(double[] left, double[] right) {
        return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
    }
}
