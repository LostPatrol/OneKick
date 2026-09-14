/** Optional CPM bridge that renders the local player's custom right leg in first person. */
package net.lostpatrol.onekick.client.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tom.cpm.api.ICPMPlugin;
import com.tom.cpm.api.IClientAPI;
import com.tom.cpm.api.ICommonAPI;
import com.tom.cpm.shared.animation.AnimationEngine.AnimationMode;
import java.util.function.Supplier;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.client.ClientKickState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.InterModComms;

public final class CpmCompatibility {
    private static IClientAPI.PlayerRenderer<
            Model, ResourceLocation, RenderType, MultiBufferSource, GameProfile> renderer;
    private static PlayerModel<AbstractClientPlayer> model;

    private CpmCompatibility() {
    }

    /** Registers the bridge through CPM's backward-compatible Forge/NeoForge IMC entry point. */
    public static void enqueuePlugin() {
        InterModComms.sendTo("cpm", "api", () -> (Supplier<?>) Plugin::new);
    }

    /**
     * Renders only the CPM right-leg root. Returning false preserves the original vanilla path.
     */
    public static boolean renderFirstPersonLeg(
            RenderHandEvent event, AbstractClientPlayer player) {
        if (renderer == null) {
            return false;
        }
        if (model == null) {
            model = new PlayerModel<>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ModelLayers.PLAYER), false);
            model.setAllVisible(false);
            model.rightLeg.visible = true;
            model.rightLeg.setPos(0.0F, 0.0F, 0.0F);
        }

        model.rightLeg.resetPose();
        model.rightLeg.setPos(0.0F, 0.0F, 0.0F);
        if (!ClientKickState.applyPlayerPose(
                player, model.rightLeg, model.rightPants, event.getPartialTick())) {
            return false;
        }

        renderer.setGameProfile(player.getGameProfile());
        renderer.setRenderModel(model);
        renderer.setRenderType(RenderType::entityTranslucent);
        renderer.preRender(event.getMultiBufferSource(), AnimationMode.HAND);
        PoseStack poseStack = event.getPoseStack();
        boolean posePushed = false;
        try {
            if (renderer.getDefaultTexture() == null) {
                return false;
            }

            // Match the established vanilla first-person placement exactly.
            poseStack.pushPose();
            posePushed = true;
            poseStack.translate(0.14F, -0.22F,
                    ClientKickState.firstPersonLegDepth(player, event.getPartialTick()));
            poseStack.scale(-0.90F, -0.90F, 0.90F);
            model.rightLeg.render(
                    poseStack,
                    event.getMultiBufferSource().getBuffer(renderer.getDefaultRenderType()),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            return true;
        } finally {
            if (posePushed) {
                poseStack.popPose();
            }
            renderer.postRender();
        }
    }

    /** CPM constructs this plugin only after its own API is available. */
    private static final class Plugin implements ICPMPlugin {
        @Override
        public void initClient(IClientAPI api) {
            renderer = api.createPlayerRenderer(
                    Model.class,
                    ResourceLocation.class,
                    RenderType.class,
                    MultiBufferSource.class,
                    GameProfile.class);
        }

        @Override
        public void initCommon(ICommonAPI api) {
        }

        @Override
        public String getOwnerModId() {
            return OneKick.MOD_ID;
        }
    }
}
