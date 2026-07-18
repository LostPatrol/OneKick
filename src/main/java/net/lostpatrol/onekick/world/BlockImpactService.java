package net.lostpatrol.onekick.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.kick.KickEnchantments;
import net.lostpatrol.onekick.kick.KickMath;
import net.lostpatrol.onekick.kick.KickSnapshot;
import net.lostpatrol.onekick.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

public final class BlockImpactService {
    private static final List<ScheduledExplosion> SCHEDULED_EXPLOSIONS = new ArrayList<>();

    private BlockImpactService() {
    }

    public static void handleImpact(
            ServerLevel level,
            LivingEntity impactedEntity,
            Vec3 impact,
            Vec3 movement,
            KickSnapshot snapshot) {
        KickEnchantments enchantments = snapshot.enchantments();
        boolean hasDisintegration = enchantments.disintegration() > 0;
        boolean hasExplosion = enchantments.unstableCollision() > 0;
        boolean tripleSynergy = hasDisintegration && hasExplosion && enchantments.kineticOverload() > 0;

        if (tripleSynergy) {
            destroyCylinder(level, impact, movement, snapshot, true);
            scheduleExplosionChain(level, impactedEntity, impact, movement, snapshot);
            return;
        }
        if (hasExplosion) {
            float power = KickMath.explosionPower(snapshot.kickSpeed(), enchantments.unstableCollision());
            explode(level, impactedEntity, impact, movement, power, hasDisintegration, snapshot);
            return;
        }
        if (hasDisintegration) {
            destroyCylinder(level, impact, movement, snapshot, false);
        }
    }

    public static void tick(MinecraftServer server) {
        long gameTick = server.getTickCount();
        Iterator<ScheduledExplosion> iterator = SCHEDULED_EXPLOSIONS.iterator();
        while (iterator.hasNext()) {
            ScheduledExplosion scheduled = iterator.next();
            if (scheduled.dueTick() > gameTick) {
                continue;
            }
            iterator.remove();
            ServerLevel level = server.getLevel(scheduled.dimension());
            if (level != null && level.hasChunkAt(BlockPos.containing(scheduled.position()))) {
                Entity excluded = level.getEntity(scheduled.excludedEntityId());
                explode(level, excluded, scheduled.position(), scheduled.movement(),
                        scheduled.power(), false, scheduled.snapshot());
            }
        }
    }

    public static void clear() {
        SCHEDULED_EXPLOSIONS.clear();
    }

    private static void scheduleExplosionChain(
            ServerLevel level,
            LivingEntity impactedEntity,
            Vec3 impact,
            Vec3 movement,
            KickSnapshot snapshot) {
        Vec3 axis = safeDirection(movement);
        double depth = KickMath.disintegrationDepth(
                snapshot.kickSpeed(), snapshot.enchantments().kineticOverload());
        double radius = KickMath.disintegrationRadius(snapshot.kickSpeed(), true);
        int count = Math.max(2, Mth.ceil(depth / 1.75D));
        float power = (float) Math.max(1.8D, radius / 1.35D);
        long now = level.getServer().getTickCount();
        for (int i = 0; i < count; i++) {
            double distance = count == 1 ? 0.0D : depth * i / (count - 1.0D);
            SCHEDULED_EXPLOSIONS.add(new ScheduledExplosion(
                    level.dimension(), now + i * 2L, impact.add(axis.scale(distance)),
                    movement, power, impactedEntity.getUUID(), snapshot));
        }
    }

    private static void explode(
            ServerLevel level,
            @Nullable Entity excludedEntity,
            Vec3 position,
            Vec3 movement,
            float power,
            boolean destroyBlocks,
            KickSnapshot snapshot) {
        ServerPlayer attacker = snapshot.attacker(level);
        DamageSource damageSource = attacker == null
                ? level.damageSources().explosion(null, null)
                : level.damageSources().explosion(attacker, attacker);
        Explosion visualExplosion = new Explosion(level, excludedEntity, damageSource, null,
                position.x, position.y, position.z, power, false, Explosion.BlockInteraction.KEEP);
        if (ForgeEventFactory.onExplosionStart(level, visualExplosion)) {
            return;
        }
        visualExplosion.explode();
        visualExplosion.finalizeExplosion(false);
        sendExplosionPacket(level, position, power, visualExplosion);
        if (destroyBlocks) {
            destroySphere(level, position, movement, power * 1.35D, snapshot);
        }
    }

    private static void sendExplosionPacket(
            ServerLevel level, Vec3 position, float power, Explosion explosion) {
        explosion.clearToBlow();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position.x, position.y, position.z) < 4096.0D) {
                player.connection.send(new ClientboundExplodePacket(
                        position.x, position.y, position.z, power,
                        explosion.getToBlow(), explosion.getHitPlayers().get(player)));
            }
        }
    }

    private static void destroyCylinder(
            ServerLevel level, Vec3 impact, Vec3 movement, KickSnapshot snapshot, boolean tripleSynergy) {
        Vec3 axis = safeDirection(movement);
        double radius = KickMath.disintegrationRadius(snapshot.kickSpeed(), tripleSynergy);
        double depth = KickMath.disintegrationDepth(
                snapshot.kickSpeed(), snapshot.enchantments().kineticOverload());
        Vec3 end = impact.add(axis.scale(depth));
        int minX = Mth.floor(Math.min(impact.x, end.x) - radius);
        int minY = Mth.floor(Math.min(impact.y, end.y) - radius);
        int minZ = Mth.floor(Math.min(impact.z, end.z) - radius);
        int maxX = Mth.floor(Math.max(impact.x, end.x) + radius);
        int maxY = Mth.floor(Math.max(impact.y, end.y) + radius);
        int maxZ = Mth.floor(Math.max(impact.z, end.z) + radius);
        List<BlockCandidate> candidates = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Vec3 relative = Vec3.atCenterOf(pos).subtract(impact);
                    double along = relative.dot(axis);
                    if (along < -0.75D || along > depth + 0.75D) {
                        continue;
                    }
                    double perpendicular = relative.subtract(axis.scale(along)).length();
                    if (perpendicular <= radius) {
                        candidates.add(new BlockCandidate(pos, along));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(BlockCandidate::distance));
        affectBlocks(level, impact, axis, movement.length(), snapshot,
                candidates.stream().map(BlockCandidate::pos).toList());
    }

    private static void destroySphere(
            ServerLevel level, Vec3 center, Vec3 movement, double radius, KickSnapshot snapshot) {
        int blockRadius = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-blockRadius, -blockRadius, -blockRadius),
                origin.offset(blockRadius, blockRadius, blockRadius))) {
            if (Vec3.atCenterOf(cursor).distanceToSqr(center) <= radius * radius) {
                candidates.add(cursor.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(center)));
        affectBlocks(level, center, safeDirection(movement), movement.length(), snapshot, candidates);
    }

    private static void affectBlocks(
            ServerLevel level,
            Vec3 impact,
            Vec3 direction,
            double impactSpeed,
            KickSnapshot snapshot,
            List<BlockPos> candidates) {
        ServerPlayer attacker = snapshot.attacker(level);
        if (attacker == null) {
            return;
        }
        boolean silkTouch = snapshot.enchantments().silkTouch();
        double dropChance = silkTouch ? 1.0D : 0.30D;
        int affected = 0;
        for (BlockPos pos : candidates) {
            if (!canDestroy(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, state, attacker);
            if (MinecraftForge.EVENT_BUS.post(breakEvent)) {
                continue;
            }

            boolean shouldDrop = silkTouch || level.random.nextDouble() < dropChance;
            boolean animate = !state.is(ModTags.DISINTEGRATION_DIRECT)
                    && !state.hasBlockEntity()
                    && state.getDestroySpeed(level, pos) <= 4.0F
                    && level.random.nextFloat() < 0.65F;
            if (animate) {
                Vec3 normalizedDirection = safeDirection(direction);
                Vec3 relative = Vec3.atCenterOf(pos).subtract(impact);
                double along = relative.dot(normalizedDirection);
                Vec3 radial = relative.subtract(normalizedDirection.scale(along));
                if (radial.lengthSqr() > 1.0E-4D) {
                    radial = radial.normalize();
                }
                Vec3 scatter = new Vec3(
                        level.random.nextDouble() - 0.5D,
                        level.random.nextDouble() - 0.5D,
                        level.random.nextDouble() - 0.5D).scale(0.28D);
                Vec3 flightDirection = normalizedDirection.scale(-1.0D)
                        .add(radial.scale(0.42D))
                        .add(scatter);
                if (flightDirection.lengthSqr() < 1.0E-4D) {
                    flightDirection = normalizedDirection.scale(-1.0D);
                }
                double strength = 0.24D + Math.max(0.0D, impactSpeed)
                        * (0.11D + level.random.nextDouble() * 0.035D);
                Vec3 velocity = flightDirection.normalize().scale(strength);
                ImpactDebrisEntity.launch(level, pos, state, velocity, attacker,
                        silkTouch ? snapshot.boots() : ItemStack.EMPTY, shouldDrop);
            } else {
                removeBlock(level, pos, state, attacker,
                        silkTouch ? snapshot.boots() : ItemStack.EMPTY, shouldDrop);
            }
            if ((affected & 7) == 0) {
                level.levelEvent(2001, pos, Block.getId(state));
            }
            affected++;
        }
    }

    private static boolean canDestroy(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !state.is(ModTags.DISINTEGRATION_IMMUNE)
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private static void removeBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            ServerPlayer attacker,
            ItemStack tool,
            boolean shouldDrop) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = shouldDrop
                ? Block.getDrops(state, level, pos, blockEntity, attacker, tool)
                : List.of();
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        drops.forEach(stack -> Block.popResource(level, pos, stack));
    }

    private static Vec3 safeDirection(Vec3 movement) {
        return movement.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : movement.normalize();
    }

    private record BlockCandidate(BlockPos pos, double distance) {
    }

    private record ScheduledExplosion(
            ResourceKey<Level> dimension,
            long dueTick,
            Vec3 position,
            Vec3 movement,
            float power,
            UUID excludedEntityId,
            KickSnapshot snapshot) {
    }
}
