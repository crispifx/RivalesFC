package com.rivalesfc.game.net;

import com.rivalesfc.game.input.PlayerInput;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Lado host (Etapa 2, paso 1): acepta UN cliente y recibe su input.
 *
 * Sin hilos: se llama a {@link #poll()} una vez por frame. Todavía no envía
 * el estado del partido (eso es el paso siguiente).
 */
public class NetHost {

    private static final long CLIENT_TIMEOUT_NANOS = 3_000_000_000L;

    private DatagramChannel channel;
    private SocketAddress client;
    private long lastRecvNanos;
    private int clientPingMs = -1; // MK5: lo que el cliente dice haber medido

    private boolean hasSeq;
    private int lastSeq;
    private final PlayerInput latest = new PlayerInput();
    private final PlayerInput scratch = new PlayerInput();
    private final ByteBuffer buf = ByteBuffer.allocate(Protocol.MAX_PACKET);
    private final ByteBuffer out = ByteBuffer.allocate(Protocol.MAX_PACKET);
    // MK6: identidad propia (va en el WELCOME) y la del cliente (llega en el HELLO).
    private int localTeam = 0;
    private String localName = "";
    private Protocol.Identity clientIdentity;

    /** Equipo y nombre que se le mandan al cliente. */
    public void setLocalIdentity(int team, String name) {
        localTeam = team;
        localName = name;
    }

    /** Identidad del cliente conectado, o null si no hay cliente (o es una versión sin identidad). */
    public Protocol.Identity getClientIdentity() {
        return clientIdentity;
    }

    /** Abre el puerto UDP. Con puerto 0 el sistema elige uno libre (útil para pruebas). */
    public void start(int port) throws IOException {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(port));
    }

    public int getPort() throws IOException {
        return ((InetSocketAddress) channel.getLocalAddress()).getPort();
    }

    /** MK5: ping del cliente tal como él lo mide (-1 si todavía no hay dato). */
    public int getClientPingMs() {
        return clientPingMs;
    }

    public boolean hasClient() {
        return client != null;
    }

    /** Leer todo lo que llegó desde el último frame. */
    public void poll() {
        if (channel == null) return;
        if (client != null && System.nanoTime() - lastRecvNanos > CLIENT_TIMEOUT_NANOS) {
            dropClient();
        }
        try {
            while (true) {
                buf.clear();
                SocketAddress from = channel.receive(buf);
                if (from == null) break;
                buf.flip();
                handle(from);
            }
        } catch (IOException e) {
            // Un paquete roto no debe tirar abajo el partido: se ignora.
        }
    }

    private void handle(SocketAddress from) throws IOException {
        int type = Protocol.readType(buf);
        if (type < 0) return;

        if (type == Protocol.HELLO) {
            Protocol.Identity id = Protocol.readIdentity(buf); // antes de reusar buf para responder
            if (client == null) {
                client = from;
                hasSeq = false;
                latest.clearTransient();
                latest.moveX = 0f;
                latest.moveY = 0f;
                latest.sprint = false;
                latest.kickHeld = false;
            }
            if (from.equals(client)) {
                lastRecvNanos = System.nanoTime();
                if (id != null) clientIdentity = id;
                // se repite si el WELCOME anterior se perdió
                out.clear();
                Protocol.writeHeader(out, Protocol.WELCOME);
                Protocol.writeIdentity(out, localTeam, localName);
                out.flip();
                channel.send(out, client);
            }
            return;
        }

        if (client == null || !from.equals(client)) return; // solo se escucha al cliente aceptado
        lastRecvNanos = System.nanoTime();

        if (type == Protocol.BYE) {
            dropClient();
        } else if (type == Protocol.PING) {
            if (buf.remaining() < 6) return;
            int stamp = buf.getInt();
            int reported = buf.getShort();
            clientPingMs = reported >= 0 ? reported : -1;
            out.clear();
            out.putShort(Protocol.MAGIC).put(Protocol.VERSION).put(Protocol.PONG).putInt(stamp);
            out.flip();
            channel.send(out, client);
        } else if (type == Protocol.INPUT) {
            if (!Protocol.readInput(buf, scratch)) return;
            if (hasSeq && (scratch.sequence - lastSeq) <= 0) return; // paquete viejo o duplicado
            hasSeq = true;
            lastSeq = scratch.sequence;
            latest.sequence = scratch.sequence;
            latest.moveX = scratch.moveX;
            latest.moveY = scratch.moveY;
            latest.sprint = scratch.sprint;
            latest.kickHeld = scratch.kickHeld;
            // Eventos de un solo disparo: se acumulan hasta que la simulación los consuma
            // (mismo problema que se arregló en la MK4 con el teclado).
            latest.kickReleased |= scratch.kickReleased;
            latest.switchPlayer |= scratch.switchPlayer;
            latest.slidePressed |= scratch.slidePressed;
        }
    }

    /**
     * Devuelve una copia del último input del cliente y limpia los eventos de un solo disparo.
     * Llamar una vez por tick de simulación.
     */
    public PlayerInput consumeInput() {
        PlayerInput copy = new PlayerInput();
        copy.sequence = latest.sequence;
        copy.moveX = latest.moveX;
        copy.moveY = latest.moveY;
        copy.sprint = latest.sprint;
        copy.kickHeld = latest.kickHeld;
        copy.kickReleased = latest.kickReleased;
        copy.switchPlayer = latest.switchPlayer;
        copy.slidePressed = latest.slidePressed;
        latest.clearTransient();
        return copy;
    }

    /**
     * Copia el input del cliente sobre {@code target} (ejes y botones sostenidos se pisan; los eventos
     * de un solo disparo se SUMAN con OR, igual que el teclado local) y limpia los eventos internos.
     * Llamar una vez por frame: la simulación borra los eventos de {@code target} cuando los usa.
     */
    public void drainInto(PlayerInput target) {
        target.moveX = latest.moveX;
        target.moveY = latest.moveY;
        target.sprint = latest.sprint;
        target.kickHeld = latest.kickHeld;
        target.kickReleased |= latest.kickReleased;
        target.switchPlayer |= latest.switchPlayer;
        target.slidePressed |= latest.slidePressed;
        latest.clearTransient();
    }

    /** Manda el estado del partido al cliente (si hay uno conectado). */
    public void sendSnapshot(Snapshot s) {
        if (channel == null || client == null) return;
        try {
            Protocol.writeHeader(out, Protocol.SNAPSHOT);
            Protocol.writeSnapshot(out, s);
            out.flip();
            channel.send(out, client);
        } catch (IOException ignored) {
        }
    }

    private void dropClient() {
        client = null;
        clientIdentity = null;
        clientPingMs = -1;
        hasSeq = false;
        latest.clearTransient();
        latest.moveX = 0f;
        latest.moveY = 0f;
        latest.sprint = false;
        latest.kickHeld = false;
    }

    private void sendSimple(byte type) throws IOException {
        Protocol.writeHeader(buf, type);
        buf.flip();
        channel.send(buf, client);
    }

    public void close() {
        if (channel == null) return;
        try {
            if (client != null) sendSimple(Protocol.BYE);
            channel.close();
        } catch (IOException ignored) {
        }
        channel = null;
        client = null;
    }
}
