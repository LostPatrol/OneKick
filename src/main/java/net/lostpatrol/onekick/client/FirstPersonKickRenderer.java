/** Renders the optional CPM or vanilla first-person leg, suppressed when YSM is installed. */
package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumSet;
import java.util.Set;
import net.lostpatrol.onekick.client.compat.CpmCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderHandEvent;

final class FirstPersonKickRenderer {
    // YSM owns custom player geometry; suppress all One Kick first-person leg paths.
    private static final String YSM_MOD_ID = "yes_steve_model";
    // Optional CPM integration identifier.
    private static final String CPM_MOD_ID = "cpm";
    // Hide the upper five pixels of the vanilla leg in first person.
    private static final int VISIBLE_LEG_START = 5;
    // Omit the bottom face to retain the established first-person silhouette.
    private static final Set<Direction> VISIBLE_FACES =
            EnumSet.complementOf(EnumSet.of(Direction.DOWN));
    private static ModelPart leg; // Baked vanilla leg geometry.
    private static ModelPart pants; // Baked outer skin layer.

    /** Prevents instantiation of the shared renderer. */
    private FirstPersonKickRenderer() {
    }

    /** Bakes the vanilla fallback leg and its outer skin layer. */
    static void initialize() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leg",
                CubeListBuilder.create().texOffs(0, 16 + VISIBLE_LEG_START)
                        .addBox(-2.0F, VISIBLE_LEG_START, -2.0F,
                                4.0F, 12.0F - VISIBLE_LEG_START, 4.0F, VISIBLE_FACES),
                PartPose.ZERO);
        root.addOrReplaceChild("pants",
                CubeListBuilder.create().texOffs(0, 32 + VISIBLE_LEG_START)
                        .addBox(-2.0F, VISIBLE_LEG_START, -2.0F,
                                4.0F, 12.0F - VISIBLE_LEG_START, 4.0F, VISIBLE_FACES),
                PartPose.ZERO);
        ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        leg = baked.getChild("leg");
        pants = baked.getChild("pants");
    }

    /** Returns true only when a leg was drawn, allowing the caller to hide the right hand. */
    static boolean render(RenderHandEvent event) {
        // Return before CPM and vanilla rendering; false also preserves normal hand rendering.
        if (ModList.get().isLoaded(YSM_MOD_ID)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (leg == null || pants == null || player == null || player.isInvisible()
                || player.isSpectator() || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }

        // Optional classes remain unreachable when CPM is absent.
        if (ModList.get().isLoaded(CPM_MOD_ID)
                && CpmCompatibility.renderFirstPersonLeg(event, player)) {
            return true;
        }

        leg.resetPose();
        pants.resetPose();
        if (!ClientKickState.applyPlayerPose(player, leg, pants, event.getPartialTick())) {
            return false;
        }
        pants.xScale = 1.125F;
        pants.zScale = 1.125F;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.14F, -0.22F,
                ClientKickState.firstPersonLegDepth(player, event.getPartialTick()));
        poseStack.scale(-0.90F, -0.90F, 0.90F);
        leg.render(poseStack,
                event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(
                        player.getSkin().texture())),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG)) {
            pants.render(poseStack,
                    event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(
                            player.getSkin().texture())),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
        return true;
    }

    /** Resolves the physical right hand for either main-arm preference. */
    static boolean isRightHand(RenderHandEvent event) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        InteractionHand rightHand = player.getMainArm() == HumanoidArm.RIGHT
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        return event.getHand() == rightHand;
    }
}
