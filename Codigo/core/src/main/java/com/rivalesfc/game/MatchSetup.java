package com.rivalesfc.game;

import com.badlogic.gdx.graphics.Color;

public final class MatchSetup {

    private MatchSetup() {
    }

    public static Team leftTeam = Team.get(0);
    public static Team rightTeam = Team.get(1);
    public static String leftName = Constants.PLAYER_LEFT_NAME;
    public static String rightName = Constants.PLAYER_RIGHT_NAME;

    public static String leftMateName = "JUGADOR 2";
    public static String rightMateName = "CPU 2";

    public static Color leftColor() {
        return leftTeam.color;
    }

    public static Color rightColor() {
        return rightTeam.color;
    }

    public static void prepare(GameMode mode) {
        String own = ownName();
        int ownTeam = Settings.teamIndex;
        int rivalTeam = Settings.rivalTeamIndex;
        switch (mode) {
            case ONLINE_CLIENT:

                set(ownTeam == 0 ? 1 : 0, "ANFITRION", ownTeam, own);
                break;
            case TWO_PLAYERS:
                set(ownTeam, own, rivalTeam, "JUGADOR 2");
                break;
            case ONE_PLAYER:
                set(ownTeam, own, rivalTeam, "CPU");
                break;
            case TWO_VS_TWO:
                set(ownTeam, own, rivalTeam, "CPU 1");
                leftMateName = "JUGADOR 2";
                rightMateName = "CPU 2";
                break;
            case FOUR_PLAYERS:

                set(ownTeam, own, rivalTeam, "JUGADOR 3");
                leftMateName = "JUGADOR 2";
                rightMateName = "JUGADOR 4";
                break;
            default:
                set(ownTeam, own, rivalTeam, "RIVAL");
                break;
        }
    }

    public static void setOnline(int hostTeam, String hostName, int clientTeam, String clientName) {
        set(hostTeam, hostName, clientTeam, clientName);
    }

    private static void set(int leftIndex, String left, int rightIndex, String right) {
        leftTeam = Team.get(leftIndex);
        rightTeam = Team.get(Team.distinctRight(leftIndex, rightIndex));
        leftName = left.isEmpty() ? "JUGADOR" : left;
        rightName = right.isEmpty() ? "RIVAL" : right;
    }

    public static String ownName() {
        String n = Settings.playerName == null ? "" : Settings.playerName.trim();
        return n.isEmpty() ? "JUGADOR" : n;
    }
}
