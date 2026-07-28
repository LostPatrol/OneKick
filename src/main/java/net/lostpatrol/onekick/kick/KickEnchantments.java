package net.lostpatrol.onekick.kick;

import net.lostpatrol.onekick.registry.ModEnchantments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public record KickEnchantments(
        int reaction,
        int aerodynamics,
        int disintegration,
        int unstableCollision,
        int kineticOverload,
        int charge,
        int overcharge,
        int angularMomentum,
        boolean silkTouch
) {
    public static KickEnchantments from(ItemStack boots) {
        if (boots.isEmpty()) {
            return new KickEnchantments(0, 0, 0, 0, 0, 0, 0, 0, false);
        }
        return new KickEnchantments(
                level(boots, ModEnchantments.REACTION),
                level(boots, ModEnchantments.AERODYNAMICS),
                level(boots, ModEnchantments.DISINTEGRATION),
                level(boots, ModEnchantments.UNSTABLE_COLLISION),
                level(boots, ModEnchantments.KINETIC_OVERLOAD),
                level(boots, ModEnchantments.CHARGE),
                level(boots, ModEnchantments.OVERCHARGE),
                level(boots, ModEnchantments.ANGULAR_MOMENTUM),
                level(boots, Enchantments.SILK_TOUCH) > 0
        );
    }

    private static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
