package net.lostpatrol.onekick.client;

final class KickAnimation {
    static final float KICK_DURATION_TICKS = 9.0F;
    static final Pose READY_POSE = new Pose(-1.20F, 0.0F, 0.0F);
    private static final Pose STRIKE_POSE = new Pose(-1.52F, 0.0F, 0.0F);
    private static final float STRIKE_TICKS = 3.0F;

    private KickAnimation() {
    }

    static Pose chargePose(int chargeLevel, double ageInTicks) {
        int level = Math.max(1, Math.min(5, chargeLevel));
        double levelProgress = (level - 1) / 4.0D;
        double circleRadius = lerp(levelProgress, Math.toRadians(6.0D), Math.toRadians(14.0D));
        double angularSpeed = lerp(levelProgress, 0.15D, 1.025D);
        double phase = ageInTicks * angularSpeed;
        double baseY = Math.cos(READY_POSE.xRot);
        double baseZ = Math.sin(READY_POSE.xRot);
        double radial = Math.sin(circleRadius);
        double directionX = Math.cos(phase) * radial;
        double directionY = baseY * Math.cos(circleRadius)
                - baseZ * Math.sin(phase) * radial;
        double directionZ = baseZ * Math.cos(circleRadius)
                + baseY * Math.sin(phase) * radial;
        return poseFromDirection(directionX, directionY, directionZ);
    }

    static Pose chargePose(
            int chargeLevel, int kineticOverloadLevel, double ageInTicks) {
        return kineticOverloadLevel > 0
                ? kineticOverloadChargePose(chargeLevel, ageInTicks)
                : chargePose(chargeLevel, ageInTicks);
    }

    static Pose kineticOverloadChargePose(int chargeLevel, double ageInTicks) {
        double amplitude = kineticOverloadJitterAmplitude(chargeLevel);
        double lateral = amplitude * (
                Math.sin(ageInTicks * 4.7D)
                        + Math.sin(ageInTicks * 11.3D + 0.7D) * 0.35D);
        double vertical = amplitude * (
                Math.sin(ageInTicks * 6.1D + 1.4D)
                        + Math.sin(ageInTicks * 13.7D) * 0.30D);
        double baseY = Math.cos(READY_POSE.xRot);
        double baseZ = Math.sin(READY_POSE.xRot);
        return poseFromDirection(
                lateral,
                baseY - baseZ * vertical,
                baseZ + baseY * vertical);
    }

    static Pose kickPose(Pose start, float elapsedTicks) {
        if (elapsedTicks <= STRIKE_TICKS) {
            return interpolateDirection(
                    start, STRIKE_POSE, smoothstep(elapsedTicks / STRIKE_TICKS));
        }
        float recovery = (elapsedTicks - STRIKE_TICKS)
                / (KICK_DURATION_TICKS - STRIKE_TICKS);
        return interpolateDirection(STRIKE_POSE, Pose.NEUTRAL, smoothstep(recovery));
    }

    static float firstPersonDepth(float elapsedTicks) {
        if (elapsedTicks <= STRIKE_TICKS) {
            return lerp(smoothstep(elapsedTicks / STRIKE_TICKS), -0.28F, -0.40F);
        }
        float recovery = (elapsedTicks - STRIKE_TICKS)
                / (KICK_DURATION_TICKS - STRIKE_TICKS);
        return lerp(smoothstep(recovery), -0.40F, -0.28F);
    }

    static double chargeRadius(int chargeLevel) {
        int level = Math.max(1, Math.min(5, chargeLevel));
        return lerp((level - 1) / 4.0D, Math.toRadians(6.0D), Math.toRadians(14.0D));
    }

    static double chargeAngularSpeed(int chargeLevel) {
        int level = Math.max(1, Math.min(5, chargeLevel));
        return lerp((level - 1) / 4.0D, 0.15D, 1.025D);
    }

    static double kineticOverloadJitterAmplitude(int chargeLevel) {
        int level = Math.max(1, Math.min(5, chargeLevel));
        return lerp((level - 1) / 4.0D, Math.toRadians(1.5D), Math.toRadians(3.0D));
    }

    private static Pose interpolateDirection(Pose start, Pose end, float progress) {
        if (progress <= 0.0F) {
            return start;
        }
        if (progress >= 1.0F) {
            return end;
        }
        double[] startDirection = direction(start);
        double[] endDirection = direction(end);
        return poseFromDirection(
                lerp(progress, startDirection[0], endDirection[0]),
                lerp(progress, startDirection[1], endDirection[1]),
                lerp(progress, startDirection[2], endDirection[2]));
    }

    private static Pose poseFromDirection(double x, double y, double z) {
        double inverseLength = 1.0D / Math.sqrt(x * x + y * y + z * z);
        double directionX = x * inverseLength;
        double directionY = y * inverseLength;
        double directionZ = z * inverseLength;
        double rightX = 1.0D - directionX * directionX;
        double rightY = -directionX * directionY;
        double rightZ = -directionX * directionZ;
        double rightInverseLength = 1.0D
                / Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        rightX *= rightInverseLength;
        rightY *= rightInverseLength;
        rightZ *= rightInverseLength;
        double forwardZ = rightX * directionY - rightY * directionX;
        return new Pose(
                (float) Math.atan2(directionZ, forwardZ),
                (float) Math.asin(clamp(-rightZ, -1.0D, 1.0D)),
                (float) Math.atan2(rightY, rightX));
    }

    private static double[] direction(Pose pose) {
        double sinX = Math.sin(pose.xRot);
        double cosX = Math.cos(pose.xRot);
        double sinY = Math.sin(pose.yRot);
        double cosY = Math.cos(pose.yRot);
        double sinZ = Math.sin(pose.zRot);
        double cosZ = Math.cos(pose.zRot);
        return new double[]{
                cosZ * sinY * sinX - sinZ * cosX,
                sinZ * sinY * sinX + cosZ * cosX,
                cosY * sinX
        };
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float progress, float start, float end) {
        return start + progress * (end - start);
    }

    private static double lerp(double progress, double start, double end) {
        return start + progress * (end - start);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Pose(float xRot, float yRot, float zRot) {
        static final Pose NEUTRAL = new Pose(0.0F, 0.0F, 0.0F);
    }
}
