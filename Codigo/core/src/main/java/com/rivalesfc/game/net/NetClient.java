package com.rivalesfc.game.net;

import com.rivalesfc.game.input.PlayerInput;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Lado cliente (Etapa 2, paso 1): se conecta a un host y le manda su input.
 *
 * Sin hilos: llamar a {@link #poll()} una vez por frame.
 * Todavía no recibe el estado del partido (paso siguiente).
 */
public class NetClient {

    public enum State { IDLE, CONNECTING, CONNECTED, FAILED, DISCONNECTED }

    private static final long HELLO_INTERVAL_NANOS = 500_000_000L;
    private static final long CONNECT_TIMEOUT_NANOS = 5_000_000_000L;
    private static final long HOST_TIMEOUT_NANOS = 3_000_000_000L;
    private static final long PING_INTERVAL_NANOS = 1_000_000_000L;

    private DatagramChannel channel;
    private State state = State.IDLE;
    private long connectStartNanos;
    private long lastHelloNanos;
    private int nextSeq;
    private long lastRecvNanos;
    private final Snapshot incoming = new Snapshot();
    private final Snapshot latest = new Snapshot();
    private boolean hasAny;
    private boolean hasNew;
    private long lastPingNanos;
    private int pingMs = -1; // MK5: ida y vuelta al host, suavizado; -1 = todavía sin medir
    private final ByteBuffer buf = ByteBuffer.allocate(Protocol.MAX_PACKET);
    // MK6: identidad propia (se manda en el HELLO) y la del host (llega en el WELCOME).
    private int localTeam = 0;
    private String localName = "";
    private Protocol.Identity hostIdentity;

    /** Equipo y nombre que se le mandan al host; llamar antes de {@link #connect}. */
    public void setLocalIdentity(int team, String name) {
        localTeam = team;
        localName = name;
    }

    /** Identidad del host, o null si todavía no llegó (o es un host sin identidad). */
    public Protocol.Identity getHostIdentity() {
        return hostIdentity;
    }

    /** Empieza a conectar; el resultado se ve en {@link #getState()} tras llamar a poll(). */
    public void connect(String host, int port) throws IOException {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port)); // filtra paquetes de otras direcciones
        state = State.CONNECTING;
        connectStartNanos = System.nanoTime();
        lastHelloNanos = 0;
        nextSeq = 0;
        hasAny = false;
        hasNew = false;
        lastPingNanos = 0;
        pingMs = -1;
        hostIdentity = null;
    }

    /** MK5: ping (ida y vuelta) en milisegundos, o -1 si todavía no se midió. */
    public int getPingMs() {
        return pingMs;
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public void poll() {
        if (channel == null || state == State.FAILED || state == State.DISCONNECTED) return;
        long now = System.nanoTime();

        if (state == State.CONNECTING) {
            if (now - connectStartNanos > CONNECT_TIMEOUT_NANOS) {
                state = State.FAILED;
                closeChannel();
                return;
            }
            if (lastHelloNanos == 0 || now - lastHelloNanos > HELLO_INTERVAL_NANOS) {
                lastHelloNanos = now;
                sendHello(); // se reintenta hasta recibir WELCOME
            }
        }

        if (state == State.CONNECTED && lastRecvNanos != 0 && now - lastRecvNanos > HOST_TIMEOUT_NANOS) {
            state = State.DISCONNECTED; // el host dejó de mandar datos
            closeChannel();
            return;
        }

        if (state == State.CONNECTED && (lastPingNanos == 0 || now - lastPingNanos > PING_INTERVAL_NANOS)) {
            lastPingNanos = now;
            sendPing(now);
        }

        try {
            while (true) {
                buf.clear();
                if (channel.read(buf) <= 0) break;
                buf.flip();
                int type = Protocol.readType(buf);
                if (type >= 0) lastRecvNanos = now;
                if (type == Protocol.WELCOME) {
                    Protocol.Identity id = Protocol.readIdentity(buf);
                    if (id != null) hostIdentity = id;
                }
                if (type == Protocol.WELCOME && state == State.CONNECTING) {
                    state = State.CONNECTED;
                    lastRecvNanos = now;
                } else if (type == Protocol.SNAPSHOT) {
                    state = State.CONNECTED; // si el WELCOME se perdió, un snapshot también prueba la conexión
                    if (Protocol.readSnapshot(buf, incoming)
                            && (!hasAny || incoming.tick - latest.tick > 0)) { // descarta paquetes viejos
                        latest.copyFrom(incoming);
                        hasAny = true;
                        hasNew = true;
                    }
                } else if (type == Protocol.PONG && Protocol.hasStamp(buf)) {
                    int rtt = (int) (now / 1_000_000L) - buf.getInt(); // la resta en int tolera la vuelta del reloj
                    if (rtt >= 0 && rtt < 10_000) {
                        pingMs = pingMs < 0 ? rtt : Math.round(pingMs * 0.7f + rtt * 0.3f);
                    }
                } else if (type == Protocol.BYE) {
                    state = State.DISCONNECTED;
                    closeChannel();
                    return;
                }
            }
        } catch (IOException e) {
            // Host todavía no está escuchando (ICMP "puerto inalcanzable"): se sigue reintentando.
        }
    }

    /** Si llegó un snapshot nuevo desde la última vez, lo copia a {@code out} y devuelve true. */
    public boolean takeSnapshot(Snapshot out) {
        if (!hasNew) return false;
        out.copyFrom(latest);
        hasNew = false;
        return true;
    }

    /** Envía el estado actual de los controles. Solo tiene efecto si ya se está conectado. */
    public void sendInput(PlayerInput in) {
        if (state != State.CONNECTED) return;
        in.sequence = nextSeq++;
        try {
            Protocol.writeHeader(buf, Protocol.INPUT);
            Protocol.writeInput(buf, in);
            buf.flip();
            channel.write(buf);
        } catch (IOException ignored) {
        }
    }

    private void sendPing(long nowNanos) {
        try {
            Protocol.writeHeader(buf, Protocol.PING);
            Protocol.writePing(buf, (int) (nowNanos / 1_000_000L), pingMs);
            buf.flip();
            channel.write(buf);
        } catch (IOException ignored) {
        }
    }

    private void sendHello() {
        try {
            Protocol.writeHeader(buf, Protocol.HELLO);
            Protocol.writeIdentity(buf, localTeam, localName);
            buf.flip();
            channel.write(buf);
        } catch (IOException ignored) {
        }
    }

    private void sendSimple(byte type) {
        try {
            Protocol.writeHeader(buf, type);
            buf.flip();
            channel.write(buf);
        } catch (IOException ignored) {
        }
    }

    public void close() {
        if (channel != null && state == State.CONNECTED) sendSimple(Protocol.BYE);
        closeChannel();
        if (state != State.FAILED) state = State.DISCONNECTED;
    }

    private void closeChannel() {
        try {
            if (channel != null) channel.close();
        } catch (IOException ignored) {
        }
        channel = null;
    }
}
