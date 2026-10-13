package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/** CompanionDog's behaviours. Kept together because they all share the dog's package-private state. */
final class CompanionDogGoals {
    static final String DELIVERED_TAG = "CompanionDogDelivered";

    private CompanionDogGoals() {
    }

    private static boolean ownerAvailable(CompanionDogEntity dog) {
        LivingEntity owner = dog.getOwner();
        return owner != null && owner.isAlive() && owner.level() == dog.level() && !owner.isSpectator();
    }

    /** Stays put while told to rest or while an action that needs the body is playing. */
    static final class HoldStillGoal extends Goal {
        private final CompanionDogEntity dog;

        HoldStillGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return dog.commandedPose.isResting() || dog.serverAction.freezesMovement();
        }

        @Override
        public void start() {
            dog.getNavigation().stop();
        }

        @Override
        public void tick() {
            dog.getNavigation().stop();
        }
    }

    /** Runs over, jumps up and spins when the owner comes back after a while. */
    static final class GreetOwnerGoal extends Goal {
        private final CompanionDogEntity dog;
        private int stage;
        private int timer;

        GreetOwnerGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return dog.isTame() && dog.ownerAwayTicks > CompanionDogEntity.GREET_AFTER_TICKS && ownerAvailable(dog)
                    && !dog.isBusy() && !dog.commandedPose.isResting()
                    && dog.distanceToSqr(dog.getOwner()) < 16 * 16;
        }

        @Override
        public boolean canContinueToUse() {
            return stage < 3 && timer < 240 && ownerAvailable(dog) && !dog.commandedPose.isResting();
        }

        @Override
        public void start() {
            dog.ownerAwayTicks = 0;
            dog.setAutoPose(CompanionDogPose.STAND, 0);
            dog.happyTicks = 240;
            stage = 0;
            timer = 0;
        }

        @Override
        public void tick() {
            LivingEntity owner = dog.getOwner();
            timer++;
            dog.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (dog.serverAction.freezesMovement()) {
                dog.getNavigation().stop();
                return;
            }
            switch (stage) {
                case 0 -> {
                    if (dog.distanceToSqr(owner) < 2.4 * 2.4) {
                        dog.getNavigation().stop();
                        dog.playAction(CompanionDogAction.JUMP);
                        stage = 1;
                    } else if (timer % 10 == 1) {
                        dog.getNavigation().moveTo(owner, 1.45);
                    }
                }
                case 1 -> {
                    if (!dog.isBusy()) {
                        dog.playAction(dog.getRandom().nextBoolean() ? CompanionDogAction.SPIN : CompanionDogAction.PLAY_BOW);
                        stage = 2;
                    }
                }
                case 2 -> {
                    if (!dog.isBusy()) {
                        stage = 3;
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void stop() {
            dog.pantingTicks = Math.max(dog.pantingTicks, 100);
        }
    }

    /** Chases fetch toys (and sticks or bones the owner drops) and brings them back. */
    static final class FetchGoal extends Goal {
        private static final String CLAIM = "CompanionDogFetchClaim";
        private static final String CLAIM_UNTIL = "CompanionDogFetchClaimUntil";
        private final CompanionDogEntity dog;
        @Nullable
        private ItemEntity target;
        private int repath;
        private int stalledTicks;
        private double bestDistance;
        private int deliveryRetryAfter;
        private final java.util.Map<java.util.UUID, Integer> skippedUntil = new java.util.HashMap<>();

        FetchGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!dog.isTame() || dog.isRecovering() || dog.commandedPose.isResting() || !ownerAvailable(dog)
                    || dog.tickCount < deliveryRetryAfter) {
                return false;
            }
            if (!dog.getMouthItem().isEmpty()) {
                return true;
            }
            target = findItem();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (dog.isRecovering() || dog.commandedPose.isResting() || !ownerAvailable(dog)
                    || dog.tickCount < deliveryRetryAfter) {
                return false;
            }
            if (!dog.getMouthItem().isEmpty()) {
                return true;
            }
            return target != null && available(target) && dog.distanceToSqr(target) < 32 * 32;
        }

        @Nullable
        private ItemEntity findItem() {
            skippedUntil.values().removeIf(until -> until <= dog.tickCount);
            List<ItemEntity> items = dog.level().getEntitiesOfClass(ItemEntity.class,
                    dog.getBoundingBox().inflate(24.0, 4.0, 24.0), this::available);
            ItemEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (ItemEntity item : items) {
                if (skippedUntil.containsKey(item.getUUID())) continue;
                double distance = dog.distanceToSqr(item);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = item;
                }
            }
            if (best != null) claim(best);
            return best;
        }

        private boolean available(ItemEntity item) {
            if (!isFetchable(item, dog.getOwner())) return false;
            var data = item.getPersistentData();
            return !data.hasUUID(CLAIM) || data.getUUID(CLAIM).equals(dog.getUUID())
                    || data.getLong(CLAIM_UNTIL) <= dog.level().getGameTime();
        }

        private void claim(ItemEntity item) {
            item.getPersistentData().putUUID(CLAIM, dog.getUUID());
            item.getPersistentData().putLong(CLAIM_UNTIL, dog.level().getGameTime() + 80);
        }

        private void releaseTarget() {
            if (target != null && target.getPersistentData().hasUUID(CLAIM)
                    && target.getPersistentData().getUUID(CLAIM).equals(dog.getUUID())) {
                target.getPersistentData().remove(CLAIM);
                target.getPersistentData().remove(CLAIM_UNTIL);
            }
        }

        private static boolean isFetchable(ItemEntity item, @Nullable LivingEntity owner) {
            if (!item.isAlive() || item.getItem().isEmpty() || item.getPersistentData().getBoolean(DELIVERED_TAG)) {
                return false;
            }
            ItemStack stack = item.getItem();
            if (CompanionDogRegistries.isFetchToy(stack)) {
                return true;
            }
            boolean thrownByOwner = owner != null && item.getOwner() == owner;
            return thrownByOwner && (stack.is(Items.STICK) || stack.is(Items.BONE));
        }

        @Override
        public void start() {
            dog.setAutoPose(CompanionDogPose.STAND, 0);
            resetProgress();
        }

        private void resetProgress() {
            repath = stalledTicks = 0;
            bestDistance = Double.MAX_VALUE;
        }

        private boolean stalled(double distance) {
            if (distance < bestDistance - 0.1) {
                bestDistance = distance;
                stalledTicks = 0;
            } else stalledTicks++;
            return stalledTicks >= 60;
        }

        private void skipTarget() {
            if (target != null) skippedUntil.put(target.getUUID(), dog.tickCount + 200);
            releaseTarget();
            target = findItem();
            dog.getNavigation().stop();
            resetProgress();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!ownerAvailable(dog) || dog.isRecovering() || dog.commandedPose.isResting()) {
                stop();
                return;
            }
            if (dog.isBusy() && dog.serverAction.freezesMovement()) {
                return;
            }
            if (dog.getMouthItem().isEmpty()) {
                if (target == null || !available(target)) {
                    releaseTarget();
                    target = findItem();
                    resetProgress();
                    if (target == null) return;
                }
                claim(target);
                dog.getLookControl().setLookAt(target, 30.0F, 30.0F);
                double distance = dog.distanceToSqr(target);
                if (distance < 1.3 * 1.3 && dog.hasLineOfSight(target)) {
                    ItemStack stack = target.getItem().copy();
                    dog.setMouthItem(stack.split(1));
                    if (stack.isEmpty()) {
                        target.discard();
                    } else {
                        target.setItem(stack);
                    }
                    dog.happyTicks = Math.max(dog.happyTicks, 200);
                    dog.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5F, 1.3F);
                    releaseTarget();
                    target = null;
                    dog.getNavigation().stop();
                    resetProgress();
                } else if (stalled(distance)) {
                    skipTarget();
                } else if (repath-- <= 0) {
                    repath = 8;
                    var path = dog.getNavigation().createPath(target, 0);
                    // Ground navigation cannot produce a path during a jump or the spawn landing.
                    if (path == null && !dog.onGround() && !dog.isInLiquid()) return;
                    // A useful partial path can reach farther balls over several replans.
                    if (path == null || !path.canReach() && path.getNodeCount() <= 1) skipTarget();
                    else dog.getNavigation().moveTo(path, 1.4);
                }
                return;
            }
            releaseTarget();
            target = null;
            LivingEntity owner = dog.getOwner();
            dog.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (dog.distanceToSqr(owner) < 1.9 * 1.9) {
                dog.getNavigation().stop();
                if (owner instanceof Player player) {
                    dog.deliverMouthItemTo(player);
                }
                dog.pantingTicks = Math.max(dog.pantingTicks, 120);
                dog.playAction(dog.getRandom().nextInt(3) == 0 ? CompanionDogAction.HEAD_TILT : CompanionDogAction.PLAY_BOW);
                target = null;
                resetProgress();
            } else if (stalled(dog.distanceToSqr(owner))) {
                if (owner instanceof net.minecraft.server.level.ServerPlayer player) dog.bringTo(player);
                deliveryRetryAfter = dog.tickCount + 40;
                dog.getNavigation().stop();
            } else if (repath-- <= 0) {
                repath = 10;
                dog.getNavigation().moveTo(owner, 1.3);
            }
        }

        @Override
        public void stop() {
            releaseTarget();
            target = null;
            dog.getNavigation().stop();
        }
    }

    /** A burst of happy running in loops around the owner. */
    static final class ZoomiesGoal extends Goal {
        private final CompanionDogEntity dog;
        private int repath;

        ZoomiesGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return dog.zoomiesTicks > 0 && !dog.commandedPose.isResting();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            dog.setAutoPose(CompanionDogPose.STAND, 0);
            repath = 0;
        }

        @Override
        public void tick() {
            dog.happyTicks = Math.max(dog.happyTicks, 40);
            if (dog.isBusy() && dog.serverAction.freezesMovement()) {
                return;
            }
            if (repath-- <= 0 || dog.getNavigation().isDone()) {
                repath = 18;
                LivingEntity owner = dog.getOwner();
                Vec3 centre = owner != null && owner.level() == dog.level() ? owner.position() : dog.position();
                double angle = dog.getRandom().nextDouble() * Math.PI * 2.0;
                double radius = 3.0 + dog.getRandom().nextDouble() * 3.5;
                dog.getNavigation().moveTo(centre.x + Math.cos(angle) * radius, centre.y,
                        centre.z + Math.sin(angle) * radius, 1.75);
            }
        }

        @Override
        public void stop() {
            dog.zoomiesTicks = 0;
            dog.pantingTicks = Math.max(dog.pantingTicks, 200);
            dog.getNavigation().stop();
            if (!dog.isBusy() && !dog.commandedPose.isResting()) {
                dog.playAction(CompanionDogAction.PLAY_BOW);
            }
        }
    }

    /** Vanilla following, switched off while CompanionDog is allowed to roam. */
    static final class FollowOwnerUnlessWanderingGoal extends FollowOwnerGoal {
        private final CompanionDogEntity dog;

        FollowOwnerUnlessWanderingGoal(CompanionDogEntity dog) {
            super(dog, 1.15, 6.0F, 2.0F);
            this.dog = dog;
        }

        @Override
        public boolean canUse() {
            return !dog.isWandering() && !dog.isBusy() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !dog.isWandering() && !dog.isBusy() && super.canContinueToUse();
        }

        @Override
        public void start() {
            dog.setAutoPose(CompanionDogPose.STAND, 0);
            super.start();
        }
    }

    /** Barks at monsters but never attacks. */
    static final class AlertBarkGoal extends Goal {
        private final CompanionDogEntity dog;
        @Nullable
        private Monster monster;
        private int barks;
        private int timer;

        AlertBarkGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!dog.isTame() || dog.tickCount % 10 != 0 || dog.commandedPose == CompanionDogPose.SLEEP) {
                return false;
            }
            List<Monster> monsters = dog.level().getEntitiesOfClass(Monster.class, dog.getBoundingBox().inflate(10.0, 4.0, 10.0),
                    m -> m.isAlive() && !m.isInvisible() && dog.hasLineOfSight(m));
            monster = monsters.isEmpty() ? null : monsters.getFirst();
            return monster != null;
        }

        @Override
        public boolean canContinueToUse() {
            return monster != null && monster.isAlive() && dog.distanceToSqr(monster) < 12 * 12
                    && dog.commandedPose != CompanionDogPose.SLEEP;
        }

        @Override
        public void start() {
            barks = 0;
            timer = 0;
            if (dog.autoPose == CompanionDogPose.SLEEP || dog.autoPose == CompanionDogPose.LIE) {
                dog.setAutoPose(CompanionDogPose.STAND, 0);
            }
        }

        @Override
        public void tick() {
            dog.alertTicks = 20;
            dog.getLookControl().setLookAt(monster, 30.0F, 30.0F);
            if (timer-- <= 0 && !dog.isBusy()) {
                if (barks < 4) {
                    dog.playAction(CompanionDogAction.BARK);
                    barks++;
                    timer = 30 + dog.getRandom().nextInt(20);
                } else {
                    dog.playSound(net.minecraft.sounds.SoundEvents.WOLF_GROWL, 0.35F, dog.getVoicePitch());
                    timer = 100 + dog.getRandom().nextInt(60);
                }
            }
        }

        @Override
        public void stop() {
            monster = null;
        }
    }

    /** Sits up, tilts its head or play-bows when someone nearby holds a treat or a ball. */
    static final class BegGoal extends Goal {
        private final CompanionDogEntity dog;
        @Nullable
        private Player player;
        private int timer;

        BegGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean interesting(Player candidate) {
            for (ItemStack stack : List.of(candidate.getMainHandItem(), candidate.getOffhandItem())) {
                if (dog.isFood(stack) || stack.is(Items.BONE) || stack.is(CompanionDogRegistries.TENNIS_BALL.get())) {
                    return true;
                }
            }
            return false;
        }

        private boolean holdsBall(Player candidate) {
            return candidate.getMainHandItem().is(CompanionDogRegistries.TENNIS_BALL.get())
                    || candidate.getOffhandItem().is(CompanionDogRegistries.TENNIS_BALL.get());
        }

        @Override
        public boolean canUse() {
            if (dog.commandedPose == CompanionDogPose.SLEEP || dog.commandedPose == CompanionDogPose.BELLY
                    || !dog.getMouthItem().isEmpty()) {
                return false;
            }
            player = dog.level().getNearestPlayer(dog, 6.0);
            return player != null && !player.isSpectator() && interesting(player);
        }

        @Override
        public boolean canContinueToUse() {
            return player != null && player.isAlive() && dog.distanceToSqr(player) < 7 * 7 && interesting(player)
                    && dog.getMouthItem().isEmpty() && dog.commandedPose != CompanionDogPose.SLEEP;
        }

        @Override
        public void start() {
            dog.getNavigation().stop();
            if (dog.autoPose == CompanionDogPose.SLEEP || dog.autoPose == CompanionDogPose.LIE) {
                dog.setAutoPose(CompanionDogPose.STAND, 0);
            }
            timer = 20 + dog.getRandom().nextInt(30);
        }

        @Override
        public void tick() {
            dog.begTicks = 10;
            dog.happyTicks = Math.max(dog.happyTicks, 10);
            dog.getLookControl().setLookAt(player, 40.0F, 40.0F);
            if (timer-- > 0 || dog.isBusy()) {
                return;
            }
            timer = 60 + dog.getRandom().nextInt(60);
            int roll = dog.getRandom().nextInt(4);
            if (holdsBall(player)) {
                dog.playAction(roll == 0 ? CompanionDogAction.HEAD_TILT : roll == 1 ? CompanionDogAction.BARK : CompanionDogAction.PLAY_BOW);
            } else if (dog.commandedPose == CompanionDogPose.LIE) {
                dog.playAction(CompanionDogAction.HEAD_TILT);
            } else {
                dog.playAction(roll == 0 ? CompanionDogAction.HEAD_TILT : roll == 1 ? CompanionDogAction.PAW : CompanionDogAction.BEG);
            }
        }

        @Override
        public void stop() {
            player = null;
            dog.begTicks = 0;
        }
    }

    /** Holds an autonomous sit, lie-down or nap chosen by the idle behaviour. */
    static final class AutoRestGoal extends Goal {
        private final CompanionDogEntity dog;

        AutoRestGoal(CompanionDogEntity dog) {
            this.dog = dog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return dog.autoPose.isResting() && !dog.commandedPose.isResting();
        }

        @Override
        public void start() {
            dog.getNavigation().stop();
        }

        @Override
        public void stop() {
            if (dog.autoPose.isResting() && !dog.commandedPose.isResting()) {
                dog.setAutoPose(CompanionDogPose.STAND, 0);
            }
        }
    }

    /** Little things a puppy does when nothing is going on. */
    static final class IdleBehaviourGoal extends Goal {
        private final CompanionDogEntity dog;

        IdleBehaviourGoal(CompanionDogEntity dog) {
            this.dog = dog;
            // Idle expressions must also run while HoldStill/AutoRest owns movement.
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            return !dog.isBusy() && dog.onGround() && !dog.isInWater() && dog.getNavigation().isDone()
                    && dog.getCompanionDogPose() != CompanionDogPose.SLEEP
                    && dog.getMouthItem().isEmpty() && dog.getRandom().nextInt(160) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            CompanionDogPose pose = dog.getCompanionDogPose();
            var random = dog.getRandom();
            if (dog.commandedPose.isResting() || dog.autoPose.isResting()) {
                if (pose == CompanionDogPose.SLEEP) {
                    return;
                }
                int roll = random.nextInt(pose == CompanionDogPose.SIT ? 4 : 3);
                dog.playAction(switch (roll) {
                    case 0 -> CompanionDogAction.YAWN;
                    case 1 -> CompanionDogAction.HEAD_TILT;
                    case 2 -> pose == CompanionDogPose.BELLY ? CompanionDogAction.YAWN : CompanionDogAction.BARK;
                    default -> CompanionDogAction.SCRATCH;
                });
                return;
            }
            boolean night = dog.level().isNight();
            int roll = random.nextInt(night ? 26 : 22);
            if (roll < 3) {
                dog.playAction(CompanionDogAction.SNIFF);
            } else if (roll < 5) {
                dog.playAction(CompanionDogAction.DIG);
            } else if (roll < 7) {
                dog.playAction(CompanionDogAction.SCRATCH);
            } else if (roll < 8) {
                dog.playAction(CompanionDogAction.YAWN);
            } else if (roll < 10) {
                dog.playAction(CompanionDogAction.HEAD_TILT);
            } else if (roll < 11) {
                dog.playAction(CompanionDogAction.STRETCH);
            } else if (roll < 12) {
                dog.playAction(CompanionDogAction.SPIN);
            } else if (roll < 15) {
                dog.setAutoPose(CompanionDogPose.SIT, 200 + random.nextInt(300));
            } else if (roll < 18) {
                dog.setAutoPose(CompanionDogPose.LIE, 300 + random.nextInt(600));
            } else if (roll < 19) {
                dog.setAutoPose(CompanionDogPose.BELLY, 120 + random.nextInt(160));
            } else if (roll < 20 && dog.isTame()) {
                dog.zoomiesTicks = 100;
            } else {
                // Short daytime nap, long sleep at night.
                dog.setAutoPose(CompanionDogPose.SLEEP, night ? 2400 + random.nextInt(2400) : 400 + random.nextInt(400));
            }
        }
    }
}
