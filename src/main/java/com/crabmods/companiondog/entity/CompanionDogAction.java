package com.crabmods.companiondog.entity;

import org.jetbrains.annotations.Nullable;

/**
 * One-shot animated behaviours. {@code requiredPose} is the body pose the keyframes were
 * authored against; null means the action only moves the head, ears, jaw or tail and can
 * play in any pose.
 */
public enum CompanionDogAction {
    NONE(0, null, false),
    PET(44, null, true),
    PAW(56, CompanionDogPose.SIT, true),
    BEG(64, CompanionDogPose.SIT, true),
    ROLL_OVER(44, CompanionDogPose.STAND, true),
    SPIN(36, CompanionDogPose.STAND, true),
    PLAY_BOW(40, CompanionDogPose.STAND, true),
    JUMP(22, CompanionDogPose.STAND, true),
    SHAKE(36, CompanionDogPose.STAND, true),
    BARK(14, null, false),
    HEAD_TILT(34, null, false),
    SNIFF(50, CompanionDogPose.STAND, true),
    DIG(56, CompanionDogPose.STAND, true),
    YAWN(36, null, false),
    SCRATCH(52, CompanionDogPose.SIT, true),
    EAT(44, null, true),
    BELLY_RUB(52, CompanionDogPose.BELLY, true),
    STRETCH(44, CompanionDogPose.STAND, true),
    HOWL(60, CompanionDogPose.SIT, true);

    private static final CompanionDogAction[] VALUES = values();

    private final int durationTicks;
    @Nullable
    private final CompanionDogPose requiredPose;
    private final boolean freezesMovement;

    CompanionDogAction(int durationTicks, @Nullable CompanionDogPose requiredPose, boolean freezesMovement) {
        this.durationTicks = durationTicks;
        this.requiredPose = requiredPose;
        this.freezesMovement = freezesMovement;
    }

    public static CompanionDogAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public int durationTicks() {
        return durationTicks;
    }

    @Nullable
    public CompanionDogPose requiredPose() {
        return requiredPose;
    }

    public boolean freezesMovement() {
        return freezesMovement;
    }
}
