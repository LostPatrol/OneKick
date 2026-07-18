package net.lostpatrol.onekick.kick;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record KickSnapshot(
        UUID attackerId,
        double kickSpeed,
        KickEnchantments enchantments,
        ItemStack boots
) {
    public KickSnapshot {
        boots = boots.copy();
    }

    @Nullable
    public ServerPlayer attacker(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(attackerId);
        return player != null && player.level() == level ? player : null;
    }
}
