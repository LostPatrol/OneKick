package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.Set;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.lostpatrol.onekick.registry.ModParticleTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
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
    private static boolean firstPersonLegRendered;

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = OneKick.MOD_ID, value = Dist.CLIENT)
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
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticleTypes.MACH_RING.get(), MachRingParticle.Provider::new);
            event.registerSpriteSet(ModParticleTypes.MACH_TRAIL.get(), MachTrailParticle.Provider::new);
            event.registerSpriteSet(ModParticleTypes.DISINTEGRATION_SMOKE.get(),
                    DisintegrationSmokeParticle.Provider::new);
        }

        @SubscribeEvent
        public static void replacePlayerModels(EntityRenderersEvent.AddLayers event) {
            FirstPersonKickRenderer.initialize();
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    boolean slim = skin == PlayerSkin.Model.SLIM;
                    ObfuscationReflectionHelper.setPrivateValue(
                            LivingEntityRenderer.class,
                            renderer,
                            new KickPlayerModel(event.getEntityModels().bakeLayer(
                                    slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim),
                            "model");
                }
            }
        }
    }

    @EventBusSubscriber(modid = OneKick.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean keyDown = minecraft.player != null
                    && minecraft.getConnection() != null
                    && minecraft.screen == null
                    && KICK_KEY.isDown();
            if (keyDown != keyWasDown && minecraft.getConnection() != null) {
                KickNetwork.sendInput(keyDown);
            }
            keyWasDown = keyDown;
            ClientKickState.tickParticles(minecraft);
        }

        @SubscribeEvent
        public static void renderTick(RenderFrameEvent.Pre event) {
            firstPersonLegRendered = false;
        }

        @SubscribeEvent
        public static void renderHand(RenderHandEvent event) {
            if (!firstPersonLegRendered && FirstPersonKickRenderer.render(event)) {
                firstPersonLegRendered = true;
            }
            if (firstPersonLegRendered && FirstPersonKickRenderer.isRightHand(event)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderHud(RenderGuiLayerEvent.Pre event) {
            if (!ClientKickState.shouldRenderChargeHud()) {
                return;
            }
            if (event.getName().equals(VanillaGuiLayers.JUMP_METER)) {
                event.setCanceled(true);
            } else if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
                ClientKickState.renderChargeHud(event.getGuiGraphics(),
                        event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
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
            firstPersonLegRendered = false;
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
