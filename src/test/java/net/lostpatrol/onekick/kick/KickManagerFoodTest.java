package net.lostpatrol.onekick.kick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.food.FoodData;
import org.junit.jupiter.api.Test;

class KickManagerFoodTest {
    @Test
    void chargeCostConsumesSaturationBeforeHunger() {
        FoodData food = foodData(10, 3.5F);

        assertEquals(5, KickManager.consumeChargeFood(food, 5));
        assertEquals(0.0F, food.getSaturationLevel(), 1.0E-6F);
        assertEquals(9, food.getFoodLevel());
    }

    @Test
    void partialSaturationPaysOneVanillaExhaustionCycle() {
        FoodData food = foodData(2, 1.5F);

        assertEquals(4, KickManager.availableChargeFood(food));
        assertEquals(4, KickManager.consumeChargeFood(food, 5));
        assertEquals(0.0F, food.getSaturationLevel(), 1.0E-6F);
        assertEquals(0, food.getFoodLevel());
    }

    @Test
    void maximumOverchargeKickCostsThirtyFiveButChargingCanExceedFullBars() {
        FoodData food = foodData(20, 20.0F);
        float maximum = KickMath.maxCharge(5, 2);
        int chargingCost = (int) Math.ceil(KickMath.chargeFoodCost(maximum));
        int releaseCost = (int) Math.ceil(KickMath.chargedKickFoodCost(maximum));

        assertTrue(chargingCost > KickManager.availableChargeFood(food));
        assertEquals(45, chargingCost);
        assertEquals(35, KickManager.consumeChargeFood(food, releaseCost));
        assertEquals(0.0F, food.getSaturationLevel(), 1.0E-6F);
        assertEquals(5, food.getFoodLevel());
    }

    private static FoodData foodData(int hunger, float saturation) {
        FoodData food = new FoodData();
        food.setFoodLevel(hunger);
        food.setSaturation(saturation);
        return food;
    }
}
