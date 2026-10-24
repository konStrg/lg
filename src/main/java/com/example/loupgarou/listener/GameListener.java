package com.example.loupgarou.listener;

import com.example.loupgarou.LoupGarouPlugin;
import com.example.loupgarou.game.Game;
import com.example.loupgarou.gui.ChoiceGui;
import com.example.loupgarou.gui.Option;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class GameListener implements Listener {

    private final LoupGarouPlugin plugin;

    public GameListener(LoupGarouPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getView().getTopInventory().getHolder() instanceof ChoiceGui gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            int slot = e.getSlot();
            Game game = plugin.gameOf(player);
            if (game != null && slot >= 0 && slot < gui.prompt().options().size()) {
                Option option = gui.prompt().options().get(slot);
                game.answer(player, gui.prompt(), option);
            }
            return;
        }
        if (plugin.gameOf(player) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && plugin.gameOf(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Game game = plugin.gameOf(e.getPlayer());
        if (game == null || !game.mustStayInCabin(e.getPlayer())) return;
        Location cabin = game.cabinOf(e.getPlayer());
        double radius = plugin.getConfig().getDouble("game.cabin-radius", 3.0);
        Location to = e.getTo();
        if (cabin == null || to.getWorld() != cabin.getWorld() || to.distanceSquared(cabin) > radius * radius) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game game = plugin.gameOf(victim);
        if (game == null) return;
        Player damager = e instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player p ? p : null;
        Game.DamageResult result = game.onDamage(victim, damager, e.getFinalDamage());
        if (result == Game.DamageResult.CANCEL) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.gameOf(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (plugin.gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        Game game = plugin.gameOf(player);
        if (game == null) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());

        if (game.matchesEvent(player, text)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> game.handleEventChat(player, text));
            return;
        }
        List<Player> audience = game.audience(player);
        if (audience == null) return;
        if (game.isCannibalDebate(player)) {
            e.setCancelled(true);
            Component line = plugin.msg().get("cannibal-chat", player.getName(), text);
            player.sendMessage(line);
            audience.forEach(p -> p.sendMessage(line));
            return;
        }
        e.viewers().removeIf(a -> a instanceof Player p && p != player && !audience.contains(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game game = plugin.gameOf(e.getPlayer());
        if (game != null && game.phase() == Game.Phase.LOBBY) game.leave(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Game game = plugin.gameOf(e.getPlayer());
        if (game != null) Bukkit.getScheduler().runTask(plugin, () -> game.rejoin(e.getPlayer()));
    }
}
