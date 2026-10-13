package com.crabmods.companiondog.entity;

import org.jetbrains.annotations.Nullable;

/** Things the owner can ask for from the interaction menu. */
public enum CompanionDogCommand {
    SIT("sit", null),
    LIE_DOWN("lie_down", null),
    STAND_UP("stand_up", null),
    SLEEP("sleep", null),
    BELLY_UP("belly_up", null),
    TOGGLE_WANDER("toggle_wander", null),
    PAW("paw", CompanionDogAction.PAW),
    BEG("beg", CompanionDogAction.BEG),
    ROLL_OVER("roll_over", CompanionDogAction.ROLL_OVER),
    SPIN("spin", CompanionDogAction.SPIN),
    PLAY_BOW("play_bow", CompanionDogAction.PLAY_BOW),
    BARK("bark", CompanionDogAction.BARK),
    SHAKE("shake", CompanionDogAction.SHAKE),
    HOWL("howl", CompanionDogAction.HOWL),
    ZOOMIES("zoomies", null),
    SET_HOME("set_home", null),
    GO_HOME("go_home", null),
    END_REST("end_rest", null),
    ARCHIVE_UNOWNED("archive_unowned", null),
    ADOPT_UNOWNED("adopt_unowned", null);

    private static final CompanionDogCommand[] VALUES = values();

    private final String key;
    @Nullable
    private final CompanionDogAction action;

    CompanionDogCommand(String key, @Nullable CompanionDogAction action) {
        this.key = key;
        this.action = action;
    }

    @Nullable
    public static CompanionDogCommand byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public String translationKey() {
        return "command.companiondog." + key;
    }

    @Nullable
    public CompanionDogAction action() {
        return action;
    }
}
