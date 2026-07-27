package net.lostpatrol.onekick.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class OneKickConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue SUPPRESS_KINETIC_OVERLOAD_BLOCK_DROPS =
            BUILDER.comment(
                            "Suppress block drops from impacts caused by boots",
                            "with Kinetic Overload. Container contents still drop normally.")
                    .define("performance.suppressKineticOverloadBlockDrops", true);

    public static final ForgeConfigSpec SERVER_SPEC = BUILDER.build();

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
