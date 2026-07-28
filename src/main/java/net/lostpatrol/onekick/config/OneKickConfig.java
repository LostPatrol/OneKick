package net.lostpatrol.onekick.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OneKickConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue SUPPRESS_KINETIC_OVERLOAD_BLOCK_DROPS =
            BUILDER.comment(
                            "Suppress block drops from impacts caused by boots",
                            "with Kinetic Overload. Container contents still drop normally.")
                    .define("performance.suppressKineticOverloadBlockDrops", true);

    public static final ModConfigSpec SERVER_SPEC = BUILDER.build();

    private OneKickConfig() {
    }

    public static boolean suppressKineticOverloadBlockDrops() {
        return SUPPRESS_KINETIC_OVERLOAD_BLOCK_DROPS.get();
    }

    public static void setSuppressKineticOverloadBlockDrops(boolean enabled) {
        SUPPRESS_KINETIC_OVERLOAD_BLOCK_DROPS.set(enabled);
        SUPPRESS_KINETIC_OVERLOAD_BLOCK_DROPS.save();
    }
}
