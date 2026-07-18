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
        int enchantmentLevel = Math.max(0, visual.level);
        double outerRadius = 1.65D + enchantmentLevel * 0.42D;
        double focusRadius = outerRadius * (0.45D + 0.35D * (1.0D - progress));
        Vec3 center = player.position().add(0.0D, 0.12D, 0.0D);
        RandomSource random = player.getRandom();
        int moteCount = 4 + enchantmentLevel * 2;
        ParticleOptions dust = new DustParticleOptions(
                Vec3.fromRGB24(chargeColor(visual.charge)).toVector3f(), full ? 1.45F : 1.0F);

        for (int i = 0; i < moteCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double shell = focusRadius * (0.72D + random.nextDouble() * 0.28D);
            double height = 0.08D + random.nextDouble() * (0.4D + player.getBbHeight() * 0.42D);
            Vec3 offset = new Vec3(Math.cos(angle) * shell, height, Math.sin(angle) * shell);
            Vec3 inward = new Vec3(-offset.x, -height * 0.18D, -offset.z).normalize()
                    .scale(0.025D + progress * 0.055D);
            Vec3 point = center.add(offset);
            ParticleOptions particle = switch (i & 3) {
                case 0 -> ParticleTypes.END_ROD;
                case 1 -> ParticleTypes.ENCHANT;
                case 2 -> ParticleTypes.SOUL;
                default -> dust;
            };
            level.addParticle(particle, point.x, point.y, point.z, inward.x, inward.y, inward.z);
        }

        emitOrbitingSoulFlames(level, player, center, outerRadius, progress);
        if (((level.getGameTime() + player.getId()) & 1L) == 0L) {
            emitMagicCircle(level, player, visual, outerRadius, full, dust);
        }
        if (full) {
            for (int i = 0; i < 8 + enchantmentLevel * 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = random.nextDouble() * outerRadius * 0.55D;
                ParticleOptions particle = i % 3 == 0
                        ? ParticleTypes.SOUL_FIRE_FLAME
                        : ParticleTypes.TOTEM_OF_UNDYING;
                level.addParticle(particle,
                        center.x + Math.cos(angle) * distance, center.y + random.nextDouble() * 0.25D,
                        center.z + Math.sin(angle) * distance, 0.0D, 0.12D + random.nextDouble() * 0.12D, 0.0D);
            }
            if ((level.getGameTime() + player.getId()) % 6L == 0L) {
                level.addParticle(ParticleTypes.FLASH, center.x, center.y + 0.2D, center.z,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void emitOrbitingSoulFlames(
            ClientLevel level, Player player, Vec3 center, double radius, double progress) {
        double rotation = level.getGameTime() * (0.11D + progress * 0.08D) + player.getId() * 0.37D;
        for (int i = 0; i < 2; i++) {
            double angle = rotation + Math.PI * i;
            double height = 0.18D + Math.sin(rotation * 1.7D + i * Math.PI) * 0.09D;
            Vec3 point = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y, point.z, 0.0D, 0.012D, 0.0D);
            if (((level.getGameTime() + i) & 1L) == 0L) {
                double trailAngle = angle - 0.22D;
                level.addParticle(ParticleTypes.SOUL,
                        center.x + Math.cos(trailAngle) * radius, point.y + 0.04D,
                        center.z + Math.sin(trailAngle) * radius, 0.0D, 0.018D, 0.0D);
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
        int points = 24 + visual.level * 3;
        double rotation = level.getGameTime() * (full ? 0.075D : 0.035D);
        for (int ring = 0; ring < rings; ring++) {
            double ringRadius = outerRadius * (full ? 0.38D + ring * 0.25D : 0.64D + ring * 0.25D);
            double ringRotation = ring % 2 == 0 ? rotation : -rotation;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points + ringRotation;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                level.addParticle(dust, x, center.y, z, 0.0D, full ? 0.012D : 0.002D, 0.0D);
                if (full && i % 4 == 0) {
                    level.addParticle(i % 8 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ELECTRIC_SPARK,
                            x, center.y + 0.02D, z,
                            0.0D, 0.035D, 0.0D);
                }
            }
        }

        int sigils = 5 + Math.max(0, visual.level) / 2;
        for (int sigil = 0; sigil < sigils; sigil++) {
            double angle = Math.PI * 2.0D * sigil / sigils - rotation;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            for (int pointIndex = 0; pointIndex < 3; pointIndex++) {
                double distance = outerRadius * (0.28D + pointIndex * 0.13D);
                double bend = pointIndex == 1 ? outerRadius * 0.06D : 0.0D;
                Vec3 point = center.add(radial.scale(distance)).add(tangent.scale(bend));
                level.addParticle(pointIndex == 1 ? ParticleTypes.ELECTRIC_SPARK : dust,
                        point.x, point.y, point.z, 0.0D, full ? 0.01D : 0.0D, 0.0D);
            }
        }
    }

    private static void emitFlightParticles(
            ClientLevel level, LivingEntity entity, KickedVisual visual) {
        Vec3 current = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Vec3 previous = visual.lastPosition;
        Vec3 movement = previous == null ? entity.getDeltaMovement() : current.subtract(previous);
        if (movement.lengthSqr() > 1.0E-5D) {
            visual.lastDirection = movement.normalize();
        }
        visual.lastPosition = current;

        int tier = KickMath.flightEffectTier(visual.visualSpeed);
        if (tier == 0) {
            return;
        }
        int age = (int) (gameTime() - visual.startedAt);
        if (tier == 1) {
            if ((age & 1) == 0) {
                spawnTrail(level, entity, visual, current, ParticleTypes.FIREWORK, 2, 0.45D);
            }
            return;
        }
        if (tier == 2) {
            spawnTrail(level, entity, visual, current, ParticleTypes.FIREWORK, 7, 0.7D);
            spawnTrail(level, entity, visual, current, ParticleTypes.SMOKE, 4, 0.55D);
            return;
        }
        if (tier == 3) {
            spawnTrail(level, entity, visual, current, ParticleTypes.FIREWORK, 12, 0.9D);
            spawnTrail(level, entity, visual, current, ParticleTypes.CLOUD, 8, 0.85D);
            spawnTrail(level, entity, visual, current, ParticleTypes.FLAME, 5, 0.7D);
            return;
        }

        spawnTrail(level, entity, visual, current, ParticleTypes.FIREWORK, 20, 1.25D);
        spawnTrail(level, entity, visual, current, ParticleTypes.CLOUD, 14, 1.2D);
        spawnTrail(level, entity, visual, current, ParticleTypes.FLAME, 9, 0.9D);
        spawnTrail(level, entity, visual, current, ParticleTypes.SPLASH, 12, 1.3D);
        if ((age & 1) == 0) {
            spawnTrail(level, entity, visual, current, ParticleTypes.CAMPFIRE_COSY_SMOKE, 5, 1.0D);
        }
        double ringRadius = Math.max(1.05D, entity.getBbWidth() * 0.75D + entity.getBbHeight() * 0.2D);
        if (previous == null) {
            Vec3 estimatedStart = current.subtract(movement);
            boolean emitted = emitMachRingsAlongSegment(level, estimatedStart, current, visual, ringRadius);
            if (!emitted) {
                emitMachRing(level, current.subtract(visual.lastDirection.scale(0.8D)),
                        visual.lastDirection, ringRadius);
            }
        } else {
            emitMachRingsAlongSegment(level, previous, current, visual, ringRadius);
        }
    }

    private static boolean emitMachRingsAlongSegment(
            ClientLevel level, Vec3 start, Vec3 end, KickedVisual visual, double radius) {
        Vec3 segment = end.subtract(start);
        double length = segment.length();
        if (length < 1.0E-4D) {
            return false;
        }
        Vec3 direction = segment.normalize();
        double interval = 1.75D;
        double distance = interval - visual.ringDistance;
        boolean emitted = false;
        while (distance <= length + 1.0E-6D) {
            emitMachRing(level, start.add(direction.scale(distance)), direction, radius);
            distance += interval;
            emitted = true;
        }
        visual.ringDistance = (visual.ringDistance + length) % interval;
        return emitted;
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

    private static void emitMachRing(ClientLevel level, Vec3 center, Vec3 direction, double radius) {
        Vec3 first = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (first.lengthSqr() < 1.0E-4D) {
            first = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        first = first.normalize();
        Vec3 second = direction.cross(first).normalize();
        ParticleOptions dust = new DustParticleOptions(
                Vec3.fromRGB24(0xC8F7FF).toVector3f(), 1.35F);
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D;
            Vec3 outward = first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle)));
            Vec3 point = center.add(outward.scale(radius));
            level.addParticle(ParticleTypes.CLOUD,
                    point.x, point.y, point.z, outward.x * 0.065D, outward.y * 0.065D, outward.z * 0.065D);
            if ((i & 1) == 0) {
                Vec3 inner = center.add(outward.scale(radius * 0.82D));
                level.addParticle(dust, inner.x, inner.y, inner.z,
                        outward.x * 0.035D, outward.y * 0.035D, outward.z * 0.035D);
            }
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
