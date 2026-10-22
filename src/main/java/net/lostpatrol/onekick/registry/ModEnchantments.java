/** Registers the boot enchantments and optional Silk Touch support. */
package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.enchantment.BootEnchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    /** Deferred Forge enchantment registry. */
    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OneKick.MOD_ID);
    /** Allows vanilla tools and boots to receive Silk Touch. */
    private static final EnchantmentCategory SILK_TOUCH_WITH_BOOTS = EnchantmentCategory.create(
            "ONEKICK_SILK_TOUCH_WITH_BOOTS",
            item -> EnchantmentCategory.DIGGER.canEnchant(item) || isBoot(item)
    );

    /** Retaliation kick enchantment. */
    public static final RegistryObject<Enchantment> REACTION = register(
            "reaction", Enchantment.Rarity.COMMON, 1, 5, 8);
    /** Air-kick flight enchantment. */
    public static final RegistryObject<Enchantment> AERODYNAMICS = register(
            "aerodynamics", Enchantment.Rarity.UNCOMMON, 3, 15, 10);
    /** Launched-entity block destruction enchantment. */
    public static final RegistryObject<Enchantment> DISINTEGRATION = register(
            "disintegration", Enchantment.Rarity.UNCOMMON, 1, 18, 12);
    /** Impact explosion enchantment. */
    public static final RegistryObject<Enchantment> UNSTABLE_COLLISION = register(
            "unstable_collision", Enchantment.Rarity.VERY_RARE, 3, 28, 10);
    /** Destructive launch amplification enchantment. */
    public static final RegistryObject<Enchantment> KINETIC_OVERLOAD = register(
            "kinetic_overload", Enchantment.Rarity.VERY_RARE, 3, 30, 10);
    /** Charged kick enchantment. */
    public static final RegistryObject<Enchantment> CHARGE = register(
            "charge", Enchantment.Rarity.UNCOMMON, 5, 12, 7);
    /** Spin enchantment. */
    public static final RegistryObject<Enchantment> ANGULAR_MOMENTUM = register(
            "angular_momentum", Enchantment.Rarity.UNCOMMON, 1, 18, 12);
    /** Additional charging capacity enchantment. */
    public static final RegistryObject<Enchantment> OVERCHARGE = register(
            "overcharge", Enchantment.Rarity.VERY_RARE, 2, 32, 18);
    /** Treasure-only boot enchantment that unlocks unrestricted destruction at full charge. */
    public static final RegistryObject<Enchantment> SINGULARITY = ENCHANTMENTS.register(
            "singularity", () -> new BootEnchantment(
                    Enchantment.Rarity.VERY_RARE, 1, 40, 0, 25, true));
    /** Rare boot enchantment that adds entity fire and flight trails. */
    public static final RegistryObject<Enchantment> AFTERBURNER = ENCHANTMENTS.register(
            "afterburner", () -> new BootEnchantment(
                    Enchantment.Rarity.RARE, 1, 25, 0, 20, false));

    /** Prevents instances of this registry holder. */
    private ModEnchantments() {
    }

    /** Attaches enchantment registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }

    /** Extends vanilla Silk Touch compatibility to boots. */
    public static void enableBootSilkTouch() {
        Enchantments.SILK_TOUCH.category = SILK_TOUCH_WITH_BOOTS;
    }

    /** Registers an ordinary boot enchantment with the established cost range. */
    private static RegistryObject<Enchantment> register(
            String name, Enchantment.Rarity rarity, int maxLevel, int baseCost, int levelCost) {
        return ENCHANTMENTS.register(name, () -> new BootEnchantment(rarity, maxLevel, baseCost, levelCost));
    }

    /** Tests whether the armor item occupies the feet slot. */
    private static boolean isBoot(Item item) {
        return item instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.FEET;
    }
}
