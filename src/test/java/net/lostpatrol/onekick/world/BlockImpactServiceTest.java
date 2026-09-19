package net.lostpatrol.onekick.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Unit tests for kick traversal predicates and capsule clip geometry. */
class BlockImpactServiceTest {
    @Test
    void lowAllocationPerpendicularDistanceMatchesVec3Calculation() {
        Random random = new Random(0x4F6E654B69636B4CL);
        for (int i = 0; i < 100_000; i++) {
            Vec3 axis = new Vec3(
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D).normalize();
            Vec3 relative = new Vec3(
                    random.nextDouble() * 256.0D - 128.0D,
                    random.nextDouble() * 256.0D - 128.0D,
                    random.nextDouble() * 256.0D - 128.0D);
            double along = relative.dot(axis);
            double expected = relative.subtract(axis.scale(along)).length();

            assertEquals(expected, BlockImpactService.perpendicularDistance(
                    relative.x, relative.y, relative.z, axis, along));
        }
    }

    @Test
    void traversalBlockingUsesHardnessAndImpassableTag() {
        assertTrue(!BlockImpactService.blocksKickTraversal(true, -1.0F, true));
        assertTrue(!BlockImpactService.blocksKickTraversal(true, 1.5F, false));
        assertTrue(BlockImpactService.blocksKickTraversal(false, -1.0F, false));
        assertTrue(BlockImpactService.blocksKickTraversal(false, 50.0F, true));
        assertTrue(!BlockImpactService.blocksKickTraversal(false, 1.5F, false));
        assertTrue(!BlockImpactService.blocksKickTraversal(false, 50.0F, false));
    }

    @Test
    void nearFaceAlongDistanceUsesTheSupportingCubeFace() {
        Vec3 impact = new Vec3(5.0D, 3.5D, 2.5D);
        AABB box = new AABB(7.0D, 3.0D, 2.0D, 8.0D, 4.0D, 3.0D);
        assertEquals(2.0D, BlockImpactService.nearFaceAlongDistance(
                impact, new Vec3(1.0D, 0.0D, 0.0D), box), 1.0E-9D);
        assertEquals(1.0D, BlockImpactService.nearFaceAlongDistance(
                new Vec3(9.0D, 3.5D, 2.5D), new Vec3(-1.0D, 0.0D, 0.0D), box), 1.0E-9D);
    }

    @Test
    void impassableClipKeepsTheImpactLayerAndCutsTheFarHemisphere() {
        double eastAbsSum = 1.0D;
        assertTrue(!BlockImpactService.isPastImpassableClip(0.5D, eastAbsSum, 0.0D));
        assertTrue(BlockImpactService.isPastImpassableClip(1.5D, eastAbsSum, 0.0D));
        assertTrue(!BlockImpactService.isPastImpassableClip(1.5D, eastAbsSum, 2.0D));
        assertTrue(BlockImpactService.isPastImpassableClip(3.5D, eastAbsSum, 2.0D));
        assertTrue(!BlockImpactService.isPastImpassableClip(
                100.0D, eastAbsSum, Double.POSITIVE_INFINITY));
    }
}
