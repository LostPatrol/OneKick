package net.lostpatrol.onekick.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class MachTrailParticle extends TextureSheetParticle {
    private static final int GROWTH_TICKS = 18;
    private final SpriteSet sprites;
    private final int fadeTicks;
    private final int animationHalfCycleTicks;
    private final int animationOffset;
    private final float baseAlpha;
    private final float rotationSpeed;

    private MachTrailParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            int lifetime,
            float size,
            SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = Mth.clamp(lifetime, 30, 160);
        this.fadeTicks = Math.max(12, this.lifetime / 4);
        this.animationHalfCycleTicks = 18 + this.random.nextInt(13);
        this.animationOffset = this.random.nextInt(this.animationHalfCycleTicks * 2);
        this.quadSize = Mth.clamp(size, 0.35F, 1.4F)
                * (0.82F + this.random.nextFloat() * 0.36F);
        this.baseAlpha = 0.46F + this.random.nextFloat() * 0.18F;
        this.hasPhysics = false;
        this.friction = 0.985F;
        this.xd = (this.random.nextDouble() - 0.5D) * 0.006D;
        this.yd = 0.001D + this.random.nextDouble() * 0.0025D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.006D;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.rotationSpeed = (this.random.nextFloat() - 0.5F) * 0.01F;
        float shade = 0.10F + this.random.nextFloat() * 0.16F;
        this.setColor(shade * 0.92F, shade * 0.95F, shade);
        this.setAlpha(this.baseAlpha);
        this.updateSprite();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateSprite();
        this.oRoll = this.roll;
        this.roll += this.rotationSpeed;
        if (this.age > this.lifetime - this.fadeTicks) {
            this.alpha = this.baseAlpha * Math.max(0.0F,
                    (float) (this.lifetime - this.age) / this.fadeTicks);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float growth = Mth.clamp(
                ((float) this.age + partialTick) / GROWTH_TICKS, 0.0F, 1.0F);
        return this.quadSize * (0.72F + growth * 0.32F);
    }

    private void updateSprite() {
        int cycleTicks = this.animationHalfCycleTicks * 2;
        int phase = (this.age + this.animationOffset) % cycleTicks;
        int animationAge = phase <= this.animationHalfCycleTicks
                ? phase
                : cycleTicks - phase;
        this.setSprite(this.sprites.get(animationAge, this.animationHalfCycleTicks));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double lifetimeTicks,
                double size,
                double ignoredZSpeed) {
            return new MachTrailParticle(
                    level, x, y, z, (int) Math.round(lifetimeTicks), (float) size, this.sprites);
        }
    }
}
