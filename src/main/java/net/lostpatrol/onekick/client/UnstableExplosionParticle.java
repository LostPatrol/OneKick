package net.lostpatrol.onekick.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class UnstableExplosionParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private UnstableExplosionParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double animationProgress,
            double visualScale,
            SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 6 + this.random.nextInt(4);
        float shade = this.random.nextFloat() * 0.6F + 0.4F;
        this.setColor(shade, shade, shade);
        this.quadSize = (float) (2.0D * Math.max(1.0D, visualScale)
                * (1.0D - Math.max(0.0D, Math.min(1.0D, animationProgress)) * 0.5D));
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
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
                double animationProgress,
                double visualScale,
                double ignored) {
            return new UnstableExplosionParticle(
                    level, x, y, z, animationProgress, visualScale, this.sprites);
        }
    }
}
