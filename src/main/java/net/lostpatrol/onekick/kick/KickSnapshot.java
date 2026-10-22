/** Keeps release-time power and shared progress throughout flight and delayed explosions. */
package net.lostpatrol.onekick.kick;

import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.registry.ModEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record KickSnapshot(
        UUID attackerId,
        double kickSpeed,
        KickEnchantments enchantments,
        ItemStack boots,
        float charge,
        KickProgress progress
) {
    /** Copies the tool so equipment changes cannot alter an in-flight kick. */
    public KickSnapshot {
        boots = boots.copy();
    }

    /** Creates an uncharged kick with independent advancement progress. */
    public KickSnapshot(UUID attackerId, double kickSpeed, KickEnchantments enchantments,
            ItemStack boots) {
        this(attackerId, kickSpeed, enchantments, boots, 0.0F);
    }

    /** Freezes attained charge; kickSpeed separately includes release hunger attenuation. */
    public KickSnapshot(UUID attackerId, double kickSpeed, KickEnchantments enchantments,
            ItemStack boots, float charge) {
        this(attackerId, kickSpeed, enchantments, boots, charge, new KickProgress());
    }

    /** Only a fully charged maximum-tier combination can erase protected blocks. */
    public boolean singularityActive() {
        return enchantments.singularity() > 0
                && enchantments.disintegration() > 0
                && enchantments.charge() >= ModEnchantments.CHARGE.get().getMaxLevel()
                && enchantments.overcharge() >= ModEnchantments.OVERCHARGE.get().getMaxLevel()
                && charge >= KickMath.maxCharge(enchantments.charge(), enchantments.overcharge());
    }

    /** Resolves the original player in the current dimension. */
    @Nullable
    public ServerPlayer attacker(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(attackerId);
        return player != null && player.level() == level ? player : null;
    }
}
