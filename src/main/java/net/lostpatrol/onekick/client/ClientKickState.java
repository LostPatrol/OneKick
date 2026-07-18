package net.lostpatrol.onekick.client;

import java.util.HashMap;
import java.util.Map;
import net.lostpatrol.onekick.network.KickNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class ClientKickState {
    private static final Map<Integer, PlayerAnimation> PLAYER_ANIMATIONS = new HashMap<>();
    private static final Map<Integer, KickedVisual> KICKED_ENTITIES = new HashMap<>();
    private static boolean charging;
    private static float charge;
    private static float chargeMaximum;

    private ClientKickState() {
    }

    public static void updateCharge(boolean active, float value, float maximum) {
        charging = active;
        charge = Math.max(0.0F, value);
        chargeMaximum = Math.max(0.0F, maximum);
    }

    public static void updatePlayerAnimation(int entityId, byte animation) {
        if (animation == KickNetwork.ANIMATION_STOP) {
            PLAYER_ANIMATIONS.remove(entityId);
            return;
        }
        PLAYER_ANIMATIONS.put(entityId, new PlayerAnimation(animation, gameTime()));
    }

    public static void updateKickedEntity(int entityId, boolean active, boolean spin) {
        if (!active) {
            KICKED_ENTITIES.remove(entityId);
            return;
        }
        KICKED_ENTITIES.put(entityId, new KickedVisual(spin, gameTime()));
    }

    public static void applyPlayerPose(
            AbstractClientPlayer player, ModelPart rightLeg, ModelPart rightPants, float partialTick) {
        PlayerAnimation animation = PLAYER_ANIMATIONS.get(player.getId());
        if (animation == null) {
            return;
        }
        float elapsed = (float) (gameTime() - animation.startedAt()) + partialTick;
        if (animation.animation() == KickNetwork.ANIMATION_KICK) {
            float progress = elapsed / 8.0F;
            if (progress >= 1.0F) {
                PLAYER_ANIMATIONS.remove(player.getId());
                return;
            }
            float extension = (float) Math.sin(Math.PI * progress);
            rightLeg.xRot = -1.65F * extension;
            rightLeg.yRot = 0.12F * extension;
            rightLeg.zRot = -0.08F * extension;
        } else if (animation.animation() == KickNetwork.ANIMATION_CHARGE) {
            if (!player.onGround() || player.isSprinting() || player.isFallFlying()
                    || player.isSwimming() || player.isPassenger()) {
                return;
            }
            float phase = (player.tickCount + partialTick) * 0.12F;
            rightLeg.xRot = -0.85F + (float) Math.sin(phase) * 0.22F;
            rightLeg.yRot = (float) Math.sin(phase) * 0.36F;
            rightLeg.zRot = (float) Math.cos(phase) * 0.28F;
        }
        rightPants.copyFrom(rightLeg);
    }

    public static boolean shouldSpin(LivingEntity entity) {
        KickedVisual visual = KICKED_ENTITIES.get(entity.getId());
        return visual != null && visual.spin();
    }

    public static float spinAngle(LivingEntity entity, float partialTick) {
        KickedVisual visual = KICKED_ENTITIES.get(entity.getId());
        if (visual == null) {
            return 0.0F;
        }
        return ((float) (gameTime() - visual.startedAt()) + partialTick) * 45.0F;
    }

    public static void renderChargeHud(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!charging || minecraft.options.hideGui || chargeMaximum <= 0.0F) {
            return;
        }
        int barWidth = 182;
        int x = (width - barWidth) / 2;
        int y = height - 59;
        int filled = Math.min(barWidth, Math.round(barWidth * charge / chargeMaximum));
        boolean fullFlash = charge >= chargeMaximum && ((gameTime() / 5L) & 1L) == 0L;
        int fillColor = fullFlash ? 0xFFFFFFFF : 0xFFFFC33C;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 6, 0xCC000000);
        graphics.fill(x, y, x + barWidth, y + 5, 0xCC3A2E1E);
        graphics.fill(x, y, x + filled, y + 5, fillColor);
        String value = String.format(java.util.Locale.ROOT, "%.2f / %.2f", charge, chargeMaximum);
        graphics.drawCenteredString(minecraft.font, value, width / 2, y - 12,
                fullFlash ? 0xFFFFFFFF : 0xFFFFE6A0);
    }

    public static void clear() {
        PLAYER_ANIMATIONS.clear();
        KICKED_ENTITIES.clear();
        charging = false;
        charge = 0.0F;
        chargeMaximum = 0.0F;
    }

    public static void removeEntity(int entityId) {
        PLAYER_ANIMATIONS.remove(entityId);
        KICKED_ENTITIES.remove(entityId);
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private record PlayerAnimation(byte animation, long startedAt) {
    }

    private record KickedVisual(boolean spin, long startedAt) {
    }
}
