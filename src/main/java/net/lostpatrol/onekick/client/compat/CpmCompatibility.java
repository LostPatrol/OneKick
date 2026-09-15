/** Optional CPM bridge limited to geometry assigned to the standard right-leg root. */
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
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.InterModComms;

public final class CpmCompatibility {
    private static IClientAPI.PlayerRenderer<
            Model, ResourceLocation, RenderType, MultiBufferSource, GameProfile> firstPersonRenderer;
    private static IClientAPI.PlayerRenderer<
            Model, ResourceLocation, RenderType, MultiBufferSource, GameProfile> thirdPersonRenderer;
    private static PlayerModel<AbstractClientPlayer> firstPersonModel;
    private static PlayerModel<AbstractClientPlayer> thirdPersonModel;
    private static ThirdPersonSession thirdPersonSession;

    private CpmCompatibility() {
    }

    /** Registers the bridge through CPM's supported Forge/NeoForge IMC entry point. */
    public static void enqueuePlugin() {
        InterModComms.sendTo("cpm", "api", () -> (Supplier<?>) Plugin::new);
    }

    /** Renders only CPM's standard right-leg root in the first-person kick position. */
    public static boolean renderFirstPersonLeg(
            RenderHandEvent event, AbstractClientPlayer player) {
        if (firstPersonRenderer == null) {
            return false;
        }
        if (firstPersonModel == null) {
            firstPersonModel = createModel();
        }

        // Avoid binding CPM unless a One Kick pose is currently active.
        firstPersonModel.rightLeg.resetPose();
        if (!ClientKickState.applyPlayerPose(
                player, firstPersonModel.rightLeg, firstPersonModel.rightPants,
                event.getPartialTick())) {
            return false;
        }

        firstPersonRenderer.setGameProfile(player.getGameProfile());
        firstPersonRenderer.setRenderModel(firstPersonModel);
        firstPersonRenderer.setRenderType(RenderType::entityTranslucent);
        firstPersonRenderer.preRender(event.getMultiBufferSource(), AnimationMode.HAND);
        PoseStack poseStack = event.getPoseStack();
        boolean posePushed = false;
        try {
            if (firstPersonRenderer.getDefaultTexture() == null) {
                return false;
            }

            // Apply after preRender so the pose targets CPM's injected redirect part.
            firstPersonModel.setAllVisible(false);
            firstPersonModel.rightLeg.visible = true;
            firstPersonModel.rightLeg.resetPose();
            firstPersonModel.rightLeg.setPos(0.0F, 0.0F, 0.0F);
            if (!ClientKickState.applyPlayerPose(
                    player, firstPersonModel.rightLeg, firstPersonModel.rightPants,
                    event.getPartialTick())) {
                return false;
            }

            poseStack.pushPose();
            posePushed = true;
            poseStack.translate(0.14F, -0.22F,
                    ClientKickState.firstPersonLegDepth(player, event.getPartialTick()));
            poseStack.scale(-0.90F, -0.90F, 0.90F);
            firstPersonModel.rightLeg.render(
                    poseStack,
                    event.getMultiBufferSource().getBuffer(
                            firstPersonRenderer.getDefaultRenderType()),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            return true;
        } finally {
            if (posePushed) {
                poseStack.popPose();
            }
            firstPersonRenderer.postRender();
        }
    }

    /** Prepares a third-person replacement only after CPM has bound the visible player model. */
    public static void beginThirdPerson(
            AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> visibleModel,
            MultiBufferSource buffers,
            float partialTick) {
        cleanupThirdPerson();
        if (thirdPersonRenderer == null || player.isInvisible() || player.isSpectator()) {
            return;
        }
        if (thirdPersonModel == null) {
            thirdPersonModel = createModel();
        }

        thirdPersonModel.rightLeg.resetPose();
        if (!ClientKickState.applyPlayerPose(
                player, thirdPersonModel.rightLeg, thirdPersonModel.rightPants, partialTick)) {
            return;
        }

        thirdPersonRenderer.setGameProfile(player.getGameProfile());
        thirdPersonRenderer.setRenderModel(thirdPersonModel);
        thirdPersonRenderer.setRenderType(RenderType::entityTranslucent);
        thirdPersonRenderer.preRender(buffers, AnimationMode.HAND);
        if (thirdPersonRenderer.getDefaultTexture() == null) {
            thirdPersonRenderer.postRender();
            return;
        }

        // HAND mode preserves an externally supplied root pose even when CPM disables vanilla animation.
        thirdPersonModel.setAllVisible(false);
        thirdPersonModel.rightLeg.visible = true;
        thirdPersonModel.rightLeg.resetPose();
        if (!ClientKickState.applyPlayerPose(
                player, thirdPersonModel.rightLeg, thirdPersonModel.rightPants, partialTick)) {
            thirdPersonRenderer.postRender();
            return;
        }

        ModelPart visibleLeg = visibleModel.rightLeg;
        ModelPart visiblePants = visibleModel.rightPants;
        thirdPersonSession = new ThirdPersonSession(
                player.getId(), visibleLeg, visiblePants,
                visibleLeg.visible, visiblePants.visible);
        visibleLeg.visible = false;
        visiblePants.visible = false;
    }

    /** Draws the prepared standard right-leg root and restores the original renderer state. */
    public static void renderThirdPerson(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player) {
        ThirdPersonSession session = thirdPersonSession;
        if (session == null || session.playerId() != player.getId()) {
            return;
        }
        try {
            thirdPersonModel.rightLeg.render(
                    poseStack,
                    buffers.getBuffer(thirdPersonRenderer.getDefaultRenderType()),
                    packedLight,
                    LivingEntityRenderer.getOverlayCoords(player, 0.0F));
        } finally {
            cleanupThirdPerson();
        }
    }

    /** Restores visibility and closes any CPM renderer left open by a canceled render. */
    public static void cleanupThirdPerson() {
        ThirdPersonSession session = thirdPersonSession;
        if (session == null) {
            return;
        }
        thirdPersonSession = null;
        session.visibleLeg().visible = session.legVisible();
        session.visiblePants().visible = session.pantsVisible();
        thirdPersonRenderer.postRender();
    }

    private static PlayerModel<AbstractClientPlayer> createModel() {
        return new PlayerModel<>(Minecraft.getInstance().getEntityModels()
                .bakeLayer(ModelLayers.PLAYER), false);
    }

    /** CPM constructs this plugin only after its own API is available. */
    private static final class Plugin implements ICPMPlugin {
        @Override
        public void initClient(IClientAPI api) {
            firstPersonRenderer = api.createPlayerRenderer(
                    Model.class,
                    ResourceLocation.class,
                    RenderType.class,
                    MultiBufferSource.class,
                    GameProfile.class);
            thirdPersonRenderer = api.createPlayerRenderer(
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

    private record ThirdPersonSession(
            int playerId,
            ModelPart visibleLeg,
            ModelPart visiblePants,
            boolean legVisible,
            boolean pantsVisible) {
    }
}
