package com.hitpo.doommc3d.interact;

/**
 * Doom linedef trigger activation types.
 *
 * Maps to the classic Doom trigger naming convention:
 * - W1: Walk Over Once
 * - WR: Walk Over Repeatable
 * - S1: Use (Use linedef) Once
 * - SR: Use Repeatable
 * - G1: Gun (Shoot) Once
 * - GR: Gun Repeatable
 * - M1: Monster Cross Once
 * - MU: Monster Use (Compat mode only)
 */
public enum TriggerActivation {
    /** Walk over trigger, fires once */
    WALK_OVER_ONCE("W1"),
    /** Walk over trigger, repeatable */
    WALK_OVER_REPEATABLE("WR"),
    /** Use (push) trigger, fires once */
    USE_ONCE("S1"),
    /** Use (push) trigger, repeatable */
    USE_REPEATABLE("SR"),
    /** Shoot trigger, fires once */
    SHOOT_ONCE("G1"),
    /** Shoot trigger, repeatable */
    SHOOT_REPEATABLE("GR"),
    /** Monster cross trigger, fires once */
    MONSTER_CROSS("M1"),
    /** Monster use trigger (Boom extension) */
    MONSTER_USE("MU");

    private final String doomLabel;

    TriggerActivation(String doomLabel) {
        this.doomLabel = doomLabel;
    }

    public String getDoomLabel() {
        return doomLabel;
    }

    /**
     * Returns true if this trigger should fire only once.
     */
    public boolean isOnce() {
        return this == WALK_OVER_ONCE || this == USE_ONCE || this == SHOOT_ONCE || this == MONSTER_CROSS;
    }

    /**
     * Returns true if this is a walk-over trigger.
     */
    public boolean isWalkOver() {
        return this == WALK_OVER_ONCE || this == WALK_OVER_REPEATABLE;
    }

    /**
     * Returns true if this is a usage/push trigger.
     */
    public boolean isUse() {
        return this == USE_ONCE || this == USE_REPEATABLE;
    }

    /**
     * Returns true if this is a shoot trigger.
     */
    public boolean isShoot() {
        return this == SHOOT_ONCE || this == SHOOT_REPEATABLE;
    }

    /**
     * Returns true if this is a monster trigger.
     */
    public boolean isMonster() {
        return this == MONSTER_CROSS || this == MONSTER_USE;
    }
}
