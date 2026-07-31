package net.lostpatrol.onekick.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.lostpatrol.onekick.kick.KickMath;
import net.lostpatrol.onekick.network.KickNetwork;
import net.lostpatrol.onekick.registry.ModParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ClientKickState {
    private static final double MACH_RING_RADIUS_MULTIPLIER = 2.4D;
    private static final double MACH_RING_START_DISTANCE = 4.0D;
    private static final int MACH_RING_MIN_POINTS = 40;
    private static final int MACH_RING_MAX_POINTS = 144;
    private static final int MACH_RING_MIN_LAYERS = 2;
    private static final int MACH_RING_MAX_LAYERS = 6;
    private static final int MACH_RING_MAX_PREDICTION_TICKS = 512;
    private static final int CHARGE_SMOKE_PARTICLE_MULTIPLIER = 3;
    private static final int UNSTABLE_EXPLOSION_LIFETIME_TICKS = 8;
    private static final ResourceLocation GUI_ICONS_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/icons.png");
    private static final Map<Integer, PlayerAnimation> PLAYER_ANIMATIONS = new HashMap<>();
    private static final Map<Integer, ChargeVisual> CHARGING_PLAYERS = new HashMap<>();
    private static final Map<Integer, KickedVisual> KICKED_ENTITIES = new HashMap<>();
    private static final List<UnstableExplosionVisual> UNSTABLE_EXPLOSIONS = new ArrayList<>();
    private static boolean charging;
    private static float charge;
    private static float chargeMaximum;

    private ClientKickState() {
    }

    public static void updateCharge(
            int entityId,
            boolean active,
            float value,
            float maximum,
            int chargeLevel,
            int kineticOverloadLevel) {
        if (active) {
            CHARGING_PLAYERS.compute(entityId, (ignored, visual) -> {
                if (visual == null) {
                    return new ChargeVisual(
                            value, maximum, chargeLevel, kineticOverloadLevel);
                }
                visual.update(value, maximum, chargeLevel, kineticOverloadLevel);
                return visual;
            });
            PlayerAnimation animation = PLAYER_ANIMATIONS.get(entityId);
            if (animation != null && animation.animation() == KickNetwork.ANIMATION_CHARGE) {
                PLAYER_ANIMATIONS.put(entityId,
                        animation.withChargeState(chargeLevel, kineticOverloadLevel));
            }
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
        long startedAt = gameTime();
        PlayerAnimation previous = PLAYER_ANIMATIONS.get(entityId);
        boolean startedFromCharge = animation == KickNetwork.ANIMATION_KICK
                && previous != null
                && previous.animation() == KickNetwork.ANIMATION_CHARGE;
        KickAnimation.Pose startPose = startedFromCharge
                ? KickAnimation.chargePose(
                        previous.chargeLevel(), previous.kineticOverloadLevel(), startedAt)
                : KickAnimation.READY_POSE;
        ChargeVisual visual = CHARGING_PLAYERS.get(entityId);
        int chargeLevel = animation == KickNetwork.ANIMATION_CHARGE && visual != null
                ? Mth.clamp(visual.level, 1, 5)
                : 1;
        int kineticOverloadLevel = animation == KickNetwork.ANIMATION_CHARGE && visual != null
                ? Math.max(0, visual.kineticOverloadLevel)
                : 0;
        PLAYER_ANIMATIONS.put(entityId,
                new PlayerAnimation(
                        animation, startedAt, chargeLevel, kineticOverloadLevel, startPose));
    }

    public static void updateKickedEntity(
            int entityId, boolean active, boolean spin, float visualSpeed, Vec3 initialVelocity) {
        if (!active) {
            KICKED_ENTITIES.remove(entityId);
            return;
        }
        KICKED_ENTITIES.put(entityId,
                new KickedVisual(spin, gameTime(), Math.max(0.0D, visualSpeed), initialVelocity));
    }

    public static void emitDisintegrationSmoke(
            Vec3 impact,
            Vec3 direction,
            double impactSpeed,
            int affectedBlocks,
            List<BlockPos> smokeOrigins) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || affectedBlocks <= 0 || smokeOrigins.isEmpty()) {
            return;
        }
        Vec3 axis = direction.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : direction.normalize();
        Vec3 first = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (first.lengthSqr() < 1.0E-4D) {
            first = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        first = first.normalize();
        Vec3 second = axis.cross(first).normalize();
        int count = KickMath.disintegrationSmokeParticleCount(
                affectedBlocks, smokeOrigins.size(), impactSpeed);
        for (int i = 0; i < count; i++) {
            int originIndex = count == 1
                    ? smokeOrigins.size() / 2
                    : (int) Math.round(i * (smokeOrigins.size() - 1.0D) / (count - 1.0D));
            Vec3 origin = Vec3.atCenterOf(smokeOrigins.get(originIndex));
            Vec3 relative = origin.subtract(impact);
            double along = relative.dot(axis);
            Vec3 radial = relative.subtract(axis.scale(along));
            Vec3 outward;
            if (radial.lengthSqr() > 1.0E-4D) {
                outward = radial.normalize();
            } else {
                double angle = level.random.nextDouble() * Mth.TWO_PI;
                outward = first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle)));
            }
            Vec3 point = origin.add(
                    (level.random.nextDouble() - 0.5D) * 0.9D,
                    (level.random.nextDouble() - 0.5D) * 0.9D,
                    (level.random.nextDouble() - 0.5D) * 0.9D);
            Vec3 scatter = new Vec3(
                    level.random.nextDouble() - 0.5D,
                    level.random.nextDouble() - 0.5D,
                    level.random.nextDouble() - 0.5D).scale(0.28D);
            Vec3 flight = axis.scale(-1.0D).add(outward.scale(0.42D)).add(scatter);
            if (flight.lengthSqr() < 1.0E-4D) {
                flight = axis.scale(-1.0D);
            }
            double strength = KickMath.disintegrationSmokeInitialSpeed(
                    impactSpeed, level.random.nextDouble());
            Vec3 velocity = flight.normalize().scale(strength);
            level.addAlwaysVisibleParticle(ModParticleTypes.DISINTEGRATION_SMOKE.get(), true,
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    public static void emitUnstableExplosion(Vec3 center, double radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        level.playLocalSound(
                center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F,
                false);
        UNSTABLE_EXPLOSIONS.add(new UnstableExplosionVisual(
                center, KickMath.unstableExplosionVisualScale(radius)));
    }

    public static void tickParticles(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        tickUnstableExplosions(level);

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
            KickedVisual visual = entry.getValue();
            if (entity instanceof LivingEntity living) {
                emitFlightParticles(level, living, visual);
            } else if (visual.machRingsPrepared) {
                emitDueMachRings(level, visual);
                if (visual.machSequenceComplete()) {
                    kickedIterator.remove();
                }
            } else {
                kickedIterator.remove();
            }
        }
    }

    public static boolean applyPlayerPose(
            AbstractClientPlayer player, ModelPart rightLeg, ModelPart rightPants, float partialTick) {
        PlayerAnimation animation = PLAYER_ANIMATIONS.get(player.getId());
        if (animation == null) {
            return false;
        }
        float elapsed = (float) (gameTime() - animation.startedAt()) + partialTick;
        if (animation.animation() == KickNetwork.ANIMATION_KICK) {
            if (elapsed >= KickAnimation.KICK_DURATION_TICKS) {
                PLAYER_ANIMATIONS.remove(player.getId());
                return false;
            }
            applyPose(rightLeg, KickAnimation.kickPose(animation.startPose(), elapsed));
        } else if (animation.animation() == KickNetwork.ANIMATION_CHARGE) {
            if (!player.onGround() || player.isSprinting() || player.isFallFlying()
                    || player.isSwimming() || player.isPassenger()) {
                return false;
            }
            ChargeVisual visual = CHARGING_PLAYERS.get(player.getId());
            int chargeLevel = visual == null
                    ? animation.chargeLevel()
                    : Mth.clamp(visual.level, 1, 5);
            int kineticOverloadLevel = visual == null
                    ? animation.kineticOverloadLevel()
                    : Math.max(0, visual.kineticOverloadLevel);
            applyPose(rightLeg, KickAnimation.chargePose(
                    chargeLevel, kineticOverloadLevel, gameTime() + partialTick));
        } else {
            return false;
        }
        rightPants.copyFrom(rightLeg);
        return true;
    }

    public static float firstPersonLegDepth(AbstractClientPlayer player, float partialTick) {
        PlayerAnimation animation = PLAYER_ANIMATIONS.get(player.getId());
        if (animation != null && animation.animation() == KickNetwork.ANIMATION_KICK) {
            float elapsed = (float) (gameTime() - animation.startedAt()) + partialTick;
            return KickAnimation.firstPersonDepth(elapsed);
        }
        return -0.28F;
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
        UNSTABLE_EXPLOSIONS.clear();
        charging = false;
        charge = 0.0F;
        chargeMaximum = 0.0F;
    }

    public static void removeEntity(int entityId) {
        PLAYER_ANIMATIONS.remove(entityId);
        CHARGING_PLAYERS.remove(entityId);
        KickedVisual visual = KICKED_ENTITIES.get(entityId);
        if (visual == null || !visual.hasPendingMachRings()) {
            KICKED_ENTITIES.remove(entityId);
        }
    }

    private static void tickUnstableExplosions(ClientLevel level) {
        Iterator<UnstableExplosionVisual> iterator = UNSTABLE_EXPLOSIONS.iterator();
        while (iterator.hasNext()) {
            UnstableExplosionVisual visual = iterator.next();
            double spread = 4.0D * visual.scale;
            double animationProgress =
                    (double) visual.age / UNSTABLE_EXPLOSION_LIFETIME_TICKS;
            for (int i = 0; i < 6; i++) {
                double x = visual.center.x
                        + (level.random.nextDouble() - level.random.nextDouble()) * spread;
                double y = visual.center.y
                        + (level.random.nextDouble() - level.random.nextDouble()) * spread;
                double z = visual.center.z
                        + (level.random.nextDouble() - level.random.nextDouble()) * spread;
                level.addAlwaysVisibleParticle(
                        ModParticleTypes.UNSTABLE_EXPLOSION.get(), true,
                        x, y, z, animationProgress, visual.scale, 0.0D);
            }
            visual.age++;
            if (visual.age >= UNSTABLE_EXPLOSION_LIFETIME_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void emitChargeParticles(ClientLevel level, Player player, ChargeVisual visual) {
        if (visual.maximum <= 0.0F) {
            return;
        }
        emitBaseChargeSmoke(level, player, visual);
        if (visual.kineticOverloadLevel > 0) {
            emitKineticOverloadChargeParticles(level, player, visual);
        }
    }

    private static void emitBaseChargeSmoke(
            ClientLevel level, Player player, ChargeVisual visual) {
        double chargeProgress = Mth.clamp(visual.charge / visual.maximum, 0.0F, 1.0F);
        ChargeSmokeBasis basis = chargeSmokeBasis(player.getLookAngle());
        Vec3 foot = chargeSmokeFoot(player);
        Vec3 eye = player.getEyePosition();
        if (chargeProgress >= 1.0D) {
            return;
        }
        if (chargeProgress <= visual.lastFunnelEmissionProgress + 1.0E-6D) {
            return;
        }
        visual.lastFunnelEmissionProgress = chargeProgress;
        emitChargeFunnel(
                level, player, eye, foot, basis, visual.maximum, chargeProgress);
    }

    private static void emitChargeFunnel(
            ClientLevel level,
            Player player,
            Vec3 eye,
            Vec3 foot,
            ChargeSmokeBasis basis,
            double maximumCharge,
            double chargeProgress) {
        double remaining = chargeConeRemainingScale(chargeProgress);
        double radius = chargeSmokeMaximumConeRadius(maximumCharge) * remaining;
        Vec3 center = chargeSmokeConeCenter(
                eye, foot, basis.forward, maximumCharge, chargeProgress);
        Vec3 funnelAxis = center.subtract(foot);
        RandomSource random = player.getRandom();
        int smokeCount = chargeSmokeConeParticleCount(maximumCharge, chargeProgress);
        float particleSize = (float) Mth.clamp(
                0.15D + chargeSmokeScale(maximumCharge) * 0.04D,
                0.17D, 0.31D);
        for (int i = 0; i < smokeCount; i++) {
            double axial = 0.08D + Math.pow(random.nextDouble(), 0.58D) * 0.92D;
            double crossSection = radius * Math.pow(axial, 0.42D)
                    * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Mth.TWO_PI;
            Vec3 point = foot
                    .add(funnelAxis.scale(axial))
                    .add(basis.right.scale(Math.cos(angle) * crossSection))
                    .add(basis.up.scale(Math.sin(angle) * crossSection));
            ChargeSmokeParticle.spawnFunnel(
                    level, player, point, chargeProgress, particleSize);
        }
    }

    static double chargeSmokeProgress(int entityId) {
        ChargeVisual visual = CHARGING_PLAYERS.get(entityId);
        if (visual == null || visual.maximum <= 0.0F) {
            return -1.0D;
        }
        return Mth.clamp(visual.charge / visual.maximum, 0.0F, 1.0F);
    }

    static Vec3 chargeSmokeFoot(Player player) {
        Vec3 horizontal = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            double yaw = Math.toRadians(player.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        horizontal = horizontal.normalize();
        Vec3 right = horizontal.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        return player.position()
                .add(horizontal.scale(0.68D))
                .add(right.scale(0.12D))
                .add(0.0D, 0.48D, 0.0D);
    }

    static ChargeSmokeBasis chargeSmokeBasis(Vec3 viewDirection) {
        Vec3 forward = viewDirection.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : viewDirection.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = right.normalize();
        return new ChargeSmokeBasis(forward, right, right.cross(forward).normalize());
    }

    static double chargeSmokeScale(double maximumCharge) {
        return maximumCharge <= 0.0D
                ? 0.0D
                : Math.sqrt(maximumCharge / 2.0D);
    }

    static double chargeSmokeMaximumReach(double maximumCharge) {
        double scale = chargeSmokeScale(maximumCharge);
        return scale <= 0.0D ? 0.0D : 1.6D + scale * 1.3D;
    }

    static double chargeSmokeMaximumConeRadius(double maximumCharge) {
        double scale = chargeSmokeScale(maximumCharge);
        return scale <= 0.0D ? 0.0D : (0.55D + scale * 0.75D) * 1.35D;
    }

    static double chargeConeRemainingScale(double progress) {
        return 1.0D - smoothstep(progress);
    }

    static Vec3 chargeSmokeConeCenter(
            Vec3 eye,
            Vec3 foot,
            Vec3 viewDirection,
            double maximumCharge,
            double progress) {
        Vec3 forward = chargeSmokeBasis(viewDirection).forward;
        Vec3 initial = eye.add(forward.scale(chargeSmokeMaximumReach(maximumCharge)));
        return foot.add(initial.subtract(foot).scale(chargeConeRemainingScale(progress)));
    }

    static int chargeSmokeConeParticleCount(double maximumCharge, double progress) {
        if (maximumCharge <= 0.0D || progress < 0.0D || progress >= 1.0D) {
            return 0;
        }
        double maximumCount = 2.0D + chargeSmokeScale(maximumCharge) * 2.6D;
        return CHARGE_SMOKE_PARTICLE_MULTIPLIER * Math.max(1, Mth.ceil(maximumCount
                * (0.35D + chargeConeRemainingScale(progress) * 0.65D)));
    }

    private static double smoothstep(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static void emitKineticOverloadChargeParticles(
            ClientLevel level, Player player, ChargeVisual visual) {
        double progress = Mth.clamp(visual.charge / visual.maximum, 0.0F, 1.0F);
        boolean full = progress >= 0.999D;
        int enchantmentLevel = Mth.clamp(visual.level, 1, 5);
        long phase = level.getGameTime() + player.getId();
        double outerRadius = 1.0D + enchantmentLevel * 0.24D
                + progress * (0.18D + enchantmentLevel * 0.08D);
        double focusRadius = outerRadius * (0.58D + 0.22D * (1.0D - progress));
        Vec3 center = player.position().add(0.0D, 0.12D, 0.0D);
        ParticleOptions dust = new DustParticleOptions(
                Vec3.fromRGB24(chargeColor(visual.charge)).toVector3f(),
                0.55F + enchantmentLevel * 0.1F + (float) progress * 0.25F);

        if (full && visual.completionBurstPending) {
            visual.completionBurstPending = false;
            emitChargeCompletionBurst(
                    level, player, center, outerRadius, enchantmentLevel, dust);
        }

        int moteInterval = full ? Math.max(6, 11 - enchantmentLevel) : Math.max(1, 6 - enchantmentLevel);
        if (phase % moteInterval == 0L) {
            int moteCount = full
                    ? 1 + enchantmentLevel / 3
                    : 1 + (enchantmentLevel - 1) / 2
                            + (int) Math.floor(progress * enchantmentLevel * 0.8D);
            emitChargeMotes(level, player, center, focusRadius, progress,
                    enchantmentLevel, moteCount, dust);
        }

        if (enchantmentLevel >= 3) {
            int orbitInterval = full
                    ? Math.max(5, 10 - enchantmentLevel)
                    : Math.max(1, 5 - enchantmentLevel);
            if (phase % orbitInterval == 0L) {
                emitOrbitingSoulFlames(level, player, center, outerRadius, progress,
                        enchantmentLevel >= 4 ? 2 : 1);
            }
        }

        int circleInterval = full
                ? 15 - enchantmentLevel
                : Math.max(2, 8 - enchantmentLevel);
        if (enchantmentLevel >= 2 && phase % circleInterval == 0L) {
            emitMagicCircle(level, player, visual, outerRadius, full, dust);
        }
    }

    private static void emitChargeMotes(
            ClientLevel level,
            Player player,
            Vec3 center,
            double focusRadius,
            double progress,
            int enchantmentLevel,
            int count,
            ParticleOptions dust) {
        RandomSource random = player.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double shell = focusRadius * (0.76D + random.nextDouble() * 0.24D);
            double height = 0.06D + random.nextDouble()
                    * (0.18D + player.getBbHeight() * (0.18D + enchantmentLevel * 0.035D));
            Vec3 offset = new Vec3(Math.cos(angle) * shell, height, Math.sin(angle) * shell);
            Vec3 inward = new Vec3(-offset.x, -height * 0.15D, -offset.z).normalize()
                    .scale(0.018D + progress * (0.025D + enchantmentLevel * 0.006D));
            Vec3 point = center.add(offset);
            ParticleOptions particle;
            if (enchantmentLevel == 1) {
                particle = dust;
            } else if (enchantmentLevel == 2) {
                particle = (i & 1) == 0 ? dust : ParticleTypes.END_ROD;
            } else if (enchantmentLevel == 3) {
                particle = switch (i % 3) {
                    case 0 -> ParticleTypes.END_ROD;
                    case 1 -> ParticleTypes.ENCHANT;
                    default -> dust;
                };
            } else {
                particle = switch (i & 3) {
                    case 0 -> ParticleTypes.END_ROD;
                    case 1 -> ParticleTypes.ENCHANT;
                    case 2 -> ParticleTypes.SOUL;
                    default -> dust;
                };
            }
            level.addParticle(particle, point.x, point.y, point.z,
                    inward.x, inward.y, inward.z);
        }
    }

    private static void emitChargeCompletionBurst(
            ClientLevel level,
            Player player,
            Vec3 center,
            double radius,
            int enchantmentLevel,
            ParticleOptions dust) {
        RandomSource random = player.getRandom();
        int count = 2 + enchantmentLevel * 2;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            Vec3 outward = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 point = center.add(outward.scale(radius * 0.42D));
            ParticleOptions particle = enchantmentLevel >= 4 && i % 3 == 0
                    ? ParticleTypes.SOUL_FIRE_FLAME
                    : dust;
            level.addParticle(particle, point.x, point.y + random.nextDouble() * 0.12D, point.z,
                    outward.x * 0.035D, 0.025D + random.nextDouble() * 0.025D,
                    outward.z * 0.035D);
        }
        if (enchantmentLevel >= 5) {
            level.addParticle(ParticleTypes.FLASH, center.x, center.y + 0.1D, center.z,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private static void emitOrbitingSoulFlames(
            ClientLevel level,
            Player player,
            Vec3 center,
            double radius,
            double progress,
            int count) {
        double rotation = level.getGameTime() * (0.11D + progress * 0.08D) + player.getId() * 0.37D;
        for (int i = 0; i < count; i++) {
            double angle = rotation + Math.PI * 2.0D * i / count;
            double height = 0.18D + Math.sin(rotation * 1.7D + i * Math.PI) * 0.09D;
            Vec3 point = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y, point.z, 0.0D, 0.012D, 0.0D);
            if (count > 1 && ((level.getGameTime() + i) & 1L) == 0L) {
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
        int enchantmentLevel = Mth.clamp(visual.level, 1, 5);
        int rings = full ? 1 : enchantmentLevel >= 5 ? 3 : enchantmentLevel >= 4 ? 2 : 1;
        int points = 8 + enchantmentLevel * 4
                + (int) Math.floor((visual.charge / visual.maximum) * enchantmentLevel * 2.0F);
        double rotation = level.getGameTime() * (full ? 0.025D : 0.02D + enchantmentLevel * 0.008D);
        for (int ring = 0; ring < rings; ring++) {
            double ringRadius = outerRadius * (full ? 0.52D : 0.56D + ring * 0.2D);
            double ringRotation = ring % 2 == 0 ? rotation : -rotation;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points + ringRotation;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                level.addParticle(dust, x, center.y, z, 0.0D, full ? 0.002D : 0.0D, 0.0D);
            }
        }

        if (full || enchantmentLevel < 4) {
            return;
        }
        int sigils = enchantmentLevel == 4 ? 3 : 5;
        for (int sigil = 0; sigil < sigils; sigil++) {
            double angle = Math.PI * 2.0D * sigil / sigils - rotation;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            for (int pointIndex = 0; pointIndex < 3; pointIndex++) {
                double distance = outerRadius * (0.28D + pointIndex * 0.13D);
                double bend = pointIndex == 1 ? outerRadius * 0.06D : 0.0D;
                Vec3 point = center.add(radial.scale(distance)).add(tangent.scale(bend));
                level.addParticle(pointIndex == 1 ? ParticleTypes.ELECTRIC_SPARK : dust,
                        point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
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
        if (!visual.machRingsPrepared) {
            prepareMachRings(entity, current, visual);
        }
        emitDueMachRings(level, visual);
    }

    private static void prepareMachRings(
            LivingEntity entity, Vec3 start, KickedVisual visual) {
        visual.machRingsPrepared = true;
        int ringCount = KickMath.machRingCount(visual.visualSpeed);
        double ringInterval = KickMath.machRingInterval(visual.visualSpeed);
        double baseRadius = Math.max(1.05D,
                entity.getBbWidth() * 0.75D + entity.getBbHeight() * 0.2D)
                * MACH_RING_RADIUS_MULTIPLIER;
        List<PredictedMachRing> predictedRings = predictMachRings(
                start, visual.initialVelocity, entity.isInWater(), ringCount, ringInterval);
        for (int ringIndex = 0; ringIndex < predictedRings.size(); ringIndex++) {
            PredictedMachRing predicted = predictedRings.get(ringIndex);
            double radius = baseRadius
                    * KickMath.machRingRadiusScale(ringIndex, visual.visualSpeed);
            long arrivalTime = visual.startedAt
                    + Math.max(0L, Math.round(predicted.arrivalTick));
            visual.machRings.add(new MachRing(
                    predicted.center,
                    predicted.direction,
                    radius,
                    arrivalTime,
                    predicted.trailPath));
        }
    }

    static List<PredictedMachRing> predictMachRings(
            Vec3 start,
            Vec3 initialVelocity,
            boolean submerged,
            int ringCount,
            double ringInterval) {
        if (initialVelocity.lengthSqr() <= 1.0E-6D
                || ringCount <= 0
                || ringInterval <= 0.0D) {
            return List.of();
        }
        List<PredictedMachRing> rings = new ArrayList<>(ringCount);
        List<Vec3> trailPath = new ArrayList<>();
        trailPath.add(start);
        Vec3 velocity = initialVelocity;
        Vec3 position = start;
        double travelled = 0.0D;
        int ringIndex = 0;
        for (int tick = 0;
                ringIndex < ringCount && tick < MACH_RING_MAX_PREDICTION_TICKS;
                tick++) {
            double segmentLength = velocity.length();
            if (segmentLength > 1.0E-6D) {
                double segmentEnd = travelled + segmentLength;
                while (ringIndex < ringCount
                        && MACH_RING_START_DISTANCE + ringIndex * ringInterval
                                <= segmentEnd + 1.0E-6D) {
                    double targetDistance = MACH_RING_START_DISTANCE
                            + ringIndex * ringInterval;
                    double progress = Mth.clamp(
                            (targetDistance - travelled) / segmentLength, 0.0D, 1.0D);
                    Vec3 direction = velocity.normalize();
                    Vec3 center = position.add(velocity.scale(progress));
                    appendTrailPoint(trailPath, center);
                    rings.add(new PredictedMachRing(
                            center, direction, tick + progress, List.copyOf(trailPath)));
                    trailPath.clear();
                    trailPath.add(center);
                    ringIndex++;
                }
                position = position.add(velocity);
                appendTrailPoint(trailPath, position);
                travelled = segmentEnd;
            }
            velocity = KickMath.nextBallisticVelocity(velocity, submerged);
        }
        return List.copyOf(rings);
    }

    private static void appendTrailPoint(List<Vec3> trailPath, Vec3 point) {
        Vec3 previous = trailPath.get(trailPath.size() - 1);
        if (previous.distanceToSqr(point) > 1.0E-12D) {
            trailPath.add(point);
        }
    }

    private static void emitDueMachRings(ClientLevel level, KickedVisual visual) {
        long now = gameTime();
        while (visual.nextMachRing < visual.machRings.size()) {
            MachRing ring = visual.machRings.get(visual.nextMachRing);
            if (ring.arrivalTime > now) {
                break;
            }
            emitMachRing(level, ring.center, ring.direction, ring.radius,
                    KickMath.machRingLifetimeTicks(visual.visualSpeed));
            emitMachTrail(level, ring.trailPath, visual.visualSpeed);
            visual.nextMachRing++;
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

    private static void emitMachRing(
            ClientLevel level, Vec3 center, Vec3 direction, double radius, int lifetimeTicks) {
        Vec3 first = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (first.lengthSqr() < 1.0E-4D) {
            first = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        first = first.normalize();
        Vec3 second = direction.cross(first).normalize();
        int points = Mth.clamp((int) Math.ceil(radius * 15.0D),
                MACH_RING_MIN_POINTS, MACH_RING_MAX_POINTS);
        int layers = Mth.clamp((int) Math.ceil(radius * 0.55D),
                MACH_RING_MIN_LAYERS, MACH_RING_MAX_LAYERS);
        double thickness = radius * 0.117D;
        for (int i = 0; i < points; i++) {
            double angleStep = Math.PI * 2.0D / points;
            double angle = angleStep * i
                    + (level.random.nextDouble() - 0.5D) * angleStep * 0.55D;
            Vec3 outward = first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle)));
            for (int layer = 0; layer < layers; layer++) {
                double layerOffset = layers == 1
                        ? 0.0D
                        : thickness * ((double) layer / (layers - 1) - 0.5D);
                double radialJitter = (level.random.nextDouble() - 0.5D) * thickness * 0.3D;
                double axialJitter = (level.random.nextDouble() - 0.5D) * thickness * 0.45D;
                Vec3 point = center.add(outward.scale(radius + layerOffset + radialJitter))
                        .add(direction.scale(axialJitter));
                level.addAlwaysVisibleParticle(ModParticleTypes.MACH_RING.get(), true,
                        point.x, point.y, point.z, lifetimeTicks, 0.0D, 0.0D);
            }
        }
    }

    private static void emitMachTrail(
            ClientLevel level,
            List<Vec3> trailPath,
            double launchSpeed) {
        double trailLength = machTrailLength(trailPath);
        if (trailLength <= 1.0E-6D) {
            return;
        }
        int lifetimeTicks = KickMath.machTrailLifetimeTicks(launchSpeed);
        int intervals = Math.max(10, Mth.ceil(trailLength * 2.5D));
        for (int i = 0; i <= intervals; i++) {
            MachTrailSample sample = sampleMachTrail(
                    trailPath, trailLength * i / intervals);
            Vec3 first = sample.direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (first.lengthSqr() < 1.0E-4D) {
                first = sample.direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            first = first.normalize();
            Vec3 second = sample.direction.cross(first).normalize();
            double jitterScale = i == 0 || i == intervals ? 0.0D : 0.035D;
            Vec3 jitter = first.scale(level.random.nextGaussian() * jitterScale)
                    .add(second.scale(level.random.nextGaussian() * jitterScale));
            Vec3 point = sample.point.add(jitter);
            level.addAlwaysVisibleParticle(ModParticleTypes.MACH_TRAIL.get(), true,
                    point.x, point.y, point.z, lifetimeTicks, 0.0D, 0.0D);
        }
    }

    static double machTrailLength(List<Vec3> trailPath) {
        double length = 0.0D;
        for (int i = 1; i < trailPath.size(); i++) {
            length += trailPath.get(i).distanceTo(trailPath.get(i - 1));
        }
        return length;
    }

    static MachTrailSample sampleMachTrail(List<Vec3> trailPath, double distance) {
        if (trailPath.isEmpty()) {
            throw new IllegalArgumentException("Mach trail path must not be empty");
        }
        double remaining = Math.max(0.0D, distance);
        Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
        for (int i = 1; i < trailPath.size(); i++) {
            Vec3 start = trailPath.get(i - 1);
            Vec3 delta = trailPath.get(i).subtract(start);
            double length = delta.length();
            if (length <= 1.0E-9D) {
                continue;
            }
            direction = delta.scale(1.0D / length);
            if (remaining <= length || i == trailPath.size() - 1) {
                return new MachTrailSample(
                        start.add(delta.scale(Mth.clamp(remaining / length, 0.0D, 1.0D))),
                        direction);
            }
            remaining -= length;
        }
        return new MachTrailSample(trailPath.get(trailPath.size() - 1), direction);
    }

    private static void applyPose(ModelPart part, KickAnimation.Pose pose) {
        part.xRot = pose.xRot();
        part.yRot = pose.yRot();
        part.zRot = pose.zRot();
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

    private record PlayerAnimation(
            byte animation,
            long startedAt,
            int chargeLevel,
            int kineticOverloadLevel,
            KickAnimation.Pose startPose) {
        private PlayerAnimation withChargeState(int level, int overloadLevel) {
            return new PlayerAnimation(
                    animation, startedAt, Mth.clamp(level, 1, 5),
                    Math.max(0, overloadLevel), startPose);
        }
    }

    record ChargeSmokeBasis(Vec3 forward, Vec3 right, Vec3 up) {
    }

    private static final class ChargeVisual {
        private float charge;
        private float maximum;
        private int level;
        private int kineticOverloadLevel;
        private boolean completionBurstPending;
        private double lastFunnelEmissionProgress = -1.0D;

        private ChargeVisual(
                float charge, float maximum, int level, int kineticOverloadLevel) {
            update(charge, maximum, level, kineticOverloadLevel);
        }

        private void update(
                float value, float maximum, int level, int kineticOverloadLevel) {
            float safeMaximum = Math.max(0.0F, maximum);
            float safeValue = Math.max(0.0F, value);
            boolean wasFull = this.maximum > 0.0F
                    && this.charge >= this.maximum - 1.0E-4F;
            boolean nowFull = safeMaximum > 0.0F
                    && safeValue >= safeMaximum - 1.0E-4F;
            if (!wasFull && nowFull) {
                this.completionBurstPending = true;
            }
            this.charge = safeValue;
            this.maximum = safeMaximum;
            this.level = Math.max(0, level);
            this.kineticOverloadLevel = Math.max(0, kineticOverloadLevel);
        }
    }

    private static final class UnstableExplosionVisual {
        private final Vec3 center;
        private final double scale;
        private int age;

        private UnstableExplosionVisual(Vec3 center, double scale) {
            this.center = center;
            this.scale = scale;
        }
    }

    private static final class KickedVisual {
        private final boolean spin;
        private final long startedAt;
        private final double visualSpeed;
        private final Vec3 initialVelocity;
        private final List<MachRing> machRings = new ArrayList<>();
        private Vec3 lastPosition;
        private Vec3 lastDirection = new Vec3(0.0D, 0.0D, 1.0D);
        private boolean machRingsPrepared;
        private int nextMachRing;

        private KickedVisual(
                boolean spin, long startedAt, double visualSpeed, Vec3 initialVelocity) {
            this.spin = spin;
            this.startedAt = startedAt;
            this.visualSpeed = visualSpeed;
            this.initialVelocity = initialVelocity;
        }

        private boolean hasPendingMachRings() {
            return this.machRingsPrepared && this.nextMachRing < this.machRings.size();
        }

        private boolean machSequenceComplete() {
            return this.machRingsPrepared && this.nextMachRing >= this.machRings.size();
        }
    }

    private record MachRing(
            Vec3 center,
            Vec3 direction,
            double radius,
            long arrivalTime,
            List<Vec3> trailPath) {
    }

    record PredictedMachRing(
            Vec3 center,
            Vec3 direction,
            double arrivalTick,
            List<Vec3> trailPath) {
    }

    record MachTrailSample(Vec3 point, Vec3 direction) {
    }
}
