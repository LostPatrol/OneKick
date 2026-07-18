package net.lostpatrol.onekick.kick;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class KickMath {
    public static final double BASE_SPEED = 1.0D;
    public static final double MOVEMENT_SPEED_COEFFICIENT = 1.8D;
    public static final double MAX_SPEED = 20.0D;
    public static final double MAX_LAUNCH_SPEED = 3.75D;
    public static final double KICK_REACH = 3.5D;
    public static final int COOLDOWN_TICKS = 12;
    public static final int MAX_AFFECTED_BLOCKS = 512;
    public static final int MAX_DEBRIS_BLOCKS = 64;
    public static final int MAX_CHAIN_EXPLOSIONS = 12;

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
        double safeEquipment = finiteOr(equipmentMultiplier, 1.0D);
        double safeMovement = Math.max(0.0D, finiteOr(movementSpeed, 0.0D));
        double safeCharge = Math.max(0.0D, finiteOr(charge, 0.0D));
        int safeOverload = Mth.clamp(overloadLevel, 0, 3);
        double chargeMultiplier = 1.0D + safeCharge * 0.4D;
        double specialMultiplier = 1.0D + safeOverload * 0.55D;
        double speed = (BASE_SPEED * safeEquipment + safeMovement * MOVEMENT_SPEED_COEFFICIENT)
                * chargeMultiplier * specialMultiplier;
        return Mth.clamp(finiteOr(speed, 0.0D), 0.0D, MAX_SPEED);
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
            return 1.0D + Mth.clamp(armor.getDefense(), 0, 10) * 0.07D;
        }
        return 1.0D;
    }

    public static double launchSpeed(double kickSpeed, LivingEntity target) {
        double healthFactor = Math.sqrt(20.0D / Math.max(1.0D, target.getMaxHealth()));
        healthFactor = Mth.clamp(healthFactor, 0.18D, 1.25D);
        double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        return Mth.clamp(kickSpeed * 0.82D * healthFactor * (1.0D - resistance * 0.65D),
                0.0D, MAX_LAUNCH_SPEED);
    }

    public static float directDamage(double kickSpeed, float charge, int overloadLevel) {
        double chargedDamage = charge >= 1.5F ? Math.max(0.0D, kickSpeed - 1.5D) * 0.8D : 0.0D;
        double overloadDamage = overloadLevel > 0 ? kickSpeed * overloadLevel * 0.7D : 0.0D;
        return (float) Math.min(40.0D, chargedDamage + overloadDamage);
    }

    public static float collisionDamage(double beforeSpeed, double afterSpeed, int overloadLevel) {
        double lostSpeed = Math.max(0.0D, beforeSpeed - afterSpeed);
        double vanillaLike = Math.max(0.0D, lostSpeed * 10.0D - 3.0D);
        return (float) Math.min(80.0D, vanillaLike * (1.0D + overloadLevel * 1.25D));
    }

    public static float maxCharge(int level) {
        return level <= 0 ? 0.0F : 1.0F + level;
    }

    public static float chargePerTick(int level) {
        return level <= 0 ? 0.0F : 0.0125F * (1.0F + level * 0.5F);
    }

    public static float chargeFoodCost(float charge) {
        return Math.max(0.0F, charge) * 2.5F;
    }

    public static double disintegrationRadius(double kickSpeed, boolean tripleSynergy) {
        double radius = 0.75D + 1.25D * Math.log1p(Math.max(0.0D, kickSpeed));
        return Mth.clamp(tripleSynergy ? radius * 1.45D : radius, 1.0D, 6.0D);
    }

    public static double disintegrationDepth(double kickSpeed, int overloadLevel) {
        double depth = 1.0D + Math.max(0.0D, kickSpeed) * 0.9D;
        return Mth.clamp(depth * (1.0D + overloadLevel * 0.65D), 1.0D, 32.0D);
    }

    public static float explosionPower(double kickSpeed, int unstableLevel) {
        return (float) Mth.clamp(0.9D + unstableLevel * 0.7D + Math.sqrt(Math.max(0.0D, kickSpeed)) * 0.5D,
                1.0D, 7.0D);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
