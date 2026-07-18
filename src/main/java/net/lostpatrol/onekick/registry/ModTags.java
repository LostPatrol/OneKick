package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<EntityType<?>> KICK_IMMUNE = entityTag("kick_immune");
    public static final TagKey<EntityType<?>> FLYING = entityTag("flying");
    public static final TagKey<Block> DISINTEGRATION_IMMUNE = blockTag("disintegration_immune");
    public static final TagKey<Block> DISINTEGRATION_DIRECT = blockTag("disintegration_direct");

    private ModTags() {
    }

    private static TagKey<EntityType<?>> entityTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, path));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, path));
    }
}
