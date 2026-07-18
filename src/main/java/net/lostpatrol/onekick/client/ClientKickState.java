package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.lostpatrol.onekick.kick.KickMath;
import net.lostpatrol.onekick.network.KickNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ClientKickState {
    private static final ResourceLocation GUI_ICONS_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/icons.png");
    private static final Map<Integer, PlayerAnimation> PLAYER_ANIMATIONS = new HashMap<>();
    private static final Map<Integer, ChargeVisual> CHARGING_PLAYERS = new HashMap<>();
    private static final Map<Integer, KickedVisual> KICKED_ENTITIES = new HashMap<>();
    private static boolean charging;
    private static float charge;
    private static float chargeMaximum;

    private ClientKickState() {
    }

    public static void updateCharge(
            int entityId, boolean active, float value, float maximum, int chargeLevel) {
        if (active) {
            CHARGING_PLAYERS.put(entityId,
                    new ChargeVisual(Math.max(0.0F, value), Math.max(0.0F, maximum), chargeLevel));
        } else {
            CHARGING_PLAYERS.remove(entityId);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            charging = active;
            charge = Math.max(0.0F, value);
            chargeMaximum = Math.max(0.0F, maximum);
        }
    }

    public static void updatePlayerAnimation(int entityId, byte animation) {
        if (animation == KickNetwork.ANIMATION_STOP) {
            PLAYER_ANIMATIONS.remove(entityId);
            return;
        }
        PLAYER_ANIMATIONS.put(entityId, new PlayerAnimation(animation, gameTime()));
    }

    public static void updateKickedEntity(
            int entityId, boolean active, boolean spin, float visualSpeed) {
        if (!active) {
            KICKED_ENTITIES.remove(entityId);
            return;
        }
        KICKED_ENTITIES.put(entityId,
                new KickedVisual(spin, gameTime(), Math.max(0.0D, visualSpeed)));
    }

    public static void tickParticles(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Iterator<Map.Entry<Integer, ChargeVisual>> chargeIterator = CHARGING_PLAYERS.entrySet().iterator();
        while (chargeIterator.hasNext()) {
            Map.Entry<Integer, ChargeVisual> entry = chargeIterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) {
                chargeIterator.remove();
                continue;
            }
            emitChargeParticles(level, player, entry.getValue());
        }

        Iterator<Map.Entry<Integer, KickedVisual>> kickedIterator = KICKED_ENTITIES.entrySet().iterator();
        while (kickedIterator.hasNext()) {
            Map.Entry<Integer, KickedVisual> entry = kickedIterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) {
                kickedIterator.remove();
                continue;
            }
            emitFlightParticles(level, living, entry.getValue());
        }
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
        return visual != null && visual.spin;
    }

    public static float spinAngle(LivingEntity entity, float partialTick) {
        KickedVisual visual = KICKED_ENTITIES.get(entity.getId());
        if (visual == null) {
            return 0.0F;
        }
        return ((float) (gameTime() - visual.startedAt) + partialTick) * 45.0F;
    }

    public static boolean shouldRenderChargeHud() {
        Minecraft minecraft = Minecraft.getInstance();
        return charging && minecraft.player != null && !minecraft.options.hideGui && chargeMaximum > 0.0F;
    }

    public static void renderChargeHud(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRenderChargeHud()) {
            return;
        }
        int barWidth = 182;
        int x = (width - barWidth) / 2;
        int y = height - 29;
        int filled = (int) (Math.min(1.0F, charge / chargeMaximum) * 183.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        graphics.blit(GUI_ICONS_LOCATION, x, y, 0, 84, barWidth, 5);
        if (filled > 0) {
            graphics.blit(GUI_ICONS_LOCATION, x, y, 0, 89, filled, 5);
        }
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        boolean full = charge >= chargeMaximum - 1.0E-4F;
        if (!full || ((gameTime() / 3L) & 1L) == 0L) {
            Component value = Component.literal(String.format(Locale.ROOT, "%.2f", charge));
            if (full) {
                value = value.copy().withStyle(ChatFormatting.BOLD);
            }
            graphics.drawCenteredString(minecraft.font, value, width / 2, y - 12,
                    0xFF000000 | chargeColor(charge));
        }
    }

    public static void clear() {
        PLAYER_ANIMATIONS.clear();
        CHARGING_PLAYERS.clear();
        KICKED_ENTITIES.clear();
        charging = false;
        charge = 0.0F;
        chargeMaximum = 0.0F;
    }

    public static void removeEntity(int entityId) {
        PLAYER_ANIMATIONS.remove(entityId);
        CHARGING_PLAYERS.remove(entityId);
        KICKED_ENTITIES.remove(entityId);
    }

    private static void emitChargeParticles(ClientLevel level, Player player, ChargeVisual visual) {
        if (visual.maximum <= 0.0F) {
            return;
        }
        double progress = Mth.clamp(visual.charge / visual.maximum, 0.0F, 1.0F);
        boolean full = progress >= 0.999D;
        double outerRadius = 1.8D + Math.max(0, visual.level) * 0.55D;
        double radius = outerRadius * (0.28D + 0.72D * (1.0D - progress));
        Vec3 center = player.position().add(0.0D, 0.08D, 0.0D);
        RandomSource random = player.getRandom();
        int cloudCount = 20 + Math.max(0, visual.level) * 7;
        ParticleOptions dust = new DustParticleOptions(
                Vec3.fromRGB24(chargeColor(visual.charge)).toVector3f(), full ? 1.45F : 1.0F);

        for (int i = 0; i < cloudCount; i++) {
            double y = random.nextDouble() * 2.0D - 1.0D;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double shell = radius * (0.62D + random.nextDouble() * 0.38D);
            Vec3 offset = new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal).scale(shell);
            Vec3 inward = offset.normalize().scale(-(0.055D + progress * 0.12D + visual.level * 0.006D));
            Vec3 point = center.add(offset);
            level.addParticle(i % 4 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                    point.x, point.y, point.z, inward.x, inward.y, inward.z);
            if (i % 5 == 0) {
                level.addParticle(dust, point.x, point.y, point.z,
                        inward.x * 0.4D, inward.y * 0.4D, inward.z * 0.4D);
            }
        }

        if (((level.getGameTime() + player.getId()) & 1L) == 0L) {
            emitMagicCircle(level, player, visual, outerRadius, full, dust);
        }
        if (full) {
            for (int i = 0; i < 12 + visual.level * 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = random.nextDouble() * outerRadius * 0.55D;
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        center.x + Math.cos(angle) * distance, center.y + random.nextDouble() * 0.25D,
                        center.z + Math.sin(angle) * distance, 0.0D, 0.12D + random.nextDouble() * 0.12D, 0.0D);
            }
            if ((level.getGameTime() + player.getId()) % 6L == 0L) {
                level.addParticle(ParticleTypes.FLASH, center.x, center.y + 0.2D, center.z,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void emitMagicCircle(
            ClientLevel level,
            Player player,
            ChargeVisual visual,
            double outerRadius,
            boolean full,
            ParticleOptions dust) {
        Vec3 center = player.position().add(0.0D, 0.045D, 0.0D);
        int rings = full ? 3 : 2;
        int points = 34 + visual.level * 6;
        double rotation = level.getGameTime() * (full ? 0.075D : 0.035D);
        for (int ring = 0; ring < rings; ring++) {
            double ringRadius = outerRadius * (full ? 0.46D + ring * 0.24D : 0.68D + ring * 0.24D);
            double ringRotation = ring % 2 == 0 ? rotation : -rotation;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points + ringRotation;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                level.addParticle(dust, x, center.y, z, 0.0D, full ? 0.012D : 0.002D, 0.0D);
                if (full && i % 4 == 0) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, center.y + 0.02D, z,
                            0.0D, 0.035D, 0.0D);
                }
            }
        }

        int spokes = full ? 12 : 8;
        int segments = 8 + visual.level;
        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = Math.PI * 2.0D * spoke / spokes - rotation;
            for (int segment = 2; segment <= segments; segment++) {
                double distance = outerRadius * 0.82D * segment / segments;
                level.addParticle(dust,
                        center.x + Math.cos(angle) * distance,
                        center.y,
                        center.z + Math.sin(angle) * distance,
                        0.0D, full ? 0.01D : 0.0D, 0.0D);
            }
        }
    }

    private static void emitFlightParticles(
            ClientLevel level, LivingEntity entity, KickedVisual visual) {
        Vec3 current = entity.position();
        Vec3 movement = visual.lastPosition == null
                ? entity.getDeltaMovement()
                : current.subtract(visual.lastPosition);
        visual.lastPosition = current;
        if (movement.lengthSqr() > 1.0E-5D) {
            visual.lastDirection = movement.normalize();
            visual.ringDistance += movement.length();
        }

        int tier = KickMath.flightEffectTier(visual.visualSpeed);
        if (tier == 0) {
            return;
        }
        Vec3 center = current.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        int age = (int) (gameTime() - visual.startedAt);
        if (tier == 1) {
            if ((age & 1) == 0) {
                spawnTrail(level, entity, visual, center, ParticleTypes.FIREWORK, 2, 0.45D);
            }
            return;
        }
        if (tier == 2) {
            spawnTrail(level, entity, visual, center, ParticleTypes.FIREWORK, 7, 0.7D);
            spawnTrail(level, entity, visual, center, ParticleTypes.SMOKE, 4, 0.55D);
            return;
        }
        if (tier == 3) {
            spawnTrail(level, entity, visual, center, ParticleTypes.FIREWORK, 12, 0.9D);
            spawnTrail(level, entity, visual, center, ParticleTypes.CLOUD, 8, 0.85D);
            spawnTrail(level, entity, visual, center, ParticleTypes.FLAME, 5, 0.7D);
            return;
        }

        spawnTrail(level, entity, visual, center, ParticleTypes.FIREWORK, 20, 1.25D);
        spawnTrail(level, entity, visual, center, ParticleTypes.CLOUD, 14, 1.2D);
        spawnTrail(level, entity, visual, center, ParticleTypes.FLAME, 9, 0.9D);
        spawnTrail(level, entity, visual, center, ParticleTypes.SPLASH, 12, 1.3D);
        if ((age & 1) == 0) {
            spawnTrail(level, entity, visual, center, ParticleTypes.CAMPFIRE_COSY_SMOKE, 5, 1.0D);
        }
        if (visual.ringDistance >= 1.75D) {
            visual.ringDistance %= 1.75D;
            emitMachRing(level, center, visual.lastDirection);
        }
    }

    private static void spawnTrail(
            ClientLevel level,
            LivingEntity entity,
            KickedVisual visual,
            Vec3 center,
            ParticleOptions particle,
            int count,
            double length) {
        RandomSource random = entity.getRandom();
        for (int i = 0; i < count; i++) {
            double behind = random.nextDouble() * length;
            Vec3 point = center.subtract(visual.lastDirection.scale(behind)).add(
                    (random.nextDouble() - 0.5D) * entity.getBbWidth() * 0.75D,
                    (random.nextDouble() - 0.5D) * entity.getBbHeight() * 0.65D,
                    (random.nextDouble() - 0.5D) * entity.getBbWidth() * 0.75D);
            Vec3 drift = visual.lastDirection.scale(-0.035D - random.nextDouble() * 0.035D).add(
                    (random.nextDouble() - 0.5D) * 0.035D,
                    (random.nextDouble() - 0.5D) * 0.035D,
                    (random.nextDouble() - 0.5D) * 0.035D);
            level.addParticle(particle, point.x, point.y, point.z, drift.x, drift.y, drift.z);
        }
    }

    private static void emitMachRing(ClientLevel level, Vec3 center, Vec3 direction) {
        Vec3 first = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (first.lengthSqr() < 1.0E-4D) {
            first = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        first = first.normalize();
        Vec3 second = direction.cross(first).normalize();
        Vec3 ringCenter = center.subtract(direction.scale(0.9D));
        ParticleOptions dust = new DustParticleOptions(
                Vec3.fromRGB24(0xC8F7FF).toVector3f(), 1.35F);
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            Vec3 outward = first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle)));
            Vec3 point = ringCenter.add(outward.scale(1.05D));
            level.addParticle(i % 3 == 0 ? ParticleTypes.CLOUD : dust,
                    point.x, point.y, point.z, outward.x * 0.065D, outward.y * 0.065D, outward.z * 0.065D);
        }
    }

    private static int chargeColor(float value) {
        if (value < 3.0F) {
            return 0x55FF55;
        }
        if (value < 5.0F) {
            return 0xFFAA00;
        }
        if (value < 8.0F) {
            return 0xFF4444;
        }
        return Mth.hsvToRgb((gameTime() % 60L) / 60.0F, 0.92F, 1.0F);
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private record PlayerAnimation(byte animation, long startedAt) {
    }

    private record ChargeVisual(float charge, float maximum, int level) {
    }

    private static final class KickedVisual {
        private final boolean spin;
        private final long startedAt;
        private final double visualSpeed;
        private Vec3 lastPosition;
        private Vec3 lastDirection = new Vec3(0.0D, 0.0D, 1.0D);
        private double ringDistance;

        private KickedVisual(boolean spin, long startedAt, double visualSpeed) {
            this.spin = spin;
            this.startedAt = startedAt;
            this.visualSpeed = visualSpeed;
        }
    }
}
