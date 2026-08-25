package net.lostpatrol.onekick.kick;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class KickMath {
    public static final double BASE_SPEED = 1.0D;
    public static final double MOVEMENT_SPEED_COEFFICIENT = 1.8D;
    public static final double KICK_REACH = 3.5D;
    public static final int COOLDOWN_TICKS = 4;
    public static final float CHARGE_SPEED_MULTIPLIER = 6.0F;
    public static final double MACH_RING_MIN_SPEED = 4.5D;
    public static final double MACH_RING_BASE_INTERVAL = 21.375D;
    public static final int MACH_RING_RENDER_LIMIT = 12;
    public static final double IRREGULAR_DESTRUCTION_MIN_SCALE = 0.82D;
    public static final double IRREGULAR_DESTRUCTION_MAX_SCALE = 1.18D;
    private static final double CHARGE_QUADRATIC_COEFFICIENT = 1.0D / 15.0D;
    private static final float BASE_CHARGE_FOOD_THRESHOLD = 6.0F;
    private static final float BASE_CHARGED_KICK_FOOD_COST = 15.0F;
    private static final float OVERCHARGED_KICK_FOOD_COST_PER_CHARGE = 5.0F / 3.0F;
    private static final double BALLISTIC_GRAVITY = 0.06D;
    private static final double SUBMERGED_DRAG = 0.82D;
    private static final double SUBMERGED_GRAVITY = 0.02D;
    private static final double MIN_IMPACT_ALIGNMENT = Math.cos(Math.toRadians(35.0D));
    private static final double UNSTABLE_EXPLOSION_MIN_VISUAL_RADIUS = 4.0D;
    private static final int DISINTEGRATION_SMOKE_PER_ORIGIN = 8;
    private static final int MAX_DISINTEGRATION_SMOKE_PARTICLES = 4096;
    private static final double BLOCK_REACTION_COEFFICIENT = 0.44D;
    private static final double ENTITY_REACTION_COEFFICIENT = 0.44D;
    private static final double AERODYNAMICS_COEFFICIENT = 0.50D;

    private KickMath() {
    }

    public static double finalSpeed(Player player, ItemStack boots, float charge, int overloadLevel) {
        double movementSpeed = Math.max(0.0D, player.isFallFlying()
                ? player.getDeltaMovement().length()
                : player.getDeltaMovement().horizontalDistance());
        return calculateFinalSpeed(equipmentMultiplier(boots), movementSpeed, charge, overloadLevel);
    }

    public static double calculateFinalSpeed(
            double equipmentMultiplier, double movementSpeed, double charge, int overloadLevel) {
        double safeEquipment = Double.isNaN(equipmentMultiplier) ? 1.0D : equipmentMultiplier;
        double safeMovement = nonNegative(movementSpeed);
        int safeOverload = Math.max(0, overloadLevel);
        double specialMultiplier = 1.0D + safeOverload * 0.55D;
        double speed = (BASE_SPEED * safeEquipment + safeMovement * MOVEMENT_SPEED_COEFFICIENT)
                * chargeMultiplier(charge) * specialMultiplier;
        return nonNegative(speed);
    }

    public static double chargeMultiplier(double charge) {
        double safeCharge = nonNegative(charge);
        return 1.0D + safeCharge * safeCharge * CHARGE_QUADRATIC_COEFFICIENT;
    }

    public static double equipmentMultiplier(ItemStack boots) {
        if (boots.isEmpty()) {
            return 1.0D;
        }
        if (boots.is(Items.LEATHER_BOOTS)) {
            return 1.08D;
        }
        if (boots.is(Items.GOLDEN_BOOTS)) {
            return 1.15D;
        }
        if (boots.is(Items.CHAINMAIL_BOOTS)) {
            return 1.19D;
        }
        if (boots.is(Items.IRON_BOOTS)) {
            return 1.24D;
        }
        if (boots.is(Items.DIAMOND_BOOTS)) {
            return 1.36D;
        }
        if (boots.is(Items.NETHERITE_BOOTS)) {
            return 1.50D;
        }
        if (boots.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.BOOTS) {
            return 1.0D + Math.max(0, armor.getDefense()) * 0.07D;
        }
        return 1.0D;
    }

    public static double launchSpeed(double kickSpeed, LivingEntity target) {
        double healthFactor = healthLaunchFactor(target.getMaxHealth());
        double resistanceFactor = resistanceLaunchFactor(
                target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        return nonNegative(kickSpeed * 0.82D * healthFactor * resistanceFactor);
    }

    public static double healthLaunchFactor(double maximumHealth) {
        return Math.pow(20.0D / Math.max(1.0D, maximumHealth), 0.15D);
    }

    public static double resistanceLaunchFactor(double resistance) {
        return Math.max(0.0D, 1.0D - Math.max(0.0D, resistance) * 0.15D);
    }

    public static Vec3 launchDirection(Vec3 look) {
        Vec3 direction = look.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
        return direction.add(0.0D, 0.12D, 0.0D).normalize();
    }

    public static double blockReactionImpulse(double kickSpeed) {
        return nonNegative(kickSpeed) * BLOCK_REACTION_COEFFICIENT;
    }

    public static double entityReactionImpulse(double kickSpeed) {
        return nonNegative(kickSpeed) * ENTITY_REACTION_COEFFICIENT;
    }

    public static double aerodynamicsImpulse(double kickSpeed) {
        return nonNegative(kickSpeed) * AERODYNAMICS_COEFFICIENT;
    }

    public static Vec3 reactionVelocity(Vec3 current, Vec3 look, double impulse) {
        Vec3 currentVelocity = current == null ? Vec3.ZERO : current;
        Vec3 direction = look == null || look.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : look.normalize();
        return currentVelocity.add(direction.scale(-nonNegative(impulse)));
    }

    public static Vec3 nextBallisticVelocity(Vec3 velocity, boolean submerged) {
        if (submerged) {
            return velocity.scale(SUBMERGED_DRAG).add(0.0D, -SUBMERGED_GRAVITY, 0.0D);
        }
        return velocity.add(0.0D, -BALLISTIC_GRAVITY, 0.0D);
    }

    public static float collisionDamage(double beforeSpeed, double afterSpeed, int overloadLevel) {
        double lostSpeed = Math.max(0.0D, beforeSpeed - afterSpeed);
        double vanillaLike = Math.max(0.0D, lostSpeed * 10.0D - 3.0D);
        return (float) nonNegative(vanillaLike * (1.0D + overloadLevel * 1.25D));
    }

    public static float maxCharge(int level, int overchargeLevel) {
        return level <= 0 ? 0.0F : (1.0F + level) * (Math.max(0, overchargeLevel) + 1.0F);
    }

    public static float chargePerTick(int level) {
        return level <= 0 ? 0.0F : 0.0125F * (1.0F + level * 0.5F) * CHARGE_SPEED_MULTIPLIER;
    }

    public static float chargeFoodCost(float charge) {
        return Math.max(0.0F, charge) * 2.5F;
    }

    public static float chargedKickFoodCost(float charge) {
        float safeCharge = Math.max(0.0F, charge);
        if (safeCharge <= BASE_CHARGE_FOOD_THRESHOLD) {
            return chargeFoodCost(safeCharge);
        }
        return BASE_CHARGED_KICK_FOOD_COST
                + (safeCharge - BASE_CHARGE_FOOD_THRESHOLD)
                * OVERCHARGED_KICK_FOOD_COST_PER_CHARGE;
    }

    public static double disintegrationRadius(double kickSpeed, boolean tripleSynergy) {
        double radius = 1.0D + 1.65D * Math.log1p(Math.max(0.0D, kickSpeed));
        return Math.max(1.0D, tripleSynergy ? radius * 1.55D : radius);
    }

    public static double disintegrationDepth(double kickSpeed, int overloadLevel) {
        double depth = 1.0D + Math.max(0.0D, kickSpeed) * 0.9D;
        return Math.max(1.0D, depth * (1.0D + overloadLevel * 0.65D));
    }

    public static double unstableExplosionRadius(double launchSpeed, int unstableLevel) {
        double levelThreeRadius = 3.3D + nonNegative(launchSpeed) * 0.455D;
        return levelThreeRadius * unstableCollisionLevelScale(unstableLevel);
    }

    public static double unstableCollisionLevelScale(int unstableLevel) {
        return Math.max(0, unstableLevel) / 3.0D;
    }

    public static double unstableExplosionVisualScale(double radius) {
        return Math.max(1.0D,
                nonNegative(radius) / UNSTABLE_EXPLOSION_MIN_VISUAL_RADIUS);
    }

    public static boolean shouldSuppressKineticOverloadBlockDrops(
            boolean protectionEnabled, int overloadLevel) {
        return protectionEnabled && overloadLevel > 0;
    }

    public static double irregularDestructionScale(double randomSample) {
        double sample = Double.isNaN(randomSample)
                ? 0.5D
                : Math.max(0.0D, Math.min(1.0D, randomSample));
        return IRREGULAR_DESTRUCTION_MIN_SCALE
                + sample * (IRREGULAR_DESTRUCTION_MAX_SCALE
                        - IRREGULAR_DESTRUCTION_MIN_SCALE);
    }

    public static double destructionCapsuleDistanceSquared(
            double along, double perpendicular, double depth) {
        double safeDepth = nonNegative(depth);
        double axialDistance = along < 0.0D
                ? -along
                : Math.max(0.0D, along - safeDepth);
        return axialDistance * axialDistance + perpendicular * perpendicular;
    }

    public static boolean isAlignedImpact(Vec3 initialVelocity, Vec3 impactVelocity) {
        if (initialVelocity.lengthSqr() < 1.0E-6D || impactVelocity.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return initialVelocity.normalize().dot(impactVelocity.normalize()) >= MIN_IMPACT_ALIGNMENT;
    }

    public static double impactTraversalDistance(
            double kickSpeed,
            double launchSpeed,
            int disintegrationLevel,
            int unstableLevel,
            int overloadLevel) {
        if (disintegrationLevel > 0 && (unstableLevel <= 0 || overloadLevel > 0)) {
            return disintegrationDepth(kickSpeed, overloadLevel);
        }
        return unstableLevel > 0 ? unstableExplosionRadius(launchSpeed, unstableLevel) : 0.0D;
    }

    public static float traversalDamage(double impactSpeed, int overloadLevel) {
        return (float) Math.max(1.0D,
                nonNegative(impactSpeed) * 1.25D * (1.0D + Math.max(0, overloadLevel) * 0.35D));
    }

    public static boolean shouldApplyTraversalDamage(
            int disintegrationLevel, int unstableCollisionLevel) {
        return disintegrationLevel > 0 && unstableCollisionLevel <= 0;
    }

    public static int flightEffectTier(double launchSpeed) {
        double speed = nonNegative(launchSpeed);
        if (speed < 0.65D) {
            return 0;
        }
        if (speed < 1.25D) {
            return 1;
        }
        if (speed < 2.25D) {
            return 2;
        }
        if (speed < MACH_RING_MIN_SPEED) {
            return 3;
        }
        return 4;
    }

    public static double machRingRadiusScale(int emittedRings, double launchSpeed) {
        double growthScale = switch (Math.max(0, emittedRings)) {
            case 0 -> 0.55D;
            case 1 -> 0.78D;
            default -> 1.0D;
        };
        return growthScale * machRingMaximumRadiusScale(launchSpeed);
    }

    public static double machRingMaximumRadiusScale(double launchSpeed) {
        return 1.0D
                + Math.log1p(Math.max(0.0D,
                        nonNegative(launchSpeed) - MACH_RING_MIN_SPEED)) * 0.85D;
    }

    public static int machRingCount(double launchSpeed) {
        double excessSpeed = Math.max(0.0D,
                nonNegative(launchSpeed) - MACH_RING_MIN_SPEED);
        int extraRings = (int) Math.min(MACH_RING_RENDER_LIMIT - 3.0D,
                Math.floor(Math.log1p(excessSpeed) * 4.0D));
        return 3 + extraRings;
    }

    public static double machRingInterval(double launchSpeed) {
        double excessSpeed = Math.max(0.0D,
                nonNegative(launchSpeed) - MACH_RING_MIN_SPEED);
        double intervalScale = 1.0D
                + Math.min(0.5D, Math.log1p(excessSpeed) * 0.16D);
        return MACH_RING_BASE_INTERVAL * intervalScale;
    }

    public static int machRingLifetimeTicks(double launchSpeed) {
        double excessSpeed = Math.max(0.0D,
                nonNegative(launchSpeed) - MACH_RING_MIN_SPEED);
        return (int) Math.min(200.0D,
                Math.round(60.0D + Math.log1p(excessSpeed) * 28.0D));
    }

    public static int machTrailLifetimeTicks(double launchSpeed) {
        return (int) Math.ceil(machRingLifetimeTicks(launchSpeed) / 0.8D);
    }

    public static float smoothFadeScale(int remainingTicks, int fadeTicks) {
        if (remainingTicks <= 0) {
            return 0.0F;
        }
        if (fadeTicks <= 0 || remainingTicks >= fadeTicks) {
            return 1.0F;
        }
        float progress = (float) remainingTicks / fadeTicks;
        return progress * progress * (3.0F - 2.0F * progress);
    }

    public static int disintegrationSmokeParticleCount(
            int affectedBlocks, double impactSpeed) {
        return disintegrationSmokeParticleCount(affectedBlocks, 0, impactSpeed);
    }

    public static int disintegrationSmokeParticleCount(
            int affectedBlocks, int smokeOriginCount, double impactSpeed) {
        double speed = nonNegative(impactSpeed);
        if (affectedBlocks <= 0 || speed <= 0.0D) {
            return 0;
        }
        double speedScale = speed * speed / 6.0D;
        double speedScaledCount = Math.ceil(
                (32.0D + Math.sqrt(affectedBlocks) * 8.0D) * speedScale);
        long coverageCount = (long) Math.max(0, smokeOriginCount)
                * DISINTEGRATION_SMOKE_PER_ORIGIN;
        return (int) Math.min(MAX_DISINTEGRATION_SMOKE_PARTICLES,
                Math.max(speedScaledCount, coverageCount));
    }

    public static double impactDebrisInitialSpeed(double impactSpeed, double variation) {
        double safeVariation = Math.max(0.0D, Math.min(1.0D, variation));
        return 0.24D + nonNegative(impactSpeed)
                * (0.11D + safeVariation * 0.035D);
    }

    public static double disintegrationSmokeInitialSpeed(
            double impactSpeed, double variation) {
        return impactDebrisInitialSpeed(impactSpeed, variation) * 0.35D;
    }

    public static boolean shouldEmitDisintegrationSmoke(
            int disintegrationLevel, int unstableCollisionLevel) {
        return disintegrationLevel > 0 && unstableCollisionLevel <= 0;
    }

    private static double nonNegative(double value) {
        return Double.isNaN(value) ? 0.0D : Math.max(0.0D, value);
    }
}
