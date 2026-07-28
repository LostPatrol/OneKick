package net.lostpatrol.onekick.advancement;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.kick.KickSnapshot;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteriaTriggers {
    public static final int MASS_DESTRUCTION_BLOCKS = 10_000;
    public static final float MASSIVE_DAMAGE = 500.0F;
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, OneKick.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, KickAdvancementTrigger> KICK_EVENT =
            TRIGGERS.register("kick_event", KickAdvancementTrigger::new);

    private ModCriteriaTriggers() {
    }

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public static void trigger(ServerPlayer player, KickAdvancementTrigger.Event event) {
        KICK_EVENT.get().trigger(player, event);
    }

    public static void trigger(
            ServerLevel level, KickSnapshot snapshot, KickAdvancementTrigger.Event event) {
        ServerPlayer attacker = snapshot.attacker(level);
        if (attacker != null) {
            trigger(attacker, event);
        }
    }
}
