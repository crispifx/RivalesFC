package com.rivalesfc.game;

import com.badlogic.gdx.graphics.Color;

/**
 * MK6: equipos y nombres del partido en curso. Estado global (igual que {@link Settings}):
 * el lobby lo prepara antes de crear la pantalla de partido y, online, la pantalla de partido
 * lo actualiza cuando llega la identidad del rival.
 * Izquierda = jugador 1 / host (arco izquierdo); derecha = jugador 2 / IA / cliente.
 */
public final class MatchSetup {

    private MatchSetup() {
    }

    public static Team leftTeam = Team.get(0);
    public static Team rightTeam = Team.get(1);
    public static String leftName = Constants.PLAYER_LEFT_NAME;
    public static String rightName = Constants.PLAYER_RIGHT_NAME;

    public static Color leftColor() {
        return leftTeam.color;
    }

    public static Color rightColor() {
        return rightTeam.color;
    }

    /** Prepara el partido con lo elegido en el lobby (modos locales y lado propio de los online). */
    public static void prepare(GameMode mode) {
        String own = ownName();
        int ownTeam = Settings.teamIndex;
        int rivalTeam = Settings.rivalTeamIndex;
        switch (mode) {
            case ONLINE_CLIENT:
                // Hasta que llegue la identidad del host, el cliente se ve a la derecha con su equipo.
                set(ownTeam == 0 ? 1 : 0, "ANFITRION", ownTeam, own);
                break;
            case TWO_PLAYERS:
                set(ownTeam, own, rivalTeam, "JUGADOR 2");
                break;
            case ONE_PLAYER:
                set(ownTeam, own, rivalTeam, "CPU");
                break;
            default: // ONLINE_HOST
                set(ownTeam, own, rivalTeam, "RIVAL");
                break;
        }
    }

    /** Online: fija ambos lados con índices y nombres ya conocidos (host = izquierda, cliente = derecha). */
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
