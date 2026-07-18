package net.lostpatrol.onekick.kick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KickMathTest {
    @Test
    void baseKickIsStableAndStationaryMovementAddsNothing() {
        assertEquals(1.0D, KickMath.calculateFinalSpeed(1.0D, 0.0D, 0.0D, 0), 1.0E-9D);
    }

    @Test
    void movementChargeAndOverloadUseSeparateMultipliers() {
        double expected = (1.0D * 1.5D + 0.25D * 1.8D) * (1.0D + 2.0D * 0.4D) * (1.0D + 0.55D);
        assertEquals(expected, KickMath.calculateFinalSpeed(1.5D, 0.25D, 2.0D, 1), 1.0E-9D);
    }

    @Test
    void unsafeInputsAreClamped() {
        assertEquals(KickMath.MAX_SPEED,
                KickMath.calculateFinalSpeed(1000.0D, 1000.0D, 1000.0D, 99), 1.0E-9D);
        assertEquals(1.0D,
                KickMath.calculateFinalSpeed(Double.NaN, -10.0D, -4.0D, -3), 1.0E-9D);
    }

    @Test
    void levelFiveChargeHasSpecifiedCapacityAndFoodCost() {
        assertEquals(6.0F, KickMath.maxCharge(5), 1.0E-6F);
        assertEquals(15.0F, KickMath.chargeFoodCost(KickMath.maxCharge(5)), 1.0E-6F);
        assertEquals(0.04375F, KickMath.chargePerTick(5), 1.0E-6F);
    }

    @Test
    void disintegrationRadiusHasDiminishingReturns() {
        double earlyGain = KickMath.disintegrationRadius(2.0D, false)
                - KickMath.disintegrationRadius(1.0D, false);
        double lateGain = KickMath.disintegrationRadius(10.0D, false)
                - KickMath.disintegrationRadius(9.0D, false);
        assertTrue(earlyGain > lateGain);
    }
}
