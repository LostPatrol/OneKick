package net.lostpatrol.onekick.kick;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.advancement.KickAdvancementTrigger;
import net.lostpatrol.onekick.advancement.ModCriteriaTriggers;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModEnchantments;
import net.lostpatrol.onekick.registry.ModTags;
import net.lostpatrol.onekick.world.BlockImpactService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KickManager {
    private static final double BLOCK_SWEEP_EPSILON = 1.0E-6D;
    private static final double SUPPORT_SURFACE_EPSILON = 1.0E-5D;
    private static final double MIN_VERTICAL_BLOCK_IMPACT_SPEED = 0.35D;
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
            KickEnchantments enchantments = KickEnchantments.from(boots);
            int chargeLevel = enchantments.charge();
            if (chargeLevel <= 0) {
                performKick(player, 0.0F);
                return;
            }
            state.charging = true;
            state.charge = 0.0F;
            state.chargeFoodDebt = 0.0F;
            state.chargeLevel = chargeLevel;
            state.overchargeLevel = enchantments.overcharge();
            state.chargeMaximum = KickMath.maxCharge(chargeLevel, state.overchargeLevel);
            KickNetwork.broadcastChargeState(
                    player, true, 0.0F, state.chargeMaximum, state.chargeLevel);
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
        KickEnchantments currentEnchantments = KickEnchantments.from(boots);
        if (!player.isAlive()
                || currentEnchantments.charge() != state.chargeLevel
                || currentEnchantments.overcharge() != state.overchargeLevel) {
            cancelCharge(player, state);
            return;
        }
        if (state.charge < state.chargeMaximum && canGrowCharge(player)) {
            float increase = Math.min(
                    KickMath.chargePerTick(state.chargeLevel), state.chargeMaximum - state.charge);
            state.charge += increase;
            if (!player.getAbilities().instabuild) {
                state.chargeFoodDebt += KickMath.chargeFoodCost(increase);
                consumeChargeFood(player, state);
            }
            if (state.charge >= state.chargeMaximum
                    && isMaximumCharge(currentEnchantments, state.charge)) {
                ModCriteriaTriggers.trigger(
                        player, KickAdvancementTrigger.Event.MAXIMUM_CHARGE);
            }
        }
        if ((player.tickCount & 1) == 0) {
            KickNetwork.broadcastChargeState(
                    player, true, state.charge, state.chargeMaximum, state.chargeLevel);
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
        if (state.traversing) {
            tickTraversal(level, entity, state);
            return;
        }

        Vec3 startPosition = entity.position();
        Vec3 observedMovement = startPosition.subtract(state.lastPosition);
        if (state.age > 1
                && observedMovement.lengthSqr() > 1.0E-6D
                && !KickMath.isAlignedImpact(state.initialVelocity, observedMovement)) {
            releaseControlledMotion(entity, state.lastVelocity);
            stopTracking(entity);
            return;
        }
        Vec3 flightVelocity = state.lastVelocity;
        if (!KickMath.isAlignedImpact(state.initialVelocity, flightVelocity)) {
            releaseControlledMotion(entity, flightVelocity);
            stopTracking(entity);
            return;
        }

        suppressVoluntaryMovement(entity);
        double beforeSpeed = flightVelocity.length();
        int overloadLevel = state.snapshot.enchantments().kineticOverload();
        FirstBlockImpact firstBlockImpact = findFirstBlockImpact(level, entity, flightVelocity);
        double firstImpactRemainingSpeed = firstBlockImpact == null
                ? beforeSpeed
                : firstBlockImpact.remainingSpeed(flightVelocity);
        boolean stopAtFirstBlock = firstBlockImpact != null
                && KickMath.collisionDamage(
                        beforeSpeed, firstImpactRemainingSpeed, overloadLevel) > 0.0F;
        Vec3 requestedMovement = stopAtFirstBlock
                ? flightVelocity.scale(firstBlockImpact.safeMovementFraction(flightVelocity))
                : flightVelocity;
        entity.move(MoverType.SELF, requestedMovement);
        Vec3 currentPosition = entity.position();
        AABB currentBounds = entity.getBoundingBox();
        Vec3 actualMovement = currentPosition.subtract(startPosition);
        Entity entityCollision = state.age > 2
                ? findEntityCollision(level, entity, state, currentPosition, currentBounds)
                : null;
        boolean movementClipped =
                actualMovement.subtract(requestedMovement).lengthSqr() > 1.0E-6D;
        boolean blockCollision = stopAtFirstBlock
                || state.age > 1
                && movementClipped
                && (entity.horizontalCollision
                || entity.verticalCollision
                && Math.abs(flightVelocity.y) > MIN_VERTICAL_BLOCK_IMPACT_SPEED);

        if (blockCollision || entityCollision != null) {
            double afterSpeed = entityCollision != null
                    ? 0.0D
                    : firstBlockImpact == null
                    ? actualMovement.length()
                    : firstImpactRemainingSpeed;
            float damage = KickMath.collisionDamage(beforeSpeed, afterSpeed, overloadLevel);
            if (damage > 0.0F
                    && entity.hurt(level.damageSources().flyIntoWall(), damage)) {
                recordKickDamage(level, state, damage);
            }
            Vec3 impact = entityCollision == null
                    ? firstBlockImpact == null
                    ? findCurrentBlockImpact(level, entity, flightVelocity, actualMovement)
                    : firstBlockImpact.location()
                    : entityCollision.getBoundingBox().getCenter();
            BlockImpactService.handleImpact(level, entity, impact, flightVelocity,
                    state.initialVelocity.length(), state.snapshot);
            KickEnchantments enchantments = state.snapshot.enchantments();
            if (entity.isAlive()
                    && (enchantments.disintegration() > 0 || enchantments.unstableCollision() > 0)) {
                startTraversal(entity, state, flightVelocity, beforeSpeed);
                return;
            }
            haltControlledMotion(entity);
            stopTracking(entity);
            return;
        }

        if (actualMovement.lengthSqr() > 1.0E-6D
                && !KickMath.isAlignedImpact(state.initialVelocity, actualMovement)) {
            releaseControlledMotion(entity, actualMovement);
            stopTracking(entity);
            return;
        }

        double speed = flightVelocity.length();
        state.slowTicks = speed < 0.12D ? state.slowTicks + 1 : 0;
        if (state.slowTicks >= 5 || state.spin && state.age > 4 && entity.onGround()) {
            haltControlledMotion(entity);
            stopTracking(entity);
            return;
        }
        state.lastPosition = currentPosition;
        state.lastBounds = currentBounds;
        state.lastVelocity = KickMath.nextBallisticVelocity(flightVelocity, entity.isInWater());
        storeControlledMotion(entity, state.lastVelocity);
    }

    public static void removePlayer(ServerPlayer player) {
        PlayerKickState state = PLAYER_STATES.remove(player.getUUID());
        if (state != null && state.charging) {
            KickNetwork.broadcastChargeState(player, false, 0.0F, 0.0F, 0);
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
                KickNetwork.sendKickedState(
                        observer, living, true, kicked.spin, kicked.initialVelocity);
            }
        }
        if (target instanceof ServerPlayer trackedPlayer) {
            PlayerKickState playerState = PLAYER_STATES.get(trackedPlayer.getUUID());
            if (playerState != null && playerState.charging) {
                KickNetwork.sendChargeState(observer, trackedPlayer, true, playerState.charge,
                        playerState.chargeMaximum, playerState.chargeLevel);
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
            int desiredCost = Mth.ceil(KickMath.chargedKickFoodCost(effectiveCharge));
            int paid = consumeChargeFood(player.getFoodData(), desiredCost);
            if (desiredCost > 0) {
                effectiveCharge *= (float) paid / desiredCost;
            }
        }
        state.charging = false;
        state.charge = 0.0F;
        state.chargeFoodDebt = 0.0F;
        KickNetwork.broadcastChargeState(
                player, false, 0.0F, state.chargeMaximum, state.chargeLevel);
        performKick(player, effectiveCharge);
    }

    private static void cancelCharge(ServerPlayer player, PlayerKickState state) {
        state.charging = false;
        state.charge = 0.0F;
        state.chargeFoodDebt = 0.0F;
        KickNetwork.broadcastChargeState(player, false, 0.0F, 0.0F, 0);
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
        ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.KICK);
        if (charge > 0.0F) {
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.CHARGED_KICK);
        }
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
            if (enchantments.aerodynamics() > 0) {
                ModCriteriaTriggers.trigger(
                        player, KickAdvancementTrigger.Event.AERODYNAMIC_AIR_KICK);
            }
            applyAerodynamics(player, playerState, enchantments.aerodynamics(), kickSpeed);
        } else if (aim.type() == AimType.BLOCK && enchantments.reaction() > 0) {
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.REACTION_HIT);
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
        if (enchantments.reaction() > 0) {
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.REACTION_HIT);
            applyEntityReaction(player, look, kickSpeed);
        }
        if (!target.isAlive()) {
            return;
        }

        Vec3 velocity = launchDirection.scale(launchSpeed);
        boolean spin = enchantments.angularMomentum() > 0
                && look.y > 0.15D
                && target.onGround()
                && !(target instanceof FlyingMob)
                && !(target instanceof FlyingAnimal)
                && !target.getType().is(ModTags.FLYING);
        KickSnapshot snapshot = new KickSnapshot(player.getUUID(), kickSpeed, enchantments, boots);
        startKickedMotion(target, snapshot, velocity, spin);
        if (spin) {
            ModCriteriaTriggers.trigger(
                    player, KickAdvancementTrigger.Event.ANGULAR_MOMENTUM_SPIN);
        }
        if (launchSpeed >= KickMath.MACH_RING_MIN_SPEED) {
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.MACH_LAUNCH);
        }
        if (isUltimateKick(enchantments, charge)) {
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.ULTIMATE_KICK);
        }
    }

    static void startKickedMotion(
            LivingEntity target, KickSnapshot snapshot, Vec3 velocity, boolean spin) {
        stopTracking(target);
        boolean originalNoAi = target instanceof Mob mob && mob.isNoAi();
        KickedMotionState motionState = new KickedMotionState(
                snapshot, target.position(), target.getBoundingBox(), velocity, spin,
                target.noPhysics, originalNoAi);
        KICKED_ENTITIES.put(target.getUUID(), motionState);
        suppressVoluntaryMovement(target);
        storeControlledMotion(target, velocity);
        KickNetwork.broadcastKickedState(target, true, spin, velocity);
    }

    private static void startTraversal(
            LivingEntity entity, KickedMotionState state, Vec3 impactVelocity, double impactSpeed) {
        KickEnchantments enchantments = state.snapshot.enchantments();
        double distance = KickMath.impactTraversalDistance(
                state.snapshot.kickSpeed(), state.initialVelocity.length(),
                enchantments.disintegration(),
                enchantments.unstableCollision(), enchantments.kineticOverload());
        if (distance <= 0.0D || impactVelocity.lengthSqr() < 1.0E-6D) {
            stopTracking(entity);
            return;
        }
        state.traversing = true;
        state.traversalDirection = impactVelocity.normalize();
        state.traversalRemaining = distance;
        state.traversalSpeed = Math.max(0.35D, impactSpeed);
        state.lastPosition = entity.position();
        state.lastBounds = entity.getBoundingBox();
        state.lastVelocity = state.traversalDirection.scale(state.traversalSpeed);
        entity.noPhysics = true;
        entity.fallDistance = 0.0F;
        suppressVoluntaryMovement(entity);
        storeControlledMotion(entity, state.lastVelocity);
    }

    private static void tickTraversal(
            ServerLevel level, LivingEntity entity, KickedMotionState state) {
        Vec3 currentPosition = entity.position();
        if (state.traversalRemaining <= 1.0E-3D) {
            haltControlledMotion(entity);
            stopTracking(entity);
            return;
        }

        double forcedSpeed = Math.min(state.traversalSpeed, state.traversalRemaining);
        Vec3 forcedVelocity = state.traversalDirection.scale(forcedSpeed);
        BlockPos nextPosition = BlockPos.containing(currentPosition.add(forcedVelocity));
        if (!level.hasChunkAt(nextPosition)) {
            haltControlledMotion(entity);
            stopTracking(entity);
            return;
        }

        suppressVoluntaryMovement(entity);
        entity.noPhysics = true;
        entity.fallDistance = 0.0F;
        entity.move(MoverType.SELF, forcedVelocity);
        Vec3 movedPosition = entity.position();
        state.traversalRemaining -= movedPosition.distanceTo(currentPosition);
        state.traversalTicks++;
        if (state.traversalTicks % 3 == 0) {
            float damage = KickMath.traversalDamage(
                    state.traversalSpeed, state.snapshot.enchantments().kineticOverload());
            entity.invulnerableTime = 0;
            if (entity.hurt(level.damageSources().flyIntoWall(), damage)) {
                recordKickDamage(level, state, damage);
            }
            if (!entity.isAlive()) {
                stopTracking(entity);
                return;
            }
        }

        state.lastPosition = movedPosition;
        state.lastBounds = entity.getBoundingBox();
        state.lastVelocity = forcedVelocity;
        if (state.traversalRemaining <= 1.0E-3D) {
            haltControlledMotion(entity);
            stopTracking(entity);
            return;
        }
        storeControlledMotion(entity, forcedVelocity);
    }

    private static void recordKickDamage(
            ServerLevel level, KickedMotionState state, float damage) {
        state.attributedDamage += damage;
        if (!state.massiveDamageTriggered
                && state.attributedDamage >= ModCriteriaTriggers.MASSIVE_DAMAGE) {
            ServerPlayer attacker = state.snapshot.attacker(level);
            if (attacker != null) {
                ModCriteriaTriggers.trigger(
                        attacker, KickAdvancementTrigger.Event.MASSIVE_DAMAGE);
                state.massiveDamageTriggered = true;
            }
        }
    }

    private static boolean isMaximumCharge(
            KickEnchantments enchantments, float charge) {
        int maximumChargeLevel = ModEnchantments.CHARGE.get().getMaxLevel();
        int maximumOverchargeLevel = ModEnchantments.OVERCHARGE.get().getMaxLevel();
        return enchantments.charge() >= maximumChargeLevel
                && enchantments.overcharge() >= maximumOverchargeLevel
                && charge >= KickMath.maxCharge(maximumChargeLevel, maximumOverchargeLevel);
    }

    private static boolean isUltimateKick(
            KickEnchantments enchantments, float charge) {
        return isMaximumCharge(enchantments, charge)
                && enchantments.kineticOverload()
                >= ModEnchantments.KINETIC_OVERLOAD.get().getMaxLevel()
                && enchantments.unstableCollision()
                >= ModEnchantments.UNSTABLE_COLLISION.get().getMaxLevel()
                && enchantments.disintegration()
                >= ModEnchantments.DISINTEGRATION.get().getMaxLevel();
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

    @Nullable
    static FirstBlockImpact findFirstBlockImpact(
            ServerLevel level, LivingEntity entity, Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-6D) {
            return null;
        }
        AABB bounds = entity.getBoundingBox();
        Vec3 startCenter = bounds.getCenter();
        AABB sweptBounds = bounds.expandTowards(movement).inflate(BLOCK_SWEEP_EPSILON);
        double halfX = bounds.getXsize() * 0.5D;
        double halfY = bounds.getYsize() * 0.5D;
        double halfZ = bounds.getZsize() * 0.5D;
        FirstBlockImpact closestImpact = null;
        double closestContactDistance = Double.MAX_VALUE;

        for (VoxelShape collisionShape : level.getBlockCollisions(entity, sweptBounds)) {
            for (AABB obstacle : collisionShape.toAabbs()) {
                if (Math.abs(movement.y) <= MIN_VERTICAL_BLOCK_IMPACT_SPEED
                        && obstacle.maxY <= bounds.minY + SUPPORT_SURFACE_EPSILON) {
                    continue;
                }
                AABB expandedObstacle = obstacle.inflate(halfX, halfY, halfZ);
                SweptBoxHit sweptHit = sweepPointAgainstBox(
                        startCenter, movement, expandedObstacle);
                if (sweptHit == null
                        || sweptHit.face().getAxis() == Direction.Axis.Y
                        && Math.abs(movement.y) <= MIN_VERTICAL_BLOCK_IMPACT_SPEED) {
                    continue;
                }
                Vec3 centerAtImpact = startCenter.add(
                        movement.scale(sweptHit.movementFraction()));
                Vec3 contact = new Vec3(
                        Mth.clamp(centerAtImpact.x, obstacle.minX, obstacle.maxX),
                        Mth.clamp(centerAtImpact.y, obstacle.minY, obstacle.maxY),
                        Mth.clamp(centerAtImpact.z, obstacle.minZ, obstacle.maxZ));
                double contactDistance = centerAtImpact.distanceToSqr(contact);
                if (closestImpact == null
                        || sweptHit.movementFraction()
                        < closestImpact.movementFraction() - BLOCK_SWEEP_EPSILON
                        || Math.abs(sweptHit.movementFraction()
                        - closestImpact.movementFraction()) <= BLOCK_SWEEP_EPSILON
                        && contactDistance < closestContactDistance) {
                    closestImpact = new FirstBlockImpact(
                            contact, sweptHit.face(), sweptHit.movementFraction());
                    closestContactDistance = contactDistance;
                }
            }
        }
        return closestImpact;
    }

    @Nullable
    private static SweptBoxHit sweepPointAgainstBox(
            Vec3 start, Vec3 movement, AABB target) {
        double entryTime = 0.0D;
        double exitTime = 1.0D;
        double entryNormalSpeed = 0.0D;
        Direction entryFace = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double origin = axisValue(start, axis);
            double delta = axisValue(movement, axis);
            double minimum = axisMinimum(target, axis);
            double maximum = axisMaximum(target, axis);
            if (Math.abs(delta) < BLOCK_SWEEP_EPSILON) {
                if (origin <= minimum || origin >= maximum) {
                    return null;
                }
                continue;
            }

            double nearTime;
            double farTime;
            Direction nearFace;
            if (delta > 0.0D) {
                nearTime = (minimum - origin) / delta;
                farTime = (maximum - origin) / delta;
                nearFace = negativeFace(axis);
            } else {
                nearTime = (maximum - origin) / delta;
                farTime = (minimum - origin) / delta;
                nearFace = positiveFace(axis);
            }
            if (nearTime > entryTime + BLOCK_SWEEP_EPSILON
                    || Math.abs(nearTime - entryTime) <= BLOCK_SWEEP_EPSILON
                    && Math.abs(delta) > entryNormalSpeed) {
                entryTime = nearTime;
                entryNormalSpeed = Math.abs(delta);
                entryFace = nearFace;
            }
            exitTime = Math.min(exitTime, farTime);
            if (entryTime > exitTime + BLOCK_SWEEP_EPSILON) {
                return null;
            }
        }
        if (entryFace == null
                || entryTime < -BLOCK_SWEEP_EPSILON
                || entryTime > 1.0D + BLOCK_SWEEP_EPSILON) {
            return null;
        }
        return new SweptBoxHit(entryFace, Mth.clamp(entryTime, 0.0D, 1.0D));
    }

    private static double axisValue(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> vector.x;
            case Y -> vector.y;
            case Z -> vector.z;
        };
    }

    private static double axisMinimum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double axisMaximum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static Direction negativeFace(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.WEST;
            case Y -> Direction.DOWN;
            case Z -> Direction.NORTH;
        };
    }

    private static Direction positiveFace(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static Vec3 findCurrentBlockImpact(
            ServerLevel level, LivingEntity entity, Vec3 movement, Vec3 actualMovement) {
        AABB bounds = entity.getBoundingBox();
        Vec3 center = bounds.getCenter();
        Vec3 blockedMovement = movement.subtract(actualMovement);
        if (entity.horizontalCollision) {
            blockedMovement = new Vec3(blockedMovement.x, 0.0D, blockedMovement.z);
        } else if (entity.verticalCollision) {
            blockedMovement = new Vec3(0.0D, blockedMovement.y, 0.0D);
        }
        Vec3 collisionDirection = blockedMovement.lengthSqr() < 1.0E-6D
                ? movement.normalize()
                : blockedMovement.normalize();
        Vec3 closestContact = null;
        double closestDistance = Double.MAX_VALUE;
        for (VoxelShape collisionShape : level.getBlockCollisions(
                entity, bounds.inflate(BLOCK_SWEEP_EPSILON))) {
            Optional<Vec3> contact = collisionShape.closestPointTo(center);
            if (contact.isEmpty()) {
                continue;
            }
            Vec3 offset = contact.get().subtract(center);
            double distance = offset.lengthSqr();
            if (offset.dot(collisionDirection) > 0.0D && distance < closestDistance) {
                closestContact = contact.get();
                closestDistance = distance;
            }
        }
        if (closestContact != null) {
            return closestContact;
        }

        double leadingDistance = Math.abs(collisionDirection.x) * bounds.getXsize() * 0.5D
                + Math.abs(collisionDirection.y) * bounds.getYsize() * 0.5D
                + Math.abs(collisionDirection.z) * bounds.getZsize() * 0.5D;
        Vec3 end = center.add(collisionDirection.scale(leadingDistance + 0.5D));
        BlockHitResult hit = level.clip(new ClipContext(center, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS
                ? center.add(collisionDirection.scale(leadingDistance))
                : hit.getLocation();
    }

    private static boolean canGrowCharge(ServerPlayer player) {
        return player.getAbilities().instabuild || availableChargeFood(player.getFoodData()) > 0;
    }

    private static void consumeChargeFood(ServerPlayer player, PlayerKickState state) {
        FoodData food = player.getFoodData();
        while (state.chargeFoodDebt >= 1.0F && consumeChargeFood(food, 1) > 0) {
            state.chargeFoodDebt -= 1.0F;
        }
    }

    static int availableChargeFood(FoodData food) {
        return Mth.ceil(food.getSaturationLevel()) + food.getFoodLevel();
    }

    static int consumeChargeFood(FoodData food, int requested) {
        int paid = 0;
        while (paid < requested && food.getSaturationLevel() > 0.0F) {
            food.setSaturation(Math.max(0.0F, food.getSaturationLevel() - 1.0F));
            paid++;
        }
        int hungerPaid = Math.min(requested - paid, food.getFoodLevel());
        food.setFoodLevel(food.getFoodLevel() - hungerPaid);
        return paid + hungerPaid;
    }

    private static void suppressVoluntaryMovement(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getNavigation().stop();
            mob.setJumping(false);
            mob.setXxa(0.0F);
            mob.setYya(0.0F);
            mob.setZza(0.0F);
        }
    }

    private static void storeControlledMotion(LivingEntity entity, Vec3 velocity) {
        entity.setDeltaMovement(entity instanceof Mob ? velocity.scale(1.0D / 0.98D) : Vec3.ZERO);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void releaseControlledMotion(LivingEntity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void haltControlledMotion(LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void stopTracking(LivingEntity entity) {
        KickedMotionState removed = KICKED_ENTITIES.remove(entity.getUUID());
        if (removed != null) {
            entity.noPhysics = removed.originalNoPhysics;
            if (entity instanceof Mob mob) {
                mob.setNoAi(removed.originalNoAi);
            }
            KickNetwork.broadcastKickedState(entity, false, false, Vec3.ZERO);
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
        private int overchargeLevel;
        private float charge;
        private float chargeMaximum;
        private float chargeFoodDebt;
    }

    private static final class KickedMotionState {
        private final KickSnapshot snapshot;
        private final boolean spin;
        private final boolean originalNoPhysics;
        private final boolean originalNoAi;
        private final Vec3 initialVelocity;
        private Vec3 lastPosition;
        private AABB lastBounds;
        private Vec3 lastVelocity;
        private int age;
        private int slowTicks;
        private boolean traversing;
        private Vec3 traversalDirection = Vec3.ZERO;
        private double traversalRemaining;
        private double traversalSpeed;
        private int traversalTicks;
        private float attributedDamage;
        private boolean massiveDamageTriggered;

        private KickedMotionState(
                KickSnapshot snapshot,
                Vec3 lastPosition,
                AABB lastBounds,
                Vec3 lastVelocity,
                boolean spin,
                boolean originalNoPhysics,
                boolean originalNoAi) {
            this.snapshot = snapshot;
            this.lastPosition = lastPosition;
            this.lastBounds = lastBounds;
            this.lastVelocity = lastVelocity;
            this.spin = spin;
            this.originalNoPhysics = originalNoPhysics;
            this.originalNoAi = originalNoAi;
            this.initialVelocity = lastVelocity;
        }
    }

    static record FirstBlockImpact(
            Vec3 location, Direction face, double movementFraction) {
        double safeMovementFraction(Vec3 movement) {
            double normalSpeed = Math.max(BLOCK_SWEEP_EPSILON, switch (face.getAxis()) {
                case X -> Math.abs(movement.x);
                case Y -> Math.abs(movement.y);
                case Z -> Math.abs(movement.z);
            });
            return Math.max(0.0D,
                    movementFraction - BLOCK_SWEEP_EPSILON * 2.0D / normalSpeed);
        }

        double remainingSpeed(Vec3 movement) {
            return switch (face.getAxis()) {
                case X -> Math.sqrt(movement.y * movement.y + movement.z * movement.z);
                case Y -> Math.sqrt(movement.x * movement.x + movement.z * movement.z);
                case Z -> Math.sqrt(movement.x * movement.x + movement.y * movement.y);
            };
        }
    }

    private record SweptBoxHit(Direction face, double movementFraction) {
    }
}
