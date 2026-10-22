/** Exercises kick motion, world effects, and data-driven progression in a real level. */
package net.lostpatrol.onekick.kick;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.advancement.KickAdvancementTrigger;
import net.lostpatrol.onekick.advancement.ModCriteriaTriggers;
import net.lostpatrol.onekick.config.OneKickConfig;
import net.lostpatrol.onekick.registry.ModEnchantments;
import net.lostpatrol.onekick.registry.ModTags;
import net.lostpatrol.onekick.world.BlockImpactService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OneKick.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KickGameTests {
    private KickGameTests() {
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void dataDrivenEnchantmentsPreserveBootRulesAndCosts(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        Map<ResourceKey<Enchantment>, Integer> expectedMaximumLevels = Map.of(
                ModEnchantments.REACTION, 1,
                ModEnchantments.AERODYNAMICS, 3,
                ModEnchantments.DISINTEGRATION, 1,
                ModEnchantments.UNSTABLE_COLLISION, 3,
                ModEnchantments.KINETIC_OVERLOAD, 3,
                ModEnchantments.CHARGE, 5,
                ModEnchantments.ANGULAR_MOMENTUM, 1,
                ModEnchantments.OVERCHARGE, 2,
                ModEnchantments.SINGULARITY, 1,
                ModEnchantments.AFTERBURNER, 1);
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);

        for (var entry : expectedMaximumLevels.entrySet()) {
            Enchantment enchantment = registry.getOrThrow(entry.getKey()).value();
            helper.assertTrue(enchantment.isSupportedItem(boots),
                    entry.getKey().location() + " does not support boots");
            helper.assertTrue(enchantment.getMaxLevel() == entry.getValue(),
                    entry.getKey().location() + " has the wrong maximum level");
            for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
                boolean newEnchantment = entry.getKey().equals(ModEnchantments.SINGULARITY)
                        || entry.getKey().equals(ModEnchantments.AFTERBURNER);
                int window = enchantment.getMaxCost(level) - enchantment.getMinCost(level);
                helper.assertTrue(newEnchantment ? window > 0 : window == 15,
                        entry.getKey().location() + " has an invalid enchanting cost window");
            }
        }

        var singularity = registry.getOrThrow(ModEnchantments.SINGULARITY);
        var afterburner = registry.getOrThrow(ModEnchantments.AFTERBURNER);
        helper.assertTrue(singularity.is(EnchantmentTags.TREASURE)
                        && !singularity.is(EnchantmentTags.IN_ENCHANTING_TABLE),
                "Singularity must be treasure-only");
        helper.assertTrue(afterburner.value().getWeight() == 2,
                "Afterburner must have rare enchantment weight");

        var silkTouch = registry.getOrThrow(Enchantments.SILK_TOUCH);
        helper.assertTrue(silkTouch.value().isSupportedItem(boots),
                "Silk Touch no longer supports boots");
        boots.enchant(registry.getOrThrow(ModEnchantments.CHARGE), 4);
        boots.enchant(silkTouch, 1);
        boots.enchant(singularity, 1);
        boots.enchant(afterburner, 1);
        KickEnchantments enchantments = KickEnchantments.from(boots);
        helper.assertTrue(enchantments.charge() == 4,
                "Kick enchantment snapshot did not read data-driven Charge");
        helper.assertTrue(enchantments.silkTouch(),
                "Kick enchantment snapshot did not read boot Silk Touch");
        helper.assertTrue(enchantments.singularity() == 1 && enchantments.afterburner() == 1,
                "Kick snapshot did not read the new boot enchantments");
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void unchargedHorizontalKickMovesTargetWithoutDirectDamage(
            GameTestHelper helper) {
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        Vec3 start = villager.position();
        float startingHealth = villager.getHealth();
        double launchSpeed = KickMath.launchSpeed(KickMath.BASE_SPEED, villager);
        Vec3 velocity = KickMath.launchDirection(new Vec3(1.0D, 0.0D, 0.0D))
                .scale(launchSpeed);
        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), 1.0D, KickEnchantments.from(ItemStack.EMPTY), ItemStack.EMPTY);
        KickManager.startKickedMotion(villager, snapshot, velocity, false);

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(villager.getX() > start.x + 6.0D,
                    "Uncharged horizontal kick did not displace the target");
            helper.assertTrue(villager.getHealth() == startingHealth,
                    "Uncharged horizontal kick directly damaged the target");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void disintegrationKickDoesNotTraverseThroughBedrock(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        for (int y = 2; y <= 5; y++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(7, y, z, Blocks.BEDROCK);
                helper.setBlock(8, y, z, Blocks.STONE);
            }
        }
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        KickEnchantments enchantments = new KickEnchantments(0, 0, 1, 0, 0, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, ItemStack.EMPTY);
        Vec3 velocity = KickMath.launchDirection(new Vec3(1.0D, 0.0D, 0.0D)).scale(8.0D);
        KickManager.startKickedMotion(villager, snapshot, velocity, false);

        helper.runAtTickTime(15, () -> {
            unregisterSnapshotAttacker(helper, attacker);
            helper.assertTrue(villager.getX() < helper.absolutePos(new BlockPos(7, 3, 2)).getX(),
                    "Disintegration traversal phased through bedrock");
            helper.assertTrue(villager.getX() > helper.absolutePos(new BlockPos(2, 3, 2)).getX(),
                    "Disintegration kick into bedrock did not move the target");
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 2)).is(Blocks.STONE),
                    "Disintegration destroyed stone behind a bedrock first impact");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void disintegrationDoesNotDestroyBlocksBehindLaterBedrock(
            GameTestHelper helper) {
        // First impact is breakable stone; later bedrock must stop the capsule.
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        for (int y = 2; y <= 5; y++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(5, y, z, Blocks.STONE);
                helper.setBlock(6, y, z, Blocks.STONE);
                helper.setBlock(7, y, z, Blocks.BEDROCK);
                helper.setBlock(8, y, z, Blocks.STONE);
            }
        }
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 4, 3, 2);
        impactedEntity.setInvulnerable(true);
        impactedEntity.setPos(helper.absoluteVec(new Vec3(4.0D, 3.0D, 2.5D)));
        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 1, 0, 3, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 12.0D, enchantments, ItemStack.EMPTY);
        Vec3 impact = helper.absoluteVec(new Vec3(5.0D, 3.5D, 2.5D));
        BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                new Vec3(16.0D, 0.0D, 0.0D), 16.0D, 5.0F, snapshot);
        unregisterSnapshotAttacker(helper, attacker);
        helper.assertTrue(helper.getBlockState(new BlockPos(5, 3, 2)).isAir(),
                "Disintegration did not destroy the first breakable wall");
        helper.assertTrue(helper.getBlockState(new BlockPos(6, 3, 2)).isAir(),
                "Disintegration stopped before the later bedrock wall");
        helper.assertTrue(helper.getBlockState(new BlockPos(7, 3, 2)).is(Blocks.BEDROCK),
                "Disintegration destroyed bedrock");
        helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 2)).is(Blocks.STONE),
                "Disintegration destroyed stone behind a later bedrock wall");
        helper.succeed();
    }

    /** A fully charged combination erases hard, tagged, and liquid blocks past bedrock. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void singularityErasesProtectedBlocksAndAwardsOverride(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        try {
            Villager target = helper.spawn(EntityType.VILLAGER, 5, 3, 2);
            target.setInvulnerable(true);
            helper.setBlock(6, 3, 2, Blocks.BEDROCK);
            helper.setBlock(7, 3, 2, Blocks.REINFORCED_DEEPSLATE);
            helper.setBlock(8, 3, 2, Blocks.STONE);
            helper.setBlock(6, 3, 1, Blocks.WATER);
            helper.assertTrue(helper.getBlockState(new BlockPos(7, 3, 2))
                            .is(ModTags.DISINTEGRATION_IMMUNE),
                    "Regression setup needs a tagged immune block");
            AdvancementHolder override = loadedAdvancement(helper, "override");
            helper.assertTrue(!attacker.getAdvancements().getOrStartProgress(override).isDone(),
                    "Override was already complete before removing bedrock");
            KickSnapshot snapshot = singularitySnapshot(attacker, 5, 2, 1, 1,
                    KickMath.maxCharge(5, 2));
            Vec3 impact = helper.absoluteVec(new Vec3(6.0D, 3.5D, 2.5D));
            BlockImpactService.handleImpact(helper.getLevel(), target, impact,
                    new Vec3(1.2D, 0.0D, 0.0D), 1.2D, 1.0F, snapshot);

            for (BlockPos position : List.of(new BlockPos(6, 3, 2), new BlockPos(7, 3, 2),
                    new BlockPos(8, 3, 2), new BlockPos(6, 3, 1))) {
                helper.assertTrue(helper.getBlockState(position).isAir(),
                        "Singularity did not erase block at " + position);
            }
            helper.assertTrue(attacker.getAdvancements().getOrStartProgress(override).isDone(),
                    "Actual bedrock removal did not award Override");
            helper.succeed();
        } finally {
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    /** Missing requirements and partial charge retain the original destruction limits. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void incompleteSingularityCannotEraseBedrockOrAwardOverride(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        try {
            Villager target = helper.spawn(EntityType.VILLAGER, 5, 3, 2);
            target.setInvulnerable(true);
            float maximum = KickMath.maxCharge(5, 2);
            List<KickSnapshot> inactive = List.of(
                    singularitySnapshot(attacker, 5, 2, 1, 1, maximum - 0.01F),
                    singularitySnapshot(attacker, 4, 2, 1, 1, maximum),
                    singularitySnapshot(attacker, 5, 1, 1, 1, maximum),
                    singularitySnapshot(attacker, 5, 2, 0, 1, maximum),
                    singularitySnapshot(attacker, 5, 2, 1, 0, maximum));
            for (KickSnapshot snapshot : inactive) {
                helper.setBlock(6, 3, 2, Blocks.BEDROCK);
                helper.setBlock(7, 3, 2, Blocks.REINFORCED_DEEPSLATE);
                helper.setBlock(8, 3, 2, Blocks.STONE);
                BlockImpactService.handleImpact(helper.getLevel(), target,
                        helper.absoluteVec(new Vec3(6.0D, 3.5D, 2.5D)),
                        new Vec3(1.2D, 0.0D, 0.0D), 1.2D, 1.0F, snapshot);
                helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(6, 3, 2));
                helper.assertBlockPresent(Blocks.REINFORCED_DEEPSLATE, new BlockPos(7, 3, 2));
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 3, 2));
            }
            helper.assertTrue(!attacker.getAdvancements()
                            .getOrStartProgress(loadedAdvancement(helper, "override")).isDone(),
                    "Override was awarded without removing bedrock");
            helper.succeed();
        } finally {
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    /** The live first-contact gate must allow Singularity into an unbreakable wall. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void singularityFirstBedrockImpactStartsTraversal(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager target = helper.spawn(EntityType.VILLAGER, 5, 3, 2);
        target.setPos(helper.absoluteVec(new Vec3(5.0D, 3.0D, 2.5D)));
        target.setInvulnerable(true);
        try {
            for (int y = 3; y <= 4; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(6, y, z, Blocks.BEDROCK);
                }
            }
            KickSnapshot snapshot = singularitySnapshot(attacker, 5, 2, 1, 1,
                    KickMath.maxCharge(5, 2));
            KickManager.startKickedMotion(target, snapshot, new Vec3(1.2D, 0.0D, 0.0D), false);
            KickManager.tickLiving(target);
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 3, 2)).isAir(),
                    "The first-impact gate prevented Singularity from removing bedrock");
            helper.assertTrue(target.noPhysics, "Singularity did not start disintegration traversal");
            KickManager.tickLiving(target);
            helper.assertTrue(target.getX() > helper.absolutePos(new BlockPos(6, 3, 2)).getX(),
                    "Singularity traversal did not move through its erased first wall");
            helper.succeed();
        } finally {
            KickManager.removeLiving(target);
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    /** Flight integration ignites both the target and a low trail, while high trails stay clear. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void afterburnerBurnsTargetAndHonorsGroundReach(GameTestHelper helper) {
        clearFireTestRoom(helper);
        fillFireTestFloor(helper, 1);
        Villager target = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        target.setPos(helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D)));
        KickSnapshot snapshot = afterburnerSnapshot(UUID.randomUUID(), 0);
        try {
            Vec3 highFrom = helper.absoluteVec(new Vec3(2.5D, 7.0D, 2.5D));
            AfterburnerService.trail(helper.getLevel(), target, highFrom,
                    highFrom.add(5.0D, 0.0D, 0.0D), snapshot);
            helper.assertTrue(snapshot.progress().ignitedBlockCount() == 0,
                    "High flight ignited ground beyond Afterburner's vertical reach");
            KickManager.startKickedMotion(target, snapshot, new Vec3(1.2D, 0.0D, 0.0D), false);
            helper.assertTrue(target.isOnFire(), "Afterburner did not ignite the kicked target");
            KickManager.tickLiving(target);
            helper.assertTrue(snapshot.progress().ignitedBlockCount() > 0,
                    "Low controlled flight did not ignite its ground projection");
            helper.assertBlockPresent(Blocks.FIRE, new BlockPos(2, 2, 2));
            helper.succeed();
        } finally {
            KickManager.removeLiving(target);
        }
    }

    /** Repeated samples cannot replace the requirement for more than 100 distinct ignitions. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void afterburnerCountsDistinctFireAndAwardsTrailblazer(GameTestHelper helper) {
        clearFireTestRoom(helper);
        fillFireTestFloor(helper, 1);
        fillFireTestFloor(helper, 4);
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        try {
            Villager target = helper.spawn(EntityType.VILLAGER, 1, 2, 1);
            KickSnapshot snapshot = afterburnerSnapshot(attacker.getUUID(), 0);
            AdvancementHolder trailblazer = loadedAdvancement(helper, "trailblazer");
            igniteFireTestLayer(helper, target, snapshot, 2);
            int lowerCount = snapshot.progress().ignitedBlockCount();
            helper.assertTrue(lowerCount > 0 && lowerCount <= 100,
                    "The first fire layer did not stay below Trailblazer's threshold");
            helper.assertTrue(!attacker.getAdvancements().getOrStartProgress(trailblazer).isDone(),
                    "Trailblazer was awarded before more than 100 distinct fires");
            igniteFireTestLayer(helper, target, snapshot, 2);
            helper.assertTrue(snapshot.progress().ignitedBlockCount() == lowerCount,
                    "Repeated trail samples counted existing fire twice");
            igniteFireTestLayer(helper, target, snapshot, 5);
            helper.assertTrue(snapshot.progress().ignitedBlockCount() > 100,
                    "Afterburner did not record over 100 distinct fire placements");
            helper.assertTrue(attacker.getAdvancements().getOrStartProgress(trailblazer).isDone(),
                    "Actual fire placements did not award Trailblazer");
            helper.succeed();
        } finally {
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    /** The unstable-collision path ignites surviving surfaces after its explosion. */
    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void afterburnerUnstableExplosionsIgniteSurvivingGround(GameTestHelper helper) {
        clearFireTestRoom(helper);
        fillFireTestFloor(helper, 2);
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        try {
            Villager target = helper.spawn(EntityType.VILLAGER, 8, 3, 2);
            target.setInvulnerable(true);
            KickSnapshot snapshot = afterburnerSnapshot(attacker.getUUID(), 3);
            Vec3 impact = helper.absoluteVec(new Vec3(8.0D, 3.5D, 2.5D));
            // Several independent explosions make the random ignition check robust.
            for (int attempt = 0; attempt < 8 && snapshot.progress().ignitedBlockCount() == 0;
                    attempt++) {
                BlockImpactService.handleImpact(helper.getLevel(), target, impact,
                        new Vec3(1.0D, 0.0D, 0.0D), 1.0D, 0.0F, snapshot);
            }
            helper.assertTrue(snapshot.progress().ignitedBlockCount() > 0,
                    "Afterburner + unstable collision did not ignite explosion surfaces");
            helper.succeed();
        } finally {
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void customKickCriterionAwardsLoadedAdvancement(GameTestHelper helper) {
        ServerPlayer player = registerSnapshotAttacker(helper);
        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, "root"));
        try {
            helper.assertTrue(advancement != null, "OneKick root advancement was not loaded");
            helper.assertTrue(
                    advancement.value().display().orElseThrow().getIcon()
                            .is(Items.LEATHER_BOOTS),
                    "OneKick root advancement icon did not decode");
            ModCriteriaTriggers.trigger(player, KickAdvancementTrigger.Event.KICK);
            helper.assertTrue(
                    player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "OneKick event did not award the root advancement");
            helper.succeed();
        } finally {
            unregisterSnapshotAttacker(helper, player);
        }
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void horizontalSweepIgnoresFloorTangency(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(x, 2, 2, Blocks.STONE);
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        KickManager.FirstBlockImpact impact = KickManager.findFirstBlockImpact(
                helper.getLevel(), villager, new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertTrue(impact == null,
                "Horizontal sweep treated the supporting floor as a wall impact");
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void lowBallisticSweepIgnoresFlatFloorAtEverySubBlockOffset(
            GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        double launchSpeed = KickMath.launchSpeed(KickMath.BASE_SPEED, villager);
        Vec3 movement = KickMath.launchDirection(new Vec3(1.0D, 0.0D, 0.0D))
                .scale(launchSpeed);
        for (int tick = 0; tick < 5; tick++) {
            movement = KickMath.nextBallisticVelocity(movement, false);
        }

        for (int step = 0; step < 128; step++) {
            double x = 2.0D + step / 128.0D;
            villager.setPos(helper.absoluteVec(new Vec3(x, 3.0D, 2.5D)));
            KickManager.FirstBlockImpact impact = KickManager.findFirstBlockImpact(
                    helper.getLevel(), villager, movement);
            helper.assertTrue(impact == null,
                    "Low ballistic sweep treated flat floor as a wall at x="
                            + x + ", movement=" + movement + ", impact=" + impact);
        }
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void nearHorizontalUnchargedKickDoesNotHitSupportingFloor(
            GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.DIRT_PATH);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        villager.setPos(helper.absoluteVec(new Vec3(2.0D, 2.9375D, 2.5D)));
        Vec3 start = villager.position();
        float startingHealth = villager.getHealth();
        double launchSpeed = KickMath.launchSpeed(KickMath.BASE_SPEED, villager);
        Vec3 velocity = KickMath.launchDirection(new Vec3(1.0D, -0.15D, 0.0D))
                .scale(launchSpeed);
        helper.assertTrue(velocity.y < 0.0D
                        && Math.abs(velocity.y) < 0.35D,
                "Regression setup did not produce a shallow downward launch");

        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), KickMath.BASE_SPEED,
                KickEnchantments.from(ItemStack.EMPTY), ItemStack.EMPTY);
        KickManager.startKickedMotion(villager, snapshot, velocity, false);
        KickManager.tickLiving(villager);

        helper.assertTrue(villager.getX() > start.x + 0.5D,
                "Shallow horizontal kick stopped against its supporting floor");
        helper.assertTrue(villager.getHealth() == startingHealth,
                "Shallow horizontal kick treated its supporting floor as a damaging wall");
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void highSpeedDownwardSweepStillDetectsFloor(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 4, 2);
        KickManager.FirstBlockImpact impact = KickManager.findFirstBlockImpact(
                helper.getLevel(), villager, new Vec3(0.2D, -2.0D, 0.0D));

        helper.assertTrue(impact != null,
                "High-speed downward sweep did not detect the floor");
        helper.assertTrue(impact.face().getAxis() == Direction.Axis.Y,
                "High-speed downward sweep did not resolve to the floor surface: " + impact);
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void firstDamagingBlockContactStopsVanillaWallSlide(GameTestHelper helper) {
        for (int y = 3; y <= 5; y++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(5, y, z, Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 4, 3, 3);
        Vec3 movement = new Vec3(1.8D, 0.0D, 0.8D);
        Vec3 start = villager.position();
        float startingHealth = villager.getHealth();
        KickManager.FirstBlockImpact impact = KickManager.findFirstBlockImpact(
                helper.getLevel(), villager, movement);
        helper.assertTrue(impact != null, "Continuous sweep did not find the wall");
        BlockPos insideWall = BlockPos.containing(
                impact.location().add(movement.normalize().scale(1.0E-4D)));
        helper.assertTrue(insideWall.equals(helper.absolutePos(new BlockPos(5, 3, 3))),
                "Sweep selected a later wall block instead of the first contact: "
                        + helper.relativePos(insideWall));

        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), 2.0D, KickEnchantments.from(ItemStack.EMPTY), ItemStack.EMPTY);
        KickManager.startKickedMotion(villager, snapshot, movement, false);
        KickManager.tickLiving(villager);

        helper.assertTrue(villager.getHealth() < startingHealth,
                "First high-energy block contact did not apply kinetic damage");
        helper.assertTrue(villager.getZ() < start.z + 0.2D,
                "Entity slid along the wall before collision: start="
                        + start + ", current=" + villager.position());
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void lethalKickStopsAtFirstWallContact(GameTestHelper helper) {
        for (int y = 3; y <= 5; y++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(5, y, z, Blocks.STONE);
            }
        }
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        Vec3 start = villager.position();
        KickSnapshot snapshot = new KickSnapshot(
                UUID.randomUUID(), 30.0D,
                KickEnchantments.from(ItemStack.EMPTY), ItemStack.EMPTY);
        KickManager.startKickedMotion(
                villager, snapshot, new Vec3(30.0D, 0.0D, 0.0D), false);
        KickManager.tickLiving(villager);

        helper.assertTrue(!villager.isAlive(),
                "Lethal first-contact kinetic damage did not kill the target");
        helper.assertTrue(villager.getX() > start.x + 1.0D,
                "Lethal kick killed the target at its launch position");
        helper.assertTrue(villager.getX() < helper.absolutePos(new BlockPos(5, 3, 2)).getX(),
                "Lethal kick moved the target through the first wall");
        helper.succeed();
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

    @GameTest(template = "flight_room", batch = "unstableDrops", timeoutTicks = 20)
    public static void unstableCollisionDoesNotDestroyItemEntities(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 6, 3, 2);
        impactedEntity.setInvulnerable(true);
        ItemEntity existingDrop = helper.spawnItem(Items.DIAMOND, 5.5F, 7.0F, 2.5F);

        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 0, 3, 0, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, ItemStack.EMPTY);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5D, 3.0D, 2.5D));
        BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                new Vec3(6.0D, 0.0D, 0.0D), 6.0D, 5.0F, snapshot);

        helper.runAtTickTime(2, () -> {
            unregisterSnapshotAttacker(helper, attacker);
            helper.assertTrue(existingDrop.isAlive(),
                    "Unstable collision destroyed an existing item entity");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void unstableCollisionUsesUniformDamageWithoutKnockback(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 6, 3, 2);
        impactedEntity.setInvulnerable(true);
        Villager nearBystander = helper.spawn(EntityType.VILLAGER, 7, 3, 2);
        Villager farBystander = helper.spawn(EntityType.VILLAGER, 13, 3, 2);
        float nearStartingHealth = nearBystander.getHealth();
        float farStartingHealth = farBystander.getHealth();
        float impactDamage = 5.0F;

        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 0, 3, 0, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, ItemStack.EMPTY);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5D, 3.0D, 2.5D));
        BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                new Vec3(6.0D, 0.0D, 0.0D), 6.0D, impactDamage, snapshot);

        unregisterSnapshotAttacker(helper, attacker);
        helper.assertTrue(Math.abs(
                        nearBystander.getHealth() - (nearStartingHealth - impactDamage)) < 1.0E-4F,
                "Near unstable-collision target did not receive the kinetic impact damage");
        helper.assertTrue(Math.abs(
                        farBystander.getHealth() - (farStartingHealth - impactDamage)) < 1.0E-4F,
                "Far unstable-collision target received distance-scaled damage");
        helper.assertTrue(nearBystander.getDeltaMovement().lengthSqr() < 1.0E-8D
                        && farBystander.getDeltaMovement().lengthSqr() < 1.0E-8D,
                "Unstable collision changed a bystander's velocity");
        helper.succeed();
    }

    @GameTest(template = "flight_room", timeoutTicks = 20)
    public static void kineticOverloadDropProtectionFollowsServerCommand(
            GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        boolean originalSetting = OneKickConfig.suppressKineticOverloadBlockDrops();
        var commandSource = helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.enchant(
                helper.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH),
                1);
        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 1, 0, 1, 0, 0, 0, true);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, boots);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 2, 3, 2);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5D, 3.0D, 2.5D));

        try {
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    commandSource, "onekick kinetic_overload_drop_protection true");
            fillKineticOverloadDropProtectionVolume(helper);
            BlockPos chestPosition = helper.absolutePos(new BlockPos(7, 3, 2));
            helper.setBlock(7, 3, 2, Blocks.CHEST);
            if (helper.getLevel().getBlockEntity(chestPosition) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, 16));
            } else {
                helper.fail("Extreme drop protection test chest has no block entity");
                return;
            }
            BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                    new Vec3(1.0D, 0.0D, 0.0D), 1.0D, 1.0F, snapshot);
            helper.assertTrue(countGlassDrops(helper, impact) == 0,
                    "Enabled Kinetic Overload drop protection created block drops");
            helper.assertTrue(countDiamondDrops(helper, impact) > 0,
                    "Kinetic Overload drop protection suppressed container contents");

            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    commandSource, "onekick kinetic_overload_drop_protection false");
            fillKineticOverloadDropProtectionVolume(helper);
            BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                    new Vec3(1.0D, 0.0D, 0.0D), 1.0D, 1.0F, snapshot);
            helper.assertTrue(countGlassDrops(helper, impact) > 0,
                    "Disabled Kinetic Overload drop protection did not restore block drops");
            helper.succeed();
        } finally {
            OneKickConfig.setSuppressKineticOverloadBlockDrops(originalSetting);
            unregisterSnapshotAttacker(helper, attacker);
        }
    }

    @GameTest(template = "flight_room", batch = "combinedDrops", timeoutTicks = 20)
    public static void disintegrationAndUnstableCollisionCreateBlockDrops(GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 6, 3, 2);
        impactedEntity.setInvulnerable(true);
        ItemEntity existingDrop = helper.spawnItem(Items.DIAMOND, 5.5F, 7.0F, 2.5F);
        fillImpactVolume(helper);

        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 1, 3, 0, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, ItemStack.EMPTY);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5D, 3.0D, 2.5D));
        BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                new Vec3(6.0D, 0.0D, 0.0D), 6.0D, 5.0F, snapshot);

        helper.runAtTickTime(2, () -> {
            long drops = countCobblestoneDrops(helper, impact);
            unregisterSnapshotAttacker(helper, attacker);
            helper.assertTrue(existingDrop.isAlive(),
                    "Disintegration + unstable collision destroyed an existing item entity");
            helper.assertTrue(drops > 0,
                    "Disintegration + unstable collision did not create any block drops");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_room", batch = "tripleDrops", timeoutTicks = 20)
    public static void combinedDisintegrationSuppressesDropsAndPreservesExistingItems(
            GameTestHelper helper) {
        ServerPlayer attacker = registerSnapshotAttacker(helper);
        attacker.setInvulnerable(true);
        Villager impactedEntity = helper.spawn(EntityType.VILLAGER, 6, 3, 2);
        impactedEntity.setInvulnerable(true);
        ItemEntity existingDrop = helper.spawnItem(Items.DIAMOND, 5.5F, 7.0F, 2.5F);
        fillImpactVolume(helper);

        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 1, 3, 1, 0, 0, 0, false);
        KickSnapshot snapshot = new KickSnapshot(
                attacker.getUUID(), 6.0D, enchantments, ItemStack.EMPTY);
        Vec3 impact = helper.absoluteVec(new Vec3(5.5D, 3.0D, 2.5D));
        BlockImpactService.handleImpact(helper.getLevel(), impactedEntity, impact,
                new Vec3(6.0D, 0.0D, 0.0D), 6.0D, 5.0F, snapshot);

        helper.runAtTickTime(8, () -> {
            long drops = countCobblestoneDrops(helper, impact);
            unregisterSnapshotAttacker(helper, attacker);
            helper.assertTrue(existingDrop.isAlive(),
                    "Triple enchantment explosions destroyed an existing item entity");
            helper.assertTrue(drops == 0,
                    "Kinetic Overload drop protection allowed triple-enchantment block drops");
            helper.succeed();
        });
    }

    /** Keeps destruction small while varying the frozen activation requirements. */
    private static KickSnapshot singularitySnapshot(ServerPlayer attacker, int chargeLevel,
            int overcharge, int disintegration, int singularity, float charge) {
        KickEnchantments enchantments = new KickEnchantments(
                0, 0, disintegration, 0, 0, chargeLevel, overcharge, 0, false, singularity, 0);
        return new KickSnapshot(attacker.getUUID(), 1.0D, enchantments, ItemStack.EMPTY, charge);
    }

    /** Supplies one shared fire counter for all effects of the test kick. */
    private static KickSnapshot afterburnerSnapshot(UUID attackerId, int unstableCollision) {
        KickEnchantments enchantments = new KickEnchantments(
                0, 0, 0, unstableCollision, 0, 0, 0, 0, false, 0, 1);
        return new KickSnapshot(attackerId, 1.0D, enchantments, ItemStack.EMPTY);
    }

    /** Resolves the real data-pack advancement and fails clearly when resources are missing. */
    private static AdvancementHolder loadedAdvancement(GameTestHelper helper, String name) {
        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(OneKick.MOD_ID, name));
        helper.assertTrue(advancement != null, "OneKick advancement was not loaded: " + name);
        return advancement;
    }

    /** Removes the template wall without modifying blocks outside the fixture. */
    private static void clearFireTestRoom(GameTestHelper helper) {
        for (BlockPos position : BlockPos.betweenClosed(0, 1, 0, 15, 7, 4)) {
            helper.setBlock(position, Blocks.AIR);
        }
    }

    /** Builds an 80-block supported layer inside the existing narrow flight room. */
    private static void fillFireTestFloor(GameTestHelper helper, int y) {
        for (BlockPos position : BlockPos.betweenClosed(0, y, 0, 15, y, 4)) {
            helper.setBlock(position, Blocks.STONE);
        }
    }

    /** Two overlapping flight segments cover a whole layer with a single kick snapshot. */
    private static void igniteFireTestLayer(GameTestHelper helper, Villager target,
            KickSnapshot snapshot, int y) {
        for (double z : new double[]{1.5D, 3.5D}) {
            AfterburnerService.trail(helper.getLevel(), target,
                    helper.absoluteVec(new Vec3(1.5D, y, z)),
                    helper.absoluteVec(new Vec3(14.5D, y, z)), snapshot);
        }
    }

    private static long countCobblestoneDrops(GameTestHelper helper, Vec3 impact) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, AABB.ofSize(impact, 30.0D, 20.0D, 20.0D)).stream()
                .filter(ItemEntity::isAlive)
                .filter(entity -> entity.getItem().is(Items.COBBLESTONE))
                .count();
    }

    private static long countGlassDrops(GameTestHelper helper, Vec3 impact) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, AABB.ofSize(impact, 30.0D, 20.0D, 20.0D)).stream()
                .filter(ItemEntity::isAlive)
                .filter(entity -> entity.getItem().is(Items.GLASS))
                .count();
    }

    private static long countDiamondDrops(GameTestHelper helper, Vec3 impact) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, AABB.ofSize(impact, 30.0D, 20.0D, 20.0D)).stream()
                .filter(ItemEntity::isAlive)
                .filter(entity -> entity.getItem().is(Items.DIAMOND))
                .count();
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> snapshotAttackers(GameTestHelper helper) {
        try {
            Field field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access the GameTest player registry", exception);
        }
    }

    private static ServerPlayer registerSnapshotAttacker(GameTestHelper helper) {
        UUID attackerId = UUID.randomUUID();
        ServerPlayer attacker = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(attackerId, "onekick-drop-test"),
                ClientInformation.createDefault());
        snapshotAttackers(helper).put(attackerId, attacker);
        return attacker;
    }

    private static void unregisterSnapshotAttacker(GameTestHelper helper, ServerPlayer attacker) {
        snapshotAttackers(helper).remove(attacker.getUUID());
    }

    private static void fillImpactVolume(GameTestHelper helper) {
        for (int x = 4; x <= 10; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }

    private static void fillKineticOverloadDropProtectionVolume(GameTestHelper helper) {
        for (int x = 4; x <= 10; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
    }
}
