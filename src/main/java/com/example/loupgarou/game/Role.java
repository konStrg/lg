package com.example.loupgarou.game;

import org.bukkit.Material;

public enum Role {
    VOYAGEUR(Faction.GREEN, "Voyageur", Material.LEATHER_BOOTS,
            "Aucun pouvoir. Vous votez et participez aux evenements."),
    MEDECIN(Faction.GREEN, "Medecin legiste", Material.GOLDEN_APPLE,
            "Une fois, apres la mort causee par un cannibale, vous pouvez ressusciter la victime."),
    BARRICADEUR(Faction.GREEN, "Barricadeur", Material.OAK_PLANKS,
            "Barricadez une cabine ou un wagon contre le cannibale (pas deux nuits de suite)."),
    RADIO(Faction.GREEN, "Radio-amateur", Material.NOTE_BLOCK,
            "Ecoutez et observez une cabine pendant la traque des cannibales."),
    VETERAN(Faction.GREEN, "Veteran", Material.STONE_SWORD,
            "Si on vous traque, vous recevez une arme plus faible que celle du cannibale."),
    KAMIKAZE(Faction.GREEN, "Kamikaze", Material.TNT,
            "Sacrifiez-vous pour tuer un cannibale. Si la cible n'en est pas un, vous mourez seul."),
    CUPIDON(Faction.GREEN, "Cupidon", Material.ROSE_BUSH,
            "Liez deux joueurs : si l'un meurt, l'autre se pend avant le vote suivant."),
    CANNIBALE(Faction.RED, "Cannibale", Material.BONE,
            "La nuit, debattez de la victime avec les autres cannibales. L'un de vous la traque."),
    PYROMANE(Faction.GREY, "Pyromane", Material.FLINT_AND_STEEL,
            "Trempez un wagon par nuit. Apres 3 wagons, allumez le feu et gagnez seul."),
    ANGE(Faction.GREY, "Ange", Material.FEATHER,
            "Vous gagnez seul si vous etes expulse au premier vote du conseil.");

    private final Faction faction;
    private final String display;
    private final Material icon;
    private final String description;

    Role(Faction faction, String display, Material icon, String description) {
        this.faction = faction;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public Faction faction() {
        return faction;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean isPoweredGreen() {
        return faction == Faction.GREEN && this != VOYAGEUR;
    }
}
