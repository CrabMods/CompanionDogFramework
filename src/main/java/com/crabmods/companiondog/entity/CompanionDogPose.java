package com.crabmods.companiondog.entity;

/** Held body poses. The client blends between them smoothly. */
public enum CompanionDogPose {
    STAND,
    SIT,
    LIE,
    SLEEP,
    BELLY;

    private static final CompanionDogPose[] VALUES = values();

    public static CompanionDogPose byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STAND;
    }

    public boolean isResting() {
        return this != STAND;
    }
}
