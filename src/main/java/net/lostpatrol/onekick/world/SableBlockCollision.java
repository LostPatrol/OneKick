/** Sweeps a world-space entity box against collision shapes in a loaded Sable plot. */
package net.lostpatrol.onekick.world;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.lostpatrol.onekick.world.SableCompatibility.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class SableBlockCollision {
    /** Separates touching faces and stabilizes comparisons. */
    private static final double EPSILON = 1.0E-6D;
    /** Axes of the world-space moving box. */
    private static final Vec3[] WORLD_AXES = {
        new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D)
    };

    private SableBlockCollision() {
    }

    /** Finds the earliest loaded plot block along the requested world-space movement. */
    @Nullable
    public static Contact first(ServerLevel level, LivingEntity entity, AABB start, Vec3 movement,
            boolean impassableOnly, double minimumVerticalSpeed) {
        if (!SableCompatibility.active() || movement.lengthSqr() < EPSILON * EPSILON) {
            return null;
        }
        AABB swept = start.expandTowards(movement).inflate(EPSILON);
        Contact closest = null;
        CollisionContext context = CollisionContext.of(entity);
        for (Frame frame : SableCompatibility.intersecting(level, swept)) {
            AABB localSwept = SableCompatibility.boundsInPlot(frame, swept);
            if (!localSwept.intersects(frame.plotBounds())) {
                continue;
            }
            AABB scan = localSwept.intersect(frame.plotBounds());
            List<SweepAxis> axes = sweepAxes(frame, start, movement);
            BlockPos minimum = BlockPos.containing(scan.minX, scan.minY, scan.minZ);
            BlockPos maximum = BlockPos.containing(
                    Math.nextDown(scan.maxX), Math.nextDown(scan.maxY), Math.nextDown(scan.maxZ));
            for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) {
                if (!level.hasChunkAt(pos) || !frame.owns(level, pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || impassableOnly && !BlockImpactService.blocksKickTraversal(level, pos, state)) {
                    continue;
                }
                var shape = state.getCollisionShape(level, pos, context);
                if (shape.isEmpty()) {
                    continue;
                }
                for (AABB localBox : impassableOnly ? List.of(new AABB(BlockPos.ZERO)) : shape.toAabbs()) {
                    AABB box = localBox.move(pos);
                    SweepHit hit = sweep(frame, start.getCenter(), box, axes, impassableOnly);
                    if (hit == null || closest != null && hit.fraction() >= closest.fraction()
                            || Math.abs(movement.y) <= minimumVerticalSpeed
                            && Math.abs(hit.normal().y) >= 1.0D - EPSILON) {
                        continue;
                    }
                    Vec3 localCenter = frame.toPlot(start.getCenter().add(movement.scale(hit.fraction())));
                    Vec3 point = new Vec3(
                            Mth.clamp(localCenter.x, box.minX, box.maxX),
                            Mth.clamp(localCenter.y, box.minY, box.maxY),
                            Mth.clamp(localCenter.z, box.minZ, box.maxZ));
                    closest = new Contact(frame, pos.immutable(), frame.toWorld(point), hit.fraction(), hit.normal());
                }
            }
        }
        return closest;
    }

    /** Finds an impassable plot block using the same swept box as ordinary plot collisions. */
    @Nullable
    public static Contact first(ServerLevel level, LivingEntity entity, AABB start, Vec3 movement,
            boolean impassableOnly) {
        return first(level, entity, start, movement, impassableOnly, -1.0D);
    }

    /** Prepares the separating axes for a world AABB and a rotated plot box. */
    private static List<SweepAxis> sweepAxes(Frame frame, AABB start, Vec3 movement) {
        Vec3[] plotAxes = new Vec3[WORLD_AXES.length];
        for (int index = 0; index < plotAxes.length; index++) {
            plotAxes[index] = frame.directionToWorld(WORLD_AXES[index]);
        }
        List<SweepAxis> axes = new ArrayList<>(15);
        for (Vec3 axis : WORLD_AXES) {
            addAxis(axes, axis, plotAxes, start, movement);
        }
        for (Vec3 axis : plotAxes) {
            addAxis(axes, axis, plotAxes, start, movement);
        }
        for (Vec3 worldAxis : WORLD_AXES) {
            for (Vec3 plotAxis : plotAxes) {
                addAxis(axes, worldAxis.cross(plotAxis), plotAxes, start, movement);
            }
        }
        return axes;
    }

    /** Projects both boxes and the movement onto one separating axis. */
    private static void addAxis(List<SweepAxis> axes, Vec3 axis,
            Vec3[] plotAxes, AABB start, Vec3 movement) {
        if (axis.lengthSqr() < EPSILON * EPSILON) {
            return;
        }
        Vec3 unit = axis.normalize();
        double radius = (Math.abs(unit.x) * start.getXsize() + Math.abs(unit.y) * start.getYsize()
                + Math.abs(unit.z) * start.getZsize()) * 0.5D;
        Vec3 localProjection = new Vec3(Math.abs(unit.dot(plotAxes[0])),
                Math.abs(unit.dot(plotAxes[1])), Math.abs(unit.dot(plotAxes[2])));
        axes.add(new SweepAxis(unit, localProjection, radius, unit.dot(movement)));
    }

    /** Intersects the time intervals during which the boxes overlap on each separating axis. */
    @Nullable
    private static SweepHit sweep(Frame frame, Vec3 start, AABB box, List<SweepAxis> axes,
            boolean impassableOnly) {
        Vec3 difference = start.subtract(frame.toWorld(box.getCenter()));
        Vec3 halfSize = new Vec3(box.getXsize() * 0.5D, box.getYsize() * 0.5D, box.getZsize() * 0.5D);
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        Vec3 normal = Vec3.ZERO;
        for (SweepAxis axis : axes) {
            double distance = difference.dot(axis.direction());
            double radius = axis.entityRadius() + halfSize.dot(axis.plotProjection());
            double speed = axis.speed();
            if (Math.abs(speed) < EPSILON) {
                if (Math.abs(distance) >= radius - EPSILON) {
                    return null;
                }
                continue;
            }
            double a = (-radius - distance) / speed;
            double b = (radius - distance) / speed;
            double near = Math.min(a, b);
            if (near > entry) {
                entry = near;
                normal = axis.direction().scale(speed > 0.0D ? -1.0D : 1.0D);
            }
            exit = Math.min(exit, Math.max(a, b));
            if (entry > exit + EPSILON) {
                return null;
            }
        }
        if (exit <= EPSILON || entry > 1.0D || !impassableOnly && entry < -EPSILON) {
            return null;
        }
        return new SweepHit(Math.max(0.0D, entry), normal);
    }

    /** Identifies a plot block and its contact in world space. */
    public record Contact(Frame frame, BlockPos block, Vec3 worldPoint, double fraction, Vec3 normal) {
    }

    /** Per-axis projections shared by all blocks of a plot. */
    private record SweepAxis(Vec3 direction, Vec3 plotProjection, double entityRadius, double speed) {
    }

    /** The earliest overlap and its outward-facing separating axis. */
    private record SweepHit(double fraction, Vec3 normal) {
    }
}
