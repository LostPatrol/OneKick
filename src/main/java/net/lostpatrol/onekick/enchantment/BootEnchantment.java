/** Configures boot enchantment levels, costs, and treasure acquisition. */
package net.lostpatrol.onekick.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public final class BootEnchantment extends Enchantment {
    private final int maxLevel;
    private final int baseCost;
    private final int levelCost;
    private final int costRange;
    private final boolean treasureOnly;

    /** Preserves the original non-treasure cost range. */
    public BootEnchantment(Rarity rarity, int maxLevel, int baseCost, int levelCost) {
        this(rarity, maxLevel, baseCost, levelCost, 15, false);
    }

    /** Defines a custom cost range and optional treasure-only acquisition. */
    public BootEnchantment(Rarity rarity, int maxLevel, int baseCost, int levelCost,
            int costRange, boolean treasureOnly) {
        super(rarity, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.levelCost = levelCost;
        this.costRange = costRange;
        this.treasureOnly = treasureOnly;
    }

    /** Returns the highest supported enchantment level. */
    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    /** Returns the minimum selection cost for this level. */
    @Override
    public int getMinCost(int level) {
        return baseCost + (level - 1) * levelCost;
    }

    /** Returns the upper end of the selection cost range. */
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + costRange;
    }

    /** Treasure enchantments remain discoverable and tradeable, but skip the table. */
    @Override
    public boolean isTreasureOnly() {
        return treasureOnly;
    }
}
