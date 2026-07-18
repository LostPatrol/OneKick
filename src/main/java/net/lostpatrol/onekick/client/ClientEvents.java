package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.Set;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping KICK_KEY = new KeyMapping(
            "key.onekick.kick",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.onekick"
    );
    private static final Set<Integer> SPIN_POSES = new HashSet<>();
    private static boolean keyWasDown;

    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = OneKick.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KICK_KEY);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.IMPACT_DEBRIS.get(), FallingBlockRenderer::new);
        }

        @SubscribeEvent
        public static void replacePlayerModels(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                if (event.getPlayerSkin(skin) instanceof PlayerRenderer renderer) {
                    boolean slim = "slim".equals(skin);
                    renderer.model = new KickPlayerModel(event.getEntityModels().bakeLayer(
                            slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = OneKick.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            boolean keyDown = minecraft.player != null
                    && minecraft.getConnection() != null
                    && minecraft.screen == null
                    && KICK_KEY.isDown();
            if (keyDown != keyWasDown && minecraft.getConnection() != null) {
                KickNetwork.sendInput(keyDown);
            }
            keyWasDown = keyDown;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderHud(RenderGuiOverlayEvent.Pre event) {
            if (!ClientKickState.shouldRenderChargeHud()) {
                return;
            }
            if (event.getOverlay().id().equals(VanillaGuiOverlay.JUMP_BAR.id())) {
                event.setCanceled(true);
            } else if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
                ClientKickState.renderChargeHud(event.getGuiGraphics(),
                        event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void beforeLivingRender(RenderLivingEvent.Pre<?, ?> event) {
            if (event.isCanceled() || !ClientKickState.shouldSpin(event.getEntity())) {
                return;
            }
            SPIN_POSES.add(event.getEntity().getId());
            event.getPoseStack().pushPose();
            double center = event.getEntity().getBbHeight() * 0.5D;
            event.getPoseStack().translate(0.0D, center, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(
                    ClientKickState.spinAngle(event.getEntity(), event.getPartialTick())));
            event.getPoseStack().translate(0.0D, -center, 0.0D);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void afterLivingRender(RenderLivingEvent.Post<?, ?> event) {
            if (SPIN_POSES.remove(event.getEntity().getId())) {
                event.getPoseStack().popPose();
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            keyWasDown = false;
            SPIN_POSES.clear();
            ClientKickState.clear();
        }

        @SubscribeEvent
        public static void entityLeaveLevel(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide) {
                ClientKickState.removeEntity(event.getEntity().getId());
            }
        }
    }
}
