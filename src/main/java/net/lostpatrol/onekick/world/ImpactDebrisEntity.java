/** Falling kick fragments with delayed loot and optional plot launch coordinates. */
package net.lostpatrol.onekick.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
    /** Plot loot is resolved before removal can invalidate the original body or block context. */
    @Nullable
    private List<ItemStack> plotDrops;

    /** Creates a fragment with vanilla falling-block motion and placement disabled by launch. */
    public ImpactDebrisEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    /** Launches a fragment using the ordinary world's existing loot timing. */
    public static ImpactDebrisEntity launch(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Vec3 velocity,
            @Nullable ServerPlayer owner,
            ItemStack tool,
            boolean dropOnLanding) {
        return launch(level, pos, state, velocity, owner, tool, dropOnLanding, null);
    }

    /** Removes a plot block while launching its visual fragment in world space. */
    public static ImpactDebrisEntity launch(
            ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity,
            @Nullable ServerPlayer owner, ItemStack tool, boolean dropOnLanding,
            @Nullable SableCompatibility.Frame frame) {
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
        if (frame == null) {
            debris.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            debris.setStartPos(pos);
        } else {
            Vec3 worldPosition = frame.toWorld(new Vec3(
                    pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
            debris.setPos(worldPosition);
            debris.setStartPos(BlockPos.containing(worldPosition));
            debris.plotDrops = dropOnLanding
                    ? Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, tool)
                    : List.of();
        }
        debris.setDeltaMovement(velocity);
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(debris);
        return debris;
    }

    @Override
    /** Releases eligible loot only once after vanilla removes the fragment. */
    public void tick() {
        boolean wasPresent = !isRemoved();
        super.tick();
        if (!level().isClientSide && wasPresent && isRemoved() && !dropsFinished) {
            finishDrops((ServerLevel) level());
        }
    }

    /** Uses stored plot loot or resolves ordinary loot at the landing position. */
    private void finishDrops(ServerLevel level) {
        dropsFinished = true;
        if (!dropOnLanding) {
            return;
        }
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null && owner.level() != level) {
            owner = null;
        }
        (plotDrops == null
                ? Block.getDrops(getBlockState(), level, blockPosition(), null, owner, dropTool)
                : plotDrops)
                .forEach(stack -> Block.popResource(level, blockPosition(), stack));
    }

    @Override
    /** Persists delayed loot ownership and any precomputed plot loot. */
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
        if (plotDrops != null) {
            ListTag drops = new ListTag();
            plotDrops.forEach(stack -> drops.add(stack.save(level().registryAccess(), new CompoundTag())));
            tag.put("OneKickPlotDrops", drops);
        }
    }

    @Override
    /** Restores fragments without requiring the original plot to remain alive. */
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dropTool = tag.contains("OneKickDropTool")
                ? ItemStack.parseOptional(
                        level().registryAccess(), tag.getCompound("OneKickDropTool"))
                : ItemStack.EMPTY;
        ownerId = tag.hasUUID("OneKickOwner") ? tag.getUUID("OneKickOwner") : null;
        dropOnLanding = tag.getBoolean("OneKickDropOnLanding");
        dropsFinished = tag.getBoolean("OneKickDropsFinished");
        plotDrops = null;
        if (tag.contains("OneKickPlotDrops", Tag.TAG_LIST)) {
            plotDrops = new ArrayList<>();
            ListTag drops = tag.getList("OneKickPlotDrops", Tag.TAG_COMPOUND);
            for (int i = 0; i < drops.size(); i++) {
                plotDrops.add(ItemStack.parseOptional(level().registryAccess(), drops.getCompound(i)));
            }
        }
    }
}
