package com.example.loupgarou.game;

import com.example.loupgarou.arena.Arena;

import java.util.UUID;

public class PlayerData {

    public final UUID uuid;
    public final String name;
    public Role role = Role.VOYAGEUR;
    public CharacterClass cls = CharacterClass.THIRD;
    public Arena.Cabin cabin;
    public boolean alive = true;
    /** Timeline tick (see Game#tick) until which the player is confined to his cabin. */
    public int confinedUntil = -1;
    /** Free to move at night (cannibals debating, hunter, hunted). */
    public boolean unlocked = false;
    public UUID lover;
    public boolean loverSuicidePending = false;
    public boolean powerUsed = false;
    public int lastBarricadeNight = -10;
    public boolean captain = false;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public boolean isConfined(int tick) {
        return tick <= confinedUntil;
    }
}
