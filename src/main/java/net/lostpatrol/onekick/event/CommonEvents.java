package net.lostpatrol.onekick.event;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.kick.KickManager;
import net.lostpatrol.onekick.world.BlockImpactService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OneKick.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            KickManager.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        KickManager.tickLiving(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BlockImpactService.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KickManager.removePlayer(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer) {
            KickManager.syncTracking(observer, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        KickManager.removeLiving(entity);
        if (entity instanceof ServerPlayer player) {
            KickManager.removePlayer(player);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            KickManager.removeLiving(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        KickManager.clearAll();
        BlockImpactService.clear();
    }
}
