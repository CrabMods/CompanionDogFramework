package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.item.CompanionDogCollarItem;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.crabmods.companiondog.world.CompanionDogBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("companiondog")
@PrefixGameTestTemplate(false)
public final class CompanionDogGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CompanionDogTest"));
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 4));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void removePlayer(GameTestHelper h, ServerPlayer player) {
        h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static CompanionDogEntity dog(GameTestHelper h) {
        CompanionDogEntity dog = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 2, 4);
        dog.setNoAi(true);
        return dog;
    }

    @GameTest(template = "empty")
    public static void ordinaryDamageCannotHurt(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        float health = dog.getHealth();
        h.assertTrue(!dog.hurt(h.getLevel().damageSources().generic(), 100), "ordinary damage accepted");
        h.assertTrue(!dog.hurt(h.getLevel().damageSources().lava(), 100), "lava damage accepted");
        h.assertTrue(!dog.hurt(h.getLevel().damageSources().fall(), 100), "fall damage accepted");
        h.assertTrue(dog.getHealth() == health && dog.isAlive(), "health changed");
        dog.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void posesAndActionFallback(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        for (CompanionDogPose pose : CompanionDogPose.values()) {
            dog.setCommandedPose(pose);
            h.assertTrue(dog.getCompanionDogPose() == pose, "pose not synchronized: " + pose);
        }
        dog.setCommandedPose(CompanionDogPose.LIE);
        dog.playAction(CompanionDogAction.PAW);
        h.assertTrue(dog.getCompanionDogPose() == CompanionDogPose.SIT, "paw must sit");
        h.runAfterDelay(65, () -> {
            h.assertTrue(dog.getCompanionDogPose() == CompanionDogPose.LIE, "action did not restore held pose");
            dog.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void boneTamesAndPetResponds(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 2));
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.isOwnedBy(player), "bone did not tame");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "bone consumption");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getServerAction() == CompanionDogAction.PET, "pet action missing");
        dog.setCommandedPose(CompanionDogPose.BELLY);
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getServerAction() == CompanionDogAction.BELLY_RUB, "belly rub missing");
        dog.discard();
        removePlayer(h, player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void boneCommandsDoNotFeedConsumeOrChangePose(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        var owner = player(h);
        var visitor = player(h);
        dog.tame(owner);
        dog.setCommandedPose(CompanionDogPose.SIT);
        dog.setMouthItem(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get()));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 2));
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COOKED_BEEF, 2));
        for (boolean sneaking : new boolean[]{false, true}) {
            owner.setShiftKeyDown(sneaking);
            h.assertTrue(dog.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction(),
                    "owner bone interaction not handled");
            h.assertTrue(owner.getMainHandItem().getCount() == 2 && owner.getOffhandItem().getCount() == 2,
                    "menu opening consumed bone or offhand food");
            h.assertTrue(dog.getCommandedPose() == CompanionDogPose.SIT && dog.getServerAction() == CompanionDogAction.NONE
                    && dog.getMouthItem().is(CompanionDogRegistries.TENNIS_BALL.get()), "menu opening changed dog state");
        }
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 2));
        h.assertTrue(dog.mobInteract(visitor, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS,
                "visitor bone interaction was accepted");
        h.assertTrue(dog.isOwnedBy(owner) && visitor.getMainHandItem().getCount() == 2,
                "visitor bone stole ownership or was consumed");
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BONE, 2));
        h.assertTrue(dog.mobInteract(owner, InteractionHand.OFF_HAND) == net.minecraft.world.InteractionResult.PASS,
                "offhand bone should not open a second menu");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(dog.mobInteract(owner, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS,
                "legacy sneak-empty interaction still captured");
        dog.discard();
        removePlayer(h, owner);
        removePlayer(h, visitor);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foodDyeAndMouthTransfer(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        var player = player(h);
        dog.tame(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_DYE, 2));
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getCollarColor() == DyeColor.BLUE, "collar not dyed");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "dye consumption");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 2));
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getServerAction() == CompanionDogAction.EAT, "eat action missing");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "food consumption");
        dog.setMouthItem(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        dog.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getMouthItem().isEmpty() && player.getMainHandItem().is(CompanionDogRegistries.TENNIS_BALL.get()),
                "mouth item transfer must not duplicate or destroy the ball");
        dog.discard();
        removePlayer(h, player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void saveRoundTrip(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        var owner = player(h);
        dog.tame(owner);
        dog.setBound(true);
        dog.setCommandedPose(CompanionDogPose.SLEEP);
        dog.setWandering(true);
        dog.setCollarColor(DyeColor.CYAN);
        dog.setMouthItem(new ItemStack(Items.STICK));
        CompoundTag saved = new CompoundTag();
        dog.saveWithoutId(saved);
        CompanionDogEntity copy = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
        copy.load(saved);
        h.assertTrue(copy.isOwnedBy(owner) && copy.isBound(), "owner/binding not saved");
        h.assertTrue(copy.getCommandedPose() == CompanionDogPose.SLEEP && copy.isWandering(), "pose/mode not saved");
        h.assertTrue(copy.getCollarColor() == DyeColor.CYAN && copy.getMouthItem().is(Items.STICK), "colour/item not saved");
        dog.discard();
        removePlayer(h, owner);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flyingBallCatch(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        TennisBallEntity ball = h.spawn(CompanionDogRegistries.TENNIS_BALL_ENTITY.get(), 4, 2, 4);
        ball.setNoGravity(true);
        ball.setPos(dog.getX(), dog.getY() + 0.4, dog.getZ());
        h.runAfterDelay(5, () -> {
            h.assertTrue(ball.isRemoved() && dog.getMouthItem().is(CompanionDogRegistries.TENNIS_BALL.get()), "ball not caught");
            dog.setCommandedPose(CompanionDogPose.SIT);
            dog.setMouthItem(ItemStack.EMPTY);
            h.assertTrue(!dog.tryCatch(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get())), "resting dog caught a ball");
            dog.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "ball-settle", timeoutTicks = 340)
    public static void ballSettlesWithoutLoss(GameTestHelper h) {
        TennisBallEntity ball = h.spawn(CompanionDogRegistries.TENNIS_BALL_ENTITY.get(), 4, 3, 4);
        h.runAfterDelay(310, () -> {
            h.assertTrue(ball.isRemoved(), "ball did not settle");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(1, 1, 1))),
                            net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(8, 4, 8)))),
                    e -> e.getItem().is(CompanionDogRegistries.TENNIS_BALL.get()));
            h.assertTrue(items.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1, "ball duplicated or lost");
            items.forEach(Entity::discard);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "fetch-delivery", timeoutTicks = 240)
    public static void fetchBringsOneBallToOwner(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 4));
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getInventory().clearContent();
        CompanionDogEntity dog = dog(h);
        dog.tame(player);
        dog.ownerAwayTicks = 0;
        dog.setNoAi(false);
        ItemEntity item = h.spawnItem(CompanionDogRegistries.TENNIS_BALL.get(), 6, 2, 4);
        h.runAfterDelay(200, () -> {
            h.assertTrue(item.isRemoved(), "original ball was not fetched: dog=" + dog.position()
                    + ", item=" + item.position() + ", owner=" + dog.getOwner()
                    + ", pose=" + dog.getCompanionDogPose() + ", action=" + dog.getServerAction()
                    + ", navDone=" + dog.getNavigation().isDone());
            h.assertTrue(dog.getMouthItem().isEmpty(), "ball not delivered");
            var delivered = h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(1),
                    e -> e.getItem().is(CompanionDogRegistries.TENNIS_BALL.get())
                            && e.getPersistentData().getBoolean("CompanionDogDelivered"));
            h.assertTrue(delivered.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "owner did not receive exactly one ball at their feet");
            delivered.forEach(Entity::discard);
            dog.discard();
            removePlayer(h, player);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void collarRecallsSameIdentity(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
        CompanionDogEntity first = CompanionDogCollarItem.summon(player);
        first.setMouthItem(new ItemStack(Items.STICK));
        CompanionDogEntity second = CompanionDogCollarItem.summon(player);
        h.assertTrue(first == second && second.getMouthItem().is(Items.STICK), "recall replaced original dog");
        java.util.UUID id = first.getUUID();
        first.discard();
        CompanionDogEntity unavailable = CompanionDogCollarItem.summon(player);
        h.assertTrue(unavailable == null, "unknown/unloaded dog must never be cloned");
        h.assertTrue(CompanionDogBindings.get(player.getServer()).dogOf(player.getUUID()).equals(id), "binding overwritten");
        removePlayer(h, player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerControlsItemsAndCommands(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        ServerPlayer owner = player(h);
        ServerPlayer visitor = player(h);
        dog.tame(owner);
        dog.setMouthItem(new ItemStack(Items.STICK));
        dog.mobInteract(visitor, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getMouthItem().is(Items.STICK) && visitor.getMainHandItem().isEmpty(),
                "visitor took the owner's item");
        dog.applyCommand(CompanionDogCommand.SLEEP, visitor);
        h.assertTrue(dog.getCommandedPose() == CompanionDogPose.STAND, "visitor changed the held pose");
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_DYE));
        dog.mobInteract(visitor, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getCollarColor() == DyeColor.RED, "visitor dyed the collar");
        dog.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.getMouthItem().isEmpty() && owner.getMainHandItem().is(Items.STICK),
                "owner could not receive the item");
        dog.discard();
        removePlayer(h, owner);
        removePlayer(h, visitor);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tamingKeepsCaughtItem(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        ServerPlayer owner = player(h);
        dog.setMouthItem(new ItemStack(CompanionDogRegistries.TENNIS_BALL.get()));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
        dog.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.assertTrue(dog.isOwnedBy(owner) && dog.getMouthItem().is(CompanionDogRegistries.TENNIS_BALL.get()),
                "taming destroyed the caught ball");
        dog.discard();
        removePlayer(h, owner);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wanderStopsActiveFollowing(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        dog.removeAllGoals(goal -> true);
        dog.setNoAi(false);
        ServerPlayer owner = player(h);
        dog.tame(owner);
        BlockPos far = h.absolutePos(new BlockPos(7, 2, 4));
        dog.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        var goal = new CompanionDogGoals.FollowOwnerUnlessWanderingGoal(dog);
        h.runAfterDelay(15, () -> {
            // FakePlayer.teleportTo is a no-op; place the fixture directly.
            owner.moveTo(far.getX() - 6.5, far.getY(), far.getZ() + 0.5);
            dog.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            dog.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            h.assertTrue(goal.canUse(), "follow could not start: owner=" + dog.getOwner()
                    + " distance=" + dog.distanceTo(owner) + " blocked=" + dog.unableToMoveToOwner()
                    + " recovery=" + dog.isRecovering() + " pose=" + dog.getCompanionDogPose());
            goal.start();
            goal.tick();
            h.assertTrue(goal.canContinueToUse(), "no active follow path to test");
            dog.setWandering(true);
            h.assertTrue(!goal.canContinueToUse(), "wander did not interrupt active following");
            goal.stop();
            h.assertTrue(dog.getNavigation().isDone(), "follow navigation was left active");
            dog.discard();
            removePlayer(h, owner);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void greetingCompletesJumpAndSecondAction(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        ServerPlayer owner = player(h);
        dog.tame(owner);
        java.util.Set<CompanionDogAction> seen = java.util.EnumSet.noneOf(CompanionDogAction.class);
        h.onEachTick(() -> seen.add(dog.getServerAction()));
        h.runAfterDelay(15, () -> dog.setNoAi(false));
        h.runAfterDelay(130, () -> {
            h.assertTrue(seen.contains(CompanionDogAction.JUMP), "greeting never jumped");
            h.assertTrue(seen.contains(CompanionDogAction.SPIN) || seen.contains(CompanionDogAction.PLAY_BOW),
                    "greeting was interrupted before its second action: " + seen);
            dog.discard();
            removePlayer(h, owner);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void jumpActuallyLeavesGroundAndLands(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        dog.removeAllGoals(goal -> true);
        dog.setNoAi(false);
        double[] startY = {0}, highestY = {0};
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (started[0]) highestY[0] = Math.max(highestY[0], dog.getY());
        });
        h.runAfterDelay(15, () -> {
            h.assertTrue(dog.onGround(), "jump fixture did not settle");
            startY[0] = highestY[0] = dog.getY();
            started[0] = true;
            dog.playAction(CompanionDogAction.JUMP);
        });
        h.runAfterDelay(65, () -> {
            h.assertTrue(highestY[0] - startY[0] > 0.45, "jump did not leave the floor");
            h.assertTrue(dog.onGround() && Math.abs(dog.getY() - startY[0]) < 0.05, "jump did not land");
            h.assertTrue(!dog.isBusy(), "jump never completed");
            dog.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void zoomiesCannotOverrideNewRestCommand(GameTestHelper h) {
        CompanionDogEntity dog = dog(h);
        ServerPlayer owner = player(h);
        dog.tame(owner);
        var goal = new CompanionDogGoals.ZoomiesGoal(dog);
        dog.zoomiesTicks = 140;
        goal.start();
        dog.applyCommand(CompanionDogCommand.SLEEP, owner);
        goal.stop();
        h.assertTrue(dog.getCompanionDogPose() == CompanionDogPose.SLEEP && !dog.isBusy(),
                "stopping zoomies replaced the owner's sleep command");
        dog.discard();
        removePlayer(h, owner);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 4000)
    public static void crossDimensionRecallPreservesIdentity(GameTestHelper h) {
        ServerPlayer owner = player(h);
        CompanionDogEntity dog = dog(h);
        dog.tame(owner);
        dog.setBound(true);
        dog.setCollarColor(DyeColor.CYAN);
        dog.setMouthItem(new ItemStack(Items.STICK));
        var home = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(),
                BlockPos.containing(com.crabmods.companiondog.world.CompanionDogSafety.find(h.getLevel(), dog, dog.blockPosition())));
        dog.setHome(home);
        java.util.UUID uuid = dog.getUUID();
        var destination = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(destination != null, "Nether not available");
        var traveller = new net.neoforged.neoforge.common.util.FakePlayer(destination, owner.getGameProfile());
        removePlayer(h, owner);
        traveller.moveTo(0.5, 100, 0.5, 0, 0);
        destination.getChunk(traveller.blockPosition());
        destination.setChunkForced(0, 0, true);
        var replaced = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos block : BlockPos.betweenClosed(-2, 99, -2, 2, 102, 2)) {
            replaced.put(block.immutable(), destination.getBlockState(block));
            destination.setBlock(block, (block.getY() == 99 ? net.minecraft.world.level.block.Blocks.STONE
                    : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 3);
        }
        destination.addNewPlayer(traveller);
        try {
            CompanionDogEntity arrived = dog.bringTo(traveller);
            h.assertTrue(arrived != null && arrived.level() == destination && uuid.equals(arrived.getUUID()),
                    "dimension transfer lost entity identity");
            h.assertTrue(arrived.isBound() && arrived.isOwnedBy(traveller)
                    && arrived.getCollarColor() == DyeColor.CYAN && arrived.getMouthItem().is(Items.STICK),
                    "dimension transfer lost saved state");
            arrived.requestHome(true);
            h.succeedWhen(() -> {
                var returned = h.getLevel().getEntity(uuid);
                h.assertTrue(returned instanceof CompanionDogEntity, "cross-dimension danger did not return original UUID"
                        + ": home=" + arrived.getHome() + " pending=" + arrived.isReturningHome()
                        + " removed=" + arrived.isRemoved() + " tick=" + arrived.tickCount
                        + " tickingChunk=" + destination.isPositionEntityTicking(arrived.blockPosition()));
                try {
                    var companion = (CompanionDogEntity) returned;
                    h.assertTrue(companion.isRecovering() && !companion.isReturningHome()
                            && home.equals(companion.getHome()) && companion.getMouthItem().is(Items.STICK)
                            && companion.getCollarColor() == DyeColor.CYAN, "cross-dimension retreat lost state");
                    companion.discard();
                } finally {
                    arrived.discard();
                    destination.removePlayerImmediately(traveller, Entity.RemovalReason.DISCARDED);
                    replaced.forEach((block, state) -> destination.setBlock(block, state, 3));
                    destination.setChunkForced(0, 0, false);
                }
            });
        } catch (Throwable failure) {
            destination.removePlayerImmediately(traveller, Entity.RemovalReason.DISCARDED);
            replaced.forEach((block, state) -> destination.setBlock(block, state, 3));
            destination.setChunkForced(0, 0, false);
            throw failure;
        }
    }
}
