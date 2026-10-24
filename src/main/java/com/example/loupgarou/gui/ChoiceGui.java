package com.example.loupgarou.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ChoiceGui implements InventoryHolder {

    private final Prompt prompt;
    private final Inventory inventory;

    public ChoiceGui(Prompt prompt) {
        this.prompt = prompt;
        int size = Math.min(54, Math.max(9, ((prompt.options().size() + 8) / 9) * 9));
        this.inventory = Bukkit.createInventory(this, size, prompt.title());
        for (int i = 0; i < prompt.options().size() && i < size; i++) {
            Option o = prompt.options().get(i);
            ItemStack icon = o.icon().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(o.label().decoration(TextDecoration.ITALIC, false));
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }
    }

    public static void open(Player player, Prompt prompt) {
        player.openInventory(new ChoiceGui(prompt).getInventory());
    }

    public Prompt prompt() {
        return prompt;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static ItemStack head(UUID uuid, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack icon(Material material) {
        return new ItemStack(material);
    }
}
