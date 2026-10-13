package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("companiondog")
@PrefixGameTestTemplate(false)
public final class CompanionDogFrisbeeTests {
    private static FrisbeeEntity disc(GameTestHelper h) {
        var disc = h.spawn(CompanionDogRegistries.FRISBEE_ENTITY.get(), 4, 3, 4);
        disc.setItem(new ItemStack(CompanionDogRegistries.FRISBEE.get()));
        return disc;
    }

    @GameTest(template = "empty", batch = "disc-physics")
    public static void forwardSpeedProvidesLiftAndVerticalThrowsStall(GameTestHelper h) {
        var disc = disc(h);
        var ball = h.spawn(CompanionDogRegistries.TENNIS_BALL_ENTITY.get(), 2, 3, 4);
        ball.setDeltaMovement(0.9, 0, 0);
        ball.tick();
        double ballGravity = -ball.getDeltaMovement().y;
        disc.setDeltaMovement(0.9, 0, 0);
        h.assertTrue(disc.getDefaultGravity() > 0 && disc.getDefaultGravity() < ballGravity / 2,
                "fast disc did not glide differently from a ball");
        disc.tick();
        h.assertTrue(disc.getDeltaMovement().x < 0.9 * 0.99 && disc.getDeltaMovement().y < 0, "drag/gravity missing");
        disc.setDeltaMovement(0, 1, 0);
        h.assertTrue(disc.getDefaultGravity() > ballGravity, "vertical disc has unlimited lift");
        disc.discard();
        ball.discard();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-impact")
    public static void impactCreatesOneItemWithoutBallBounce(GameTestHelper h) {
        var disc = disc(h);
        Vec3 hit = h.absoluteVec(new Vec3(4, 2, 4));
        disc.setDeltaMovement(1, -0.2, 0);
        disc.onHitBlock(new BlockHitResult(hit, Direction.WEST, BlockPos.containing(hit), false));
        disc.onHitBlock(new BlockHitResult(hit, Direction.WEST, BlockPos.containing(hit), false));
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, disc.getBoundingBox().inflate(1),
                item -> item.getItem().is(CompanionDogRegistries.FRISBEE.get()));
        h.assertTrue(disc.isRemoved() && items.size() == 1 && items.getFirst().getItem().getCount() == 1,
                "impact duplicated or lost the disc");
        h.assertTrue(items.getFirst().getDeltaMovement().equals(Vec3.ZERO), "disc bounced like a ball");
        items.forEach(Entity::discard);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-harmless")
    public static void entityImpactDoesNotDamageOrPush(GameTestHelper h) {
        var target = h.spawn(EntityType.COW, 5, 2, 4);
        target.setNoAi(true);
        target.setDeltaMovement(Vec3.ZERO);
        float health = target.getHealth();
        var disc = disc(h);
        disc.onHitEntity(new EntityHitResult(target));
        h.assertTrue(target.getHealth() == health && target.getDeltaMovement().equals(Vec3.ZERO), "disc became a weapon");
        h.assertTrue(disc.isRemoved(), "disc did not settle on entity impact");
        h.getLevel().getEntitiesOfClass(ItemEntity.class, disc.getBoundingBox().inflate(1)).forEach(Entity::discard);
        target.discard();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-catch")
    public static void twoDogsCatchOnlyOneDisc(GameTestHelper h) {
        var a = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 3, 4);
        var b = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 3, 4);
        a.setNoAi(true);
        b.setNoAi(true);
        var disc = disc(h);
        disc.setNoGravity(true);
        disc.tick();
        h.assertTrue(disc.isRemoved() && a.getMouthItem().getCount() + b.getMouthItem().getCount() == 1,
                "air catch duplicated or missed a disc");
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-rest")
    public static void restingDogCannotCatchADisc(GameTestHelper h) {
        var dog = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 3, 4);
        dog.setNoAi(true);
        dog.setCommandedPose(CompanionDogPose.SIT);
        var disc = disc(h);
        disc.setNoGravity(true);
        disc.tick();
        h.assertTrue(!disc.isRemoved() && dog.getMouthItem().isEmpty(), "resting dog caught a disc");
        disc.discard();
        dog.discard();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-save")
    public static void reloadPreservesFlightTimeoutAndItemData(GameTestHelper h) {
        var disc = disc(h);
        CompoundTag data = disc.saveWithoutId(new CompoundTag());
        data.putInt("CompanionDogFlightTicks", 300);
        disc.discard();
        var restored = new FrisbeeEntity(CompanionDogRegistries.FRISBEE_ENTITY.get(), h.getLevel());
        restored.load(data);
        h.getLevel().addFreshEntity(restored);
        restored.tick();
        var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, restored.getBoundingBox().inflate(1),
                item -> item.getItem().is(CompanionDogRegistries.FRISBEE.get()));
        h.assertTrue(restored.isRemoved() && items.size() == 1, "reload reset lifetime or lost item data");
        items.forEach(Entity::discard);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "disc-water", timeoutTicks = 40)
    public static void waterSettlesTheDisc(GameTestHelper h) {
        for (BlockPos pos : BlockPos.betweenClosed(3, 1, 3, 5, 3, 5)) h.setBlock(pos, Blocks.WATER);
        var disc = disc(h);
        disc.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
        h.runAfterDelay(10, () -> {
            h.assertTrue(disc.isRemoved(), "submerged disc kept flying");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, disc.getBoundingBox().inflate(3),
                    item -> item.getItem().is(CompanionDogRegistries.FRISBEE.get()));
            h.assertTrue(items.size() == 1, "water lost or duplicated disc");
            items.forEach(Entity::discard);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "disc-use")
    public static void survivalAndCreativeThrowsUseRealItemPath(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Disc"));
        player.moveTo(h.absoluteVec(new Vec3(2, 2, 2)));
        h.getLevel().addNewPlayer(player);
        for (boolean creative : new boolean[] {false, true}) {
            player.getAbilities().instabuild = creative;
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CompanionDogRegistries.FRISBEE.get(), 3));
            CompanionDogRegistries.FRISBEE.get().use(h.getLevel(), player, InteractionHand.OFF_HAND);
            h.assertTrue(player.getOffhandItem().getCount() == (creative ? 3 : 2), "incorrect disc consumption");
        }
        var discs = h.getLevel().getEntitiesOfClass(FrisbeeEntity.class, player.getBoundingBox().inflate(3));
        h.assertTrue(discs.size() == 2 && discs.stream().allMatch(d -> d.getOwner() == player && d.getItem().getCount() == 1),
                "thrower or projectile stack invalid");
        discs.forEach(Entity::discard);
        h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        h.succeed();
    }
}
