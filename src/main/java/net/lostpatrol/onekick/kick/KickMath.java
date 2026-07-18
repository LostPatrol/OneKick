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
    public static final int COOLDOWN_TICKS = 12;
    public static final float CHARGE_SPEED_MULTIPLIER = 6.0F;
    private static final double CHARGE_QUADRATIC_COEFFICIENT = 1.0D / 15.0D;
    private static final double PROJECTILE_AIR_DRAG = 0.99D;
    private static final double PROJECTILE_WATER_DRAG = 0.8D;
    private static final double PROJECTILE_GRAVITY = 0.03D;
    private static final double MIN_IMPACT_ALIGNMENT = Math.cos(Math.toRadians(35.0D));

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

    public static Vec3 nextFlightVelocity(Vec3 velocity, boolean inWater, boolean noGravity) {
        double drag = inWater ? PROJECTILE_WATER_DRAG : PROJECTILE_AIR_DRAG;
        Vec3 next = velocity.scale(drag);
        return noGravity ? next : next.add(0.0D, -PROJECTILE_GRAVITY, 0.0D);
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

    public static double disintegrationRadius(double kickSpeed, boolean tripleSynergy) {
        double radius = 1.0D + 1.65D * Math.log1p(Math.max(0.0D, kickSpeed));
        return Math.max(1.0D, tripleSynergy ? radius * 1.55D : radius);
    }

    public static double disintegrationDepth(double kickSpeed, int overloadLevel) {
        double depth = 1.0D + Math.max(0.0D, kickSpeed) * 0.9D;
        return Math.max(1.0D, depth * (1.0D + overloadLevel * 0.65D));
    }

    public static float explosionPower(double kickSpeed, int unstableLevel) {
        return (float) Math.max(1.0D,
                0.9D + unstableLevel * 0.7D + Math.sqrt(Math.max(0.0D, kickSpeed)) * 0.75D);
    }

    public static boolean isAlignedImpact(Vec3 initialVelocity, Vec3 impactVelocity) {
        if (initialVelocity.lengthSqr() < 1.0E-6D || impactVelocity.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return initialVelocity.normalize().dot(impactVelocity.normalize()) >= MIN_IMPACT_ALIGNMENT;
    }

    public static double impactTraversalDistance(
            double kickSpeed, int disintegrationLevel, int unstableLevel, int overloadLevel) {
        if (disintegrationLevel > 0 && (unstableLevel <= 0 || overloadLevel > 0)) {
            return disintegrationDepth(kickSpeed, overloadLevel);
        }
        return unstableLevel > 0 ? Math.max(2.0D, explosionPower(kickSpeed, unstableLevel) * 1.35D) : 0.0D;
    }

    public static float traversalDamage(double impactSpeed, int overloadLevel) {
        return (float) Math.max(1.0D,
                nonNegative(impactSpeed) * 1.25D * (1.0D + Math.max(0, overloadLevel) * 0.35D));
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
        if (speed < 3.25D) {
            return 3;
        }
        return 4;
    }

    private static double nonNegative(double value) {
        return Double.isNaN(value) ? 0.0D : Math.max(0.0D, value);
    }
}
