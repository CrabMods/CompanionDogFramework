package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("companiondog")
@PrefixGameTestTemplate(false)
public final class CompanionDogFetchTests {
    private record Fixture(GameTestHelper h, FakePlayer owner, CompanionDogEntity a, CompanionDogEntity b,
                           CompanionDogGoals.FetchGoal first, CompanionDogGoals.FetchGoal second, ItemEntity ball) {
        void close() {
            first.stop();
            second.stop();
            ball.discard();
            a.discard();
            b.discard();
            h.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
    }
    private static Fixture fixture(GameTestHelper h, int count) {
        var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Fetch"));
        owner.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        h.getLevel().addNewPlayer(owner);
        var a = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 2, 4);
        var b = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 2, 4);
        a.tame(owner);
        b.tame(owner);
        a.setNoAi(true);
        b.setNoAi(true);
        var ball = h.spawnItem(CompanionDogRegistries.TENNIS_BALL.get(), 4, 2, 4);
        ball.setPos(a.getX() + 0.2, a.getY() + 0.2, a.getZ());
        ball.setNoGravity(true);
        ball.setItem(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get(), count));
        ball.setNeverPickUp();
        return new Fixture(h, owner, a, b, new CompanionDogGoals.FetchGoal(a), new CompanionDogGoals.FetchGoal(b), ball);
    }

    @GameTest(template = "empty", batch = "fetch-claim")
    public static void twoDogsDoNotClaimOneBall(GameTestHelper h) {
        var f = fixture(h, 1);
        h.assertTrue(f.first.canUse(), "first claim failed");
        f.second.canUse();
        h.assertTrue(f.ball.getPersistentData().getUUID("CompanionDogFetchClaim").equals(f.a.getUUID()),
                "second dog stole first dog's reservation");
        f.second.stop();
        f.first.start();
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().getCount() == 1 && f.b.getMouthItem().isEmpty() && f.ball.isRemoved(),
                "single ball transfer failed: a=" + f.a.getMouthItem() + " b=" + f.b.getMouthItem()
                        + " removed=" + f.ball.isRemoved() + " sight=" + f.a.hasLineOfSight(f.ball));
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fetch-frisbee")
    public static void frisbeeUsesTheSameClaimAndDeliveryRules(GameTestHelper h) {
        var f = fixture(h, 1);
        f.ball.setItem(new ItemStack(CompanionDogRegistries.FRISBEE.get(), 2));
        h.assertTrue(f.first.canUse() && !f.second.canUse(), "two dogs claimed the same disc stack");
        f.first.start();
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().is(CompanionDogRegistries.FRISBEE.get())
                && f.a.getMouthItem().getCount() == 1 && f.ball.getItem().getCount() == 1, "disc split");
        h.assertTrue(f.second.canUse(), "remaining disc stayed reserved");
        f.second.start();
        f.second.tick();
        h.assertTrue(f.b.getMouthItem().is(CompanionDogRegistries.FRISBEE.get()) && f.ball.isRemoved(), "second disc lost");
        f.a.deliverMouthItemTo(f.owner);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, f.owner.getBoundingBox().inflate(1),
                item -> item.getItem().is(CompanionDogRegistries.FRISBEE.get()));
        h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1
                && drops.getFirst().getPersistentData().getBoolean(CompanionDogGoals.DELIVERED_TAG), "disc redelivery loop");
        drops.forEach(Entity::discard);
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fetch-stack")
    public static void stoppedDogReleasesClaimAndStackConservesItems(GameTestHelper h) {
        var f = fixture(h, 3);
        h.assertTrue(f.first.canUse(), "first claim failed");
        f.first.stop();
        h.assertTrue(f.second.canUse(), "interrupted goal retained its reservation");
        f.second.start();
        f.second.tick();
        h.assertTrue(f.b.getMouthItem().getCount() == 1, "second dog did not take one item");
        h.assertTrue(f.first.canUse(), "remaining stack still locked");
        f.first.start();
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().getCount() == 1, "first dog did not take the next item");
        h.assertTrue(f.a.getMouthItem().getCount() + f.b.getMouthItem().getCount()
                + f.ball.getItem().getCount() == 3, "stack split duplicated or lost items");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fetch-expiry", timeoutTicks = 120)
    public static void abandonedClaimExpires(GameTestHelper h) {
        var f = fixture(h, 1);
        h.assertTrue(f.first.canUse() && !f.second.canUse(), "reservation fixture");
        f.a.discard();
        h.runAfterDelay(85, () -> {
            h.assertTrue(f.second.canUse(), "claim never expired after dog vanished");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "fetch-removed")
    public static void removedOrChangedTargetCannotBePickedUp(GameTestHelper h) {
        var f = fixture(h, 1);
        h.assertTrue(f.first.canUse(), "initial target");
        f.ball.setItem(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
        h.assertTrue(!f.first.canContinueToUse(), "changed item stayed fetchable");
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().isEmpty(), "picked up a non-fetchable replacement");
        f.ball.discard();
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().isEmpty(), "picked up removed target");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fetch-blocked")
    public static void blockedBallDoesNotHoldGoalForever(GameTestHelper h) {
        var f = fixture(h, 1);
        BlockPos box = h.absolutePos(new BlockPos(6, 1, 6));
        for (BlockPos pos : BlockPos.betweenClosed(box.offset(-1, 0, -1), box.offset(1, 2, 1))) {
            h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        }
        f.ball.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(box.above()));
        h.assertTrue(f.first.canUse(), "blocked target not selected for test");
        f.first.start();
        for (int i = 0; i < 80; i++) f.first.tick();
        h.assertTrue(!f.first.canContinueToUse() && f.a.getMouthItem().isEmpty(), "stalled target never released");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "fetch-interrupted")
    public static void interruptedFetchDoesNotUseOldTargetOrOfflineOwner(GameTestHelper h) {
        var f = fixture(h, 1);
        h.assertTrue(f.first.canUse(), "initial claim");
        f.first.start();
        f.a.setCommandedPose(CompanionDogPose.SIT);
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().isEmpty()
                && !f.ball.getPersistentData().hasUUID("CompanionDogFetchClaim"), "rest did not cancel stale goal tick");
        f.a.setCommandedPose(CompanionDogPose.STAND);
        h.assertTrue(f.first.canUse(), "resumed fetch");
        f.a.setMouthItem(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get()));
        h.getLevel().removePlayerImmediately(f.owner, Entity.RemovalReason.DISCARDED);
        f.first.tick();
        h.assertTrue(f.a.getMouthItem().getCount() == 1
                && !f.ball.getPersistentData().hasUUID("CompanionDogFetchClaim"), "logout lost mouth item or claim");
        f.close();
        h.succeed();
    }
}
