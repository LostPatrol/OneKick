package net.lostpatrol.onekick.advancement;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.kick.KickSnapshot;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ModCriteriaTriggers {
    public static final int MASS_DESTRUCTION_BLOCKS = 10_000;
    public static final float MASSIVE_DAMAGE = 500.0F;
    public static final KickAdvancementTrigger KICK_EVENT = CriteriaTriggers.register(
            new KickAdvancementTrigger(
                    ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, "kick_event")));

    private ModCriteriaTriggers() {
    }

    public static void register() {
    }

    public static void trigger(ServerPlayer player, KickAdvancementTrigger.Event event) {
        KICK_EVENT.trigger(player, event);
    }

    public static void trigger(
            ServerLevel level, KickSnapshot snapshot, KickAdvancementTrigger.Event event) {
        ServerPlayer attacker = snapshot.attacker(level);
        if (attacker != null) {
            trigger(attacker, event);
        }
    }
}
