package com.example.loupgarou.command;

import com.example.loupgarou.LoupGarouPlugin;
import com.example.loupgarou.arena.Arena;
import com.example.loupgarou.game.CharacterClass;
import com.example.loupgarou.game.Game;
import com.example.loupgarou.game.PlayerData;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LgCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("join", "leave", "start", "stop", "menu", "role", "setup");
    private static final List<String> SETUP = List.of("lobby", "council", "debate", "eject", "pos1", "pos2",
            "wagon", "cabin", "clearcabins", "status");

    private final LoupGarouPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public LgCommand(LoupGarouPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var msg = plugin.msg();
        if (args.length == 0) {
            sender.sendMessage(msg.prefixed("not-in-game"));
            sender.sendMessage(net.kyori.adventure.text.Component.text("/lg " + String.join("|", SUB)));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("player-only"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> join(player);
            case "leave" -> {
                Game g = plugin.gameOf(player);
                if (g == null) player.sendMessage(msg.prefixed("not-in-game"));
                else g.leave(player);
            }
            case "start" -> {
                if (!player.hasPermission("lg.admin")) {
                    player.sendMessage(msg.get("no-permission"));
                    return true;
                }
                Game g = plugin.game();
                if (g == null || !g.forceStart()) {
                    player.sendMessage(msg.prefixed("not-enough", plugin.getConfig().getInt("game.min-players", 5)));
                }
            }
            case "stop" -> {
                if (!player.hasPermission("lg.admin")) {
                    player.sendMessage(msg.get("no-permission"));
                    return true;
                }
                Game g = plugin.game();
                if (g != null) g.stop();
            }
            case "menu" -> {
                Game g = plugin.gameOf(player);
                if (g == null || !g.reopenPrompt(player)) player.sendMessage(msg.prefixed("no-menu"));
            }
            case "role" -> {
                Game g = plugin.gameOf(player);
                PlayerData pd = g == null ? null : g.data(player.getUniqueId());
                if (pd == null || !g.isRunning()) {
                    player.sendMessage(msg.prefixed("not-in-game"));
                } else {
                    player.sendMessage(msg.prefixed("your-role", pd.role.display()));
                    player.sendMessage(msg.prefixed("your-role-desc", pd.role.description()));
                    player.sendMessage(msg.prefixed("your-class", pd.cls.display()));
                }
            }
            case "setup" -> {
                if (!player.hasPermission("lg.admin")) {
                    player.sendMessage(msg.get("no-permission"));
                    return true;
                }
                setup(player, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> player.sendMessage(net.kyori.adventure.text.Component.text("/lg " + String.join("|", SUB)));
        }
        return true;
    }

    private void join(Player player) {
        var msg = plugin.msg();
        if (plugin.gameOf(player) != null) {
            player.sendMessage(msg.prefixed("already-in-game"));
            return;
        }
        List<String> missing = plugin.arena().missing(plugin.getConfig().getInt("game.min-players", 5));
        if (!missing.isEmpty()) {
            player.sendMessage(msg.prefixed("arena-incomplete", String.join(", ", missing)));
            return;
        }
        Game g = plugin.getOrCreateGame();
        if (!g.join(player)) player.sendMessage(msg.prefixed("game-running"));
    }

    private void setup(Player p, String[] a) {
        var msg = plugin.msg();
        Arena arena = plugin.arena();
        if (a.length == 0) {
            p.sendMessage(net.kyori.adventure.text.Component.text("/lg setup " + String.join("|", SETUP)));
            return;
        }
        Location here = p.getLocation();
        switch (a[0].toLowerCase()) {
            case "lobby" -> { arena.setLobby(here); p.sendMessage(msg.prefixed("setup-ok", "lobby")); }
            case "council" -> { arena.setCouncil(here); p.sendMessage(msg.prefixed("setup-ok", "council")); }
            case "debate" -> { arena.setDebate(here); p.sendMessage(msg.prefixed("setup-ok", "debate")); }
            case "eject" -> { arena.setEject(here); p.sendMessage(msg.prefixed("setup-ok", "eject")); }
            case "pos1" -> { pos1.put(p.getUniqueId(), here); p.sendMessage(msg.prefixed("setup-ok", "pos1")); }
            case "pos2" -> { pos2.put(p.getUniqueId(), here); p.sendMessage(msg.prefixed("setup-ok", "pos2")); }
            case "wagon" -> {
                Location l1 = pos1.get(p.getUniqueId());
                Location l2 = pos2.get(p.getUniqueId());
                if (a.length < 2 || l1 == null || l2 == null) {
                    p.sendMessage(net.kyori.adventure.text.Component.text("/lg setup pos1, pos2 puis /lg setup wagon <nom>"));
                    return;
                }
                arena.setWagon(a[1], Arena.Region.of(l1, l2));
                p.sendMessage(msg.prefixed("setup-ok", "wagon " + a[1]));
            }
            case "cabin" -> {
                if (a.length < 2) {
                    p.sendMessage(net.kyori.adventure.text.Component.text("/lg setup cabin <FIRST|SECOND|THIRD>"));
                    return;
                }
                try {
                    CharacterClass cls = CharacterClass.valueOf(a[1].toUpperCase());
                    Arena.Cabin c = arena.addCabin(cls, here);
                    p.sendMessage(msg.prefixed("setup-ok", "cabin #" + c.id() + " " + cls.display()));
                } catch (IllegalArgumentException ex) {
                    p.sendMessage(net.kyori.adventure.text.Component.text("FIRST, SECOND ou THIRD"));
                }
            }
            case "clearcabins" -> { arena.clearCabins(); p.sendMessage(msg.prefixed("setup-ok", "cabins effaces")); }
            case "status" -> {
                int min = plugin.getConfig().getInt("game.min-players", 5);
                List<String> missing = arena.missing(min);
                p.sendMessage(net.kyori.adventure.text.Component.text("Cabines: " + arena.cabins().size()
                        + " | Wagons: " + arena.wagons().keySet()
                        + (missing.isEmpty() ? " | OK" : " | manque: " + String.join(", ", missing))));
            }
            default -> p.sendMessage(net.kyori.adventure.text.Component.text("/lg setup " + String.join("|", SETUP)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUB.stream().filter(s -> s.startsWith(args[0].toLowerCase())).forEach(out::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            SETUP.stream().filter(s -> s.startsWith(args[1].toLowerCase())).forEach(out::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("cabin")) {
            for (CharacterClass c : CharacterClass.values()) out.add(c.name());
        }
        return out;
    }
}
