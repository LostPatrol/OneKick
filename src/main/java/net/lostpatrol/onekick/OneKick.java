package net.lostpatrol.onekick;

import net.lostpatrol.onekick.config.OneKickConfig;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModEnchantments;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.lostpatrol.onekick.registry.ModParticleTypes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(OneKick.MOD_ID)
public final class OneKick {
    public static final String MOD_ID = "onekick";

    public OneKick() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, OneKickConfig.SERVER_SPEC);
        ModEnchantments.register(modBus);
        ModEntityTypes.register(modBus);
        ModParticleTypes.register(modBus);
        modBus.addListener(this::commonSetup);
        KickNetwork.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModEnchantments::enableBootSilkTouch);
    }
}
