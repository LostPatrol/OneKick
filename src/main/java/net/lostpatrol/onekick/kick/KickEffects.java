package net.lostpatrol.onekick.kick;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

}
