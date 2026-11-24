/** Optional server-side Sable coordinate frames for kick block interactions. */
package net.lostpatrol.onekick.world;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;

/** Keeps Sable linkage behind its loader check and exposes only vanilla types. */
public final class SableCompatibility {
    /** Prevents construction of this static compatibility bridge. */
    private SableCompatibility() {
    }

    /** Returns whether the optional server integration is available. */
    public static boolean active() {
        return ModList.get().isLoaded("sable");
    }

    /** Resolves an actual plot coordinate, rejecting wrapped plot-grid aliases. */
    public static @Nullable Frame at(ServerLevel level, BlockPos plotBlock) {
        return active() ? Api.at(level, plotBlock) : null;
    }

    /** Finds live frames whose world bounds intersect the requested volume. */
    public static List<Frame> intersecting(ServerLevel level, AABB worldBounds) {
        return active() ? Api.intersecting(level, worldBounds) : List.of();
    }

    /** Conservatively encloses all eight transformed world-volume corners. */
    public static AABB boundsInPlot(Frame frame, AABB worldBounds) {
        Vec3 first = frame.toPlot(new Vec3(worldBounds.minX, worldBounds.minY, worldBounds.minZ));
        AABB result = new AABB(first, first);
        for (int corner = 1; corner < 8; corner++) {
            Vec3 point = frame.toPlot(new Vec3(
                    (corner & 1) == 0 ? worldBounds.minX : worldBounds.maxX,
                    (corner & 2) == 0 ? worldBounds.minY : worldBounds.maxY,
                    (corner & 4) == 0 ? worldBounds.minZ : worldBounds.maxZ));
            result = result.minmax(new AABB(point, point));
        }
        return result;
    }

    /** A pose snapshot remains usable for effects after the body is removed. */
    public interface Frame {
        /** Returns the persistent sublevel identity. */
        UUID id();

        /** Projects a stored plot position into world space. */
        Vec3 toWorld(Vec3 position);

        /** Projects a world position into stored plot coordinates. */
        Vec3 toPlot(Vec3 position);

        /** Transforms a displacement without applying translation. */
        Vec3 directionToWorld(Vec3 direction);

        /** Inversely transforms a displacement without applying translation. */
        Vec3 directionToPlot(Vec3 direction);

        /** Returns the snapshot block volume, including the final block's extent. */
        AABB plotBounds();

        /** Confirms the coordinate still belongs to this exact live sublevel. */
        boolean owns(ServerLevel level, BlockPos pos);

        /** Returns whether the original sublevel is still registered and live. */
        boolean isLive();
    }

    /** Loaded only after the mod check; all direct Sable references stay here. */
    private static final class Api {
        /** Prevents construction of the lazy API holder. */
        private Api() {
        }

        /** Looks up a live sublevel while checking its unwrapped plot extent. */
        private static @Nullable Frame at(ServerLevel level, BlockPos pos) {
            var sublevel = Sable.HELPER.getContaining(level, pos);
            if (!(sublevel instanceof ServerSubLevel serverSublevel)
                    || sublevel.isRemoved() || !sublevel.getPlot().contains(pos.getX(), pos.getZ())) {
                return null;
            }
            return snapshot(level, serverSublevel);
        }

        /** Uses Sable's server broad phase and excludes empty or removed plots. */
        private static List<Frame> intersecting(ServerLevel level, AABB bounds) {
            List<Frame> frames = new ArrayList<>();
            var container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return frames;
            }
            BoundingBox3d query = new BoundingBox3d(bounds);
            for (ServerSubLevel sublevel : container.getAllSubLevels()) {
                var global = sublevel.boundingBox();
                // Assembly populates plot blocks before the first tick publishes their world bounds.
                boolean firstTickPending = global.minX() == global.maxX()
                        && global.minY() == global.maxY() && global.minZ() == global.maxZ();
                if (sublevel.isRemoved() || !firstTickPending && !global.intersects(query)) {
                    continue;
                }
                Frame frame = snapshot(level, sublevel);
                if (frame != null && frame.isLive() && (!firstTickPending
                        || boundsInPlot(frame, bounds).intersects(frame.plotBounds()))) {
                    frames.add(frame);
                }
            }
            return frames;
        }

        /** Copies pose and inclusive bounds before destruction can remove the body. */
        private static @Nullable Frame snapshot(ServerLevel level, ServerSubLevel sublevel) {
            var bounds = sublevel.getPlot().getBoundingBox();
            if (bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) {
                return null;
            }
            AABB volume = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    (double) bounds.maxX() + 1, (double) bounds.maxY() + 1, (double) bounds.maxZ() + 1);
            return new SableFrame(level, sublevel, volume);
        }

        /** Sable-backed implementation hidden from absent-mod class loading. */
        private static final class SableFrame implements Frame {
            /** Original level and object prevent identity reuse after plot reassignment. */
            private final ServerLevel level;
            private final ServerSubLevel sublevel;
            /** Deep copy avoids reading a removed body's mutable current pose. */
            private final Pose3d pose;
            /** Full block volume and an interior coordinate used for registry checks. */
            private final AABB bounds;
            private final BlockPos anchor;
            /** Persistent identity is stable even after the plot is removed. */
            private final UUID id;

            /** Captures immutable interaction geometry without retaining a physics handle. */
            private SableFrame(ServerLevel level, ServerSubLevel sublevel, AABB bounds) {
                this.level = level;
                this.sublevel = sublevel;
                this.pose = new Pose3d(sublevel.logicalPose());
                this.bounds = bounds;
                this.anchor = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
                this.id = sublevel.getUniqueId();
            }

            /** Returns the captured identity. */
            @Override
            public UUID id() {
                return id;
            }

            /** Includes the pose rotation point, rotation, scale, and translation. */
            @Override
            public Vec3 toWorld(Vec3 position) {
                return pose.transformPosition(position);
            }

            /** Applies the full inverse captured pose. */
            @Override
            public Vec3 toPlot(Vec3 position) {
                return pose.transformPositionInverse(position);
            }

            /** Applies scale and all three rotation axes to a displacement. */
            @Override
            public Vec3 directionToWorld(Vec3 direction) {
                var vector = new Vector3d(direction.x, direction.y, direction.z).mul(pose.scale());
                pose.orientation().transform(vector);
                return new Vec3(vector.x, vector.y, vector.z);
            }

            /** Reverses rotation and scale without subtracting large plot coordinates. */
            @Override
            public Vec3 directionToPlot(Vec3 direction) {
                var vector = new Vector3d(direction.x, direction.y, direction.z);
                pose.orientation().transformInverse(vector);
                vector.mul(1.0 / pose.scale().x(), 1.0 / pose.scale().y(), 1.0 / pose.scale().z());
                return new Vec3(vector.x, vector.y, vector.z);
            }

            /** Returns the captured block-volume bounds. */
            @Override
            public AABB plotBounds() {
                return bounds;
            }

            /** Checks exact live object ownership as well as non-aliased plot extent. */
            @Override
            public boolean owns(ServerLevel candidateLevel, BlockPos pos) {
                return candidateLevel == level && !sublevel.isRemoved()
                        && sublevel.getPlot().contains(pos.getX(), pos.getZ())
                        && Sable.HELPER.getContaining(level, pos) == sublevel;
            }

            /** Registration checks do not access any native physics handle. */
            @Override
            public boolean isLive() {
                return owns(level, anchor);
            }
        }
    }
}
