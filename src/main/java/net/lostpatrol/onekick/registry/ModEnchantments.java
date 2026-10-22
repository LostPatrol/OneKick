/** Registry keys and level limits for the boot enchantments. */
package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ModEnchantments {
    /** Retaliation kick enchantment. */
    public static final ResourceKey<Enchantment> REACTION = key("reaction");
    /** Air-kick flight enchantment. */
    public static final ResourceKey<Enchantment> AERODYNAMICS = key("aerodynamics");
    /** Launched-entity block destruction enchantment. */
    public static final ResourceKey<Enchantment> DISINTEGRATION = key("disintegration");
    /** Impact explosion enchantment. */
    public static final ResourceKey<Enchantment> UNSTABLE_COLLISION = key("unstable_collision");
    /** Destructive launch amplification enchantment. */
    public static final ResourceKey<Enchantment> KINETIC_OVERLOAD = key("kinetic_overload");
    /** Charged kick enchantment. */
    public static final ResourceKey<Enchantment> CHARGE = key("charge");
    /** Spin enchantment. */
    public static final ResourceKey<Enchantment> ANGULAR_MOMENTUM = key("angular_momentum");
    /** Additional charging capacity enchantment. */
    public static final ResourceKey<Enchantment> OVERCHARGE = key("overcharge");
    /** Treasure enchantment that unlocks unrestricted destruction at full charge. */
    public static final ResourceKey<Enchantment> SINGULARITY = key("singularity");
    /** Ignition and fire-trail enchantment. */
    public static final ResourceKey<Enchantment> AFTERBURNER = key("afterburner");

    /** Maximum supported Disintegration level. */
    public static final int MAX_DISINTEGRATION_LEVEL = 1;
    /** Maximum supported Unstable Collision level. */
    public static final int MAX_UNSTABLE_COLLISION_LEVEL = 3;
    /** Maximum supported Kinetic Overload level. */
    public static final int MAX_KINETIC_OVERLOAD_LEVEL = 3;
    /** Maximum supported Charge level. */
    public static final int MAX_CHARGE_LEVEL = 5;
    /** Maximum supported Overcharge level. */
    public static final int MAX_OVERCHARGE_LEVEL = 2;

    /** Prevents instances of this registry-key holder. */
    private ModEnchantments() {
    }

    /** Creates a key in the mod's enchantment namespace. */
    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, name));
    }
}
