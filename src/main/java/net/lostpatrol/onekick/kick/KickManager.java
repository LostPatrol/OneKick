package net.lostpatrol.onekick.kick;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModTags;
import net.lostpatrol.onekick.world.BlockImpactService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KickManager {
    private static final Map<UUID, PlayerKickState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, KickedMotionState> KICKED_ENTITIES = new HashMap<>();

    private KickManager() {
    }

    public static void handleInput(ServerPlayer player, boolean pressed) {
        PlayerKickState state = state(player);
        if (!player.isAlive() || player.isSpectator()) {
            if (state.charging) {
                cancelCharge(player, state);
            }
            return;
        }
        if (pressed) {
            if (state.cooldown > 0 || state.charging) {
                if (state.cooldown > 0) {
                    player.displayClientMessage(Component.translatable("message.onekick.cooldown"), true);
                }
                return;
            }
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            int chargeLevel = KickEnchantments.from(boots).charge();
            if (chargeLevel <= 0) {
                performKick(player, 0.0F);
                return;
            }
            state.charging = true;
            state.charge = 0.0F;
            state.chargeFoodDebt = 0.0F;
            state.chargeLevel = chargeLevel;
            state.chargeMaximum = KickMath.maxCharge(chargeLevel);
            KickNetwork.sendChargeState(player, true, 0.0F, state.chargeMaximum);
            KickNetwork.broadcastPlayerAnimation(player, KickNetwork.ANIMATION_CHARGE);
        } else if (state.charging) {
            releaseCharge(player, state);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        PlayerKickState state = state(player);
        if (state.cooldown > 0) {
            state.cooldown--;
        }
        if (player.onGround()) {
            state.airUses = 0;
        }
        if (!state.charging) {
            return;
        }

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int currentLevel = KickEnchantments.from(boots).charge();
        if (!player.isAlive() || currentLevel != state.chargeLevel) {
            cancelCharge(player, state);
            return;
        }
        if (canGrowCharge(player)) {
            float increase = KickMath.chargePerTick(state.chargeLevel);
            state.charge += increase;
            if (!player.getAbilities().instabuild) {
                state.chargeFoodDebt += KickMath.chargeFoodCost(increase);
                consumeChargeFood(player, state);
            }
        }
        KickEffects.emitCharge(player, state.charge, state.chargeMaximum, state.chargeLevel);
        if ((player.tickCount & 1) == 0) {
            KickNetwork.sendChargeState(player, true, state.charge, state.chargeMaximum);
        }
    }

    public static void tickLiving(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        KickedMotionState state = KICKED_ENTITIES.get(entity.getUUID());
        if (state == null) {
            return;
        }
        state.age++;
        if (!entity.isAlive()) {
            stopTracking(entity);
            return;
        }

        Vec3 currentPosition = entity.position();
        Vec3 currentVelocity = entity.getDeltaMovement();
        AABB currentBounds = entity.getBoundingBox();
        Entity entityCollision = state.age > 2
                ? findEntityCollision(level, entity, state, currentPosition, currentBounds)
                : null;
        boolean blockCollision = state.age > 1 && (entity.horizontalCollision
                || entity.verticalCollision && Math.abs(state.lastVelocity.y) > 0.35D);

        if (blockCollision || entityCollision != null) {
            double beforeSpeed = state.lastVelocity.length();
            double afterSpeed = currentVelocity.length();
            float damage = KickMath.collisionDamage(beforeSpeed, afterSpeed,
                    state.snapshot.enchantments().kineticOverload());
            if (damage <= 0.0F && beforeSpeed > 0.65D) {
                damage = (float) ((beforeSpeed - 0.35D) * 6.0D
                        * (1.0D + state.snapshot.enchantments().kineticOverload()));
            }
            if (damage > 0.0F) {
                entity.hurt(level.damageSources().flyIntoWall(), damage);
            }
            Vec3 impact = entityCollision == null
                    ? findBlockImpact(level, entity, state.lastVelocity)
                    : entityCollision.getBoundingBox().getCenter();
            BlockImpactService.handleImpact(level, impact, state.lastVelocity, state.snapshot);
            stopTracking(entity);
            return;
        }

        double speed = currentVelocity.length();
        state.ringDistance += currentPosition.distanceTo(state.lastPosition);
        boolean emitMachRing = speed >= 3.35D && state.ringDistance >= 2.0D;
        if (emitMachRing) {
            state.ringDistance %= 2.0D;
        }
        KickEffects.emitTrail(level, entity, speed, state.age, emitMachRing);
        state.slowTicks = speed < 0.12D ? state.slowTicks + 1 : 0;
        if (state.slowTicks >= 5 || state.spin && state.age > 4 && entity.onGround()) {
            stopTracking(entity);
            return;
        }
        state.lastPosition = currentPosition;
        state.lastBounds = currentBounds;
        state.lastVelocity = currentVelocity;
    }

    public static void removePlayer(ServerPlayer player) {
        PlayerKickState state = PLAYER_STATES.remove(player.getUUID());
        if (state != null && state.charging) {
            KickNetwork.sendChargeState(player, false, 0.0F, 0.0F);
            KickNetwork.broadcastPlayerAnimation(player, KickNetwork.ANIMATION_STOP);
        }
    }

    public static void removeLiving(LivingEntity entity) {
        stopTracking(entity);
    }

    public static void syncTracking(ServerPlayer observer, Entity target) {
        if (target instanceof LivingEntity living) {
            KickedMotionState kicked = KICKED_ENTITIES.get(living.getUUID());
            if (kicked != null) {
                KickNetwork.sendKickedState(observer, living, true, kicked.spin);
            }
        }
        if (target instanceof ServerPlayer trackedPlayer) {
            PlayerKickState playerState = PLAYER_STATES.get(trackedPlayer.getUUID());
            if (playerState != null && playerState.charging) {
                KickNetwork.sendPlayerAnimation(observer, trackedPlayer, KickNetwork.ANIMATION_CHARGE);
            }
        }
    }

    public static void clearAll() {
        PLAYER_STATES.clear();
        KICKED_ENTITIES.clear();
    }

    private static void releaseCharge(ServerPlayer player, PlayerKickState state) {
        float effectiveCharge = state.charge;
        if (!player.getAbilities().instabuild && effectiveCharge > 0.0F) {
            int desiredCost = Mth.ceil(KickMath.chargeFoodCost(effectiveCharge));
            int available = player.getFoodData().getFoodLevel();
            int paid = Math.min(desiredCost, available);
            if (desiredCost > 0) {
                effectiveCharge *= (float) paid / desiredCost;
            }
            setFoodLevel(player.getFoodData(), available - paid);
        }
        state.charging = false;
        state.charge = 0.0F;
        state.chargeFoodDebt = 0.0F;
        KickNetwork.sendChargeState(player, false, 0.0F, state.chargeMaximum);
        performKick(player, effectiveCharge);
    }

    private static void cancelCharge(ServerPlayer player, PlayerKickState state) {
        state.charging = false;
        state.charge = 0.0F;
        state.chargeFoodDebt = 0.0F;
        KickNetwork.sendChargeState(player, false, 0.0F, 0.0F);
        KickNetwork.broadcastPlayerAnimation(player, KickNetwork.ANIMATION_STOP);
    }

    private static void performKick(ServerPlayer player, float charge) {
        PlayerKickState playerState = state(player);
        playerState.cooldown = KickMath.COOLDOWN_TICKS;
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET).copy();
        KickEnchantments enchantments = KickEnchantments.from(boots);
        double kickSpeed = KickMath.finalSpeed(player, boots, charge, enchantments.kineticOverload());
        AimResult aim = findAim(player);

        KickEffects.playKick(player, charge);
        KickNetwork.broadcastPlayerAnimation(player, KickNetwork.ANIMATION_KICK);
        if (aim.entity() instanceof LivingEntity target
                && target.isAlive()
                && !(target instanceof EnderDragon)
                && !target.getType().is(ModTags.KICK_IMMUNE)) {
            kickEntity(player, target, boots, enchantments, kickSpeed, charge);
            return;
        }

        boolean airborneBlockCountsAsAir = !player.onGround()
                && enchantments.aerodynamics() > 0
                && aim.type() == AimType.BLOCK;
        if (airborneBlockCountsAsAir || aim.type() == AimType.AIR) {
            applyAerodynamics(player, playerState, enchantments.aerodynamics(), kickSpeed);
        } else if (aim.type() == AimType.BLOCK && enchantments.reaction() > 0) {
            applyBlockReaction(player, kickSpeed);
        }
    }

    private static void kickEntity(
            ServerPlayer player,
            LivingEntity target,
            ItemStack boots,
            KickEnchantments enchantments,
            double kickSpeed,
            float charge) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 launchDirection = KickMath.launchDirection(look);
        double launchSpeed = KickMath.launchSpeed(kickSpeed, target);
        float directDamage = KickMath.directDamage(kickSpeed, charge, enchantments.kineticOverload());
        if (directDamage > 0.0F) {
            target.hurt(target.damageSources().playerAttack(player), directDamage);
        }
        if (enchantments.reaction() > 0) {
            applyEntityReaction(player, look, kickSpeed);
        }
        if (!target.isAlive()) {
            return;
        }

        Vec3 velocity = launchDirection.scale(launchSpeed);
        target.setDeltaMovement(velocity);
        target.hasImpulse = true;
        target.hurtMarked = true;
        boolean spin = enchantments.angularMomentum() > 0
                && look.y > 0.15D
                && target.onGround()
                && !(target instanceof FlyingMob)
                && !(target instanceof FlyingAnimal)
                && !target.getType().is(ModTags.FLYING);
        KickSnapshot snapshot = new KickSnapshot(player.getUUID(), kickSpeed, enchantments, boots);
        KICKED_ENTITIES.put(target.getUUID(), new KickedMotionState(
                snapshot, target.position(), target.getBoundingBox(), velocity, spin));
        KickNetwork.broadcastKickedState(target, true, spin);
    }

    private static void applyBlockReaction(ServerPlayer player, double kickSpeed) {
        double reaction = Math.max(0.0D, kickSpeed * 0.22D);
        player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().normalize().scale(-reaction)));
        player.hurtMarked = true;
    }

    private static void applyEntityReaction(ServerPlayer player, Vec3 look, double kickSpeed) {
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-5D) {
            return;
        }
        horizontal = horizontal.normalize();
        Vec3 movement = player.getDeltaMovement();
        double forward = movement.dot(horizontal);
        double reaction = Math.max(0.0D, kickSpeed * 0.14D);
        double reduction = forward > 0.0D ? Math.min(forward, reaction) : reaction;
        player.setDeltaMovement(movement.subtract(horizontal.scale(reduction)));
        player.hurtMarked = true;
    }

    private static void applyAerodynamics(
            ServerPlayer player, PlayerKickState state, int level, double kickSpeed) {
        if (level <= 0 || !player.onGround() && state.airUses >= level) {
            return;
        }
        if (!player.onGround()) {
            state.airUses++;
        }
        double reaction = Math.max(0.0D, kickSpeed * 0.16D * (1.0D + level * 0.15D));
        Vec3 push = player.getLookAngle().normalize().scale(-reaction);
        player.setDeltaMovement(player.getDeltaMovement().add(push));
        player.hurtMarked = true;
    }

    private static AimResult findAim(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 fullEnd = start.add(direction.scale(KickMath.KICK_REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(start, fullEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 clippedEnd = blockHit.getType() == HitResult.Type.MISS ? fullEnd : blockHit.getLocation();
        AABB searchBox = player.getBoundingBox().expandTowards(direction.scale(KickMath.KICK_REACH)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, clippedEnd, searchBox,
                EntitySelector.NO_SPECTATORS.and(Entity::isPickable));
        if (entityHit != null) {
            return new AimResult(AimType.ENTITY, entityHit.getEntity(), entityHit.getLocation());
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            return new AimResult(AimType.BLOCK, null, blockHit.getLocation());
        }
        return new AimResult(AimType.AIR, null, fullEnd);
    }

    @Nullable
    private static Entity findEntityCollision(
            ServerLevel level,
            LivingEntity kicked,
            KickedMotionState state,
            Vec3 currentPosition,
            AABB currentBounds) {
        AABB swept = state.lastBounds.minmax(currentBounds).inflate(0.15D);
        List<Entity> candidates = level.getEntities(kicked, swept,
                entity -> entity != kicked
                        && entity.isAlive()
                        && entity.isPickable()
                        && !entity.getUUID().equals(state.snapshot.attackerId())
                        && !entity.isSpectator());
        for (Entity candidate : candidates) {
            AABB hitBox = candidate.getBoundingBox().inflate(0.12D);
            Optional<Vec3> clip = hitBox.clip(state.lastPosition, currentPosition);
            if (clip.isPresent() || hitBox.intersects(currentBounds)) {
                return candidate;
            }
        }
        return null;
    }

    private static Vec3 findBlockImpact(ServerLevel level, LivingEntity entity, Vec3 movement) {
        Vec3 direction = movement.lengthSqr() < 1.0E-6D ? Vec3.ZERO : movement.normalize();
        Vec3 start = entity.position();
        Vec3 end = start.add(direction.scale(entity.getBbWidth() * 0.75D + 0.5D));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static boolean canGrowCharge(ServerPlayer player) {
        return player.getAbilities().instabuild || player.getFoodData().getFoodLevel() > 0;
    }

    private static void consumeChargeFood(ServerPlayer player, PlayerKickState state) {
        FoodData food = player.getFoodData();
        while (state.chargeFoodDebt >= 1.0F && food.getFoodLevel() > 0) {
            setFoodLevel(food, food.getFoodLevel() - 1);
            state.chargeFoodDebt -= 1.0F;
        }
    }

    private static void setFoodLevel(FoodData food, int value) {
        food.setFoodLevel(Math.max(0, value));
        food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
    }

    private static void stopTracking(LivingEntity entity) {
        KickedMotionState removed = KICKED_ENTITIES.remove(entity.getUUID());
        if (removed != null) {
            KickNetwork.broadcastKickedState(entity, false, false);
        }
    }

    private static PlayerKickState state(ServerPlayer player) {
        return PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerKickState());
    }

    private enum AimType {
        ENTITY,
        BLOCK,
        AIR
    }

    private record AimResult(AimType type, @Nullable Entity entity, Vec3 location) {
    }

    private static final class PlayerKickState {
        private int cooldown;
        private int airUses;
        private boolean charging;
        private int chargeLevel;
        private float charge;
        private float chargeMaximum;
        private float chargeFoodDebt;
    }

    private static final class KickedMotionState {
        private final KickSnapshot snapshot;
        private final boolean spin;
        private Vec3 lastPosition;
        private AABB lastBounds;
        private Vec3 lastVelocity;
        private int age;
        private int slowTicks;
        private double ringDistance;

        private KickedMotionState(
                KickSnapshot snapshot,
                Vec3 lastPosition,
                AABB lastBounds,
                Vec3 lastVelocity,
                boolean spin) {
            this.snapshot = snapshot;
            this.lastPosition = lastPosition;
            this.lastBounds = lastBounds;
            this.lastVelocity = lastVelocity;
            this.spin = spin;
        }
    }
}
