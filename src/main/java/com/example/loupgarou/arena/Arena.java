package com.example.loupgarou.arena;

import com.example.loupgarou.game.CharacterClass;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Arena {

    public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Region of(Location a, Location b) {
            return new Region(a.getWorld().getName(),
                    Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                    Math.min(a.getBlockZ(), b.getBlockZ()),
                    Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()),
                    Math.max(a.getBlockZ(), b.getBlockZ()));
        }

        public boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= minX && l.getBlockX() <= maxX
                    && l.getBlockY() >= minY && l.getBlockY() <= maxY
                    && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }
    }

    public record Cabin(int id, CharacterClass cls, Location location) {}

    private final File file;
    private Location lobby;
    private Location council;
    private Location debate;
    private Location eject;
    private final Map<String, Region> wagons = new LinkedHashMap<>();
    private final List<Cabin> cabins = new ArrayList<>();

    public Arena(File file) {
        this.file = file;
        load();
    }

    public Location lobby() { return lobby; }
    public Location council() { return council; }
    public Location debate() { return debate != null ? debate : council; }
    public Location eject() { return eject != null ? eject : lobby; }
    public Map<String, Region> wagons() { return wagons; }
    public List<Cabin> cabins() { return cabins; }

    public void setLobby(Location l) { lobby = l.clone(); save(); }
    public void setCouncil(Location l) { council = l.clone(); save(); }
    public void setDebate(Location l) { debate = l.clone(); save(); }
    public void setEject(Location l) { eject = l.clone(); save(); }

    public void setWagon(String name, Region region) {
        wagons.put(name.toLowerCase(), region);
        save();
    }

    public Cabin addCabin(CharacterClass cls, Location l) {
        Cabin cabin = new Cabin(cabins.size(), cls, l.clone());
        cabins.add(cabin);
        save();
        return cabin;
    }

    public void clearCabins() {
        cabins.clear();
        save();
    }

    public String wagonAt(Location l) {
        for (Map.Entry<String, Region> e : wagons.entrySet()) {
            if (e.getValue().contains(l)) return e.getKey();
        }
        return null;
    }

    public int maxPlayers() {
        return cabins.size();
    }

    public List<String> missing(int minPlayers) {
        List<String> missing = new ArrayList<>();
        if (lobby == null) missing.add("lobby");
        if (council == null) missing.add("council");
        if (wagons.isEmpty()) missing.add("wagon (au moins 1)");
        if (cabins.size() < minPlayers) missing.add("cabin (" + cabins.size() + "/" + minPlayers + ")");
        return missing;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        lobby = y.getLocation("lobby");
        council = y.getLocation("council");
        debate = y.getLocation("debate");
        eject = y.getLocation("eject");
        ConfigurationSection w = y.getConfigurationSection("wagons");
        if (w != null) {
            for (String name : w.getKeys(false)) {
                ConfigurationSection s = w.getConfigurationSection(name);
                if (s == null) continue;
                wagons.put(name, new Region(s.getString("world", "world"),
                        s.getInt("minX"), s.getInt("minY"), s.getInt("minZ"),
                        s.getInt("maxX"), s.getInt("maxY"), s.getInt("maxZ")));
            }
        }
        List<?> list = y.getList("cabins", List.of());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                try {
                    CharacterClass cls = CharacterClass.valueOf(String.valueOf(m.get("class")));
                    Object loc = m.get("location");
                    if (loc instanceof Location l) {
                        cabins.add(new Cabin(cabins.size(), cls, l));
                    }
                } catch (IllegalArgumentException ignored) {
                    // unknown class in file
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("lobby", lobby);
        y.set("council", council);
        y.set("debate", debate);
        y.set("eject", eject);
        for (Map.Entry<String, Region> e : wagons.entrySet()) {
            String p = "wagons." + e.getKey();
            Region r = e.getValue();
            y.set(p + ".world", r.world());
            y.set(p + ".minX", r.minX());
            y.set(p + ".minY", r.minY());
            y.set(p + ".minZ", r.minZ());
            y.set(p + ".maxX", r.maxX());
            y.set(p + ".maxY", r.maxY());
            y.set(p + ".maxZ", r.maxZ());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Cabin c : cabins) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("class", c.cls().name());
            m.put("location", c.location());
            list.add(m);
        }
        y.set("cabins", list);
        try {
            y.save(file);
        } catch (IOException ex) {
            Bukkit.getLogger().warning("Impossible de sauvegarder l'arene: " + ex.getMessage());
        }
    }

    public World world() {
        return lobby != null ? lobby.getWorld() : null;
    }
}
