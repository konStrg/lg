package com.example.loupgarou.game;

import java.util.Collection;

/** Pure win logic, independent of Bukkit. */
public final class WinChecker {

    public enum Outcome { NONE, GREEN, RED, PYROMANE, ANGE, DRAW }

    private WinChecker() {}

    /**
     * @param alive      roles of the players still alive
     * @param redExisted true once at least one red has been assigned during the game
     */
    public static Outcome check(Collection<Role> alive, boolean redExisted) {
        if (alive.isEmpty()) return Outcome.DRAW;
        long reds = alive.stream().filter(r -> r.faction() == Faction.RED).count();
        long greens = alive.stream().filter(r -> r.faction() == Faction.GREEN).count();

        if (reds > 0) {
            return reds == alive.size() ? Outcome.RED : Outcome.NONE;
        }
        if (!redExisted) return Outcome.NONE;
        if (greens > 0) return Outcome.GREEN;
        return alive.contains(Role.PYROMANE) ? Outcome.PYROMANE : Outcome.DRAW;
    }
}
