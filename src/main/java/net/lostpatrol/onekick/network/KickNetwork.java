package net.lostpatrol.onekick.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.client.ClientKickState;
import net.lostpatrol.onekick.kick.KickManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class KickNetwork {
    public static final byte ANIMATION_CHARGE = 0;
    public static final byte ANIMATION_KICK = 1;
    public static final byte ANIMATION_STOP = 2;
    private static final String PROTOCOL = "7";
    private static final int MAX_DISINTEGRATION_SMOKE_ORIGINS = 256;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int packetId;

    private KickNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(KickInputPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KickInputPacket::encode)
                .decoder(KickInputPacket::decode)
                .consumerMainThread(KickNetwork::handleInput)
                .add();
        CHANNEL.messageBuilder(ChargeStatePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChargeStatePacket::encode)
                .decoder(ChargeStatePacket::decode)
                .consumerMainThread(KickNetwork::handleChargeState)
                .add();
        CHANNEL.messageBuilder(PlayerAnimationPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerAnimationPacket::encode)
                .decoder(PlayerAnimationPacket::decode)
                .consumerMainThread(KickNetwork::handlePlayerAnimation)
                .add();
        CHANNEL.messageBuilder(KickedEntityPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KickedEntityPacket::encode)
                .decoder(KickedEntityPacket::decode)
                .consumerMainThread(KickNetwork::handleKickedEntity)
                .add();
        CHANNEL.messageBuilder(
                        DisintegrationSmokePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DisintegrationSmokePacket::encode)
                .decoder(DisintegrationSmokePacket::decode)
                .consumerMainThread(KickNetwork::handleDisintegrationSmoke)
                .add();
        CHANNEL.messageBuilder(
                        UnstableExplosionPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnstableExplosionPacket::encode)
                .decoder(UnstableExplosionPacket::decode)
                .consumerMainThread(KickNetwork::handleUnstableExplosion)
                .add();
        CHANNEL.messageBuilder(
                        PlayerImpulsePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerImpulsePacket::encode)
                .decoder(PlayerImpulsePacket::decode)
                .consumerMainThread(KickNetwork::handlePlayerImpulse)
                .add();
    }

    public static void sendInput(boolean pressed) {
        CHANNEL.sendToServer(new KickInputPacket(pressed));
    }

    public static void broadcastChargeState(
            ServerPlayer player,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel,
            int kineticOverloadLevel) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new ChargeStatePacket(
                        player.getId(), active, charge, maximum,
                        chargeLevel, kineticOverloadLevel));
    }

    public static void sendChargeState(
            ServerPlayer receiver,
            ServerPlayer chargingPlayer,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel,
            int kineticOverloadLevel) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                new ChargeStatePacket(
                        chargingPlayer.getId(), active, charge, maximum,
                        chargeLevel, kineticOverloadLevel));
    }

    public static void broadcastPlayerAnimation(ServerPlayer player, byte animation) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PlayerAnimationPacket(player.getId(), animation));
    }

    public static void sendPlayerAnimation(ServerPlayer receiver, Entity player, byte animation) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                new PlayerAnimationPacket(player.getId(), animation));
    }

    public static void broadcastKickedState(
            LivingEntity entity, boolean active, boolean spin, Vec3 initialVelocity) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                KickedEntityPacket.create(entity.getId(), active, spin, initialVelocity));
    }

    public static void sendKickedState(
            ServerPlayer receiver,
            LivingEntity entity,
            boolean active,
            boolean spin,
            Vec3 initialVelocity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                KickedEntityPacket.create(entity.getId(), active, spin, initialVelocity));
    }

    public static void broadcastDisintegrationSmoke(
            ServerLevel level,
            Vec3 impact,
            Vec3 direction,
            double impactSpeed,
            int affectedBlocks,
            List<BlockPos> affectedPositions) {
        if (affectedBlocks <= 0 || affectedPositions.isEmpty()) {
            return;
        }
        List<BlockPos> smokeOrigins = sampleSmokeOrigins(affectedPositions);
        DisintegrationSmokePacket packet = new DisintegrationSmokePacket(
                impact.x, impact.y, impact.z,
                direction.x, direction.y, direction.z,
                (float) impactSpeed, affectedBlocks, smokeOrigins);
        Vec3 center = smokeOriginCenter(smokeOrigins);
        double range = Math.min(256.0D, 64.0D + smokeOriginExtent(smokeOrigins, center));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= range * range) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    public static void sendPlayerImpulse(ServerPlayer player, Vec3 velocity) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PlayerImpulsePacket(player.getId(), velocity.x, velocity.y, velocity.z));
    }

    public static void broadcastUnstableExplosion(
            ServerLevel level, Vec3 position, float radius) {
        UnstableExplosionPacket packet =
                new UnstableExplosionPacket(position.x, position.y, position.z, radius);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position) < 4096.0D) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    private static List<BlockPos> sampleSmokeOrigins(List<BlockPos> positions) {
        if (positions.size() <= MAX_DISINTEGRATION_SMOKE_ORIGINS) {
            return List.copyOf(positions);
        }
        List<BlockPos> sampled = new ArrayList<>(MAX_DISINTEGRATION_SMOKE_ORIGINS);
        int lastIndex = positions.size() - 1;
        for (int i = 0; i < MAX_DISINTEGRATION_SMOKE_ORIGINS; i++) {
            int index = (int) Math.round(
                    i * lastIndex / (double) (MAX_DISINTEGRATION_SMOKE_ORIGINS - 1));
            sampled.add(positions.get(index));
        }
        return List.copyOf(sampled);
    }

    private static Vec3 smokeOriginCenter(List<BlockPos> origins) {
        BlockPos first = origins.get(0);
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos origin : origins) {
            minX = Math.min(minX, origin.getX());
            minY = Math.min(minY, origin.getY());
            minZ = Math.min(minZ, origin.getZ());
            maxX = Math.max(maxX, origin.getX());
            maxY = Math.max(maxY, origin.getY());
            maxZ = Math.max(maxZ, origin.getZ());
        }
        return new Vec3(
                (minX + maxX + 1.0D) * 0.5D,
                (minY + maxY + 1.0D) * 0.5D,
                (minZ + maxZ + 1.0D) * 0.5D);
    }

    private static double smokeOriginExtent(List<BlockPos> origins, Vec3 center) {
        double maximumDistanceSquared = 0.0D;
        for (BlockPos origin : origins) {
            maximumDistanceSquared = Math.max(maximumDistanceSquared,
                    Vec3.atCenterOf(origin).distanceToSqr(center));
        }
        return Math.sqrt(maximumDistanceSquared);
    }

    private static void handleInput(KickInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = contextSupplier.get().getSender();
        if (player != null) {
            KickManager.handleInput(player, packet.pressed());
        }
    }

    private static void handleChargeState(
            ChargeStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.updateCharge(packet.entityId(), packet.active(), packet.charge(),
                        packet.maximum(), packet.chargeLevel(), packet.kineticOverloadLevel()));
    }

    private static void handlePlayerAnimation(
            PlayerAnimationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.updatePlayerAnimation(packet.entityId(), packet.animation()));
    }

    private static void handleKickedEntity(
            KickedEntityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.updateKickedEntity(
                        packet.entityId(), packet.active(), packet.spin(), packet.visualSpeed(),
                        new Vec3(packet.initialX(), packet.initialY(), packet.initialZ())));
    }

    private static void handleDisintegrationSmoke(
            DisintegrationSmokePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.emitDisintegrationSmoke(
                        new Vec3(packet.impactX(), packet.impactY(), packet.impactZ()),
                        new Vec3(packet.directionX(), packet.directionY(), packet.directionZ()),
                        packet.impactSpeed(), packet.affectedBlocks(), packet.smokeOrigins()));
    }

    private static void handleUnstableExplosion(
            UnstableExplosionPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.emitUnstableExplosion(
                        new Vec3(packet.x(), packet.y(), packet.z()), packet.radius()));
    }

    private static void handlePlayerImpulse(
            PlayerImpulsePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientKickState.applyPlayerImpulse(
                        packet.entityId(),
                        new Vec3(packet.x(), packet.y(), packet.z())));
    }

    private record KickInputPacket(boolean pressed) {
        private static void encode(KickInputPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.pressed);
        }

        private static KickInputPacket decode(FriendlyByteBuf buffer) {
            return new KickInputPacket(buffer.readBoolean());
        }
    }

    private record ChargeStatePacket(
            int entityId,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel,
            int kineticOverloadLevel) {
        private static void encode(ChargeStatePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeBoolean(packet.active);
            buffer.writeFloat(packet.charge);
            buffer.writeFloat(packet.maximum);
            buffer.writeVarInt(packet.chargeLevel);
            buffer.writeVarInt(packet.kineticOverloadLevel);
        }

        private static ChargeStatePacket decode(FriendlyByteBuf buffer) {
            return new ChargeStatePacket(buffer.readVarInt(), buffer.readBoolean(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    private record PlayerAnimationPacket(int entityId, byte animation) {
        private static void encode(PlayerAnimationPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeByte(packet.animation);
        }

        private static PlayerAnimationPacket decode(FriendlyByteBuf buffer) {
            return new PlayerAnimationPacket(buffer.readVarInt(), buffer.readByte());
        }
    }

    private record KickedEntityPacket(
            int entityId,
            boolean active,
            boolean spin,
            float visualSpeed,
            double initialX,
            double initialY,
            double initialZ) {
        private static KickedEntityPacket create(
                int entityId, boolean active, boolean spin, Vec3 initialVelocity) {
            return new KickedEntityPacket(
                    entityId, active, spin, (float) initialVelocity.length(),
                    initialVelocity.x, initialVelocity.y, initialVelocity.z);
        }

        private static void encode(KickedEntityPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeBoolean(packet.active);
            buffer.writeBoolean(packet.spin);
            buffer.writeFloat(packet.visualSpeed);
            buffer.writeDouble(packet.initialX);
            buffer.writeDouble(packet.initialY);
            buffer.writeDouble(packet.initialZ);
        }

        private static KickedEntityPacket decode(FriendlyByteBuf buffer) {
            return new KickedEntityPacket(
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    }

    private record DisintegrationSmokePacket(
            double impactX,
            double impactY,
            double impactZ,
            double directionX,
            double directionY,
            double directionZ,
            float impactSpeed,
            int affectedBlocks,
            List<BlockPos> smokeOrigins) {
        private static void encode(
                DisintegrationSmokePacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.impactX);
            buffer.writeDouble(packet.impactY);
            buffer.writeDouble(packet.impactZ);
            buffer.writeDouble(packet.directionX);
            buffer.writeDouble(packet.directionY);
            buffer.writeDouble(packet.directionZ);
            buffer.writeFloat(packet.impactSpeed);
            buffer.writeVarInt(packet.affectedBlocks);
            buffer.writeVarInt(packet.smokeOrigins.size());
            packet.smokeOrigins.forEach(buffer::writeBlockPos);
        }

        private static DisintegrationSmokePacket decode(FriendlyByteBuf buffer) {
            double impactX = buffer.readDouble();
            double impactY = buffer.readDouble();
            double impactZ = buffer.readDouble();
            double directionX = buffer.readDouble();
            double directionY = buffer.readDouble();
            double directionZ = buffer.readDouble();
            float impactSpeed = buffer.readFloat();
            int affectedBlocks = buffer.readVarInt();
            int originCount = buffer.readVarInt();
            if (originCount < 0 || originCount > MAX_DISINTEGRATION_SMOKE_ORIGINS) {
                throw new IllegalArgumentException("Invalid disintegration smoke origin count: "
                        + originCount);
            }
            List<BlockPos> smokeOrigins = new ArrayList<>(originCount);
            for (int i = 0; i < originCount; i++) {
                smokeOrigins.add(buffer.readBlockPos());
            }
            return new DisintegrationSmokePacket(
                    impactX, impactY, impactZ, directionX, directionY, directionZ,
                    impactSpeed, affectedBlocks, List.copyOf(smokeOrigins));
        }
    }

    private record UnstableExplosionPacket(double x, double y, double z, float radius) {
        private static void encode(
                UnstableExplosionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.x);
            buffer.writeDouble(packet.y);
            buffer.writeDouble(packet.z);
            buffer.writeFloat(packet.radius);
        }

        private static UnstableExplosionPacket decode(FriendlyByteBuf buffer) {
            return new UnstableExplosionPacket(
                    buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat());
        }
    }

    private record PlayerImpulsePacket(int entityId, double x, double y, double z) {
        private static void encode(PlayerImpulsePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeDouble(packet.x);
            buffer.writeDouble(packet.y);
            buffer.writeDouble(packet.z);
        }

        private static PlayerImpulsePacket decode(FriendlyByteBuf buffer) {
            return new PlayerImpulsePacket(
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble());
        }
    }
}
