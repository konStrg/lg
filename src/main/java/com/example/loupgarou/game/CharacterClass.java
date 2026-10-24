package com.example.loupgarou.game;

public enum CharacterClass {
    FIRST("1ere classe"),
    SECOND("2eme classe"),
    THIRD("3eme classe");

    private final String display;

    CharacterClass(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
