package net.lostpatrol.onekick.kick;

import net.lostpatrol.onekick.registry.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public record KickEnchantments(
        int reaction,
        int aerodynamics,
        int disintegration,
        int unstableCollision,
        int kineticOverload,
        int charge,
        int angularMomentum,
        boolean silkTouch
) {
    public static KickEnchantments from(ItemStack boots) {
        if (boots.isEmpty()) {
            return new KickEnchantments(0, 0, 0, 0, 0, 0, 0, false);
        }
        return new KickEnchantments(
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.REACTION.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.AERODYNAMICS.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DISINTEGRATION.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.UNSTABLE_COLLISION.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.KINETIC_OVERLOAD.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.CHARGE.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ANGULAR_MOMENTUM.get(), boots),
                EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, boots) > 0
        );
    }
}
