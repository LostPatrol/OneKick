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
    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OneKick.MOD_ID);
    private static final EnchantmentCategory SILK_TOUCH_WITH_BOOTS = EnchantmentCategory.create(
            "ONEKICK_SILK_TOUCH_WITH_BOOTS",
            item -> EnchantmentCategory.DIGGER.canEnchant(item) || isBoot(item)
    );

    public static final RegistryObject<Enchantment> REACTION = register(
            "reaction", Enchantment.Rarity.COMMON, 1, 5, 8);
    public static final RegistryObject<Enchantment> AERODYNAMICS = register(
            "aerodynamics", Enchantment.Rarity.UNCOMMON, 3, 15, 10);
    public static final RegistryObject<Enchantment> DISINTEGRATION = register(
            "disintegration", Enchantment.Rarity.UNCOMMON, 1, 18, 12);
    public static final RegistryObject<Enchantment> UNSTABLE_COLLISION = register(
            "unstable_collision", Enchantment.Rarity.VERY_RARE, 3, 28, 10);
    public static final RegistryObject<Enchantment> KINETIC_OVERLOAD = register(
            "kinetic_overload", Enchantment.Rarity.VERY_RARE, 3, 30, 10);
    public static final RegistryObject<Enchantment> CHARGE = register(
            "charge", Enchantment.Rarity.UNCOMMON, 5, 12, 7);
    public static final RegistryObject<Enchantment> ANGULAR_MOMENTUM = register(
            "angular_momentum", Enchantment.Rarity.UNCOMMON, 1, 18, 12);
    public static final RegistryObject<Enchantment> OVERCHARGE = register(
            "overcharge", Enchantment.Rarity.VERY_RARE, 2, 32, 18);

    private ModEnchantments() {
    }

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }

    public static void enableBootSilkTouch() {
        Enchantments.SILK_TOUCH.category = SILK_TOUCH_WITH_BOOTS;
    }

    private static RegistryObject<Enchantment> register(
            String name, Enchantment.Rarity rarity, int maxLevel, int baseCost, int levelCost) {
        return ENCHANTMENTS.register(name, () -> new BootEnchantment(rarity, maxLevel, baseCost, levelCost));
    }

    private static boolean isBoot(Item item) {
        return item instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.FEET;
    }
}
