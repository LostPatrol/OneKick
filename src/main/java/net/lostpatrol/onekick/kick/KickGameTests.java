package net.lostpatrol.onekick.kick;

import java.util.UUID;
import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OneKick.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KickGameTests {
    private KickGameTests() {
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void controlledMobFlightChangesPositionWithoutDirectDamage(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        Vec3 start = villager.position();
        float startingHealth = villager.getHealth();
        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), 1.0D, KickEnchantments.from(ItemStack.EMPTY), ItemStack.EMPTY);
        KickManager.startKickedMotion(villager, snapshot, new Vec3(1.0D, 0.3D, 0.0D), false);

        helper.runAtTickTime(5, () -> {
            helper.assertTrue(villager.getX() > start.x + 3.0D,
                    "No-AI kicked mob did not receive server-controlled displacement");
            helper.assertTrue(villager.getY() > start.y,
                    "Kicked mob did not follow the expected rising ballistic arc");
            helper.assertTrue(villager.getHealth() == startingHealth,
                    "The kick itself changed the target's health");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_room", timeoutTicks = 30)
    public static void disintegrationTraversalMovesBeyondImpactAndAppliesPeriodicDamage(
            GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 3, 3));
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 3);
        Vec3 start = villager.position();
        float startingHealth = villager.getHealth();
        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 1, 0, 0, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), 8.0D, enchantments, ItemStack.EMPTY);
        KickManager.startKickedMotion(villager, snapshot, new Vec3(0.6D, 0.1D, 0.0D), false);

        helper.runAtTickTime(22, () -> {
            helper.assertTrue(villager.isAlive(), "Traversal damage killed the validation target");
            helper.assertTrue(villager.getX() > start.x + 9.0D,
                    "Disintegration traversal did not carry the target beyond the impact wall: start="
                            + start + ", current=" + villager.position());
            helper.assertTrue(villager.getHealth() < startingHealth - 3.0F,
                    "Traversal did not add periodic damage after impact: start="
                            + startingHealth + ", current=" + villager.getHealth());
            helper.assertTrue(!villager.noPhysics && !villager.isNoAi(),
                    "Traversal did not restore collision and AI state");
            helper.succeed();
        });
    }
}
