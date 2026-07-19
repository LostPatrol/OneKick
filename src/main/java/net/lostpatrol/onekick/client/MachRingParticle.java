package net.lostpatrol.onekick.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class MachRingParticle extends TextureSheetParticle {
    private static final int SMOKE_GROWTH_TICKS = 24;
    private final SpriteSet sprites;
    private final int fadeTicks;
    private final int smokeHalfCycleTicks;
    private final int smokeAnimationOffset;
    private final float baseAlpha;
    private final float rotationSpeed;
    private final float sizePulseOffset;

    private MachRingParticle(
            ClientLevel level, double x, double y, double z, int lifetime, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = Math.max(20, Math.min(200, lifetime));
        this.fadeTicks = Math.max(10, Math.min(24, this.lifetime / 6));
        this.smokeHalfCycleTicks = 22 + this.random.nextInt(13);
        this.smokeAnimationOffset = this.random.nextInt(this.smokeHalfCycleTicks * 2);
        this.quadSize = 0.54F + this.random.nextFloat() * 0.20F;
        this.baseAlpha = 0.42F + this.random.nextFloat() * 0.14F;
        this.hasPhysics = false;
        this.friction = 0.99F;
        this.xd = (this.random.nextDouble() - 0.5D) * 0.009D;
        this.yd = 0.0015D + this.random.nextDouble() * 0.0035D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.009D;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.rotationSpeed = (this.random.nextFloat() - 0.5F) * 0.012F;
        this.sizePulseOffset = this.random.nextFloat() * Mth.TWO_PI;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.setAlpha(this.baseAlpha);
        this.updateSmokeSprite();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateSmokeSprite();
        this.oRoll = this.roll;
        this.roll += this.rotationSpeed;
        if (this.age > this.lifetime - this.fadeTicks) {
            this.alpha = this.baseAlpha * Math.max(0.0F,
                    (float) (this.lifetime - this.age) / this.fadeTicks);
        } else {
            this.alpha = this.baseAlpha;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float growth = Mth.clamp(
                ((float) this.age + partialTick) / SMOKE_GROWTH_TICKS, 0.0F, 1.0F);
        float pulse = 0.975F + Mth.sin(
                ((float) this.age + partialTick) * 0.11F + this.sizePulseOffset) * 0.025F;
        return this.quadSize * (0.7F + growth * 0.38F) * pulse;
    }

    private void updateSmokeSprite() {
        int cycleTicks = this.smokeHalfCycleTicks * 2;
        int phase = (this.age + this.smokeAnimationOffset) % cycleTicks;
        int animationAge = phase <= this.smokeHalfCycleTicks
                ? phase
                : cycleTicks - phase;
        this.setSprite(this.sprites.get(animationAge, this.smokeHalfCycleTicks));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
                double ignoredYSpeed,
                double ignoredZSpeed) {
            return new MachRingParticle(
                    level, x, y, z, (int) Math.round(lifetimeTicks), this.sprites);
        }
    }
}
