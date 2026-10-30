package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.MatchSetup;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.Team;
import com.rivalesfc.game.net.NetClient;
import com.rivalesfc.game.net.NetHost;
import com.rivalesfc.game.net.NetInfo;
import com.rivalesfc.game.net.Protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Preparación del partido (MK8): modo de juego, nombre y equipos en su propia pantalla (antes estaba todo en el
 * lobby). A la izquierda la lista de modos (el elegido queda resaltado); a la derecha el enfrentamiento con las
 * camisetas y los campos de nombre / equipo / rival / IP. Las filas se recorren con arriba-abajo.
 */
public class SetupScreen extends MenuScreen {

    static final GameMode[] MODES = {GameMode.ONE_PLAYER, GameMode.TWO_PLAYERS, GameMode.TWO_VS_TWO,
            GameMode.ONLINE_HOST, GameMode.ONLINE_CLIENT};
    private static final String[] TITLES = {"1 JUGADOR", "2 JUGADORES", "2 VS 2 COOPERATIVO", "ONLINE: CREAR PARTIDA", "ONLINE: UNIRSE"};
    private static final String[] DESCS = {
            "Pantalla completa: vos (WASD) contra la IA",
            "Pantalla dividida local: WASD vs. Flechas",
            "Dos compañeros (WASD + Flechas) contra 2 IA",
            "Sos el anfitrión: tu rival entra con tu IP",
            "Entrá a la partida de un anfitrión con su IP"};

    private static final int R_MODE = 0, R_NAME = 1, R_TEAM = 2, R_RIVAL = 3, R_IP = 4, R_START = 5;
    private static final int ID_MODE_BASE = 50;     // 50..54: clic en un modo
    private static final int ID_LEFT = 200, ID_RIGHT = 100;

    private int mode = Settings.modeIndex;
    private int focus = R_MODE;
    private String joinIp = Settings.lastHostIp;
    private String status = "";
    private final String localIp = NetInfo.localAddress();

    public SetupScreen(RivalesFCGame game) {
        super(game);
    }

    private boolean online() {
        return mode >= 3;
    }

    /** Filas que se pueden recorrer según el modo (el rival no se elige online; la IP solo al unirse). */
    private List<Integer> rows() {
        List<Integer> r = new ArrayList<>();
        r.add(R_MODE);
        r.add(R_NAME);
        r.add(R_TEAM);
        if (!online()) r.add(R_RIVAL);
        if (mode == 4) r.add(R_IP);
        r.add(R_START);
        return r;
    }

    @Override
    public void show() {
        super.show();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (focus == R_IP && (Character.isDigit(c) || c == '.' || c == ':') && joinIp.length() < 21) {
                    joinIp += c;
                    status = "";
                    return true;
                }
                if (focus == R_NAME && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')
                        && Settings.playerName.length() < Protocol.MAX_NAME_LEN) {
                    Settings.playerName += Character.toUpperCase(c);
                    return true;
                }
                return false;
            }
        });
    }

    private void move(int d) {
        List<Integer> r = rows();
        int i = Math.max(0, r.indexOf(focus));
        focus = r.get((i + d + r.size()) % r.size());
    }

    private void change(int row, int dir) {
        int n = Team.ALL.length;
        if (row == R_MODE) {
            mode = (mode + dir + MODES.length) % MODES.length;
            Settings.modeIndex = mode;
            status = "";
        } else if (row == R_TEAM) {
            Settings.teamIndex = (Settings.teamIndex + dir + n) % n;
            if (Settings.rivalTeamIndex == Settings.teamIndex) Settings.rivalTeamIndex = (Settings.rivalTeamIndex + dir + n) % n;
        } else if (row == R_RIVAL) {
            int idx = (Settings.rivalTeamIndex + dir + n) % n;
            if (idx == Settings.teamIndex) idx = (idx + dir + n) % n;
            Settings.rivalTeamIndex = idx;
        } else {
            return;
        }
        Settings.save();
    }

    @Override
    protected void update(float dt, int click) {
        boolean typing = focus == R_NAME || focus == R_IP;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Settings.save();
            game.setScreen(new LobbyScreen(game));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || (!typing && Gdx.input.isKeyJustPressed(Input.Keys.W))) move(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || (!typing && Gdx.input.isKeyJustPressed(Input.Keys.S))
                || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) move(1);
        if (focus == R_NAME && Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && !Settings.playerName.isEmpty()) {
            Settings.playerName = Settings.playerName.substring(0, Settings.playerName.length() - 1);
        }
        if (focus == R_IP && Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && !joinIp.isEmpty()) {
            joinIp = joinIp.substring(0, joinIp.length() - 1);
        }
        int dir = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || (!typing && Gdx.input.isKeyJustPressed(Input.Keys.A))) dir = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || (!typing && Gdx.input.isKeyJustPressed(Input.Keys.D))) dir = 1;
        if (dir != 0) change(focus, dir);

        if (!typing) {
            for (int i = 0; i < MODES.length; i++) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                    mode = i;
                    Settings.modeIndex = i;
                }
            }
        }

        boolean enter = Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
        boolean confirm = enter || (!typing && Gdx.input.isKeyJustPressed(Input.Keys.SPACE));
        boolean start = false;

        if (click >= 0) {
            if (click >= ID_MODE_BASE && click < ID_MODE_BASE + MODES.length) {
                mode = click - ID_MODE_BASE;
                Settings.modeIndex = mode;
                focus = R_MODE;
            } else if (click == 400) {
                focus = R_START;
                start = true;
            } else {
                int r = click % 100;
                if (rows().contains(r)) {
                    focus = r;
                    if (click >= ID_LEFT) change(r, -1);
                    else if (click >= ID_RIGHT) change(r, 1);
                }
            }
        }
        if (confirm) {
            if (focus == R_START || focus == R_IP) start = true;
            else move(1);
        }
        if (start) {
            launch();
        }
    }

    private void launch() {
        Settings.modeIndex = mode;
        GameMode m = MODES[mode];
        if (m == GameMode.ONLINE_HOST) {
            startOnlineHost();
        } else if (m == GameMode.ONLINE_CLIENT) {
            startOnlineClient();
        } else {
            Settings.save();
            MatchSetup.prepare(m);
            game.setScreen(new MatchScreen(game, m));
        }
    }

    private void startOnlineHost() {
        NetHost host = new NetHost();
        try {
            host.start(Protocol.DEFAULT_PORT);
        } catch (IOException e) {
            status = "No se pudo abrir el puerto " + Protocol.DEFAULT_PORT + " (¿ya hay otro juego abierto?)";
            return;
        }
        Settings.save();
        host.setLocalIdentity(Settings.teamIndex, MatchSetup.ownName());
        MatchSetup.prepare(GameMode.ONLINE_HOST);
        game.setScreen(new MatchScreen(game, GameMode.ONLINE_HOST, host, null, localIp));
    }

    private void startOnlineClient() {
        String text = joinIp.trim();
        if (text.isEmpty()) {
            status = "Escribí la IP del anfitrión (la muestra su pantalla de partida).";
            focus = R_IP;
            return;
        }
        String ip = text;
        int port = Protocol.DEFAULT_PORT;
        int colon = text.indexOf(':');
        try {
            if (colon >= 0) {
                ip = text.substring(0, colon);
                port = Integer.parseInt(text.substring(colon + 1));
            }
            NetClient client = new NetClient();
            client.setLocalIdentity(Settings.teamIndex, MatchSetup.ownName());
            client.connect(ip, port);
            Settings.lastHostIp = text;
            Settings.save();
            MatchSetup.prepare(GameMode.ONLINE_CLIENT);
            game.setScreen(new MatchScreen(game, GameMode.ONLINE_CLIENT, null, client, text));
        } catch (IOException | RuntimeException e) {
            status = "IP o puerto inválido: " + text;
        }
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    protected void draw() {
        drawHeader("Preparar partido");

        // --- izquierda: modos ---
        float lx = 50f, lw = 560f, ch = 76f, gap = 8f;
        float y = 600f;
        text("MODO DE JUEGO", lx, y + 18f, 1.1f, GOLD, -1);
        y -= ch + 14f;
        for (int i = 0; i < MODES.length; i++) {
            boolean chosen = i == mode;
            boolean focused = chosen && focus == R_MODE;
            rect(lx, y, lw, ch, chosen ? PANEL_SEL : PANEL);
            border(lx, y, lw, ch, chosen ? GOLD : new Color(0.4f, 0.4f, 0.45f, 1f));
            if (focused) border(lx + 3, y + 3, lw - 6, ch - 6, GOLD);
            text((i + 1) + "", lx + 22f, y + ch - 24f, 1.6f, chosen ? GOLD : GREY, -1);
            text(TITLES[i], lx + 62f, y + ch - 14f, 1.3f, chosen ? GOLD : Color.WHITE, -1);
            text(DESCS[i], lx + 62f, y + 30f, 0.9f, GREY, -1);
            hot(lx, y, lw, ch, ID_MODE_BASE + i);
            y -= ch + gap;
        }

        // --- derecha: enfrentamiento y campos ---
        float rx = 660f, rw = 570f;
        Team mine = Team.get(Settings.teamIndex);
        Team rival = online() ? null : Team.get(Settings.rivalTeamIndex);
        text("ENFRENTAMIENTO", rx, 600f + 18f, 1.1f, GOLD, -1);
        float py = 470f, ph = 120f;
        rect(rx, py, rw, ph, PANEL);
        border(rx, py, rw, ph, new Color(0.4f, 0.4f, 0.45f, 1f));
        drawKit(rx + 24f, py + 18f, 84f, 84f, mine);
        text(mine.name, rx + 126f, py + ph - 26f, 1.4f, mine.color, -1);
        text(Settings.playerName.isEmpty() ? "JUGADOR" : Settings.playerName, rx + 126f, py + 44f, 1.0f, Color.WHITE, -1);
        text("VS", rx + rw / 2f + 20f, py + ph / 2f + 14f, 1.4f, GOLD, 0);
        if (rival != null) {
            drawKit(rx + rw - 108f, py + 18f, 84f, 84f, rival);
            text(rival.name, rx + rw - 126f, py + ph - 26f, 1.4f, rival.color, 1);
            String rn = mode == 0 ? "CPU" : mode == 1 ? "JUGADOR 2" : "CPU 1 + CPU 2";
            text(rn, rx + rw - 126f, py + 44f, 1.0f, Color.WHITE, 1);
        } else {
            text("RIVAL ONLINE", rx + rw - 24f, py + ph - 26f, 1.3f, GREY, 1);
            text("elige su propio equipo", rx + rw - 24f, py + 44f, 0.95f, GREY, 1);
        }

        float h = 56f, ry = 395f;
        drawRow(rx, ry, rw, h, "TU NOMBRE (escribilo)", Settings.playerName + (focus == R_NAME && (int) (time * 2) % 2 == 0 ? "_" : ""),
                focus == R_NAME, false, R_NAME, 0, 0);
        ry -= h + 10f;
        drawRow(rx, ry, rw, h, "TU EQUIPO", mine.name, focus == R_TEAM, true, R_TEAM, ID_LEFT + R_TEAM, ID_RIGHT + R_TEAM);
        if (!online()) {
            ry -= h + 10f;
            String lab = mode == 0 ? "EQUIPO RIVAL (IA)" : mode == 1 ? "EQUIPO DEL JUGADOR 2" : "EQUIPO RIVAL (2 IA)";
            drawRow(rx, ry, rw, h, lab, rival.name, focus == R_RIVAL, true, R_RIVAL, ID_LEFT + R_RIVAL, ID_RIGHT + R_RIVAL);
        }
        if (mode == 4) {
            ry -= h + 10f;
            drawRow(rx, ry, rw, h, "IP DEL ANFITRIÓN", joinIp + (focus == R_IP && (int) (time * 2) % 2 == 0 ? "_" : ""),
                    focus == R_IP, false, R_IP, 0, 0);
        } else if (mode == 3) {
            ry -= h + 10f;
            text("Tu IP para pasarle al otro jugador:  " + localIp, rx, ry + h - 18f, 1.0f, new Color(0.9f, 0.95f, 0.7f, 1f), -1);
        }

        // --- botón de comenzar ---
        float bw = 460f, bh = 70f, bx = VW / 2f - bw / 2f, by = 78f;
        boolean sel = focus == R_START;
        rect(bx, by, bw, bh, sel ? new Color(0.22f, 0.45f, 0.24f, 1f) : PANEL);
        border(bx, by, bw, bh, sel ? GOLD : new Color(0.55f, 0.55f, 0.6f, 1f));
        if (sel) border(bx + 3, by + 3, bw - 6, bh - 6, GOLD);
        String go = mode == 3 ? "CREAR PARTIDA" : mode == 4 ? "UNIRSE" : "¡A JUGAR!";
        text(go, VW / 2f, by + bh - 16f, 1.7f, sel ? GOLD : Color.WHITE, 0);
        hot(bx, by, bw, bh, 400);

        if (!status.isEmpty()) {
            text(status, VW / 2f, 162f, 1.0f, new Color(1f, 0.45f, 0.4f, 1f), 0);
        }
        drawFooter("Arriba/Abajo: fila   -   Izq/Der: cambiar   -   1 a 5: modo   -   ENTER: seguir / jugar   -   ESC: volver");
    }
}
