package com.rivalesfc.game.net;

import com.rivalesfc.game.input.PlayerInput;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/** Prueba mínima por loopback, sin LibGDX. Correr con main(). */
public class NetSelfTest {

    static int fails = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLA ") + name);
        if (!ok) fails++;
    }

    static void pump(NetHost h, NetClient c, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            h.poll();
            c.poll();
            Thread.sleep(2);
        }
    }

    public static void main(String[] args) throws Exception {
        NetHost host = new NetHost();
        host.start(0);
        int port = host.getPort();
        NetClient client = new NetClient();
        host.setLocalIdentity(2, "ana");
        client.setLocalIdentity(5, "Beto 9!@#");
        client.connect("127.0.0.1", port);

        pump(host, client, 300);
        check("MK6: host recibe equipo y nombre limpio del cliente",
                host.getClientIdentity() != null && host.getClientIdentity().team == 5
                        && host.getClientIdentity().name.equals("BETO 9"));
        check("MK6: cliente recibe equipo y nombre del host",
                client.getHostIdentity() != null && client.getHostIdentity().team == 2
                        && client.getHostIdentity().name.equals("ANA"));
        check("MK6: nombre largo se recorta a 12", Protocol.cleanName("abcdefghijklmnopqrst").length() == 12);
        check("MK6: sin cuerpo no hay identidad", Protocol.readIdentity(ByteBuffer.allocate(0)) == null);
        check("cliente conectado", client.isConnected());
        check("host ve al cliente", host.hasClient());

        PlayerInput in = new PlayerInput();
        in.moveX = 1f; in.moveY = -0.5f; in.sprint = true; in.kickHeld = true;
        client.sendInput(in);
        in.kickHeld = false; in.kickReleased = true;
        client.sendInput(in);
        in.kickReleased = false; in.moveX = 0.25f;   // frame siguiente, sin el evento
        client.sendInput(in);
        pump(host, client, 100);

        PlayerInput got = host.consumeInput();
        check("ejes del último paquete", got.moveX == 0.25f && got.moveY == -0.5f);
        check("sprint recibido", got.sprint);
        check("kickReleased no se pierde (pegajoso)", got.kickReleased);
        check("kickReleased se consume una vez", !host.consumeInput().kickReleased);

        // --- Snapshot host -> cliente ---
        Snapshot a = new Snapshot();
        a.tick = 5; a.phase = 1; a.half = 2; a.scoreLeft = 3; a.scoreRight = 1; a.possessionLeft = 62;
        a.lastGoalByLeft = true; a.halfTimeRemaining = 91.5f; a.phaseTimer = 2.5f; a.replayProgress = 0.4f;
        a.ballX = 1.25f; a.ballY = -3.5f; a.ballAngle = 0.7f; a.ballVX = 12f; a.ballVY = -4f;
        a.px[0] = -5f; a.py[0] = 2f; a.pvx[0] = 1f; a.pvy[0] = 2f; a.sliding[0] = true; a.kickPower[0] = 0.8f;
        a.px[1] = 6f; a.py[1] = -1f; a.pvx[1] = -3f; a.pvy[1] = 0.5f; a.sliding[1] = false; a.kickPower[1] = 0.1f;
        a.ky[0] = 0.5f; a.kvy[0] = 1f; a.kdive[0] = 0.9f; a.kdiving[0] = true;
        a.ky[1] = -0.5f; a.kvy[1] = -1f; a.kdive[1] = 0f; a.kdiving[1] = false;
        a.kickCount = 7; a.slideCount = 2; a.saveCount = 1; a.lastKickPower = 0.66f;
        a.shots[0] = 4; a.shotsOnTarget[1] = 2; a.tackles[0] = 3; a.saves[1] = 5;
        host.sendSnapshot(a);
        pump(host, client, 100);
        Snapshot b = new Snapshot();
        check("cliente recibe snapshot", client.takeSnapshot(b));
        boolean same = b.tick == 5 && b.phase == 1 && b.half == 2 && b.scoreLeft == 3 && b.scoreRight == 1
                && b.possessionLeft == 62 && b.lastGoalByLeft && b.halfTimeRemaining == 91.5f
                && b.phaseTimer == 2.5f && b.replayProgress == 0.4f
                && b.ballX == 1.25f && b.ballY == -3.5f && b.ballAngle == 0.7f && b.ballVX == 12f && b.ballVY == -4f
                && b.px[0] == -5f && b.py[0] == 2f && b.pvx[0] == 1f && b.pvy[0] == 2f && b.sliding[0] && b.kickPower[0] == 0.8f
                && b.px[1] == 6f && b.py[1] == -1f && b.pvx[1] == -3f && b.pvy[1] == 0.5f && !b.sliding[1] && b.kickPower[1] == 0.1f
                && b.ky[0] == 0.5f && b.kvy[0] == 1f && b.kdive[0] == 0.9f && b.kdiving[0]
                && b.ky[1] == -0.5f && b.kvy[1] == -1f && b.kdive[1] == 0f && !b.kdiving[1]
                && b.kickCount == 7 && b.slideCount == 2 && b.saveCount == 1 && b.lastKickPower == 0.66f
                && b.shots[0] == 4 && b.shotsOnTarget[1] == 2 && b.tackles[0] == 3 && b.saves[1] == 5;
        check("snapshot llega idéntico (todos los campos)", same);
        check("sin snapshot nuevo no devuelve nada", !client.takeSnapshot(b));

        a.tick = 3; a.scoreLeft = 99;               // más viejo: debe descartarse
        host.sendSnapshot(a);
        pump(host, client, 60);
        check("snapshot viejo descartado", !client.takeSnapshot(b));
        a.tick = 6; a.scoreLeft = 4;
        host.sendSnapshot(a);
        pump(host, client, 60);
        check("snapshot más nuevo aceptado", client.takeSnapshot(b) && b.scoreLeft == 4);

        // Simula ~40 s de partido a 30 Hz con input constante en el cliente: nada se cae ni se satura
        int received = 0;
        for (int i = 0; i < 1200; i++) {
            a.tick = 100 + i;
            host.sendSnapshot(a);
            in.moveX = 1f;
            client.sendInput(in);
            host.poll(); client.poll();
            if (client.takeSnapshot(b)) received++;
        }
        check("1200 snapshots seguidos: llegan >95%", received > 1140);
        check("sigue conectado", client.isConnected() && host.hasClient());

        // --- MK5: ping medido por PING/PONG ---
        pump(host, client, 1300); // el cliente manda un PING por segundo
        check("cliente mide ping (loopback < 50 ms)", client.getPingMs() >= 0 && client.getPingMs() < 50);
        pump(host, client, 1200); // el segundo PING ya lleva el ping medido
        check("host conoce el ping del cliente", host.getClientPingMs() >= 0 && host.getClientPingMs() < 50);

        // --- MK5: interpolación de snapshots ---
        SnapshotInterpolator ip = new SnapshotInterpolator();
        Snapshot out = new Snapshot();
        check("interpolador vacío no devuelve nada", !ip.sample(0, out));
        long ms = 1_000_000L;
        Snapshot s1 = new Snapshot(); s1.tick = 1; s1.ballX = 0f; s1.px[0] = 0f; s1.scoreLeft = 0; s1.ky[0] = 0f;
        Snapshot s2 = new Snapshot(); s2.tick = 2; s2.ballX = 1f; s2.px[0] = 2f; s2.scoreLeft = 1; s2.ky[0] = 1f;
        ip.push(s1, 1000 * ms);
        ip.push(s2, 1033 * ms);
        long d = SnapshotInterpolator.DELAY_NANOS;
        check("interpolador: mitad de camino", ip.sample(1000 * ms + d + 16 * ms + ms / 2, out)
                && Math.abs(out.ballX - 0.5f) < 0.01f && Math.abs(out.px[0] - 1f) < 0.02f && Math.abs(out.ky[0] - 0.5f) < 0.01f);
        ip.sample(1000 * ms + d + 6 * ms, out);   // alpha ~0.2: más cerca del primero
        boolean discreteA = out.scoreLeft == 0;
        ip.sample(1000 * ms + d + 30 * ms, out);  // alpha ~0.9: más cerca del segundo
        check("interpolador: dato discreto del más cercano", discreteA && out.scoreLeft == 1);
        ip.sample(1000 * ms + d - 50 * ms, out);
        check("interpolador: antes del primero devuelve el primero", out.ballX == 0f);
        ip.sample(1033 * ms + d + 50 * ms, out);
        check("interpolador: sin futuro extrapola poco, sin pasarse", out.ballX >= 1f && out.ballX < 1.5f);

        Snapshot s3 = new Snapshot(); s3.tick = 3; s3.ballX = 1f; s3.px[0] = -9f; s3.pvx[0] = 0f; // saque: salto de 11 m
        ip.push(s3, 1066 * ms);
        ip.sample(1033 * ms + d + 16 * ms, out);
        check("interpolador: teletransporte no se interpola", out.px[0] == -9f);

        // Orden circular: más de 8 snapshots no rompen el buffer
        SnapshotInterpolator ring = new SnapshotInterpolator();
        for (int i = 0; i < 20; i++) {
            Snapshot s = new Snapshot(); s.tick = i; s.ballX = i;
            ring.push(s, (2000 + i * 33L) * ms);
        }
        ring.sample((2000 + 15 * 33L) * ms + d + 16 * ms, out);
        check("interpolador: buffer circular (bola entre 15 y 16)", out.ballX > 15f && out.ballX < 16f);

        // Paquete basura y NaN no rompen nada
        DatagramChannel evil = DatagramChannel.open();
        evil.send(ByteBuffer.wrap(new byte[]{1, 2, 3}), new InetSocketAddress("127.0.0.1", port));
        pump(host, client, 50);
        check("basura ignorada", host.hasClient());

        // Un segundo cliente no puede entrar
        NetClient other = new NetClient();
        other.connect("127.0.0.1", port);
        pump(host, other, 300);
        check("segundo cliente rechazado", !other.isConnected());
        other.close();

        // Despedida
        client.close();
        pump(host, client, 100);
        check("host detecta BYE", !host.hasClient());

        // Conectar a un puerto donde no hay host -> falla sin colgarse
        host.close();
        NetClient lost = new NetClient();
        lost.connect("127.0.0.1", port);
        long t0 = System.currentTimeMillis();
        while (lost.getState() == NetClient.State.CONNECTING && System.currentTimeMillis() - t0 < 7000) {
            lost.poll();
            Thread.sleep(10);
        }
        check("timeout sin host", lost.getState() == NetClient.State.FAILED);

        System.out.println(fails == 0 ? "\nTODO OK" : "\nHAY FALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
