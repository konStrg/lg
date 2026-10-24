package com.example.loupgarou.game;

import com.example.loupgarou.LoupGarouPlugin;
import com.example.loupgarou.arena.Arena;
import com.example.loupgarou.gui.ChoiceGui;
import com.example.loupgarou.gui.Option;
import com.example.loupgarou.gui.Prompt;
import com.example.loupgarou.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Game {

    public enum Phase { LOBBY, DAY, NIGHT, ENDED }

    public enum DamageResult { ALLOW, CANCEL }

    private record Snapshot(Location location, GameMode mode, ItemStack[] contents, double health, int food) {}

    private static final class Hunt {
        final PlayerData hunter;
        final PlayerData victim;
        boolean over;
        boolean hunterDied;
        boolean victimDied;

        Hunt(PlayerData hunter, PlayerData victim) {
            this.hunter = hunter;
            this.victim = victim;
        }
    }

    private final LoupGarouPlugin plugin;
    private final Arena arena;
    private final Msg msg;
    private final FileConfiguration cfg;
    private final Random random = new Random();

    private final Map<UUID, PlayerData> players = new LinkedHashMap<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new HashMap<>();
    private final List<Component> news = new ArrayList<>();
    private final Set<Role> assignedPowered = EnumSet.noneOf(Role.class);

    private Phase phase = Phase.LOBBY;
    private int dayNumber = 0;
    private boolean redExisted;
    private boolean firstCouncilDone;

    private BukkitTask ticker;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private int secondsLeft;
    private int secondsTotal = 1;
    private BooleanSupplier doneEarly;
    private Runnable onEnd;
    private boolean cannibalDebate;

    private Hunt hunt;
    private PlayerData lastVictim;
    private final Map<UUID, UUID> kamikazeTargets = new HashMap<>();
    private UUID healTarget;
    private final Set<UUID> barricadedCabins = new HashSet<>();
    private final Set<String> barricadedWagons = new HashSet<>();
    private UUID radioTarget;
    private final Set<String> soakedWagons = new HashSet<>();

    private String eventCode;
    private final List<UUID> eventWinners = new ArrayList<>();

    public Game(LoupGarouPlugin plugin) {
        this.plugin = plugin;
        this.arena = plugin.arena();
        this.msg = plugin.msg();
        this.cfg = plugin.getConfig();
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    // ------------------------------------------------------------------ accessors

    public Phase phase() { return phase; }
    public Arena arena() { return arena; }
    public boolean isRunning() { return phase == Phase.DAY || phase == Phase.NIGHT; }
    public PlayerData data(UUID id) { return players.get(id); }
    public int playerCount() { return players.size(); }
    public int dayNumber() { return dayNumber; }

    public int tick() {
        return phase == Phase.NIGHT ? dayNumber * 2 : dayNumber * 2 - 1;
    }

    private Player online(PlayerData pd) {
        return pd == null ? null : Bukkit.getPlayer(pd.uuid);
    }

    private List<PlayerData> alive() {
        return players.values().stream().filter(p -> p.alive).collect(Collectors.toList());
    }

    private List<PlayerData> actors(Predicate<PlayerData> filter) {
        return players.values().stream()
                .filter(p -> p.alive && !p.isConfined(tick()) && online(p) != null)
                .filter(filter).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ lobby

    public boolean join(Player player) {
        if (phase != Phase.LOBBY) return false;
        if (players.containsKey(player.getUniqueId())) return true;
        if (players.size() >= arena.maxPlayers()) return false;
        players.put(player.getUniqueId(), new PlayerData(player.getUniqueId(), player.getName()));
        snapshots.put(player.getUniqueId(), new Snapshot(player.getLocation(), player.getGameMode(),
                player.getInventory().getContents(), player.getHealth(), player.getFoodLevel()));
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.teleport(arena.lobby());
        player.showBossBar(bar);
        broadcast(msg.prefixed("joined", player.getName(), players.size()));
        updateLobbyCountdown();
        return true;
    }

    public void leave(Player player) {
        PlayerData pd = players.get(player.getUniqueId());
        if (pd == null) return;
        if (phase == Phase.LOBBY) {
            broadcast(msg.prefixed("left", player.getName()));
            players.remove(player.getUniqueId());
            restore(player);
            updateLobbyCountdown();
            if (players.isEmpty()) plugin.dropGame(this);
        } else if (phase != Phase.ENDED) {
            if (pd.alive) {
                broadcast(msg.prefixed("left", player.getName()));
                kill(pd);
                checkWin();
            }
            restore(player);
            players.remove(player.getUniqueId());
        }
    }

    public void rejoin(Player player) {
        PlayerData pd = players.get(player.getUniqueId());
        if (pd == null || phase == Phase.LOBBY || phase == Phase.ENDED) return;
        player.showBossBar(bar);
        if (!pd.alive) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(arena.council());
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            Location to = phase == Phase.NIGHT && !pd.unlocked || pd.isConfined(tick()) ? cabinLocation(pd) : arena.council();
            player.teleport(to);
        }
    }

    private void updateLobbyCountdown() {
        int min = cfg.getInt("game.min-players", 5);
        if (players.size() >= min) {
            if (onEnd == null) {
                step(msg.get("lobby-bar"), cfg.getInt("game.lobby-countdown", 30),
                        () -> players.size() >= arena.maxPlayers(), this::startGame);
                broadcast(msg.prefixed("countdown", secondsLeft));
            }
        } else if (onEnd != null && phase == Phase.LOBBY) {
            clearStep();
            bar.name(msg.get("lobby-bar"));
            bar.progress(1f);
        }
    }

    public boolean forceStart() {
        if (phase != Phase.LOBBY || players.size() < cfg.getInt("game.min-players", 5)) return false;
        clearStep();
        startGame();
        return true;
    }

    // ------------------------------------------------------------------ start

    private void startGame() {
        clearStep();
        List<PlayerData> list = new ArrayList<>(players.values());
        Collections.shuffle(list, random);
        int n = list.size();

        List<Double> chanceList = cfg.getDoubleList("roles.initial-chance");
        double[] chances = chanceList.stream().mapToDouble(Double::doubleValue).toArray();
        List<Role> roles = RoleDistributor.initial(n, chances, cfg.getBoolean("roles.guarantee-initial-red", true), random);

        int first = Math.max(1, Math.round(n * 0.25f));
        int second = Math.max(1, Math.round(n * 0.35f));
        Map<CharacterClass, List<Arena.Cabin>> cabinsByClass = new EnumMap<>(CharacterClass.class);
        for (CharacterClass c : CharacterClass.values()) cabinsByClass.put(c, new ArrayList<>());
        for (Arena.Cabin c : arena.cabins()) cabinsByClass.get(c.cls()).add(c);
        cabinsByClass.values().forEach(Collections::shuffle);
        List<Arena.Cabin> unused = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            PlayerData pd = list.get(i);
            pd.role = roles.get(i);
            if (pd.role.faction() == Faction.RED) redExisted = true;
            if (pd.role.isPoweredGreen()) assignedPowered.add(pd.role);
            CharacterClass wanted = i < first ? CharacterClass.FIRST : i < first + second ? CharacterClass.SECOND : CharacterClass.THIRD;
            List<Arena.Cabin> pool = cabinsByClass.get(wanted);
            if (pool.isEmpty()) {
                pool = cabinsByClass.values().stream().filter(l -> !l.isEmpty()).findFirst().orElse(unused);
            }
            pd.cabin = pool.isEmpty() ? null : pool.remove(0);
            pd.cls = pd.cabin != null ? pd.cabin.cls() : wanted;
        }

        dayNumber = 1;
        phase = Phase.DAY;
        for (PlayerData pd : players.values()) {
            Player p = online(pd);
            if (p == null) continue;
            p.sendMessage(msg.prefixed("start"));
            announceRole(p, pd);
        }
        beginDay();
    }

    private void announceRole(Player p, PlayerData pd) {
        p.showTitle(Title.title(msg.get("your-role", pd.role.display()), msg.get("your-role-desc", pd.role.description())));
        p.sendMessage(msg.prefixed("your-role", pd.role.display()));
        p.sendMessage(msg.prefixed("your-role-desc", pd.role.description()));
        p.sendMessage(msg.prefixed("your-class", pd.cls.display()));
    }

    // ------------------------------------------------------------------ day

    private void beginDay() {
        if (phase == Phase.ENDED) return;
        phase = Phase.DAY;
        cannibalDebate = false;
        applyDayEffects();
        for (PlayerData pd : players.values()) {
            pd.unlocked = false;
            Player p = online(pd);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.DARKNESS);
            if (pd.alive) {
                p.teleport(pd.isConfined(tick()) ? cabinLocation(pd) : arena.council());
            }
        }
        for (Component c : news) broadcast(c);
        news.clear();

        if (dayNumber % 2 == 1) {
            for (PlayerData pd : alive()) {
                if (pd.loverSuicidePending) {
                    kill(pd);
                    broadcast(msg.prefixed("lover-suicide", pd.name));
                }
            }
        }
        if (checkWin()) return;

        boolean vote = dayNumber % 2 == 1;
        Component sub = msg.get(vote ? "day-vote-sub" : "day-event-sub");
        for (Player p : onlinePlayers()) p.showTitle(Title.title(msg.get("day-title", dayNumber), sub));

        if (!vote) {
            startEvent();
        } else if (dayNumber == 1) {
            startVote(true);
        } else {
            startVote(false);
        }
    }

    // ---- voting

    private void startVote(boolean captainElection) {
        Map<UUID, Integer> votes = new HashMap<>();
        for (PlayerData voter : actors(p -> true)) {
            List<Option> options = playerOptions(t -> t.uuid != voter.uuid);
            if (options.isEmpty()) continue;
            Component title = msg.get(captainElection ? "vote-captain-title" : "vote-title");
            prompt(voter, title, options, value -> {
                int weight = voter.captain ? 2 : 1;
                votes.merge((UUID) value, weight, Integer::sum);
            });
        }
        int seconds = cfg.getInt(captainElection ? "durations.captain-vote" : "durations.council-vote", 60);
        step(msg.get("bar-day", dayNumber, msg.raw(captainElection ? "vote-captain-title" : "vote-title")),
                seconds, prompts::isEmpty, () -> {
                    clearPrompts();
                    if (captainElection) resolveCaptain(votes);
                    else resolveCouncil(votes);
                });
    }

    private Optional<UUID> topVote(Map<UUID, Integer> votes) {
        if (votes.isEmpty()) return Optional.empty();
        int max = Collections.max(votes.values());
        List<UUID> top = votes.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).collect(Collectors.toList());
        return Optional.of(top.get(random.nextInt(top.size())));
    }

    private void resolveCaptain(Map<UUID, Integer> votes) {
        Optional<UUID> top = topVote(votes);
        if (top.isPresent() && players.get(top.get()) != null) {
            PlayerData c = players.get(top.get());
            c.captain = true;
            broadcast(msg.prefixed("captain-elected", c.name));
        } else {
            broadcast(msg.prefixed("captain-none"));
        }
        beginNight();
    }

    private void resolveCouncil(Map<UUID, Integer> votes) {
        Optional<UUID> top = topVote(votes);
        boolean first = !firstCouncilDone;
        firstCouncilDone = true;
        if (top.isEmpty()) {
            broadcast(msg.prefixed("vote-result-none"));
            beginNight();
            return;
        }
        PlayerData target = players.get(top.get());
        broadcast(msg.prefixed("vote-result", target.name));

        PlayerData captain = players.values().stream().filter(p -> p.captain && p.alive && online(p) != null).findFirst().orElse(null);
        if (captain == null) {
            applySanction(target, random.nextBoolean(), first);
            return;
        }
        List<Option> options = List.of(
                new Option(msg.get("opt-eject"), ChoiceGui.icon(Material.IRON_DOOR), "eject"),
                new Option(msg.get("opt-confine"), ChoiceGui.icon(Material.IRON_BARS), "confine"));
        prompt(captain, msg.get("captain-decide-title", target.name), options, v -> {
            clearStep();
            applySanction(target, "eject".equals(v), first);
        });
        step(msg.get("bar-day", dayNumber, msg.raw("captain-decide-title", target.name)),
                cfg.getInt("durations.captain-decision", 20), prompts::isEmpty, () -> {
                    clearPrompts();
                    applySanction(target, random.nextBoolean(), first);
                });
    }

    private void applySanction(PlayerData target, boolean eject, boolean firstVote) {
        if (phase == Phase.ENDED) return;
        if (eject) {
            kill(target);
            Player p = online(target);
            if (p != null) p.teleport(arena.eject());
            broadcast(deathComponent("ejected", "ejected-hidden", target));
            if (firstVote && target.role == Role.ANGE) {
                broadcast(msg.prefixed("angel-win"));
                finish(WinChecker.Outcome.ANGE);
                return;
            }
            if (checkWin()) return;
        } else {
            target.confinedUntil = tick() + 3;
            broadcast(msg.prefixed("confined", target.name));
            Player p = online(target);
            if (p != null) {
                p.sendMessage(msg.prefixed("confined-you"));
                p.teleport(cabinLocation(target));
            }
        }
        beginNight();
    }

    // ---- events

    private void startEvent() {
        eventCode = "TRAIN-" + (1000 + random.nextInt(9000));
        eventWinners.clear();
        int eligible = actors(p -> true).size();
        int winners = Math.max(1, Math.round(eligible * (float) cfg.getDouble("events.winners-ratio", 0.25)));
        broadcast(msg.prefixed("event-start", eventCode));
        step(msg.get("bar-day", dayNumber, msg.raw("day-event-sub")), cfg.getInt("durations.event", 60),
                () -> eventWinners.size() >= winners, this::endEvent);
    }

    /** Read-only check used from the async chat thread. */
    public boolean matchesEvent(Player player, String message) {
        PlayerData pd = players.get(player.getUniqueId());
        return eventCode != null && phase == Phase.DAY && pd != null && pd.alive && !pd.isConfined(tick())
                && message.trim().equalsIgnoreCase(eventCode);
    }

    /** @return true when the chat message was consumed by the running event */
    public boolean handleEventChat(Player player, String message) {
        PlayerData pd = players.get(player.getUniqueId());
        if (!matchesEvent(player, message)) return false;
        if (!eventWinners.contains(pd.uuid)) {
            eventWinners.add(pd.uuid);
            player.sendMessage(msg.prefixed("event-success"));
        }
        return true;
    }

    private void endEvent() {
        eventCode = null;
        broadcast(msg.prefixed("event-end", eventWinners.size()));
        double ratio = cfg.getDouble("events.red-ratio", 0.25);
        for (UUID id : eventWinners) {
            PlayerData pd = players.get(id);
            if (pd == null || !pd.alive || pd.role != Role.VOYAGEUR) continue;
            int aliveReds = (int) alive().stream().filter(p -> p.role.faction() == Faction.RED).count();
            RoleDistributor.pickUpgrade(alive().size(), aliveReds, assignedPowered, ratio, random)
                    .ifPresent(role -> assignRole(pd, role));
        }
        beginNight();
    }

    private void assignRole(PlayerData pd, Role role) {
        pd.role = role;
        pd.powerUsed = false;
        if (role == Role.CANNIBALE) redExisted = true;
        else assignedPowered.add(role);
        Player p = online(pd);
        if (p != null) {
            p.showTitle(Title.title(msg.get("role-assigned-title", role.display()), msg.get("your-role-desc", role.description())));
            p.sendMessage(msg.prefixed("your-role", role.display()));
            p.sendMessage(msg.prefixed("your-role-desc", role.description()));
        }
    }

    // ------------------------------------------------------------------ night

    private void beginNight() {
        if (phase == Phase.ENDED) return;
        phase = Phase.NIGHT;
        hunt = null;
        cannibalDebate = false;
        applyNightEffects();
        for (PlayerData pd : alive()) {
            pd.unlocked = false;
            Player p = online(pd);
            if (p == null) continue;
            p.teleport(cabinLocation(pd));
            p.sendMessage(msg.prefixed("night-sleep"));
            if (cfg.getBoolean("effects.night-darkness", true)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, true, false, false));
            }
        }
        boolean green = dayNumber % 2 == 1;
        Component sub = msg.get(green ? "night-green-sub" : "night-red-sub");
        for (Player p : onlinePlayers()) p.showTitle(Title.title(msg.get("night-title", dayNumber), sub));
        if (green) greenNight();
        else redNight();
    }

    private Location cabinLocation(PlayerData pd) {
        return pd.cabin != null ? pd.cabin.location() : arena.council();
    }

    // ---- green night

    private void greenNight() {
        kamikazeTargets.clear();
        healTarget = null;

        for (PlayerData pd : actors(p -> p.role == Role.CUPIDON && !p.powerUsed)) {
            prompt(pd, msg.get("cupid-first"), playerOptions(t -> true), first -> {
                prompt(pd, msg.get("cupid-second"), playerOptions(t -> !t.uuid.equals(first)), second -> {
                    PlayerData a = players.get(first);
                    PlayerData b = players.get(second);
                    a.lover = b.uuid;
                    b.lover = a.uuid;
                    pd.powerUsed = true;
                    Player pa = online(a);
                    Player pb = online(b);
                    if (pa != null) pa.sendMessage(msg.prefixed("lover-linked", b.name));
                    if (pb != null) pb.sendMessage(msg.prefixed("lover-linked", a.name));
                });
            });
        }
        for (PlayerData pd : actors(p -> p.role == Role.KAMIKAZE && !p.powerUsed)) {
            List<Option> options = playerOptions(t -> t.uuid != pd.uuid);
            options.add(skipOption());
            prompt(pd, msg.get("kamikaze-title"), options, v -> {
                if (v instanceof UUID target) {
                    kamikazeTargets.put(pd.uuid, target);
                    pd.powerUsed = true;
                }
            });
        }
        if (lastVictim != null && !lastVictim.alive) {
            for (PlayerData pd : actors(p -> p.role == Role.MEDECIN && !p.powerUsed)) {
                List<Option> options = new ArrayList<>();
                options.add(new Option(msg.get("medecin-heal", lastVictim.name), ChoiceGui.head(lastVictim.uuid, lastVictim.name), lastVictim.uuid));
                options.add(skipOption());
                prompt(pd, msg.get("medecin-title"), options, v -> {
                    if (v instanceof UUID id) {
                        healTarget = id;
                        pd.powerUsed = true;
                    }
                });
            }
        }
        for (PlayerData pd : actors(p -> p.role == Role.BARRICADEUR && p.lastBarricadeNight != dayNumber - 2)) {
            List<Option> options = new ArrayList<>();
            for (PlayerData t : alive()) {
                options.add(new Option(msg.get("barricade-cabin", t.name), ChoiceGui.head(t.uuid, t.name), "cabin:" + t.uuid));
            }
            for (String wagon : arena.wagons().keySet()) {
                options.add(new Option(msg.get("barricade-wagon", wagon), ChoiceGui.icon(Material.OAK_DOOR), "wagon:" + wagon));
            }
            options.add(skipOption());
            prompt(pd, msg.get("action-title"), options, v -> {
                if (!(v instanceof String s) || "skip".equals(s)) return;
                pd.lastBarricadeNight = dayNumber;
                if (s.startsWith("cabin:")) barricadedCabins.add(UUID.fromString(s.substring(6)));
                else barricadedWagons.add(s.substring(7));
                Player p = online(pd);
                if (p != null) p.sendMessage(msg.prefixed("barricade-set", s.substring(s.indexOf(':') + 1)));
            });
        }
        for (PlayerData pd : actors(p -> p.role == Role.RADIO)) {
            List<Option> options = playerOptions(t -> true);
            options.add(skipOption());
            prompt(pd, msg.get("action-title"), options, v -> {
                if (v instanceof UUID id) {
                    radioTarget = id;
                    Player p = online(pd);
                    PlayerData t = players.get(id);
                    if (p != null && t != null) p.sendMessage(msg.prefixed("radio-set", t.name));
                }
            });
        }
        pyromanePrompts();

        step(msg.get("bar-night", dayNumber, msg.raw("night-green-sub")), cfg.getInt("durations.night-actions", 45),
                prompts::isEmpty, this::resolveGreenNight);
    }

    private void pyromanePrompts() {
        for (PlayerData pd : actors(p -> p.role == Role.PYROMANE)) {
            List<Option> options = new ArrayList<>();
            for (String wagon : arena.wagons().keySet()) {
                if (!soakedWagons.contains(wagon)) {
                    options.add(new Option(msg.get("barricade-wagon", wagon), ChoiceGui.icon(Material.WATER_BUCKET), wagon));
                }
            }
            if (soakedWagons.size() >= 3) {
                options.add(new Option(msg.get("pyro-fire"), ChoiceGui.icon(Material.FLINT_AND_STEEL), "fire"));
            }
            options.add(skipOption());
            prompt(pd, msg.get("pyro-title"), options, v -> {
                Player p = online(pd);
                if ("fire".equals(v)) {
                    broadcast(msg.prefixed("pyro-win"));
                    finish(WinChecker.Outcome.PYROMANE);
                } else if (v instanceof String wagon && !"skip".equals(wagon)) {
                    soakedWagons.add(wagon);
                    if (p != null) p.sendMessage(msg.prefixed("pyro-soaked", wagon, soakedWagons.size()));
                }
            });
        }
    }

    private void resolveGreenNight() {
        clearPrompts();
        if (phase == Phase.ENDED) return;
        for (Map.Entry<UUID, UUID> e : kamikazeTargets.entrySet()) {
            PlayerData kamikaze = players.get(e.getKey());
            PlayerData target = players.get(e.getValue());
            if (kamikaze == null || !kamikaze.alive) continue;
            news.add(msg.prefixed("sacrifice", kamikaze.name));
            kill(kamikaze);
            if (target != null && target.alive && target.role == Role.CANNIBALE) {
                kill(target);
                news.add(msg.prefixed("sacrifice-hit", target.name));
            } else {
                news.add(msg.prefixed("sacrifice-miss"));
            }
        }
        if (healTarget != null) {
            PlayerData t = players.get(healTarget);
            if (t != null && !t.alive) revive(t);
        }
        lastVictim = null;
        if (checkWin()) return;
        dayNumber++;
        beginDay();
    }

    private void revive(PlayerData pd) {
        pd.alive = true;
        Player p = online(pd);
        if (p != null) {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(cabinLocation(pd));
        }
        news.add(msg.prefixed("revived", pd.name));
    }

    // ---- red night

    private void redNight() {
        List<PlayerData> debaters = actors(p -> p.role == Role.CANNIBALE);
        Map<UUID, UUID> votes = new HashMap<>();
        Set<UUID> voted = new HashSet<>();
        if (!debaters.isEmpty()) {
            cannibalDebate = true;
            for (PlayerData pd : debaters) {
                pd.unlocked = true;
                Player p = online(pd);
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.teleport(arena.debate());
                p.sendMessage(msg.prefixed("cannibal-debate"));
                List<Option> options = playerOptions(t -> t.role != Role.CANNIBALE);
                options.add(new Option(msg.get("cannibal-abstain"), ChoiceGui.icon(Material.BARRIER), "skip"));
                prompt(pd, msg.get("cannibal-title"), options, v -> {
                    voted.add(pd.uuid);
                    if (v instanceof UUID id) votes.put(pd.uuid, id);
                });
            }
        }
        step(msg.get("bar-night", dayNumber, msg.raw("night-red-sub")), cfg.getInt("durations.cannibal-debate", 60),
                prompts::isEmpty, () -> {
                    clearPrompts();
                    resolveDebate(debaters, votes);
                });
    }

    private void resolveDebate(List<PlayerData> debaters, Map<UUID, UUID> votes) {
        if (phase == Phase.ENDED) return;
        cannibalDebate = false;
        Map<UUID, Integer> tally = new HashMap<>();
        votes.values().forEach(t -> tally.merge(t, 1, Integer::sum));
        int abstain = debaters.size() - votes.size();
        Optional<UUID> top = topVote(tally);
        boolean eat = top.isPresent() && Collections.max(tally.values()) > abstain;
        if (debaters.isEmpty() || !eat) {
            for (PlayerData d : debaters) {
                Player p = online(d);
                if (p != null) p.sendMessage(msg.prefixed("no-hunt"));
            }
            endRedNight();
            return;
        }
        PlayerData victim = players.get(top.get());
        List<PlayerData> candidates = debaters.stream().filter(d -> top.get().equals(votes.get(d.uuid))).collect(Collectors.toList());
        PlayerData hunter = candidates.get(random.nextInt(candidates.size()));
        startHunt(debaters, hunter, victim);
    }

    private void startHunt(List<PlayerData> debaters, PlayerData hunterData, PlayerData victim) {
        String wagon = arena.wagonAt(cabinLocation(victim));
        if (barricadedCabins.contains(victim.uuid) || (wagon != null && barricadedWagons.contains(wagon))) {
            for (PlayerData d : debaters) {
                Player p = online(d);
                if (p != null) p.sendMessage(msg.prefixed("barricade-resists"));
            }
            for (PlayerData d : debaters) d.unlocked = false;
            endRedNight();
            return;
        }
        hunt = new Hunt(hunterData, victim);
        for (PlayerData d : debaters) {
            if (d == hunterData) continue;
            d.unlocked = false;
            Player p = online(d);
            if (p != null) {
                p.teleport(cabinLocation(d));
                p.sendMessage(msg.prefixed("cannibal-others"));
                if (cfg.getBoolean("effects.night-darkness", true)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, true, false, false));
                }
            }
        }
        Player hp = online(hunterData);
        if (hp != null) {
            hunterData.unlocked = true;
            hp.teleport(cabinLocation(hunterData));
            hp.sendMessage(msg.prefixed("cannibal-hunter", victim.name));
            hp.getInventory().addItem(weapon(cfg.getString("hunt.hunter-weapon", "IRON_SWORD"), "Couteau de boucher"));
        }
        Player vp = online(victim);
        if (vp != null) {
            victim.unlocked = true;
            vp.removePotionEffect(PotionEffectType.DARKNESS);
            vp.sendMessage(msg.prefixed("hunt-start"));
            if (victim.role == Role.VETERAN) {
                vp.getInventory().addItem(weapon(cfg.getString("hunt.veteran-weapon", "STONE_SWORD"), "Arme de veteran"));
            }
        }
        step(msg.get("bar-hunt", dayNumber), cfg.getInt("durations.hunt-max", 180),
                () -> hunt == null || hunt.over, this::endHunt);
    }

    private ItemStack weapon(String material, String name) {
        Material m = Material.matchMaterial(material);
        ItemStack item = new ItemStack(m != null ? m : Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private void endHunt() {
        if (hunt == null) {
            endRedNight();
            return;
        }
        Hunt h = hunt;
        if (h.hunterDied) {
            news.add(msg.prefixed("hunt-hunter-dead"));
        }
        if (h.victimDied) {
            lastVictim = h.victim;
        } else if (!h.hunterDied) {
            Player hp = online(h.hunter);
            if (hp != null) hp.sendMessage(msg.prefixed("hunt-timeout"));
        }
        endRedNight();
    }

    private void endRedNight() {
        clearPrompts();
        if (phase == Phase.ENDED) return;
        if (hunt != null) {
            for (PlayerData pd : new PlayerData[]{hunt.hunter, hunt.victim}) {
                Player p = online(pd);
                if (p != null) p.getInventory().clear();
            }
        }
        hunt = null;
        barricadedCabins.clear();
        barricadedWagons.clear();
        radioTarget = null;
        for (PlayerData pd : players.values()) pd.unlocked = false;
        if (checkWin()) return;
        dayNumber++;
        beginDay();
    }

    // ---- hunt combat

    public DamageResult onDamage(Player damaged, Player damager, double finalDamage) {
        PlayerData dd = players.get(damaged.getUniqueId());
        if (dd == null) return DamageResult.ALLOW;
        if (phase != Phase.NIGHT || hunt == null || hunt.over || damager == null) return DamageResult.CANCEL;
        PlayerData da = players.get(damager.getUniqueId());
        boolean valid = (da == hunt.hunter && dd == hunt.victim) || (da == hunt.victim && dd == hunt.hunter);
        if (!valid) return DamageResult.CANCEL;
        if (damaged.getHealth() - finalDamage > 0) return DamageResult.ALLOW;
        hunt.over = true;
        if (dd == hunt.victim) {
            hunt.victimDied = true;
            news.add(deathComponent("death-announce", "death-announce-hidden", dd));
        } else {
            hunt.hunterDied = true;
        }
        kill(dd);
        return DamageResult.CANCEL;
    }

    // ------------------------------------------------------------------ deaths & win

    private Component deathComponent(String key, String hiddenKey, PlayerData pd) {
        if (cfg.getBoolean("game.reveal-role-on-death", true)) return msg.prefixed(key, pd.name, pd.role.display());
        return msg.prefixed(hiddenKey, pd.name);
    }

    private void kill(PlayerData pd) {
        if (!pd.alive) return;
        pd.alive = false;
        pd.unlocked = false;
        prompts.remove(pd.uuid);
        Player p = online(pd);
        if (p != null) {
            p.closeInventory();
            p.getInventory().clear();
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(arena.council());
        }
        if (pd.lover != null) {
            PlayerData l = players.get(pd.lover);
            if (l != null && l.alive) l.loverSuicidePending = true;
        }
    }

    private boolean checkWin() {
        if (phase == Phase.ENDED || phase == Phase.LOBBY) return false;
        List<Role> roles = alive().stream().map(p -> p.role).collect(Collectors.toList());
        WinChecker.Outcome o = WinChecker.check(roles, redExisted);
        if (o == WinChecker.Outcome.NONE) return false;
        finish(o);
        return true;
    }

    private void finish(WinChecker.Outcome outcome) {
        if (phase == Phase.ENDED) return;
        phase = Phase.ENDED;
        clearStep();
        clearPrompts();
        String key = switch (outcome) {
            case GREEN -> "win-green";
            case RED -> "win-red";
            case PYROMANE -> "win-pyro";
            case ANGE -> "win-ange";
            default -> "win-none";
        };
        broadcast(msg.prefixed(key));
        for (Player p : onlinePlayers()) p.showTitle(Title.title(msg.get(key), Component.empty()));
        StringBuilder sb = new StringBuilder();
        for (PlayerData pd : players.values()) sb.append(pd.name).append(" (").append(pd.role.display()).append(") ");
        broadcast(Component.text(sb.toString().trim()));
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 200L);
    }

    public void stop() {
        if (phase != Phase.ENDED) {
            broadcast(msg.prefixed("game-stopped"));
            phase = Phase.ENDED;
        }
        cleanup();
    }

    private void cleanup() {
        clearStep();
        clearPrompts();
        for (UUID id : new ArrayList<>(players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        World w = arena.world();
        if (w != null) {
            w.setStorm(false);
            w.setTime(cfg.getInt("effects.day-time", 1000));
        }
        players.clear();
        plugin.dropGame(this);
    }

    private void restore(Player p) {
        p.hideBossBar(bar);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        Snapshot s = snapshots.remove(p.getUniqueId());
        p.closeInventory();
        if (s != null) {
            p.getInventory().setContents(s.contents());
            p.setGameMode(s.mode());
            p.setFoodLevel(s.food());
            p.setHealth(Math.min(20, Math.max(1, s.health())));
            p.teleport(s.location());
        } else {
            p.getInventory().clear();
        }
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        for (UUID id : new ArrayList<>(players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
        players.clear();
    }

    // ------------------------------------------------------------------ steps / ticker

    private void step(Component title, int seconds, BooleanSupplier early, Runnable end) {
        secondsLeft = seconds;
        secondsTotal = Math.max(1, seconds);
        doneEarly = early;
        onEnd = end;
        bar.name(title);
        bar.progress(1f);
    }

    private void clearStep() {
        onEnd = null;
        doneEarly = null;
    }

    private void tickSecond() {
        if (phase == Phase.NIGHT && hunt != null && !hunt.over && secondsLeft % 3 == 0) radioFeed();
        if (onEnd == null) return;
        secondsLeft--;
        bar.progress(Math.max(0f, Math.min(1f, secondsLeft / (float) secondsTotal)));
        if (phase == Phase.LOBBY && secondsLeft > 0 && secondsLeft <= 5) {
            broadcast(msg.prefixed("countdown", secondsLeft));
        }
        if (secondsLeft <= 0 || (doneEarly != null && doneEarly.getAsBoolean())) {
            Runnable r = onEnd;
            clearStep();
            r.run();
        }
    }

    private void radioFeed() {
        if (radioTarget == null) return;
        PlayerData radio = players.values().stream().filter(p -> p.alive && p.role == Role.RADIO).findFirst().orElse(null);
        PlayerData target = players.get(radioTarget);
        Player rp = online(radio);
        if (rp == null || target == null) return;
        String zone = zoneOf(cabinLocation(target));
        List<String> names = new ArrayList<>();
        for (PlayerData pd : alive()) {
            Player p = online(pd);
            if (p != null && zone.equals(zoneOf(p.getLocation()))) names.add(pd.name);
        }
        rp.sendActionBar(names.isEmpty() ? msg.get("radio-empty") : msg.get("radio-listen", String.join(", ", names)));
    }

    // ------------------------------------------------------------------ prompts

    private void prompt(PlayerData pd, Component title, List<Option> options, java.util.function.Consumer<Object> callback) {
        Player p = online(pd);
        if (p == null) return;
        Prompt prompt = new Prompt(pd.uuid, title, options, callback);
        prompts.put(pd.uuid, prompt);
        ChoiceGui.open(p, prompt);
    }

    public void answer(Player player, Prompt prompt, Option option) {
        if (prompts.get(player.getUniqueId()) != prompt) return;
        prompts.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(msg.prefixed("action-done"));
        prompt.callback().accept(option.value());
    }

    public boolean reopenPrompt(Player player) {
        Prompt pr = prompts.get(player.getUniqueId());
        if (pr == null) return false;
        ChoiceGui.open(player, pr);
        return true;
    }

    private void clearPrompts() {
        for (UUID id : new ArrayList<>(prompts.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof ChoiceGui) p.closeInventory();
        }
        prompts.clear();
    }

    private List<Option> playerOptions(Predicate<PlayerData> filter) {
        List<Option> options = new ArrayList<>();
        for (PlayerData t : alive()) {
            if (filter.test(t)) options.add(new Option(Component.text(t.name), ChoiceGui.head(t.uuid, t.name), t.uuid));
        }
        return options;
    }

    private Option skipOption() {
        return new Option(msg.get("skip"), ChoiceGui.icon(Material.BARRIER), "skip");
    }

    // ------------------------------------------------------------------ audience (chat + voice)

    public String zoneOf(Location l) {
        double radius = cfg.getDouble("game.cabin-radius", 3.0);
        for (Arena.Cabin c : arena.cabins()) {
            Location cl = c.location();
            if (cl.getWorld() != null && cl.getWorld().equals(l.getWorld()) && cl.distanceSquared(l) <= radius * radius) {
                return "cabin:" + c.id();
            }
        }
        String wagon = arena.wagonAt(l);
        return wagon != null ? "wagon:" + wagon : "out";
    }

    /** Players allowed to hear {@code sender}; null when the sender is not part of the game (do not interfere). */
    public List<Player> audience(Player sender) {
        PlayerData pd = players.get(sender.getUniqueId());
        if (pd == null || phase == Phase.LOBBY || phase == Phase.ENDED) return null;
        List<Player> result = new ArrayList<>();
        List<Player> dead = players.values().stream().filter(p -> !p.alive).map(this::online)
                .filter(p -> p != null).collect(Collectors.toList());
        if (!pd.alive) return dead;
        result.addAll(dead);

        if (phase == Phase.NIGHT) {
            if (cannibalDebate && pd.unlocked && pd.role == Role.CANNIBALE) {
                for (PlayerData o : alive()) {
                    Player p = online(o);
                    if (p != null && o.unlocked && o.role == Role.CANNIBALE) result.add(p);
                }
                return result;
            }
            if (hunt == null) return result;
        }
        String zone = zoneOf(sender.getLocation());
        for (PlayerData o : alive()) {
            Player p = online(o);
            if (p != null && p != sender && zone.equals(zoneOf(p.getLocation()))) result.add(p);
        }
        if (phase == Phase.NIGHT && radioTarget != null) {
            PlayerData target = players.get(radioTarget);
            PlayerData radio = players.values().stream().filter(p -> p.alive && p.role == Role.RADIO).findFirst().orElse(null);
            Player rp = online(radio);
            if (target != null && rp != null && zone.equals(zoneOf(cabinLocation(target))) && !result.contains(rp)) result.add(rp);
        }
        return result;
    }

    public boolean isCannibalDebate(Player p) {
        PlayerData pd = players.get(p.getUniqueId());
        return phase == Phase.NIGHT && cannibalDebate && pd != null && pd.alive && pd.unlocked && pd.role == Role.CANNIBALE;
    }

    public boolean mustStayInCabin(Player p) {
        PlayerData pd = players.get(p.getUniqueId());
        if (pd == null || !pd.alive || pd.cabin == null) return false;
        return (phase == Phase.NIGHT && !pd.unlocked) || ((phase == Phase.DAY || phase == Phase.NIGHT) && pd.isConfined(tick()));
    }

    public Location cabinOf(Player p) {
        PlayerData pd = players.get(p.getUniqueId());
        return pd == null ? null : cabinLocation(pd);
    }

    // ------------------------------------------------------------------ misc helpers

    private List<Player> onlinePlayers() {
        return players.values().stream().map(this::online).filter(p -> p != null).collect(Collectors.toList());
    }

    private void broadcast(Component c) {
        for (Player p : onlinePlayers()) p.sendMessage(c);
    }

    private void applyDayEffects() {
        World w = arena.world();
        if (w == null) return;
        w.setTime(cfg.getInt("effects.day-time", 1000));
        w.setStorm(false);
    }

    private void applyNightEffects() {
        World w = arena.world();
        if (w == null) return;
        w.setTime(cfg.getInt("effects.night-time", 18000));
        if (cfg.getBoolean("effects.night-storm", true)) w.setStorm(true);
    }
}
