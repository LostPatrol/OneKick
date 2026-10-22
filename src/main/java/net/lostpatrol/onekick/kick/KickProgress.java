/** Tracks successful effects shared by the flight and delayed impacts of one kick. */
package net.lostpatrol.onekick.kick;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class KickProgress {
    /** Trailblazer requires strictly more than this many distinct fire positions. */
    private static final int TRAILBLAZER_THRESHOLD = 100;
    /** Packed positions prevent repeated ignition from counting twice. */
    private final Set<Long> ignitedBlocks = new HashSet<>();

    /** Records a successful placement and reports the first threshold crossing. */
    public boolean recordIgnitedBlock(BlockPos position) {
        return ignitedBlocks.add(position.asLong())
                && ignitedBlocks.size() == TRAILBLAZER_THRESHOLD + 1;
    }

    /** Returns the number of distinct blocks ignited by this kick. */
    public int ignitedBlockCount() {
        return ignitedBlocks.size();
    }
}
