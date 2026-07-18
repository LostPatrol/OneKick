package net.lostpatrol.onekick.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;

public final class KickPlayerModel extends PlayerModel<AbstractClientPlayer> {
    public KickPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partialTick = ageInTicks - player.tickCount;
        ClientKickState.applyPlayerPose(player, rightLeg, rightPants, partialTick);
    }
}
