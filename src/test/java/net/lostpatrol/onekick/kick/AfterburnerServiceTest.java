/** Verifies Afterburner's strength scaling, continuous sampling, and per-kick counting. */
package net.lostpatrol.onekick.kick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class AfterburnerServiceTest {
    /** Strong kicks extend burning in direct proportion to their additional speed. */
    @Test
    void burningDurationGrowsWithKickStrength() {
        assertEquals(2.0F, AfterburnerService.burnSeconds(0.0D));
        assertEquals(4.0F, AfterburnerService.burnSeconds(1.0D));
        assertEquals(14.0F, AfterburnerService.burnSeconds(6.0D));
        assertEquals(2.0F, AfterburnerService.burnSeconds(Double.NaN));
        assertEquals(2.0F, AfterburnerService.burnSeconds(-1.0D));
    }

    /** Normal high-speed segments retain half-block spacing instead of endpoint-only effects. */
    @Test
    void fastFlightIsSampledAcrossTheWholeSegment() {
        assertEquals(1, AfterburnerService.trailSampleCount(0.0D));
        assertEquals(1, AfterburnerService.trailSampleCount(0.1D));
        assertEquals(60, AfterburnerService.trailSampleCount(30.0D));
        assertTrue(AfterburnerService.trailSampleCount(Double.MAX_VALUE) <= 2048);
    }

    /** Flight and delayed impacts must count distinct placements toward the strict threshold. */
    @Test
    void trailblazerRequiresOneHundredAndOneDistinctIgnitions() {
        KickProgress progress = new KickProgress();
        for (int x = 0; x < 100; x++) {
            assertFalse(progress.recordIgnitedBlock(new BlockPos(x, 64, 0)));
        }
        assertEquals(100, progress.ignitedBlockCount());
        assertFalse(progress.recordIgnitedBlock(new BlockPos(0, 64, 0)));
        assertTrue(progress.recordIgnitedBlock(new BlockPos(100, 64, 0)));
        assertFalse(progress.recordIgnitedBlock(new BlockPos(101, 64, 0)));
        assertFalse(progress.recordIgnitedBlock(new BlockPos(100, 64, 0)));
        assertEquals(102, progress.ignitedBlockCount());
        assertEquals(0, new KickProgress().ignitedBlockCount());
    }
}
