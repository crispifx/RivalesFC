package com.rivalesfc.game;

public enum GameMode {
    ONE_PLAYER,
    TWO_PLAYERS,

    TWO_VS_TWO,

    FOUR_PLAYERS,

    ONLINE_HOST,

    ONLINE_CLIENT;

    public boolean isTeamMode() {
        return this == TWO_VS_TWO || this == FOUR_PLAYERS;
    }

    public boolean isOnline() {
        return this == ONLINE_HOST || this == ONLINE_CLIENT;
    }
}
