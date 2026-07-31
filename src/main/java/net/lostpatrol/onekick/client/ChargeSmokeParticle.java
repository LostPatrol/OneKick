package net.lostpatrol.onekick.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ChargeSmokeParticle extends TextureSheetParticle {
    private static SpriteSet registeredSprites;
    private final SpriteSet sprites;
    private final Vec3 start;
    private final Vec3 fallbackTarget;
    private final int targetEntityId;
    private final double startChargeProgress;
    private final boolean ringParticle;
    private final boolean trackedCharge;
    private final float baseAlpha;
    private float motionProgress;
    private int finishedTicks;

    private ChargeSmokeParticle(
            ClientLevel level,
            Vec3 start,
            Vec3 target,
            int targetEntityId,
            double startChargeProgress,
            boolean ringParticle,
            boolean trackedCharge,
            float size,
            SpriteSet sprites) {
        super(level, start.x, start.y, start.z);
        this.sprites = sprites;
        this.start = start;
        this.fallbackTarget = target;
        this.targetEntityId = targetEntityId;
        this.startChargeProgress = Mth.clamp(startChargeProgress, 0.0D, 1.0D);
        this.ringParticle = ringParticle;
        this.trackedCharge = trackedCharge;
        this.lifetime = trackedCharge ? Integer.MAX_VALUE : 12;
        this.hasPhysics = false;
        this.quadSize = size * (0.88F + this.random.nextFloat() * 0.24F);
        this.baseAlpha = 0.72F + this.random.nextFloat() * 0.18F;
        float shade = 0.86F + this.random.nextFloat() * 0.14F;
        this.setColor(shade, shade, shade);
        this.setAlpha(this.baseAlpha);
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.setSprite(this.sprites.get(0, 7));
    }

    static void spawnRing(
            ClientLevel level,
            Player player,
            Vec3 point,
            double chargeProgress,
            float size) {
        spawn(level, player, point, chargeProgress, true, size);
    }

    static void spawnFunnel(
            ClientLevel level,
            Player player,
            Vec3 point,
            double chargeProgress,
            float size) {
        spawn(level, player, point, chargeProgress, false, size);
    }

    private static void spawn(
            ClientLevel level,
            Player player,
            Vec3 point,
            double chargeProgress,
            boolean ringParticle,
            float size) {
        if (registeredSprites == null) {
            return;
        }
        Minecraft.getInstance().particleEngine.add(new ChargeSmokeParticle(
                level,
                point,
                ClientKickState.chargeSmokeFoot(player),
                player.getId(),
                chargeProgress,
                ringParticle,
                true,
                size,
                registeredSprites));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;

        double chargeProgress;
        Vec3 target = this.fallbackTarget;
        if (this.trackedCharge) {
            chargeProgress = ClientKickState.chargeSmokeProgress(this.targetEntityId);
            Entity entity = this.level.getEntity(this.targetEntityId);
            if (chargeProgress < 0.0D || !(entity instanceof Player player)) {
                this.remove();
                return;
            }
            target = ClientKickState.chargeSmokeFoot(player);
        } else {
            chargeProgress = Mth.clamp((double) this.age / this.lifetime, 0.0D, 1.0D);
        }

        double remainingRatio;
        if (this.ringParticle) {
            double startingRemaining = ClientKickState.chargeRingRemainingScale(
                    this.startChargeProgress);
            remainingRatio = startingRemaining <= 1.0E-6D
                    ? 0.0D
                    : ClientKickState.chargeRingRemainingScale(chargeProgress)
                            / startingRemaining;
            this.motionProgress = (float) Mth.clamp(1.0D - remainingRatio, 0.0D, 1.0D);
        } else {
            double localProgress = (chargeProgress - this.startChargeProgress)
                    / Math.max(1.0E-6D, 1.0D - this.startChargeProgress);
            this.motionProgress = (float) smoothstep(localProgress);
            remainingRatio = 1.0D - this.motionProgress;
        }

        Vec3 position = this.start.add(
                target.subtract(this.start).scale(this.motionProgress));
        this.setPos(position.x, position.y, position.z);
        this.setSprite(this.sprites.get(
                Mth.clamp(Mth.floor(this.motionProgress * 7.0F), 0, 7), 7));
        this.alpha = this.baseAlpha * (float) (0.34D + remainingRatio * 0.66D);

        if (chargeProgress >= 1.0D
                || !this.trackedCharge && this.motionProgress >= 1.0F) {
            this.setPos(target.x, target.y, target.z);
            this.alpha = this.baseAlpha * 0.18F;
            if (this.finishedTicks++ >= 1) {
                this.remove();
            }
        } else if (!this.trackedCharge && this.age >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float remaining = 1.0F - this.motionProgress;
        float scale = this.ringParticle
                ? 0.28F + remaining * 0.72F
                : 0.52F + remaining * 0.48F;
        return this.quadSize * scale;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static double smoothstep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            registeredSprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double targetOffsetX,
                double targetOffsetY,
                double targetOffsetZ) {
            Vec3 start = new Vec3(x, y, z);
            return new ChargeSmokeParticle(
                    level,
                    start,
                    start.add(targetOffsetX, targetOffsetY, targetOffsetZ),
                    -1,
                    0.0D,
                    false,
                    false,
                    0.22F,
                    this.sprites);
        }
    }
}
