package com.example.loupgarou.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

public class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final YamlConfiguration yaml;

    public Msg(Plugin plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        this.yaml = YamlConfiguration.loadConfiguration(f);
    }

    public String raw(String key, Object... args) {
        String s = yaml.getString(key, key);
        return args.length == 0 ? s : String.format(s, args);
    }

    public Component get(String key, Object... args) {
        return LEGACY.deserialize(raw(key, args));
    }

    public Component prefixed(String key, Object... args) {
        return LEGACY.deserialize(raw("prefix") + raw(key, args));
    }

    public static Component color(String legacy) {
        return LEGACY.deserialize(legacy);
    }
}
