package net.lostpatrol.onekick.client;

import net.lostpatrol.onekick.kick.KickMath;
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
    private final float sizePulseOffset;

    private MachTrailParticle(
            ClientLevel level, double x, double y, double z, int lifetime, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = Mth.clamp(lifetime, 40, 300);
        this.fadeTicks = Math.max(32, Math.round(this.lifetime * 0.5F));
        this.animationHalfCycleTicks = 20 + this.random.nextInt(13);
        this.animationOffset = this.random.nextInt(this.animationHalfCycleTicks * 2);
        this.quadSize = 0.24F + this.random.nextFloat() * 0.09F;
        this.baseAlpha = 0.54F + this.random.nextFloat() * 0.18F;
        this.hasPhysics = false;
        this.friction = 0.99F;
        this.xd = (this.random.nextDouble() - 0.5D) * 0.003D;
        this.yd = 0.0005D + this.random.nextDouble() * 0.0015D;
        this.zd = (this.random.nextDouble() - 0.5D) * 0.003D;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.rotationSpeed = (this.random.nextFloat() - 0.5F) * 0.009F;
        this.sizePulseOffset = this.random.nextFloat() * Mth.TWO_PI;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.setAlpha(this.baseAlpha);
        this.updateSprite();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateSprite();
        this.oRoll = this.roll;
        this.roll += this.rotationSpeed;
        this.alpha = this.baseAlpha * KickMath.smoothFadeScale(
                this.lifetime - this.age, this.fadeTicks);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float growth = Mth.clamp(
                ((float) this.age + partialTick) / GROWTH_TICKS, 0.0F, 1.0F);
        float pulse = 0.97F + Mth.sin(
                ((float) this.age + partialTick) * 0.12F + this.sizePulseOffset) * 0.03F;
        return this.quadSize * (0.72F + growth * 0.34F) * pulse;
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
            return new MachTrailParticle(
                    level, x, y, z, (int) Math.round(lifetimeTicks), this.sprites);
        }
    }
}
