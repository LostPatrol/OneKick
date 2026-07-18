package net.lostpatrol.onekick.kick;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class KickEffects {
    private KickEffects() {
    }

    public static void playKick(ServerPlayer player, float charge) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 0.9F, 0.85F + level.random.nextFloat() * 0.2F);
        if (charge < 1.0F) {
            return;
        }
        Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.9D)).add(0.0D, -0.65D, 0.0D);
        if (charge < 2.5F) {
            level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 8,
                    0.18D, 0.12D, 0.18D, 0.025D);
        } else if (charge < 4.5F) {
            level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 18,
                    0.28D, 0.18D, 0.28D, 0.05D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    origin.x, origin.y, origin.z, 16,
                    0.3D, 0.2D, 0.3D, 0.12D);
        } else {
            level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 4,
                    0.3D, 0.2D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, origin.x, origin.y, origin.z, 24,
                    0.4D, 0.25D, 0.4D, 0.06D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    origin.x, origin.y, origin.z, 28,
                    0.45D, 0.3D, 0.45D, 0.2D);
        }
    }

    public static void emitCharge(ServerPlayer player, float charge, float maximum, int levelValue) {
        if ((player.tickCount + player.getId()) % 3 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double progress = maximum <= 0.0F ? 0.0D : Math.min(1.0D, Math.max(0.0D, charge / maximum));
        double angle = (player.tickCount * (0.18D + levelValue * 0.02D)) % (Math.PI * 2.0D);
        double radius = 0.55D + progress * 0.45D;
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        double y = player.getY() + 0.25D + progress * 1.3D;
        level.sendParticles(levelValue >= 4 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT,
                x, y, z, 2 + levelValue / 2, 0.04D, 0.04D, 0.04D, 0.015D);
        if (charge >= maximum && maximum > 0.0F && player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D, player.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void emitTrail(
            ServerLevel level, LivingEntity entity, double speed, int age, boolean emitMachRing) {
        if (speed < 0.8D) {
            return;
        }
        Vec3 position = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        int fireworkCount = speed < 1.6D ? 1 : speed < 2.8D ? 4 : 8;
        if (speed >= 1.6D || (age & 1) == 0) {
            level.sendParticles(ParticleTypes.FIREWORK, position.x, position.y, position.z, fireworkCount,
                    entity.getBbWidth() * 0.25D, entity.getBbHeight() * 0.2D,
                    entity.getBbWidth() * 0.25D, 0.03D);
        }
        if (speed >= 2.8D) {
            level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 5,
                    0.2D, 0.25D, 0.2D, 0.02D);
        }
        if (speed >= 3.35D) {
            level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 4,
                    0.2D, 0.25D, 0.2D, 0.02D);
            level.sendParticles(ParticleTypes.SPLASH, position.x, position.y, position.z, 8,
                    entity.getBbWidth() * 0.45D, entity.getBbHeight() * 0.45D,
                    entity.getBbWidth() * 0.45D, 0.08D);
            if ((age & 3) == 0) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        position.x, position.y, position.z, 2, 0.18D, 0.25D, 0.18D, 0.015D);
            }
            if (emitMachRing) {
                emitMachRing(level, position, entity.getDeltaMovement());
            }
        }
    }

    private static void emitMachRing(ServerLevel level, Vec3 center, Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-5D) {
            return;
        }
        Vec3 direction = movement.normalize();
        Vec3 first = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (first.lengthSqr() < 1.0E-4D) {
            first = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        first = first.normalize();
        Vec3 second = direction.cross(first).normalize();
        Vec3 ringCenter = center.subtract(direction.scale(0.8D));
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            Vec3 point = ringCenter.add(first.scale(Math.cos(angle) * 0.85D))
                    .add(second.scale(Math.sin(angle) * 0.85D));
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
