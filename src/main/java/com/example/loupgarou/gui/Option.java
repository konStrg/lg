package com.example.loupgarou.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

public record Option(Component label, ItemStack icon, Object value) {}
