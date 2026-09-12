/** Optional Player Animation Library bridge for One Kick's third-person right-leg pose. */
package net.lostpatrol.onekick.client.compat;

import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import java.util.List;
import java.util.Map;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.client.ClientKickState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public final class PalCompatibility {
    private static final ResourceLocation KICK_LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, "kick");
    // Gameplay layers conventionally start at 1500, above Awakening's ability layer.
    private static final int KICK_LAYER_PRIORITY = 1500;
    private static boolean registered;

    private PalCompatibility() {
    }

    /** Registers one isolated animation layer per player when PAL is present. */
    public static void register() {
        if (registered) {
            return;
        }
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                KICK_LAYER_ID, KICK_LAYER_PRIORITY, KickAnimationLayer::new);
        registered = true;
    }

    /**
     * Reuses the existing pose calculation without changing its non-PAL rendering path.
     * PAL's default first-person mode is intentionally retained, so this layer is skipped there.
     */
    private static final class KickAnimationLayer implements IAnimation {
        private static final String RIGHT_LEG_BONE = "right_leg";
        private final AbstractClientPlayer player;
        private final ModelPart rightLeg = emptyPart();
        private final ModelPart rightPants = emptyPart();
        private boolean active;
        private boolean prepared;

        private KickAnimationLayer(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public void tick(AnimationData data) {
            prepared = false;
        }

        @Override
        public void setupAnim(AnimationData data) {
            active = ClientKickState.applyPlayerPose(
                    player, rightLeg, rightPants, data.getPartialTick());
            prepared = true;
        }

        @Override
        public boolean isActive() {
            if (!prepared) {
                active = ClientKickState.applyPlayerPose(player, rightLeg, rightPants, 0.0F);
                prepared = true;
            }
            return active;
        }

        @Override
        public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
            if (RIGHT_LEG_BONE.equals(bone.getName())) {
                bone.updateRotation(rightLeg.xRot, rightLeg.yRot, rightLeg.zRot);
            }
            return bone;
        }

        private static ModelPart emptyPart() {
            return new ModelPart(List.of(), Map.of());
        }
    }
}
