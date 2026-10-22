/** Applies Afterburner's entity fire, continuous flight trail, and surface ignition. */
package net.lostpatrol.onekick.kick;

import net.lostpatrol.onekick.advancement.KickAdvancementTrigger;
import net.lostpatrol.onekick.advancement.ModCriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AfterburnerService {
    /** Each point keeps ordinary high-speed trails visually connected. */
    private static final double TRAIL_SPACING = 0.5D;
    /** Bound packet and world work for command-created extreme velocities. */
    private static final int MAX_TRAIL_SAMPLES = 2048;
    /** Ground ignition reaches at most four blocks below the entity's feet. */
    private static final int GROUND_REACH = 4;
    /** Ignite the immediate neighborhood of the flight's vertical projection. */
    private static final int GROUND_RADIUS = 1;
    /** Explosion surfaces have a one-in-three chance of catching fire. */
    private static final int EXPLOSION_IGNITION_CHANCE = 3;
    /** Each explosion visits at most this many voxels, including large delayed chains. */
    private static final int MAX_EXPLOSION_CANDIDATES = 4096;
    /** Uncharged kicks still burn briefly before the speed-dependent duration. */
    private static final float BASE_BURN_SECONDS = 2.0F;
    /** Every unit of kick strength adds two seconds of burning. */
    private static final double BURN_SECONDS_PER_SPEED = 2.0D;

    /** Utility class; all effects run on the server thread. */
    private AfterburnerService() {
    }

    /** Ignites a directly kicked entity for a duration based on the kick strength. */
    public static void igniteTarget(LivingEntity target, KickSnapshot snapshot) {
        if (snapshot.enchantments().afterburner() > 0) {
            target.igniteForSeconds(burnSeconds(snapshot.kickSpeed()));
        }
    }

    /** Samples the entire traveled segment so fast entities leave a visible burning trail. */
    public static void trail(
            ServerLevel level, LivingEntity target, Vec3 from, Vec3 to, KickSnapshot snapshot) {
        if (snapshot.enchantments().afterburner() <= 0) {
            return;
        }
        int samples = trailSampleCount(from.distanceTo(to));
        for (int index = 0; index <= samples; index++) {
            Vec3 feet = from.lerp(to, (double) index / samples);
            Vec3 center = feet.add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                    2, 0.12D, 0.12D, 0.12D, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z,
                    1, 0.16D, 0.16D, 0.16D, 0.01D);
            igniteGround(level, feet, snapshot);
        }
    }

    /** Randomly ignites surviving surfaces inside an unstable explosion's radius. */
    public static void igniteExplosion(
            ServerLevel level, Vec3 center, float radius, KickSnapshot snapshot) {
        if (snapshot.enchantments().afterburner() <= 0
                || !Float.isFinite(radius) || radius <= 0.0F) {
            return;
        }
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(center.y - radius));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(center.y + radius));
        if (minY > maxY) {
            return;
        }
        int minX = Mth.floor(center.x - radius);
        int maxX = Mth.floor(center.x + radius);
        int minZ = Mth.floor(center.z - radius);
        int maxZ = Mth.floor(center.z + radius);
        double width = (double) maxX - minX + 1.0D;
        double depth = (double) maxZ - minZ + 1.0D;
        int height = maxY - minY + 1;
        double radiusSquared = (double) radius * radius;
        if (width * height * depth <= MAX_EXPLOSION_CANDIDATES) {
            for (BlockPos position : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                tryIgniteExplosionPosition(level, position, center, radiusSquared, snapshot);
            }
            return;
        }
        // Full-height random sampling also reaches cave floors without a cubic scan per blast.
        for (int attempt = 0; attempt < MAX_EXPLOSION_CANDIDATES; attempt++) {
            BlockPos position = new BlockPos(
                    Mth.floor(minX + level.random.nextDouble() * width),
                    minY + level.random.nextInt(height),
                    Mth.floor(minZ + level.random.nextDouble() * depth));
            tryIgniteExplosionPosition(level, position, center, radiusSquared, snapshot);
        }
    }

    /** Restricts random ignition to real fire positions inside the requested sphere. */
    private static void tryIgniteExplosionPosition(
            ServerLevel level, BlockPos position, Vec3 center, double radiusSquared,
            KickSnapshot snapshot) {
        if (position.distToCenterSqr(center) <= radiusSquared
                && level.random.nextInt(EXPLOSION_IGNITION_CHANCE) == 0) {
            tryIgnite(level, position, snapshot);
        }
    }

    /** Keeps burn time proportional to finite, nonnegative kick strength. */
    static float burnSeconds(double kickSpeed) {
        double speed = Double.isFinite(kickSpeed) ? Math.max(0.0D, kickSpeed) : 0.0D;
        return (float) Math.min(Float.MAX_VALUE,
                BASE_BURN_SECONDS + speed * BURN_SECONDS_PER_SPEED);
    }

    /** Bounds extreme command velocities while retaining both ends of the trail. */
    static int trailSampleCount(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return 1;
        }
        return (int) Math.min(MAX_TRAIL_SAMPLES, Math.ceil(distance / TRAIL_SPACING));
    }

    /** Searches down from the projection and stops at the first obstructing surface. */
    private static void igniteGround(ServerLevel level, Vec3 feet, KickSnapshot snapshot) {
        BlockPos projection = BlockPos.containing(feet);
        for (int x = -GROUND_RADIUS; x <= GROUND_RADIUS; x++) {
            for (int z = -GROUND_RADIUS; z <= GROUND_RADIUS; z++) {
                for (int depth = 0; depth <= GROUND_REACH; depth++) {
                    BlockPos position = projection.offset(x, -depth, z);
                    if (!level.hasChunkAt(position) || level.isOutsideBuildHeight(position)) {
                        break;
                    }
                    // Never search through a roof, liquid, or already burning surface.
                    if (!level.getBlockState(position).isAir()) {
                        break;
                    }
                    if (tryIgnite(level, position, snapshot)) {
                        break;
                    }
                }
            }
        }
    }

    /** Counts only newly placed fire that has a real supporting surface. */
    private static boolean tryIgnite(ServerLevel level, BlockPos position, KickSnapshot snapshot) {
        if (!level.hasChunkAt(position) || level.isOutsideBuildHeight(position)
                || !level.getBlockState(position).isAir()) {
            return false;
        }
        BlockPos supportPosition = position.below();
        BlockState support = level.getBlockState(supportPosition);
        if (!support.isFaceSturdy(level, supportPosition, Direction.UP)
                && !support.isFlammable(level, supportPosition, Direction.UP)) {
            return false;
        }
        BlockState fire = BaseFireBlock.getState(level, position);
        if (!fire.canSurvive(level, position) || !level.setBlock(position, fire, Block.UPDATE_ALL)) {
            return false;
        }
        if (snapshot.progress().recordIgnitedBlock(position)) {
            ModCriteriaTriggers.trigger(level, snapshot, KickAdvancementTrigger.Event.TRAILBLAZER);
        }
        return true;
    }
}
