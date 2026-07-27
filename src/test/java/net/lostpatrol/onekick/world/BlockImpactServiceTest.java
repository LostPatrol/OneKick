package net.lostpatrol.onekick.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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
}
