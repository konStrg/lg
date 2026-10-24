package com.example.loupgarou;

import com.example.loupgarou.arena.Arena;
import com.example.loupgarou.command.LgCommand;
import com.example.loupgarou.game.Game;
import com.example.loupgarou.listener.GameListener;
import com.example.loupgarou.util.Msg;
import com.example.loupgarou.voice.VoiceHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LoupGarouPlugin extends JavaPlugin {

    private Arena arena;
    private Msg msg;
    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        msg = new Msg(this);
        arena = new Arena(new File(getDataFolder(), "arena.yml"));

        LgCommand command = new LgCommand(this);
        getCommand("lg").setExecutor(command);
        getCommand("lg").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        if (getConfig().getBoolean("voice.enabled", true)
                && getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                VoiceHook.register(this);
                getLogger().info("Simple Voice Chat detecte: integration activee.");
            } catch (Throwable t) {
                getLogger().warning("Integration Simple Voice Chat impossible: " + t);
            }
        }
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
    }

    public Arena arena() { return arena; }
    public Msg msg() { return msg; }
    public Game game() { return game; }

    public Game gameOf(Player player) {
        return game != null && game.data(player.getUniqueId()) != null ? game : null;
    }

    public Game getOrCreateGame() {
        if (game == null) game = new Game(this);
        return game;
    }

    public void dropGame(Game g) {
        if (game == g) {
            g.shutdown();
            game = null;
        }
    }
}
