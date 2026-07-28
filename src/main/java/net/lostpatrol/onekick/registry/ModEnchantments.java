package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ModEnchantments {
    public static final ResourceKey<Enchantment> REACTION = key("reaction");
    public static final ResourceKey<Enchantment> AERODYNAMICS = key("aerodynamics");
    public static final ResourceKey<Enchantment> DISINTEGRATION = key("disintegration");
    public static final ResourceKey<Enchantment> UNSTABLE_COLLISION = key("unstable_collision");
    public static final ResourceKey<Enchantment> KINETIC_OVERLOAD = key("kinetic_overload");
    public static final ResourceKey<Enchantment> CHARGE = key("charge");
    public static final ResourceKey<Enchantment> ANGULAR_MOMENTUM = key("angular_momentum");
    public static final ResourceKey<Enchantment> OVERCHARGE = key("overcharge");

    public static final int MAX_DISINTEGRATION_LEVEL = 1;
    public static final int MAX_UNSTABLE_COLLISION_LEVEL = 3;
    public static final int MAX_KINETIC_OVERLOAD_LEVEL = 3;
    public static final int MAX_CHARGE_LEVEL = 5;
    public static final int MAX_OVERCHARGE_LEVEL = 2;

    private ModEnchantments() {
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, name));
    }
}
