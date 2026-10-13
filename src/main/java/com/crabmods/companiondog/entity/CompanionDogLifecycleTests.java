package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.CompanionDogGameEvents;
import com.crabmods.companiondog.item.CompanionDogCollarItem;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.crabmods.companiondog.world.CompanionDogBindings;
import com.crabmods.companiondog.world.CompanionDogSafety;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("companiondog")
@PrefixGameTestTemplate(false)
public final class CompanionDogLifecycleTests {
    private record Fixture(GameTestHelper h, ServerPlayer owner, CompanionDogEntity dog, GlobalPos home) {
        void close() {
            dog.discard();
            h.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
    }

    private static Fixture fixture(GameTestHelper h) {
        var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Lifecycle"));
        BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        owner.moveTo(Vec3.atBottomCenterOf(pos));
        h.getLevel().addNewPlayer(owner);
        CompanionDogEntity dog = CompanionDogCollarItem.summon(owner);
        h.assertTrue(dog != null, "safe first acquisition failed");
        dog.setNoAi(true);
        dog.setCommandedPose(CompanionDogPose.STAND);
        return new Fixture(h, owner, dog, dog.getHome());
    }

    private static void safeAndAlive(Fixture f) {
        var dog = f.dog;
        f.h.assertTrue(dog.isAlive() && !dog.isRemoved() && !dog.isDeadOrDying() && dog.deathTime == 0
                && dog.hurtTime == 0 && dog.getHealth() > 0, "death or hurt state entered");
        f.h.assertTrue(!dog.isReturningHome() && dog.isRecovering()
                && dog.getCommandedPose() == CompanionDogPose.LIE, "not resting at home");
        f.h.assertTrue(dog.position().distanceTo(Vec3.atBottomCenterOf(dog.getHome().pos())) < 0.2,
                "did not return to home");
    }

    @GameTest(template = "empty")
    public static void allDamageFamiliesRetreatWithoutDeath(GameTestHelper h) {
        var f = fixture(h);
        f.dog.setCollarColor(DyeColor.CYAN);
        f.dog.setMouthItem(new ItemStack(Items.STICK));
        f.dog.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 1, 6))));
        var damage = h.getLevel().damageSources();
        for (var source : java.util.List.of(damage.generic(), damage.genericKill(), damage.fellOutOfWorld(),
                damage.lava(), damage.inFire(), damage.onFire(), damage.drown(), damage.inWall(),
                damage.freeze(), damage.fall(), damage.cactus(), damage.magic(), damage.wither(),
                damage.explosion(null, null))) {
            h.assertTrue(!f.dog.hurt(source, Float.MAX_VALUE), "accepted damage");
        }
        h.assertTrue(f.dog.isReturningHome() && !f.dog.isPickable(), "danger did not start retreat");
        h.runAfterDelay(5, () -> {
            safeAndAlive(f);
            h.assertTrue(f.dog.getMouthItem().is(Items.STICK) && f.dog.getCollarColor() == DyeColor.CYAN,
                    "retreat lost appearance or mouth item");
            h.assertTrue(!f.dog.displayFireAnimation(), "fire visual");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void directDieHealthAndVoidAreSafe(GameTestHelper h) {
        var f = fixture(h);
        f.dog.die(h.getLevel().damageSources().genericKill());
        f.dog.setHealth(0);
        f.dog.setHealth(Float.NaN);
        f.dog.onBelowWorld();
        h.runAfterDelay(5, () -> {
            safeAndAlive(f);
            h.assertTrue(f.dog.getDeathSound() == null && f.dog.getHurtSound(h.getLevel().damageSources().generic()) == null,
                    "death/hurt sound remains");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2450)
    public static void fullRestRequiresExplicitReleaseAndExpires(GameTestHelper h) {
        var f = fixture(h);
        f.dog.hurt(h.getLevel().damageSources().generic(), Float.MAX_VALUE);
        long[] deadline = {0};
        h.runAfterDelay(5, () -> {
            safeAndAlive(f);
            deadline[0] = f.dog.getRestUntil();
            h.assertTrue(f.dog.restSeconds() >= 119, "rest shorter than two minutes");
            for (var command : CompanionDogCommand.values()) {
                if (command != CompanionDogCommand.SET_HOME && command != CompanionDogCommand.GO_HOME
                        && command != CompanionDogCommand.END_REST) f.dog.applyCommand(command, f.owner);
            }
            h.assertTrue(f.dog.getCommandedPose() == CompanionDogPose.LIE && !f.dog.isZooming(), "command bypass");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
            f.dog.mobInteract(f.owner, InteractionHand.MAIN_HAND);
            h.assertTrue(f.dog.getServerAction() == CompanionDogAction.EAT, "cannot feed resting dog");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.dog.mobInteract(f.owner, InteractionHand.MAIN_HAND);
            h.assertTrue(f.dog.getServerAction() == CompanionDogAction.PET, "cannot pet resting dog");
            h.assertTrue(CompanionDogCollarItem.summon(f.owner) == null, "recall bypass");
            h.assertTrue(!f.dog.tryCatch(new ItemStack(Items.STICK)), "fetch bypass");
            f.dog.applyCommand(CompanionDogCommand.GO_HOME, f.owner);
        });
        h.runAfterDelay(15, () -> {
            h.assertTrue(f.dog.getRestUntil() == deadline[0], "going home extended rest");
            f.dog.hurt(h.getLevel().damageSources().generic(), 1);
        });
        h.runAfterDelay(30, () -> h.assertTrue(f.dog.getRestUntil() == deadline[0], "repeat hit extended rest"));
        h.runAfterDelay(2390, () -> h.assertTrue(f.dog.isRecovering(), "rest expired early"));
        h.runAfterDelay(2410, () -> {
            h.assertTrue(!f.dog.isRecovering() && f.dog.getCommandedPose() == CompanionDogPose.LIE,
                    "expiry should allow commands but stay at home");
            h.assertTrue(CompanionDogCollarItem.summon(f.owner) == f.dog && f.dog.getCommandedPose() == CompanionDogPose.STAND,
                    "could not resume outing after full rest");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockedHomeWaitsForExplicitSafeHome(GameTestHelper h) {
        var f = fixture(h);
        f.dog.setBound(false);
        f.dog.setHome(GlobalPos.of(h.getLevel().dimension(), f.home.pos().above(100)));
        f.dog.requestHome(true);
        h.runAfterDelay(10, () -> {
            h.assertTrue(f.dog.isReturningHome() && f.dog.isInvisible() && !f.dog.isPickable(), "unsafe home was used");
            h.assertTrue(f.dog.setHomeFrom(f.owner), "new safe home rejected");
        });
        h.runAfterDelay(20, () -> {
            safeAndAlive(f);
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unsafeAcquisitionDoesNotBind(GameTestHelper h) {
        var owner = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Unsafe"));
        owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 50, 3))));
        h.assertTrue(CompanionDogCollarItem.summon(owner) == null, "spawned without floor");
        h.assertTrue(CompanionDogBindings.get(owner.getServer()).dogOf(owner.getUUID()) == null, "failed summon bound owner");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hazardsAndHomeNbtAreValidated(GameTestHelper h) {
        var f = fixture(h);
        var pos = f.home.pos();
        h.assertTrue(CompanionDogSafety.safe(h.getLevel(), f.dog, pos), "fixture not safe");
        for (var hazard : java.util.List.of(Blocks.LAVA, Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.FIRE,
                Blocks.POWDER_SNOW, Blocks.CAMPFIRE, Blocks.WITHER_ROSE, Blocks.SWEET_BERRY_BUSH)) {
            var old = h.getLevel().getBlockState(pos.east());
            h.getLevel().setBlock(pos.east(), hazard.defaultBlockState(), 3);
            h.assertTrue(!CompanionDogSafety.safe(h.getLevel(), f.dog, pos), "hazard accepted: " + hazard);
            h.getLevel().setBlock(pos.east(), old, 3);
        }
        f.dog.requestHome(true);
        CompoundTag saved = new CompoundTag();
        f.dog.saveWithoutId(saved);
        var copy = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
        copy.load(saved);
        h.assertTrue(copy.isReturningHome() && copy.isOwnedBy(f.owner) && f.home.equals(copy.getHome()),
                "pending lifecycle not serialized");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void noProjectileShieldAggroOrDigIncome(GameTestHelper h) {
        var f = fixture(h);
        h.assertTrue(!f.dog.canBeHitByProjectile() && !f.dog.isPushable(), "companion can shield");
        var zombie = h.spawn(EntityType.ZOMBIE, 7, 1, 7);
        zombie.setNoAi(true);
        zombie.setTarget(f.dog);
        h.assertTrue(zombie.getTarget() == null, "companion can tank aggro");
        zombie.discard();
        f.dog.playAction(CompanionDogAction.DIG);
        h.runAfterDelay(100, () -> {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, f.dog.getBoundingBox().inflate(3));
            h.assertTrue(drops.isEmpty(), "cosmetic digging generated items");
            h.assertTrue(!f.dog.canMate(f.dog), "immortal breeding");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void ownerDeathAndLogoutReturnHome(GameTestHelper h) {
        var f = fixture(h);
        CompanionDogGameEvents.onPlayerDeath(new LivingDeathEvent(f.owner, h.getLevel().damageSources().generic()));
        h.runAfterDelay(5, () -> {
            h.assertTrue(!f.dog.isRecovering() && !f.dog.isReturningHome()
                    && f.dog.getCommandedPose() == CompanionDogPose.LIE, "owner death should go home without recovery");
            long deadline = f.dog.getRestUntil();
            CompanionDogGameEvents.onLogout(new PlayerEvent.PlayerLoggedOutEvent(f.owner));
            h.runAfterDelay(5, () -> {
                h.assertTrue(f.dog.getRestUntil() == deadline, "logout reset rest");
                f.close();
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ordinaryDamageHealsWithoutRest(GameTestHelper h) {
        var f = fixture(h);
        f.dog.hurt(h.getLevel().damageSources().generic(), 3);
        h.assertTrue(f.dog.getHealth() == 17 && !f.dog.isRecovering(), "ordinary hit triggered retreat");
        f.dog.hurt(h.getLevel().damageSources().generic(), 3);
        h.assertTrue(f.dog.getHealth() == 17, "damage cooldown ignored");
        h.runAfterDelay(80, () -> h.assertTrue(f.dog.getHealth() == 17 && !f.dog.isRecovering(),
                "healed before quiet delay or started resting"));
        h.runAfterDelay(240, () -> {
            h.assertTrue(f.dog.getHealth() == f.dog.getMaxHealth() && !f.dog.isRecovering()
                    && f.dog.deathTime == 0 && f.dog.hurtTime == 0, "quiet automatic healing failed");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void onlyOwnerCanEndRestAndRecallAgain(GameTestHelper h) {
        var f = fixture(h);
        f.dog.die(h.getLevel().damageSources().generic());
        h.runAfterDelay(5, () -> {
            safeAndAlive(f);
            var visitor = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Visitor"));
            f.dog.applyCommand(CompanionDogCommand.END_REST, visitor);
            h.assertTrue(f.dog.isRecovering(), "visitor ended owner's recovery");
            f.dog.applyCommand(CompanionDogCommand.END_REST, f.owner);
            h.assertTrue(!f.dog.isRecovering() && f.dog.getRestUntil() == 0
                    && f.dog.getCommandedPose() == CompanionDogPose.STAND, "explicit release failed");
            h.assertTrue(CompanionDogCollarItem.summon(f.owner) == f.dog, "released dog cannot be recalled");
            CompoundTag saved = new CompoundTag();
            f.dog.saveWithoutId(saved);
            var copy = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
            copy.load(saved);
            h.assertTrue(!copy.isRecovering(), "released rest reappears on load");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void recallSupersedesDeferredLogoutWithoutHiding(GameTestHelper h) {
        var f = fixture(h);
        var bindings = CompanionDogBindings.get(f.owner.getServer());
        bindings.requestOwnerHome(f.owner.getUUID(), false);
        h.assertTrue(CompanionDogCollarItem.summon(f.owner) == f.dog, "recall failed");
        h.assertTrue(bindings.takeHomeRequest(f.owner.getUUID()) == null, "stale home request still pending");
        h.runAfterDelay(10, () -> {
            h.assertTrue(!f.dog.isInvisible() && !f.dog.isReturningHome() && !f.dog.isRecovering()
                    && f.dog.distanceToSqr(f.owner) < 25, "recalled dog disappeared on first tick");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void recallRescuesNonLethalBlockedHomeButNotLethalRest(GameTestHelper h) {
        var f = fixture(h);
        f.dog.setBound(false);
        f.dog.setHome(GlobalPos.of(h.getLevel().dimension(), f.home.pos().above(100)));
        f.dog.requestHome(false);
        h.assertTrue(f.dog.bringTo(f.owner) == f.dog && !f.dog.isInvisible(), "cannot recall pending ordinary return");
        f.dog.requestHome(true);
        h.assertTrue(f.dog.bringTo(f.owner) == null && f.dog.isReturningHome(), "recall bypassed lethal pending rest");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recallChoosesVisibleNonOverlappingFloor(GameTestHelper h) {
        var f = fixture(h);
        var origin = f.owner.blockPosition();
        for (int dx = -4; dx <= 4; dx++) for (int dy = 0; dy <= 3; dy++) {
            h.getLevel().setBlock(origin.offset(dx, dy, 1), Blocks.STONE.defaultBlockState(), 3);
        }
        var safe = CompanionDogSafety.findForOwner(f.owner, f.dog);
        h.assertTrue(safe != null && safe.z < origin.getZ() + 1, "selected hidden side of wall");
        h.assertTrue(!f.dog.getDimensions(f.dog.getPose()).makeBoundingBox(safe)
                .intersects(f.owner.getBoundingBox()), "dog overlaps owner");
        f.owner.moveTo(f.owner.position().add(0, 100, 0));
        var before = f.dog.position();
        h.assertTrue(CompanionDogCollarItem.summon(f.owner) == null && f.dog.position().equals(before),
                "unsafe recall moved the dog");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deferredOwnerHomeRequestIsDurable(GameTestHelper h) {
        var f = fixture(h);
        var bindings = CompanionDogBindings.get(f.owner.getServer());
        bindings.requestOwnerHome(f.owner.getUUID(), true);
        bindings.requestOwnerHome(f.owner.getUUID(), false);
        var saved = bindings.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(saved.getCompound("HomeRequests").getBoolean(f.owner.getUUID().toString()),
                "logout downgraded or failed to persist a death return");
        h.runAfterDelay(5, () -> {
            safeAndAlive(f);
            h.assertTrue(bindings.takeHomeRequest(f.owner.getUUID()) == null, "loaded companion did not consume queued home request");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void wrongEpochAndDuplicateIdentityAreRejected(GameTestHelper h) {
        var f = fixture(h);
        var bindings = CompanionDogBindings.get(f.owner.getServer());
        CompoundTag tag = new CompoundTag();
        f.dog.saveWithoutId(tag);
        var stale = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
        stale.load(tag);
        stale.setBindingEpoch(99);
        h.assertTrue(!h.getLevel().addFreshEntity(stale), "stale recovery epoch was admitted");
        var clone = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
        clone.load(tag);
        clone.setUUID(UUID.randomUUID());
        h.assertTrue(!h.getLevel().addFreshEntity(clone), "different UUID replaced bound companion");
        h.assertTrue(bindings.findLoaded(f.owner.getServer(), f.owner.getUUID()) == f.dog, "original lost");
        bindings.checkpoint(f.dog);
        var data = bindings.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(!data.getCompound("Backups").getCompound(f.owner.getUUID().toString()).contains("Mouth"),
                "backup can duplicate mouth inventory");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void firstLoginGiftIsIdempotentAndRecoverable(GameTestHelper h) {
        var f = fixture(h);
        f.owner.getInventory().clearContent();
        CompanionDogGameEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(f.owner));
        CompanionDogGameEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(f.owner));
        h.assertTrue(f.owner.getInventory().countItem(CompanionDogRegistries.COMPANION_DOG_COLLAR.get()) == 1, "duplicate login gift");
        h.assertTrue(f.owner.getData(CompanionDogRegistries.RECEIVED_COLLAR), "gift marker missing");
        var recipe = h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("companiondog", "companiondog_collar"));
        h.assertTrue(recipe.isPresent(), "lost collar cannot be crafted again");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void administratorCanQuietlyRemoveExtraDog(GameTestHelper h) {
        var dog = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 2, 4);
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(2),
                "kill " + dog.getStringUUID());
        h.assertTrue(dog.isRemoved() && dog.deathTime == 0 && dog.getHealth() > 0,
                "extra dog could not be quietly removed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaKillReleasesOnlyItsBindingAndRejectsStaleCopy(GameTestHelper h) {
        var f = fixture(h);
        var old = new CompoundTag();
        f.dog.saveWithoutId(old);
        f.dog.kill();
        var bindings = CompanionDogBindings.get(f.owner.getServer());
        h.assertTrue(f.dog.isRemoved() && f.dog.deathTime == 0 && f.dog.getHealth() > 0, "kill entered death");
        h.assertTrue(bindings.dogOf(f.owner.getUUID()) == null, "kill left an unusable binding");
        var stale = CompanionDogRegistries.COMPANION_DOG.get().create(h.getLevel());
        stale.load(old);
        h.assertTrue(!h.getLevel().addFreshEntity(stale), "killed identity came back from stale data");
        var reloaded = CompanionDogBindings.load(bindings.save(new CompoundTag(), h.getLevel().registryAccess()),
                h.getLevel().registryAccess());
        h.assertTrue(reloaded.dogOf(f.owner.getUUID()) == null
                && reloaded.epochOf(f.owner.getUUID()) == f.dog.getBindingEpoch() + 1
                && !reloaded.acceptIdentity(stale), "released identity returned after saved data reload");
        var replacement = CompanionDogCollarItem.summon(f.owner);
        h.assertTrue(replacement != null && !replacement.getUUID().equals(f.dog.getUUID())
                && !replacement.isRecovering(), "collar cannot welcome a new companion after explicit removal");
        replacement.discard();
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void opBoneManagementRetainsOwnershipAndPermissionGuards(GameTestHelper h) {
        var op = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Operator")) {
            @Override public boolean hasPermissions(int level) { return level <= 2; }
        };
        var visitor = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Visitor")) {
            @Override public boolean hasPermissions(int level) { return false; }
        };
        op.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
        var dog = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 1, 4);
        dog.applyCommand(CompanionDogCommand.ARCHIVE_UNOWNED, visitor);
        h.assertTrue(!dog.isRemoved(), "non-op quietly removed a dog");
        dog.tame(visitor);
        dog.applyCommand(CompanionDogCommand.ARCHIVE_UNOWNED, op);
        h.assertTrue(!dog.isRemoved(), "op bone removed an owned dog");
        dog.setOwnerUUID(null);
        dog.setBound(true);
        dog.applyCommand(CompanionDogCommand.ARCHIVE_UNOWNED, op);
        h.assertTrue(!dog.isRemoved(), "op removed bound orphan");
        dog.setBound(false);
        dog.applyCommand(CompanionDogCommand.ARCHIVE_UNOWNED, op);
        h.assertTrue(dog.isRemoved() && dog.deathTime == 0 && dog.getHealth() > 0, "quiet removal entered death");
        h.assertTrue(op.getMainHandItem().getCount() == 1, "removal consumed bone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaKillFeedbackIsGentleAndExecuteResultSurvives(GameTestHelper h) {
        var dog = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 1, 4);
        var messages = new java.util.ArrayList<String>();
        var sink = new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var server = h.getLevel().getServer();
        int[] result = {-1};
        var source = server.createCommandSourceStack().withSource(sink).withPermission(2)
                .withCallback((success, value) -> result[0] = success ? value : -1);
        server.getCommands().performPrefixedCommand(source, "execute as " + dog.getStringUUID() + " run kill @s");
        h.assertTrue(dog.isRemoved() && dog.deathTime == 0, "execute kill did not remove gently");
        h.assertTrue(result[0] == 1, "execute kill lost its result");
        h.assertTrue(messages.stream().anyMatch(message -> message.contains("quietly"))
                && messages.stream().noneMatch(message -> message.contains("Killed")), "kill feedback was not gentle: " + messages);
        var second = h.spawn(CompanionDogRegistries.COMPANION_DOG.get(), 4, 2, 4);
        var item = h.spawnItem(Items.STICK, 4, 2, 4);
        String tag = "kill_test_" + UUID.randomUUID().toString().replace("-", "");
        second.addTag(tag);
        item.addTag(tag);
        messages.clear();
        server.getCommands().performPrefixedCommand(source, "kill @e[tag=" + tag + "]");
        h.assertTrue(second.isRemoved() && second.deathTime == 0 && item.isRemoved() && result[0] == 2,
                "mixed kill did not preserve targets/result: dog=" + second.isRemoved()
                        + " item=" + item.isRemoved() + " result=" + result[0] + " messages=" + messages);
        h.assertTrue(messages.size() == 2 && messages.stream().anyMatch(message -> message.contains("quietly"))
                && messages.stream().anyMatch(message -> message.contains("Killed") && !message.contains("CompanionDog")),
                "mixed kill combined companions with vanilla death feedback: " + messages);
        h.succeed();
    }
}
