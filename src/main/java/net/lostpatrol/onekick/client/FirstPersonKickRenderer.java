package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumSet;
import java.util.Set;
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
import net.minecraftforge.client.event.RenderHandEvent;

final class FirstPersonKickRenderer {
    private static final int VISIBLE_LEG_START = 5;
    private static final Set<Direction> VISIBLE_FACES =
            EnumSet.complementOf(EnumSet.of(Direction.DOWN));
    private static ModelPart leg;
    private static ModelPart pants;

    private FirstPersonKickRenderer() {
    }

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

    static boolean render(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (leg == null || pants == null || player == null || player.isInvisible()
                || player.isSpectator() || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
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
                        player.getSkinTextureLocation())),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        if (player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG)) {
            pants.render(poseStack,
                    event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(
                            player.getSkinTextureLocation())),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
        return true;
    }

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
