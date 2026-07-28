package net.lostpatrol.onekick.network;

import java.util.ArrayList;
import java.util.List;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.client.ClientKickState;
import net.lostpatrol.onekick.kick.KickManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class KickNetwork {
    public static final byte ANIMATION_CHARGE = 0;
    public static final byte ANIMATION_KICK = 1;
    public static final byte ANIMATION_STOP = 2;
    private static final String PROTOCOL = "5";
    private static final int MAX_DISINTEGRATION_SMOKE_ORIGINS = 1024;

    private KickNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(KickInputPacket.TYPE, KickInputPacket.STREAM_CODEC,
                KickNetwork::handleInput);
        registrar.playToClient(ChargeStatePacket.TYPE, ChargeStatePacket.STREAM_CODEC,
                ClientHandlers::handleChargeState);
        registrar.playToClient(PlayerAnimationPacket.TYPE, PlayerAnimationPacket.STREAM_CODEC,
                ClientHandlers::handlePlayerAnimation);
        registrar.playToClient(KickedEntityPacket.TYPE, KickedEntityPacket.STREAM_CODEC,
                ClientHandlers::handleKickedEntity);
        registrar.playToClient(
                DisintegrationSmokePacket.TYPE,
                DisintegrationSmokePacket.STREAM_CODEC,
                ClientHandlers::handleDisintegrationSmoke);
    }

    public static void sendInput(boolean pressed) {
        PacketDistributor.sendToServer(new KickInputPacket(pressed));
    }

    public static void broadcastChargeState(
            ServerPlayer player, boolean active, float charge, float maximum, int chargeLevel) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new ChargeStatePacket(player.getId(), active, charge, maximum, chargeLevel));
    }

    public static void sendChargeState(
            ServerPlayer receiver,
            ServerPlayer chargingPlayer,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel) {
        PacketDistributor.sendToPlayer(
                receiver,
                new ChargeStatePacket(
                        chargingPlayer.getId(), active, charge, maximum, chargeLevel));
    }

    public static void broadcastPlayerAnimation(ServerPlayer player, byte animation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, new PlayerAnimationPacket(player.getId(), animation));
    }

    public static void sendPlayerAnimation(ServerPlayer receiver, Entity player, byte animation) {
        PacketDistributor.sendToPlayer(
                receiver, new PlayerAnimationPacket(player.getId(), animation));
    }

    public static void broadcastKickedState(
            LivingEntity entity, boolean active, boolean spin, Vec3 initialVelocity) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                entity, KickedEntityPacket.create(
                        entity.getId(), active, spin, initialVelocity));
    }

    public static void sendKickedState(
            ServerPlayer receiver,
            LivingEntity entity,
            boolean active,
            boolean spin,
            Vec3 initialVelocity) {
        PacketDistributor.sendToPlayer(
                receiver,
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
                PacketDistributor.sendToPlayer(player, packet);
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
        BlockPos first = origins.getFirst();
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

    private static void handleInput(KickInputPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            KickManager.handleInput(player, packet.pressed());
        }
    }

    private static ResourceLocation packetId(String path) {
        return ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, path);
    }

    private static final class ClientHandlers {
        private ClientHandlers() {
        }

        private static void handleChargeState(
                ChargeStatePacket packet, IPayloadContext context) {
            ClientKickState.updateCharge(packet.entityId(), packet.active(), packet.charge(),
                    packet.maximum(), packet.chargeLevel());
        }

        private static void handlePlayerAnimation(
                PlayerAnimationPacket packet, IPayloadContext context) {
            ClientKickState.updatePlayerAnimation(packet.entityId(), packet.animation());
        }

        private static void handleKickedEntity(
                KickedEntityPacket packet, IPayloadContext context) {
            ClientKickState.updateKickedEntity(
                    packet.entityId(), packet.active(), packet.spin(), packet.visualSpeed(),
                    new Vec3(packet.initialX(), packet.initialY(), packet.initialZ()));
        }

        private static void handleDisintegrationSmoke(
                DisintegrationSmokePacket packet, IPayloadContext context) {
            ClientKickState.emitDisintegrationSmoke(
                    new Vec3(packet.impactX(), packet.impactY(), packet.impactZ()),
                    new Vec3(packet.directionX(), packet.directionY(), packet.directionZ()),
                    packet.impactSpeed(), packet.affectedBlocks(), packet.smokeOrigins());
        }
    }

    private record KickInputPacket(boolean pressed) implements CustomPacketPayload {
        private static final Type<KickInputPacket> TYPE =
                new Type<>(packetId("kick_input"));
        private static final StreamCodec<RegistryFriendlyByteBuf, KickInputPacket> STREAM_CODEC =
                StreamCodec.ofMember(KickInputPacket::encode, KickInputPacket::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(pressed);
        }

        private static KickInputPacket decode(RegistryFriendlyByteBuf buffer) {
            return new KickInputPacket(buffer.readBoolean());
        }

        @Override
        public Type<KickInputPacket> type() {
            return TYPE;
        }
    }

    private record ChargeStatePacket(
            int entityId,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel
    ) implements CustomPacketPayload {
        private static final Type<ChargeStatePacket> TYPE =
                new Type<>(packetId("charge_state"));
        private static final StreamCodec<RegistryFriendlyByteBuf, ChargeStatePacket> STREAM_CODEC =
                StreamCodec.ofMember(ChargeStatePacket::encode, ChargeStatePacket::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeBoolean(active);
            buffer.writeFloat(charge);
            buffer.writeFloat(maximum);
            buffer.writeVarInt(chargeLevel);
        }

        private static ChargeStatePacket decode(RegistryFriendlyByteBuf buffer) {
            return new ChargeStatePacket(
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readVarInt());
        }

        @Override
        public Type<ChargeStatePacket> type() {
            return TYPE;
        }
    }

    private record PlayerAnimationPacket(
            int entityId,
            byte animation
    ) implements CustomPacketPayload {
        private static final Type<PlayerAnimationPacket> TYPE =
                new Type<>(packetId("player_animation"));
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerAnimationPacket>
                STREAM_CODEC = StreamCodec.ofMember(
                        PlayerAnimationPacket::encode, PlayerAnimationPacket::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeByte(animation);
        }

        private static PlayerAnimationPacket decode(RegistryFriendlyByteBuf buffer) {
            return new PlayerAnimationPacket(buffer.readVarInt(), buffer.readByte());
        }

        @Override
        public Type<PlayerAnimationPacket> type() {
            return TYPE;
        }
    }

    private record KickedEntityPacket(
            int entityId,
            boolean active,
            boolean spin,
            float visualSpeed,
            double initialX,
            double initialY,
            double initialZ
    ) implements CustomPacketPayload {
        private static final Type<KickedEntityPacket> TYPE =
                new Type<>(packetId("kicked_entity"));
        private static final StreamCodec<RegistryFriendlyByteBuf, KickedEntityPacket> STREAM_CODEC =
                StreamCodec.ofMember(KickedEntityPacket::encode, KickedEntityPacket::decode);

        private static KickedEntityPacket create(
                int entityId, boolean active, boolean spin, Vec3 initialVelocity) {
            return new KickedEntityPacket(
                    entityId, active, spin, (float) initialVelocity.length(),
                    initialVelocity.x, initialVelocity.y, initialVelocity.z);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId);
            buffer.writeBoolean(active);
            buffer.writeBoolean(spin);
            buffer.writeFloat(visualSpeed);
            buffer.writeDouble(initialX);
            buffer.writeDouble(initialY);
            buffer.writeDouble(initialZ);
        }

        private static KickedEntityPacket decode(RegistryFriendlyByteBuf buffer) {
            return new KickedEntityPacket(
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readFloat(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble());
        }

        @Override
        public Type<KickedEntityPacket> type() {
            return TYPE;
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
            List<BlockPos> smokeOrigins
    ) implements CustomPacketPayload {
        private static final Type<DisintegrationSmokePacket> TYPE =
                new Type<>(packetId("disintegration_smoke"));
        private static final StreamCodec<RegistryFriendlyByteBuf, DisintegrationSmokePacket>
                STREAM_CODEC = StreamCodec.ofMember(
                        DisintegrationSmokePacket::encode, DisintegrationSmokePacket::decode);

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeDouble(impactX);
            buffer.writeDouble(impactY);
            buffer.writeDouble(impactZ);
            buffer.writeDouble(directionX);
            buffer.writeDouble(directionY);
            buffer.writeDouble(directionZ);
            buffer.writeFloat(impactSpeed);
            buffer.writeVarInt(affectedBlocks);
            buffer.writeVarInt(smokeOrigins.size());
            smokeOrigins.forEach(buffer::writeBlockPos);
        }

        private static DisintegrationSmokePacket decode(RegistryFriendlyByteBuf buffer) {
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
                throw new IllegalArgumentException(
                        "Invalid disintegration smoke origin count: " + originCount);
            }
            List<BlockPos> smokeOrigins = new ArrayList<>(originCount);
            for (int i = 0; i < originCount; i++) {
                smokeOrigins.add(buffer.readBlockPos());
            }
            return new DisintegrationSmokePacket(
                    impactX, impactY, impactZ,
                    directionX, directionY, directionZ,
                    impactSpeed, affectedBlocks, List.copyOf(smokeOrigins));
        }

        @Override
        public Type<DisintegrationSmokePacket> type() {
            return TYPE;
        }
    }
}
