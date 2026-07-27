package net.lostpatrol.onekick.client;

import net.lostpatrol.onekick.kick.KickMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class DisintegrationSmokeParticle extends TextureSheetParticle {
    private static final int FADE_TICKS = 30;
    private final float baseAlpha;

    private DisintegrationSmokeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed) {
        super(level, x, y, z);
        boolean primaryCloud = this.random.nextFloat() < 0.82F;
        this.scale(primaryCloud
                ? 6.2F + this.random.nextFloat() * 3.8F
                : 2.8F + this.random.nextFloat() * 2.2F);
        this.setSize(0.25F, 0.25F);
        this.lifetime = this.random.nextInt(25) + 40;
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.baseAlpha = 0.54F + this.random.nextFloat() * 0.20F;
        float shade = primaryCloud
                ? 0.38F + this.random.nextFloat() * 0.20F
                : 0.64F + this.random.nextFloat() * 0.20F;
        this.setColor(shade, shade, shade);
        this.setAlpha(this.baseAlpha);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime || this.alpha <= 0.0F) {
            this.remove();
            return;
        }
        this.xd += this.random.nextFloat() / 5000.0F
                * (this.random.nextBoolean() ? 1.0D : -1.0D);
        this.zd += this.random.nextFloat() / 5000.0F
                * (this.random.nextBoolean() ? 1.0D : -1.0D);
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.985D;
        this.yd *= 0.86D;
        this.zd *= 0.985D;
        this.alpha = this.baseAlpha * KickMath.smoothFadeScale(
                this.lifetime - this.age, FADE_TICKS);
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
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            DisintegrationSmokeParticle particle = new DisintegrationSmokeParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
