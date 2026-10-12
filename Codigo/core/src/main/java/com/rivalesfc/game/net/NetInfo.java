package com.rivalesfc.game.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Utilidad mínima: averigua la IP de la red local para mostrársela al host. */
public final class NetInfo {

    private NetInfo() {
    }

    /** Primera IPv4 privada (192.168.x.x, 10.x.x.x...) de una interfaz activa, o "?" si no hay. */
    public static String localAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "?";
    }
}
