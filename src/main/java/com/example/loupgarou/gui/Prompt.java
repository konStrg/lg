package com.example.loupgarou.gui;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public record Prompt(UUID player, Component title, List<Option> options, Consumer<Object> callback) {}
