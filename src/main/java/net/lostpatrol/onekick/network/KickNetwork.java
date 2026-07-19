package net.lostpatrol.onekick.network;

import java.util.function.Supplier;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.client.ClientKickState;
import net.lostpatrol.onekick.kick.KickManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    private static final String PROTOCOL = "3";
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
    }

    public static void sendInput(boolean pressed) {
        CHANNEL.sendToServer(new KickInputPacket(pressed));
    }

    public static void broadcastChargeState(
            ServerPlayer player, boolean active, float charge, float maximum, int chargeLevel) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new ChargeStatePacket(player.getId(), active, charge, maximum, chargeLevel));
    }

    public static void sendChargeState(
            ServerPlayer receiver,
            ServerPlayer chargingPlayer,
            boolean active,
            float charge,
            float maximum,
            int chargeLevel) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                new ChargeStatePacket(chargingPlayer.getId(), active, charge, maximum, chargeLevel));
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
                        packet.maximum(), packet.chargeLevel()));
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

    private record KickInputPacket(boolean pressed) {
        private static void encode(KickInputPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.pressed);
        }

        private static KickInputPacket decode(FriendlyByteBuf buffer) {
            return new KickInputPacket(buffer.readBoolean());
        }
    }

    private record ChargeStatePacket(
            int entityId, boolean active, float charge, float maximum, int chargeLevel) {
        private static void encode(ChargeStatePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeBoolean(packet.active);
            buffer.writeFloat(packet.charge);
            buffer.writeFloat(packet.maximum);
            buffer.writeVarInt(packet.chargeLevel);
        }

        private static ChargeStatePacket decode(FriendlyByteBuf buffer) {
            return new ChargeStatePacket(buffer.readVarInt(), buffer.readBoolean(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readVarInt());
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
}
