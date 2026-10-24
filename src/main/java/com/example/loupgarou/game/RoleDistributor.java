package com.example.loupgarou.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Pure role distribution logic, independent of Bukkit. */
public final class RoleDistributor {

    private static final List<Role> POWERED_GREENS = List.of(
            Role.MEDECIN, Role.BARRICADEUR, Role.RADIO, Role.VETERAN, Role.KAMIKAZE, Role.CUPIDON);

    private RoleDistributor() {}

    /** chances = cumulative-free probabilities for 1, 2, 3 powered roles (the remainder gives 0). */
    static int rollCount(double[] chances, Random random) {
        double r = random.nextDouble();
        double acc = 0;
        int[] counts = {1, 2, 3};
        for (int i = 0; i < chances.length && i < counts.length; i++) {
            acc += chances[i];
            if (r < acc) return counts[i];
        }
        return 0;
    }

    public static List<Role> initial(int players, double[] chances, boolean guaranteeRed, Random random) {
        List<Role> roles = new ArrayList<>(players);
        roles.add(random.nextBoolean() ? Role.PYROMANE : Role.ANGE);

        int maxReds = Math.max(1, (players - 1) / 3);
        int reds = Math.min(rollCount(chances, random), maxReds);
        if (guaranteeRed) reds = Math.max(reds, 1);
        for (int i = 0; i < reds; i++) roles.add(Role.CANNIBALE);

        int room = players - roles.size() - 1;
        int greens = Math.min(Math.min(rollCount(chances, random), POWERED_GREENS.size()), Math.max(0, room));
        List<Role> pool = new ArrayList<>(POWERED_GREENS);
        Collections.shuffle(pool, random);
        roles.addAll(pool.subList(0, greens));

        while (roles.size() < players) roles.add(Role.VOYAGEUR);
        Collections.shuffle(roles, random);
        return roles;
    }

    /**
     * Role given to a Voyageur who succeeded a day event.
     *
     * @param assigned powered roles already handed out this game (non-cannibal)
     */
    public static Optional<Role> pickUpgrade(int alive, int aliveReds, Set<Role> assigned,
                                             double redRatio, Random random) {
        int targetReds = Math.max(1, (int) Math.round(alive * redRatio));
        double redChance = aliveReds < targetReds ? 0.6 : 0.15;
        if (random.nextDouble() < redChance) return Optional.of(Role.CANNIBALE);

        Set<Role> free = EnumSet.noneOf(Role.class);
        free.addAll(POWERED_GREENS);
        free.removeAll(assigned);
        if (free.isEmpty()) return Optional.empty();
        List<Role> list = new ArrayList<>(free);
        return Optional.of(list.get(random.nextInt(list.size())));
    }
}
