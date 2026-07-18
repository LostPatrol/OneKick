package net.lostpatrol.onekick.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public final class BootEnchantment extends Enchantment {
    private final int maxLevel;
    private final int baseCost;
    private final int levelCost;

    public BootEnchantment(Rarity rarity, int maxLevel, int baseCost, int levelCost) {
        super(rarity, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.levelCost = levelCost;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public int getMinCost(int level) {
        return baseCost + (level - 1) * levelCost;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }
}
