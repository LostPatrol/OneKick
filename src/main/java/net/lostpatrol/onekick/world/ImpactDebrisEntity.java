package net.lostpatrol.onekick.world;

import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class ImpactDebrisEntity extends FallingBlockEntity {
    private ItemStack dropTool = ItemStack.EMPTY;
    @Nullable
    private UUID ownerId;
    private boolean dropOnLanding;
    private boolean dropsFinished;

    public ImpactDebrisEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    public static ImpactDebrisEntity launch(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Vec3 velocity,
            @Nullable ServerPlayer owner,
            ItemStack tool,
            boolean dropOnLanding) {
        ImpactDebrisEntity debris = new ImpactDebrisEntity(ModEntityTypes.IMPACT_DEBRIS.get(), level);
        BlockState visualState = state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                : state;
        CompoundTag baseData = new CompoundTag();
        baseData.put("BlockState", NbtUtils.writeBlockState(visualState));
        baseData.putBoolean("DropItem", false);
        baseData.putBoolean("CancelDrop", true);
        debris.readAdditionalSaveData(baseData);
        debris.dropItem = false;
        debris.disableDrop();
        debris.dropTool = tool.copy();
        debris.ownerId = owner == null ? null : owner.getUUID();
        debris.dropOnLanding = dropOnLanding;
        debris.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        debris.setStartPos(pos);
        debris.setDeltaMovement(velocity);
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(debris);
        return debris;
    }

    @Override
    public void tick() {
        boolean wasPresent = !isRemoved();
        super.tick();
        if (!level().isClientSide && wasPresent && isRemoved() && !dropsFinished) {
            finishDrops((ServerLevel) level());
        }
    }

    private void finishDrops(ServerLevel level) {
        dropsFinished = true;
        if (!dropOnLanding) {
            return;
        }
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() != level) {
            owner = null;
        }
        Block.getDrops(getBlockState(), level, blockPosition(), null, owner, dropTool)
                .forEach(stack -> Block.popResource(level, blockPosition(), stack));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!dropTool.isEmpty()) {
            tag.put("OneKickDropTool",
                    dropTool.save(level().registryAccess(), new CompoundTag()));
        }
        if (ownerId != null) {
            tag.putUUID("OneKickOwner", ownerId);
        }
        tag.putBoolean("OneKickDropOnLanding", dropOnLanding);
        tag.putBoolean("OneKickDropsFinished", dropsFinished);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dropTool = tag.contains("OneKickDropTool")
                ? ItemStack.parseOptional(
                        level().registryAccess(), tag.getCompound("OneKickDropTool"))
                : ItemStack.EMPTY;
        ownerId = tag.hasUUID("OneKickOwner") ? tag.getUUID("OneKickOwner") : null;
        dropOnLanding = tag.getBoolean("OneKickDropOnLanding");
        dropsFinished = tag.getBoolean("OneKickDropsFinished");
    }
}
