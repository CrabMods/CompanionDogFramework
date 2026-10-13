package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.crabmods.companiondog.world.CompanionDogBindings;
import com.crabmods.companiondog.world.CompanionDogSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * CompanionDog, a small ginger puppy. Everything the dog does on the server is expressed as a held
 * {@link CompanionDogPose} plus at most one one-shot {@link CompanionDogAction}; the client model turns
 * those into animation.
 */
public class CompanionDogEntity extends TamableAnimal {
    /** Set by the client entry point; opens the interaction menu. */
    public static Consumer<CompanionDogEntity> openMenuHook = entity -> {
    };

    public static final int MOOD_BEGGING = 1;
    public static final int MOOD_PANTING = 2;
    public static final int MOOD_HAPPY = 4;
    public static final int MOOD_WANDER = 8;
    public static final int MOOD_ALERT = 16;

    private static final int ACTION_BITS = 5;
    private static final int ACTION_MASK = (1 << ACTION_BITS) - 1;
    static final int GREET_AFTER_TICKS = 1200;

    private static final EntityDataAccessor<Integer> DATA_COMPANION_DOG_POSE =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.INT);
    /** (serial << 5) | action ordinal, so a repeated action still restarts on the client. */
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLLAR =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_MOUTH =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_MOOD =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REST_TICKS =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING =
            SynchedEntityData.defineId(CompanionDogEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int RECOVERY_TICKS = 2400;
    private static final TicketType<UUID> HOME_TICKET = TicketType.create("companiondog_home", UUID::compareTo, 100);

    // ---- server state
    CompanionDogPose commandedPose = CompanionDogPose.STAND;
    CompanionDogPose autoPose = CompanionDogPose.STAND;
    int autoPoseTicks;
    CompanionDogAction serverAction = CompanionDogAction.NONE;
    private int actionAge;
    private int actionSerial;
    int happyTicks;
    int pantingTicks;
    int alertTicks;
    int begTicks;
    int zoomiesTicks;
    int ownerAwayTicks = GREET_AFTER_TICKS * 2;
    private int runningTicks;
    private boolean bound;
    private boolean wet;
    private ItemStack lastFood = ItemStack.EMPTY;
    @Nullable private GlobalPos home;
    private long restUntil;
    private long bindingEpoch;
    private boolean needsRest;
    private boolean healthRetreat;
    private int healingDelay;
    private int returnAttempt;
    private int returnBackoff = 40;
    private boolean waitingNotice;
    private Vec3 waitingPosition;

    // ---- client state
    private CompanionDogAction clientAction = CompanionDogAction.NONE;
    private int clientActionStart;
    private final float[] poseWeights = new float[CompanionDogPose.values().length];
    private final float[] poseWeightsO = new float[CompanionDogPose.values().length];
    private boolean poseWeightsInitialised;
    @Nullable
    private CompanionDogPose debugPose;
    @Nullable
    private CompanionDogAction debugAction;
    private float debugProgress = -1.0F;

    public CompanionDogEntity(EntityType<? extends CompanionDogEntity> type, Level level) {
        super(type, level);
        this.setTame(false, false);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COMPANION_DOG_POSE, CompanionDogPose.STAND.ordinal());
        builder.define(DATA_ACTION, 0);
        builder.define(DATA_COLLAR, DyeColor.RED.getId());
        builder.define(DATA_MOUTH, ItemStack.EMPTY);
        builder.define(DATA_MOOD, (byte) 0);
        builder.define(DATA_REST_TICKS, 0);
        builder.define(DATA_RETURNING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CompanionDogGoals.GreetOwnerGoal(this));
        goalSelector.addGoal(2, new CompanionDogGoals.HoldStillGoal(this));
        goalSelector.addGoal(3, new CompanionDogGoals.FetchGoal(this));
        goalSelector.addGoal(4, new CompanionDogGoals.ZoomiesGoal(this));
        goalSelector.addGoal(5, new CompanionDogGoals.FollowOwnerUnlessWanderingGoal(this));
        goalSelector.addGoal(6, new CompanionDogGoals.AlertBarkGoal(this));
        goalSelector.addGoal(6, new CompanionDogGoals.BegGoal(this));
        goalSelector.addGoal(7, new CompanionDogGoals.AutoRestGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new CompanionDogGoals.IdleBehaviourGoal(this));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ synced accessors

    public CompanionDogPose getCompanionDogPose() {
        return CompanionDogPose.byId(entityData.get(DATA_COMPANION_DOG_POSE));
    }

    public CompanionDogPose getCommandedPose() {
        return commandedPose;
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId(entityData.get(DATA_COLLAR));
    }

    public void setCollarColor(DyeColor color) {
        entityData.set(DATA_COLLAR, color.getId());
    }

    public ItemStack getMouthItem() {
        return entityData.get(DATA_MOUTH);
    }

    public void setMouthItem(ItemStack stack) {
        entityData.set(DATA_MOUTH, stack.copy());
    }

    public boolean hasMood(int flag) {
        return (entityData.get(DATA_MOOD) & flag) != 0;
    }

    private void setMood(int flag, boolean value) {
        byte current = entityData.get(DATA_MOOD);
        byte next = (byte) (value ? current | flag : current & ~flag);
        if (next != current) {
            entityData.set(DATA_MOOD, next);
        }
    }

    public boolean isWandering() {
        return hasMood(MOOD_WANDER);
    }

    public void setWandering(boolean wandering) {
        setMood(MOOD_WANDER, wandering);
    }

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public CompanionDogAction getServerAction() {
        return serverAction;
    }

    // ------------------------------------------------------------------ actions

    /** Starts a one-shot action on the server. Returns false if the dog cannot do it right now. */
    public boolean playAction(CompanionDogAction action) {
        if (level().isClientSide || action == CompanionDogAction.NONE) {
            return false;
        }
        if (isReturningHome() || isRecovering() && action != CompanionDogAction.PET
                && action != CompanionDogAction.EAT && action != CompanionDogAction.HEAD_TILT
                && action != CompanionDogAction.YAWN) return false;
        if (action == CompanionDogAction.BELLY_RUB && getCompanionDogPose() != CompanionDogPose.BELLY) {
            return false;
        }
        serverAction = action;
        actionAge = 0;
        actionSerial = (actionSerial + 1) & 0xFFFFF;
        entityData.set(DATA_ACTION, (actionSerial << ACTION_BITS) | action.ordinal());
        if (action.freezesMovement()) {
            getNavigation().stop();
        }
        updateEffectivePose();
        return true;
    }

    private void finishAction() {
        serverAction = CompanionDogAction.NONE;
        actionAge = 0;
        entityData.set(DATA_ACTION, actionSerial << ACTION_BITS);
        updateEffectivePose();
    }

    public boolean isBusy() {
        return serverAction != CompanionDogAction.NONE;
    }

    public boolean isZooming() {
        return zoomiesTicks > 0;
    }

    public void setCommandedPose(CompanionDogPose pose) {
        if (isRecovering() && pose != CompanionDogPose.LIE) return;
        commandedPose = pose;
        autoPose = CompanionDogPose.STAND;
        autoPoseTicks = 0;
        zoomiesTicks = 0;
        if (serverAction != CompanionDogAction.NONE) {
            finishAction();
        }
        getNavigation().stop();
        updateEffectivePose();
    }

    void setAutoPose(CompanionDogPose pose, int ticks) {
        autoPose = pose;
        autoPoseTicks = ticks;
        updateEffectivePose();
    }

    private CompanionDogPose computeEffectivePose() {
        if (serverAction.requiredPose() != null) {
            return serverAction.requiredPose();
        }
        if (commandedPose.isResting()) {
            return commandedPose;
        }
        return autoPose;
    }

    private void updateEffectivePose() {
        CompanionDogPose effective = computeEffectivePose();
        if (effective != getCompanionDogPose()) {
            entityData.set(DATA_COMPANION_DOG_POSE, effective.ordinal());
        }
        setOrderedToSit(commandedPose.isResting());
        setInSittingPose(effective.isResting());
    }

    public void applyCommand(CompanionDogCommand command, Player player) {
        if (level().isClientSide || !player.isAlive() || player.isSpectator() || isRemoved()) return;
        if (command == CompanionDogCommand.ARCHIVE_UNOWNED || command == CompanionDogCommand.ADOPT_UNOWNED) {
            if (!canManageUnowned(player) || !player.getMainHandItem().is(Items.BONE)) return;
            if (command == CompanionDogCommand.ARCHIVE_UNOWNED) {
                kill();
                player.displayClientMessage(Component.translatable("message.companiondog.archived_unowned"), true);
            } else adopt(player, player.getMainHandItem());
            return;
        }
        if (!isOwnedBy(player)) return;
        if (command == CompanionDogCommand.SET_HOME && player instanceof ServerPlayer owner) {
            if (isBound()) CompanionDogBindings.get(owner.getServer()).setHome(owner);
            else setHomeFrom(owner);
            return;
        }
        if (command == CompanionDogCommand.GO_HOME) {
            requestHome(false);
            return;
        }
        if (command == CompanionDogCommand.END_REST) {
            if (isReturningHome()) {
                player.displayClientMessage(Component.translatable("message.companiondog.waiting_home"), true);
                return;
            }
            if (!isRecovering()) return;
            restUntil = 0;
            needsRest = false;
            healthRetreat = false;
            entityData.set(DATA_REST_TICKS, 0);
            setCommandedPose(CompanionDogPose.STAND);
            playAction(CompanionDogAction.STRETCH);
            if (level() instanceof ServerLevel server) CompanionDogBindings.get(server.getServer()).checkpoint(this);
            player.displayClientMessage(Component.translatable("message.companiondog.rest_ended"), true);
            return;
        }
        if (isRecovering()) {
            player.displayClientMessage(Component.translatable("message.companiondog.resting", restSeconds()), true);
            return;
        }
        switch (command) {
            case SIT -> setCommandedPose(CompanionDogPose.SIT);
            case LIE_DOWN -> setCommandedPose(CompanionDogPose.LIE);
            case SLEEP -> setCommandedPose(CompanionDogPose.SLEEP);
            case BELLY_UP -> setCommandedPose(CompanionDogPose.BELLY);
            case STAND_UP -> {
                boolean wasAsleep = getCompanionDogPose() == CompanionDogPose.SLEEP;
                setCommandedPose(CompanionDogPose.STAND);
                playAction(wasAsleep ? CompanionDogAction.STRETCH : CompanionDogAction.SHAKE);
            }
            case TOGGLE_WANDER -> {
                setWandering(!isWandering());
                player.displayClientMessage(Component.translatable(isWandering()
                        ? "message.companiondog.wander_on" : "message.companiondog.wander_off"), true);
            }
            case ZOOMIES -> {
                if (commandedPose.isResting()) {
                    setCommandedPose(CompanionDogPose.STAND);
                }
                zoomiesTicks = 140;
            }
            default -> {
                CompanionDogAction action = command.action();
                if (action != null) {
                    if (action.requiredPose() == CompanionDogPose.STAND && commandedPose == CompanionDogPose.SLEEP) {
                        setCommandedPose(CompanionDogPose.STAND);
                    }
                    playAction(action);
                }
            }
        }
        happyTicks = Math.max(happyTicks, 80);
    }

    // ------------------------------------------------------------------ server tick

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && !isRemoved()) {
            serverTick();
        }
    }

    private void serverTick() {
        if (!lifecycleTick()) return;
        if (healingDelay > 0) healingDelay--;
        else if (getHealth() < getMaxHealth() && tickCount % 40 == 0) heal(1.0F);
        if (serverAction != CompanionDogAction.NONE) {
            actionAge++;
            onActionTick(serverAction, actionAge);
            if (actionAge >= serverAction.durationTicks()) {
                finishAction();
            }
        }
        if (happyTicks > 0) happyTicks--;
        if (pantingTicks > 0) pantingTicks--;
        if (alertTicks > 0) alertTicks--;
        if (begTicks > 0) begTicks--;
        if (zoomiesTicks > 0) zoomiesTicks--;
        if (autoPoseTicks > 0 && --autoPoseTicks == 0 && autoPose.isResting()) {
            wakeFromAutoPose();
        }

        // Shake off water the way a real puppy does.
        if (isInWaterRainOrBubble()) {
            wet = true;
        } else if (wet && onGround() && serverAction == CompanionDogAction.NONE && !commandedPose.isResting()) {
            wet = false;
            autoPose = CompanionDogPose.STAND;
            playAction(CompanionDogAction.SHAKE);
        }

        // Running for a while makes the puppy pant.
        if (getDeltaMovement().horizontalDistanceSqr() > 0.012) {
            if (++runningTicks > 50) {
                pantingTicks = Math.max(pantingTicks, 120);
            }
        } else {
            runningTicks = Math.max(0, runningTicks - 2);
        }

        LivingEntity owner = getOwner();
        boolean ownerNear = owner != null && owner.level() == level() && distanceToSqr(owner) < 20 * 20;
        if (!ownerNear) {
            ownerAwayTicks++;
        } else if (ownerAwayTicks > GREET_AFTER_TICKS && commandedPose.isResting() && distanceToSqr(owner) < 10 * 10) {
            // Told to stay: greet from where it is instead of running over.
            ownerAwayTicks = 0;
            happyTicks = 160;
            if (commandedPose != CompanionDogPose.SLEEP) {
                playAction(CompanionDogAction.BARK);
            }
        }

        // Sleep when the owner sleeps; wake up when morning comes.
        if (owner instanceof Player player && ownerNear && player.isSleeping()
                && !commandedPose.isResting() && autoPose != CompanionDogPose.SLEEP && serverAction == CompanionDogAction.NONE) {
            setAutoPose(CompanionDogPose.SLEEP, 2400);
        }
        if (autoPose == CompanionDogPose.SLEEP && level().isDay() && tickCount % 20 == 0
                && !(owner instanceof Player player && player.isSleeping()) && random.nextInt(6) == 0) {
            wakeFromAutoPose();
        }
        if (autoPose.isResting() && owner != null && !isWandering()
                && (owner.level() != level() || distanceToSqr(owner) > 9 * 9)) {
            setAutoPose(CompanionDogPose.STAND, 0);
        }

        updateEffectivePose();
        setMood(MOOD_HAPPY, happyTicks > 0);
        setMood(MOOD_PANTING, pantingTicks > 0);
        setMood(MOOD_ALERT, alertTicks > 0);
        setMood(MOOD_BEGGING, begTicks > 0);

        if (bound && tickCount % 40 == 0 && level() instanceof ServerLevel serverLevel) {
            CompanionDogBindings.get(serverLevel.getServer()).checkpoint(this);
        }
    }

    void wakeFromAutoPose() {
        boolean wasAsleep = autoPose == CompanionDogPose.SLEEP;
        setAutoPose(CompanionDogPose.STAND, 0);
        if (serverAction == CompanionDogAction.NONE) {
            playAction(wasAsleep ? CompanionDogAction.STRETCH : CompanionDogAction.YAWN);
        }
    }

    private void onActionTick(CompanionDogAction action, int age) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        switch (action) {
            case PET -> {
                if (age == 10 || age == 30) hearts(server, 2);
                if (age == 22) playSound(SoundEvents.WOLF_PANT, 0.45F, getVoicePitch());
            }
            case BELLY_RUB -> {
                if (age % 14 == 6) hearts(server, 2);
                if (age == 4) playSound(SoundEvents.WOLF_PANT, 0.5F, getVoicePitch());
            }
            case PAW -> {
                if (age == 42) hearts(server, 3);
            }
            case BEG -> {
                if (age == 10 || age == 40) playSound(SoundEvents.WOLF_WHINE, 0.4F, getVoicePitch() + 0.1F);
            }
            case BARK -> {
                if (age == 2 || age == 8) bark();
            }
            case PLAY_BOW -> {
                if (age == 14) bark();
            }
            case HOWL -> {
                if (age == 8) playSound(SoundEvents.WOLF_HOWL, 0.6F, getVoicePitch() + 0.1F);
            }
            case SHAKE -> {
                if (age == 1) playSound(SoundEvents.WOLF_SHAKE, 0.7F, getVoicePitch());
                if (age > 6 && age < 30 && age % 3 == 0) {
                    server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.55, getZ(), 6, 0.25, 0.1, 0.25, 0.0);
                }
            }
            case JUMP -> {
                if (age == 2 && onGround()) {
                    Vec3 motion = getDeltaMovement();
                    setDeltaMovement(motion.x, 0.42, motion.z);
                    hasImpulse = true;
                }
            }
            case SNIFF -> {
                if (age % 16 == 4) playSound(SoundEvents.SNIFFER_SNIFFING, 0.3F, 2.0F);
            }
            case DIG -> {
                if (age >= 8 && age <= 48 && age % 5 == 0) {
                    BlockState ground = level().getBlockState(blockPosition().below());
                    if (ground.isAir()) {
                        ground = Blocks.DIRT.defaultBlockState();
                    }
                    Vec3 front = frontOffset(0.45);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            front.x, getY() + 0.1, front.z, 6, 0.12, 0.05, 0.12, 0.1);
                    playSound(ground.getSoundType().getHitSound(), 0.4F, 1.2F);
                }
            }
            case YAWN -> {
                if (age == 8) playSound(SoundEvents.WOLF_WHINE, 0.3F, getVoicePitch() + 0.25F);
            }
            case EAT -> {
                if (age >= 6 && age <= 34 && age % 6 == 0) {
                    playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.25F + random.nextFloat() * 0.2F);
                    if (!lastFood.isEmpty()) {
                        Vec3 mouth = frontOffset(0.4);
                        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, lastFood),
                                mouth.x, getY() + 0.25, mouth.z, 4, 0.08, 0.05, 0.08, 0.05);
                    }
                }
                if (age == 40) hearts(server, 3);
            }
            case ROLL_OVER -> {
                if (age == 36) hearts(server, 2);
            }
            case SPIN -> {
                if (age == 6) bark();
            }
            case HEAD_TILT -> {
                if (age == 6) playSound(SoundEvents.WOLF_WHINE, 0.25F, getVoicePitch() + 0.2F);
            }
            default -> {
            }
        }
    }

    Vec3 frontOffset(double distance) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * distance, getY(), getZ() + Mth.cos(yaw) * distance);
    }

    void hearts(ServerLevel server, int count) {
        server.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.85, getZ(), count, 0.25, 0.12, 0.25, 0.0);
    }

    void bark() {
        playSound(SoundEvents.WOLF_AMBIENT, 0.7F, getVoicePitch() + 0.1F);
    }

    // ------------------------------------------------------------------ fetching

    /** Called by a flying fetch toy that touched the dog. */
    public boolean tryCatch(ItemStack stack) {
        if (isRecovering() || !getMouthItem().isEmpty() || commandedPose.isResting() || getCompanionDogPose().isResting()) {
            return false;
        }
        setMouthItem(stack);
        happyTicks = Math.max(happyTicks, 160);
        playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.3F);
        if (serverAction == CompanionDogAction.NONE) {
            playAction(CompanionDogAction.JUMP);
        }
        return true;
    }

    void deliverMouthItemTo(Player player) {
        ItemStack stack = getMouthItem();
        if (stack.isEmpty()) {
            return;
        }
        setMouthItem(ItemStack.EMPTY);
        ItemEntity drop = new ItemEntity(level(), player.getX(), player.getY() + 0.3, player.getZ(), stack);
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setNoPickUpDelay();
        drop.getPersistentData().putBoolean(CompanionDogGoals.DELIVERED_TAG, true);
        level().addFreshEntity(drop);
        playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.1F);
        happyTicks = Math.max(happyTicks, 160);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isReturningHome()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        boolean owner = isOwnedBy(player);
        if (stack.is(Items.BONE) && hand == InteractionHand.MAIN_HAND && canManageUnowned(player)) {
            if (level().isClientSide) openMenuHook.accept(this);
            return InteractionResult.SUCCESS;
        }
        if (isTame() && stack.is(Items.BONE)) {
            if (!owner || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (level().isClientSide) openMenuHook.accept(this);
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide) {
            boolean handled = (stack.isEmpty() && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive()
                    && (getMouthItem().isEmpty() || !isTame() || owner))
                    || isFood(stack) || stack.is(Items.BONE)
                    || (owner && stack.getItem() instanceof DyeItem);
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        if (!isTame() && (stack.is(Items.BONE) || isFood(stack))) {
            adopt(player, stack);
            return InteractionResult.SUCCESS;
        }
        if (owner && stack.getItem() instanceof DyeItem dye) {
            if (dye.getDyeColor() != getCollarColor()) {
                setCollarColor(dye.getDyeColor());
                stack.consume(1, player);
                playAction(CompanionDogAction.HEAD_TILT);
            }
            return InteractionResult.SUCCESS;
        }
        if (isFood(stack)) {
            lastFood = stack.copyWithCount(1);
            stack.consume(1, player);
            heal(4.0F);
            happyTicks = Math.max(happyTicks, 200);
            playAction(CompanionDogAction.EAT);
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (player.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            ItemStack held = getMouthItem();
            if (!held.isEmpty()) {
                if (isTame() && !owner) return InteractionResult.PASS;
                setMouthItem(ItemStack.EMPTY);
                player.setItemInHand(hand, held);
                happyTicks = Math.max(happyTicks, 120);
                playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
                return InteractionResult.SUCCESS;
            }
            CompanionDogPose pose = getCompanionDogPose();
            if (pose == CompanionDogPose.SLEEP) {
                if (commandedPose == CompanionDogPose.SLEEP) {
                    setCommandedPose(CompanionDogPose.STAND);
                    playAction(CompanionDogAction.STRETCH);
                } else {
                    wakeFromAutoPose();
                }
            } else if (pose == CompanionDogPose.BELLY) {
                playAction(CompanionDogAction.BELLY_RUB);
            } else {
                playAction(CompanionDogAction.PET);
            }
            happyTicks = Math.max(happyTicks, 160);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public boolean canManageUnowned(Player player) {
        return player.hasPermissions(2) && getOwnerUUID() == null && !isBound() && !isReturningHome();
    }

    private void adopt(Player player, ItemStack stack) {
        stack.consume(1, player);
        tame(player);
        if (player instanceof ServerPlayer serverPlayer) setHomeFrom(serverPlayer);
        setCommandedPose(CompanionDogPose.STAND);
        level().broadcastEntityEvent(this, (byte) 7);
        happyTicks = 200;
        playAction(CompanionDogAction.SPIN);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.WOLF_FOOD);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        return false;
    }

    // ------------------------------------------------------------------ companion lifecycle

    @Nullable
    public GlobalPos getHome() { return home; }

    public void setHome(GlobalPos value) {
        home = value;
        returnAttempt = 0;
        returnBackoff = 40;
        waitingNotice = false;
    }

    public long getBindingEpoch() { return bindingEpoch; }
    public void setBindingEpoch(long value) { bindingEpoch = value; }
    public boolean isReturningHome() { return entityData.get(DATA_RETURNING); }
    private int remainingRestTicks() {
        return level().isClientSide ? entityData.get(DATA_REST_TICKS)
                : (int) Math.max(0, Math.min(RECOVERY_TICKS, restUntil - lifecycleTime()));
    }
    public boolean isRecovering() { return isReturningHome() || remainingRestTicks() > 0; }
    public int restSeconds() { return (remainingRestTicks() + 19) / 20; }
    public long getRestUntil() { return restUntil; }

    public boolean setHomeFrom(ServerPlayer owner) {
        Vec3 safe = CompanionDogSafety.find(owner.serverLevel(), this, owner.blockPosition());
        if (safe == null) {
            owner.displayClientMessage(Component.translatable("message.companiondog.unsafe"), true);
            return false;
        }
        setHome(GlobalPos.of(owner.level().dimension(), BlockPos.containing(safe)));
        owner.displayClientMessage(Component.translatable("message.companiondog.home_set"), true);
        return true;
    }

    private long lifecycleTime() {
        return level() instanceof ServerLevel server ? server.getServer().overworld().getGameTime() : 0;
    }

    /** Lethal danger changes the companion lifecycle without entering the death path. */
    public void requestHome(boolean danger) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        if (isReturningHome()) {
            needsRest |= danger && restUntil <= lifecycleTime();
            return;
        }
        needsRest = danger && restUntil <= lifecycleTime();
        entityData.set(DATA_RETURNING, true);
        waitingPosition = position();
        returnAttempt = 0;
        returnBackoff = 40;
        waitingNotice = false;
        getNavigation().stop();
        finishAction();
        zoomiesTicks = 0;
        setWandering(false);
        commandedPose = CompanionDogPose.LIE;
        autoPose = CompanionDogPose.STAND;
        autoPoseTicks = 0;
        updateEffectivePose();
        stopRiding();
        ejectPassengers();
        if (isLeashed()) dropLeash(true, true);
        clearFire();
        setTicksFrozen(0);
        setAirSupply(getMaxAirSupply());
        removeAllEffects();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        CompanionDogBindings.get(server.getServer()).checkpoint(this);
        if (getOwner() instanceof ServerPlayer owner) {
            owner.displayClientMessage(Component.translatable(danger
                    ? "message.companiondog.safe_return" : "message.companiondog.going_home"), true);
        }
    }

    private boolean lifecycleTick() {
        ServerLevel server = (ServerLevel) level();
        CompanionDogBindings bindings = CompanionDogBindings.get(server.getServer());
        if (bound && !bindings.acceptIdentity(this)) {
            discard();
            return false;
        }
        if (bound && getOwnerUUID() != null && bindings.homeOf(getOwnerUUID()) != null) {
            home = bindings.homeOf(getOwnerUUID());
        }
        if (bound && getOwnerUUID() != null) {
            Boolean queued = bindings.takeHomeRequest(getOwnerUUID());
            if (queued != null) requestHome(queued);
        }
        if (home == null && onGround()) {
            Vec3 safe = CompanionDogSafety.find(server, this, blockPosition());
            if (safe != null) {
                home = GlobalPos.of(level().dimension(), BlockPos.containing(safe));
                if (bound) bindings.checkpoint(this);
            }
        }
        entityData.set(DATA_REST_TICKS, (int) Math.max(0, Math.min(RECOVERY_TICKS, restUntil - lifecycleTime())));
        if (healthRetreat) {
            healthRetreat = false;
            requestHome(true);
        }
        if (!isReturningHome()) return true;

        if (waitingPosition == null) waitingPosition = position();
        setPos(waitingPosition);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        clearFire();
        if (returnAttempt-- > 0) return false;
        returnAttempt = returnBackoff;
        returnBackoff = Math.min(1200, Math.max(200, returnBackoff * 2));
        if (home == null) return waitingForHome(server);
        ServerLevel destination = server.getServer().getLevel(home.dimension());
        if (destination == null) return waitingForHome(server);
        destination.getChunkSource().addRegionTicket(HOME_TICKET, new ChunkPos(home.pos()), 2, getUUID());
        destination.getChunk(home.pos());
        Vec3 safe = CompanionDogSafety.find(destination, this, home.pos());
        if (safe == null) return waitingForHome(server);
        // Serialize the arrived state into a cross-dimension transfer, never a second live copy.
        restUntil = needsRest ? lifecycleTime() + RECOVERY_TICKS : restUntil;
        entityData.set(DATA_REST_TICKS, (int) Math.max(0, restUntil - lifecycleTime()));
        entityData.set(DATA_RETURNING, false);
        needsRest = false;
        waitingPosition = null;
        CompanionDogEntity arrived = moveSafely(destination, safe, getYRot());
        if (arrived == null) {
            entityData.set(DATA_RETURNING, true);
            waitingPosition = position();
        } else {
            if (arrived.isRecovering()) arrived.setHealth(arrived.getMaxHealth());
            arrived.commandedPose = CompanionDogPose.LIE;
            arrived.updateEffectivePose();
            bindings.checkpoint(arrived);
        }
        return false;
    }

    private boolean waitingForHome(ServerLevel server) {
        if (!waitingNotice && getOwnerUUID() != null) {
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(getOwnerUUID());
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("message.companiondog.waiting_home"), true);
                waitingNotice = true;
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isRecovering() || !(amount > 0)
                || isInvulnerableTo(source)
                || source.is(DamageTypeTags.IS_FIRE) && hasEffect(MobEffects.FIRE_RESISTANCE)) return false;
        // Keep NeoForge damage reductions/cancellation, but never emit hurt/death visuals or knockback.
        DamageContainer damage = new DamageContainer(source, Math.min(amount, Float.MAX_VALUE));
        damageContainers.push(damage);
        try {
            if (CommonHooks.onEntityIncomingDamage(this, damage)) return false;
            float incoming = damage.getNewDamage();
            if (!(incoming > 0)) return false;
            if (invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (incoming <= lastHurt) return false;
                damage.setNewDamage(incoming - lastHurt);
            } else {
                invulnerableTime = damage.getPostAttackInvulnerabilityTicks();
            }
            lastHurt = incoming;
            float before = getHealth();
            super.actuallyHurt(source, damage.getNewDamage());
            if (healthRetreat) {
                healthRetreat = false;
                requestHome(true);
            } else if (getHealth() < before) {
                healingDelay = 100;
            }
        } finally {
            damageContainers.pop();
        }
        return false;
    }

    @Override
    public void die(DamageSource source) { requestHome(true); }

    @Override
    public void kill() {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        CompanionDogBindings.get(server.getServer()).release(this);
        discard();
    }

    @Override
    public void setHealth(float health) {
        if (!(health > 0)) {
            healthRetreat = true;
            super.setHealth(getMaxHealth());
        } else super.setHealth(health);
    }

    @Override
    public boolean canBeHitByProjectile() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPickable() { return !isReturningHome() && super.isPickable(); }

    @Override
    public boolean isInvisible() { return isReturningHome() || super.isInvisible(); }

    @Override
    public void push(Entity other) {}

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected boolean isImmobile() { return isRecovering() || super.isImmobile(); }

    @Override
    protected void onBelowWorld() {
        requestHome(true);
    }

    /** Moves the dog next to its owner, across dimensions if needed. Returns the dog that arrived. */
    @Nullable
    public CompanionDogEntity bringTo(ServerPlayer owner) {
        if (!owner.isAlive() || owner.isSpectator() || !owner.getUUID().equals(getOwnerUUID()) || isRemoved()) return null;
        CompanionDogBindings bindings = CompanionDogBindings.get(owner.getServer());
        Boolean queued = isBound() ? bindings.takeHomeRequest(owner.getUUID()) : null;
        if (Boolean.TRUE.equals(queued) || healthRetreat) {
            healthRetreat = false;
            requestHome(true);
        }
        if (needsRest || remainingRestTicks() > 0) {
            owner.displayClientMessage(Component.translatable(isReturningHome()
                    ? "message.companiondog.waiting_home" : "message.companiondog.resting", restSeconds()), true);
            return null;
        }
        Vec3 target = CompanionDogSafety.findForOwner(owner, this);
        if (target == null) {
            owner.displayClientMessage(Component.translatable("message.companiondog.unsafe"), true);
            return null;
        }
        // An explicit recall supersedes an older logout/go-home request, even before the first loaded tick.
        entityData.set(DATA_RETURNING, false);
        entityData.set(DATA_REST_TICKS, 0);
        waitingPosition = null;
        returnAttempt = 0;
        removeEffect(MobEffects.INVISIBILITY);
        setInvisible(false);
        return moveSafely(owner.serverLevel(), target, owner.getYRot() + 180);
    }

    @Nullable
    private CompanionDogEntity moveSafely(ServerLevel destination, Vec3 target, float facing) {
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
        clearFire();
        setTicksFrozen(0);
        setAirSupply(getMaxAirSupply());
        getNavigation().stop();
        stopRiding();
        ejectPassengers();
        if (isLeashed()) dropLeash(true, true);
        if (destination == level()) {
            moveTo(target.x, target.y, target.z, facing, 0.0F);
            setYHeadRot(facing);
            setYBodyRot(facing);
            hasImpulse = true;
            // Re-pair all viewers from the new absolute position and full metadata.
            // A teleport alone cannot repair a viewer that has lost its tracked client entity.
            destination.getChunkSource().removeEntity(this);
            destination.getChunkSource().addEntity(this);
            return this;
        }
        Entity moved = changeDimension(new DimensionTransition(destination, target, Vec3.ZERO,
                facing, 0.0F, DimensionTransition.DO_NOTHING));
        return moved instanceof CompanionDogEntity companiondog ? companiondog : null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ------------------------------------------------------------------ sounds

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (isReturningHome()) return null;
        CompanionDogPose pose = getCompanionDogPose();
        if (pose == CompanionDogPose.SLEEP) {
            return null;
        }
        if (hasMood(MOOD_PANTING) || random.nextInt(3) == 0) {
            return SoundEvents.WOLF_PANT;
        }
        return random.nextInt(4) == 0 ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return 1.45F + (random.nextFloat() - random.nextFloat()) * 0.08F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.1F, 1.35F);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (bound && level() instanceof ServerLevel serverLevel) {
            CompanionDogBindings.get(serverLevel.getServer()).remember(this);
        }
        ItemStack mouth = getMouthItem();
        if (!mouth.isEmpty()) {
            tag.put("Mouth", mouth.save(registryAccess()));
        }
        tag.putString("CompanionDogPose", commandedPose.name());
        tag.putByte("CollarColor", (byte) getCollarColor().getId());
        tag.putBoolean("Wander", isWandering());
        tag.putBoolean("Bound", bound);
        if (home != null) tag.put("CompanionDogHome", CompanionDogSafety.encode(home));
        tag.putLong("RestUntil", restUntil);
        tag.putBoolean("ReturningHome", isReturningHome());
        tag.putBoolean("NeedsRest", needsRest);
        tag.putLong("BindingEpoch", bindingEpoch);
        tag.putInt("HealingDelay", healingDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMouthItem(tag.contains("Mouth") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Mouth")) : ItemStack.EMPTY);
        try {
            commandedPose = CompanionDogPose.valueOf(tag.getString("CompanionDogPose"));
        } catch (IllegalArgumentException ignored) {
            commandedPose = CompanionDogPose.STAND;
        }
        if (tag.contains("CollarColor")) {
            setCollarColor(DyeColor.byId(tag.getByte("CollarColor")));
        }
        setWandering(tag.getBoolean("Wander"));
        bound = tag.getBoolean("Bound");
        home = CompanionDogSafety.decode(tag.getCompound("CompanionDogHome"));
        restUntil = Math.max(0, tag.getLong("RestUntil"));
        needsRest = tag.getBoolean("NeedsRest");
        bindingEpoch = tag.getLong("BindingEpoch");
        healingDelay = Math.clamp(tag.getInt("HealingDelay"), 0, 100);
        entityData.set(DATA_RETURNING, tag.getBoolean("ReturningHome"));
        entityData.set(DATA_REST_TICKS, (int) Math.max(0, Math.min(RECOVERY_TICKS, restUntil - lifecycleTime())));
        if (isRecovering()) commandedPose = CompanionDogPose.LIE;
        updateEffectivePose();
    }

    // ------------------------------------------------------------------ client animation state

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key) && level().isClientSide) {
            clientAction = CompanionDogAction.byId(entityData.get(DATA_ACTION) & ACTION_MASK);
            clientActionStart = tickCount;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        }
    }

    private void clientTick() {
        CompanionDogPose target = debugPose != null ? debugPose : getCompanionDogPose();
        boolean snap = !poseWeightsInitialised || debugPose != null;
        poseWeightsInitialised = true;
        for (CompanionDogPose pose : CompanionDogPose.values()) {
            int i = pose.ordinal();
            float goal = pose == target ? 1.0F : 0.0F;
            poseWeightsO[i] = snap ? goal : poseWeights[i];
            poseWeights[i] = snap ? goal : Mth.approach(poseWeights[i], goal, 0.125F);
        }
    }

    /** Smoothed 0..1 weight of a held pose, for blending on the client. */
    public float poseWeight(CompanionDogPose pose, float partialTick) {
        if (debugPose != null) return pose == debugPose ? 1.0F : 0.0F;
        if (!poseWeightsInitialised) {
            return pose == getCompanionDogPose() ? 1.0F : 0.0F;
        }
        float total = 0.0F;
        float selected = 0.0F;
        for (CompanionDogPose candidate : CompanionDogPose.values()) {
            int i = candidate.ordinal();
            float t = Mth.lerp(partialTick, poseWeightsO[i], poseWeights[i]);
            float weight = t * t * (3.0F - 2.0F * t);
            total += weight;
            if (candidate == pose) selected = weight;
        }
        return total > 0.0F ? selected / total : 0.0F;
    }

    public CompanionDogAction getClientAction(float partialTick) {
        if (debugAction != null) {
            return debugAction;
        }
        if (clientAction == CompanionDogAction.NONE) {
            return CompanionDogAction.NONE;
        }
        float elapsed = tickCount - clientActionStart + partialTick;
        return elapsed < clientAction.durationTicks() ? clientAction : CompanionDogAction.NONE;
    }

    /** 0..1 progress through the current client action. */
    public float getClientActionProgress(float partialTick) {
        if (debugAction != null) {
            return debugProgress;
        }
        if (clientAction == CompanionDogAction.NONE) {
            return 0.0F;
        }
        float elapsed = tickCount - clientActionStart + partialTick;
        return Mth.clamp(elapsed / clientAction.durationTicks(), 0.0F, 1.0F);
    }

    /** Freezes the client-side animation for deterministic screenshots in the test harness. */
    public void setDebugAnimation(@Nullable CompanionDogPose pose, @Nullable CompanionDogAction action, float progress) {
        debugPose = pose;
        debugAction = action;
        debugProgress = progress;
    }

    public boolean hasDebugAnimation() {
        return debugPose != null || debugAction != null;
    }
}
