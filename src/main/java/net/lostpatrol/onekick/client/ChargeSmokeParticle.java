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
    static final double TRAVEL_DISTANCE_PER_TICK = 0.28D;
    private static SpriteSet registeredSprites;
    private final SpriteSet sprites;
    private final Vec3 fallbackTarget;
    private final int targetEntityId;
    private final boolean trackedCharge;
    private final float baseAlpha;
    private final double initialDistance;
    private float motionProgress;

    private ChargeSmokeParticle(
            ClientLevel level,
            Vec3 start,
            Vec3 target,
            int targetEntityId,
            boolean trackedCharge,
            float size,
            SpriteSet sprites) {
        super(level, start.x, start.y, start.z);
        this.sprites = sprites;
        this.fallbackTarget = target;
        this.targetEntityId = targetEntityId;
        this.trackedCharge = trackedCharge;
        this.initialDistance = Math.max(
                TRAVEL_DISTANCE_PER_TICK, start.distanceTo(target));
        this.lifetime = trackedCharge
                ? Integer.MAX_VALUE
                : Mth.ceil(this.initialDistance / TRAVEL_DISTANCE_PER_TICK) + 1;
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

    static void spawnFunnel(
            ClientLevel level,
            Player player,
            Vec3 point,
            float size) {
        if (registeredSprites == null) {
            return;
        }
        Minecraft.getInstance().particleEngine.add(new ChargeSmokeParticle(
                level,
                point,
                ClientKickState.chargeSmokeFoot(player),
                player.getId(),
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

        Vec3 target = this.fallbackTarget;
        if (this.trackedCharge) {
            Entity entity = this.level.getEntity(this.targetEntityId);
            if (!ClientKickState.isChargeSmokeActive(this.targetEntityId)
                    || !(entity instanceof Player player)) {
                this.remove();
                return;
            }
            target = ClientKickState.chargeSmokeFoot(player);
        }

        Vec3 next = nextPositionAtConstantSpeed(new Vec3(this.x, this.y, this.z), target);
        double remainingDistance = next.distanceTo(target);
        double remainingRatio = Mth.clamp(
                remainingDistance / this.initialDistance, 0.0D, 1.0D);
        this.motionProgress = (float) (1.0D - remainingRatio);
        this.setPos(next.x, next.y, next.z);
        this.setSprite(this.sprites.get(
                Mth.clamp(Mth.floor(this.motionProgress * 7.0F), 0, 7), 7));
        this.alpha = this.baseAlpha * (float) (0.34D + remainingRatio * 0.66D);

        if (remainingDistance <= 1.0E-6D || this.age >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float remaining = 1.0F - this.motionProgress;
        float scale = 0.52F + remaining * 0.48F;
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

    static Vec3 nextPositionAtConstantSpeed(Vec3 current, Vec3 target) {
        Vec3 offset = target.subtract(current);
        double distance = offset.length();
        if (distance <= TRAVEL_DISTANCE_PER_TICK) {
            return target;
        }
        return current.add(offset.scale(TRAVEL_DISTANCE_PER_TICK / distance));
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
                    false,
                    0.22F,
                    this.sprites);
        }
    }
}
