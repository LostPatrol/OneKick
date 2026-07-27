package net.lostpatrol.onekick.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.lostpatrol.onekick.config.OneKickConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class OneKickCommands {
    private OneKickCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("onekick")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("kinetic_overload_drop_protection")
                        .executes(context -> showKineticOverloadDropProtection(context.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setKineticOverloadDropProtection(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static int showKineticOverloadDropProtection(CommandSourceStack source) {
        boolean enabled = OneKickConfig.suppressKineticOverloadBlockDrops();
        source.sendSuccess(() -> Component.translatable(
                "commands.onekick.kinetic_overload_drop_protection.status",
                stateName(enabled)), false);
        return 1;
    }

    private static int setKineticOverloadDropProtection(CommandSourceStack source, boolean enabled) {
        OneKickConfig.setSuppressKineticOverloadBlockDrops(enabled);
        source.sendSuccess(() -> Component.translatable(
                "commands.onekick.kinetic_overload_drop_protection.updated",
                stateName(enabled)), true);
        return 1;
    }

    private static Component stateName(boolean enabled) {
        return Component.translatable(enabled
                ? "commands.onekick.kinetic_overload_drop_protection.enabled"
                : "commands.onekick.kinetic_overload_drop_protection.disabled");
    }
}
