package net.lostpatrol.onekick;

import net.lostpatrol.onekick.advancement.ModCriteriaTriggers;
import net.lostpatrol.onekick.config.OneKickConfig;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.lostpatrol.onekick.registry.ModParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(OneKick.MOD_ID)
public final class OneKick {
    public static final String MOD_ID = "onekick";

    public OneKick(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, OneKickConfig.SERVER_SPEC);
        ModEntityTypes.register(modBus);
        ModParticleTypes.register(modBus);
        ModCriteriaTriggers.register(modBus);
        modBus.addListener(KickNetwork::register);
    }
}
