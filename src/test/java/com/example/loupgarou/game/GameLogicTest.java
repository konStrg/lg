package com.example.loupgarou.game;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLogicTest {

    private static final double[] CHANCES = {0.30, 0.15, 0.01};

    @Test
    void greensWinWhenAllRedsDead() {
        assertEquals(WinChecker.Outcome.GREEN,
                WinChecker.check(List.of(Role.VOYAGEUR, Role.PYROMANE), true));
    }

    @Test
    void redsWinOnlyWhenEveryNonRedIsDead() {
        assertEquals(WinChecker.Outcome.NONE,
                WinChecker.check(List.of(Role.CANNIBALE, Role.ANGE), true));
        assertEquals(WinChecker.Outcome.RED,
                WinChecker.check(List.of(Role.CANNIBALE, Role.CANNIBALE), true));
    }

    @Test
    void noGreenWinBeforeAnyRedExisted() {
        assertEquals(WinChecker.Outcome.NONE, WinChecker.check(List.of(Role.VOYAGEUR, Role.ANGE), false));
    }

    @Test
    void lonePyromaneWinsAndEmptyIsDraw() {
        assertEquals(WinChecker.Outcome.PYROMANE, WinChecker.check(List.of(Role.PYROMANE), true));
        assertEquals(WinChecker.Outcome.DRAW, WinChecker.check(List.of(Role.ANGE), true));
        assertEquals(WinChecker.Outcome.DRAW, WinChecker.check(List.of(), true));
    }

    @Test
    void initialDistributionHasOneGreyAndAtLeastOneRed() {
        Random random = new Random(42);
        for (int n = 5; n <= 20; n++) {
            for (int i = 0; i < 200; i++) {
                List<Role> roles = RoleDistributor.initial(n, CHANCES, true, random);
                assertEquals(n, roles.size());
                assertEquals(1, roles.stream().filter(r -> r.faction() == Faction.GREY).count());
                long reds = roles.stream().filter(r -> r == Role.CANNIBALE).count();
                assertTrue(reds >= 1 && reds <= Math.max(1, (n - 1) / 3));
                assertTrue(roles.contains(Role.VOYAGEUR));
                for (Role r : Role.values()) {
                    if (r.isPoweredGreen()) assertTrue(roles.stream().filter(x -> x == r).count() <= 1);
                }
            }
        }
    }

    @Test
    void upgradeNeverDuplicatesAssignedPoweredRoles() {
        Random random = new Random(7);
        Set<Role> assigned = EnumSet.of(Role.MEDECIN, Role.BARRICADEUR, Role.RADIO, Role.VETERAN, Role.KAMIKAZE);
        for (int i = 0; i < 500; i++) {
            RoleDistributor.pickUpgrade(8, 1, assigned, 0.25, random).ifPresent(r ->
                    assertTrue(r == Role.CANNIBALE || r == Role.CUPIDON));
        }
        Set<Role> all = EnumSet.of(Role.MEDECIN, Role.BARRICADEUR, Role.RADIO, Role.VETERAN, Role.KAMIKAZE, Role.CUPIDON);
        for (int i = 0; i < 500; i++) {
            assertFalse(RoleDistributor.pickUpgrade(8, 3, all, 0.25, random)
                    .filter(r -> r != Role.CANNIBALE).isPresent());
        }
    }

    @Test
    void confinementWindowMatchesTimeline() {
        PlayerData pd = new PlayerData(UUID.randomUUID(), "x");
        int voteTick = 2 * 3 - 1; // day 3
        pd.confinedUntil = voteTick + 3;
        assertTrue(pd.isConfined(2 * 3));      // night 3
        assertTrue(pd.isConfined(2 * 4 - 1));  // day 4
        assertTrue(pd.isConfined(2 * 4));      // night 4
        assertFalse(pd.isConfined(2 * 5 - 1)); // day 5
    }
}
