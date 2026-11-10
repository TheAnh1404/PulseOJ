package com.vku.pulseoj.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Common network utilities for IP discovery, RMI host configuration, and socket checks.
 */
public final class NetworkUtils {

    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    private NetworkUtils() {}

    /**
     * Resolves the primary LAN IPv4 address (excluding loopback 127.0.0.1 and virtual adapters)
     * and sets System property "java.rmi.server.hostname" to ensure cross-machine RMI callbacks work.
     *
     * @return Discovered IPv4 LAN address as string, or "127.0.0.1" fallback.
     */
    public static String configureRmiHostname() {
        String lanIp = getLocalLanIpv4();
        System.setProperty("java.rmi.server.hostname", lanIp);
        logger.info("Configured RMI server hostname to: {}", lanIp);
        return lanIp;
    }

    /**
     * Detects the most suitable active non-loopback IPv4 address on the machine.
     */
    public static String getLocalLanIpv4() {
        try {
            List<InetAddress> candidateIps = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "127.0.0.1";
            }

            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }

                String displayName = nif.getDisplayName().toLowerCase();
                // Filter out common virtual / container adapters
                if (displayName.contains("virtual") || displayName.contains("vmware") ||
                    displayName.contains("vbox") || displayName.contains("docker") ||
                    displayName.contains("wsl") || displayName.contains("hyper-v")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        candidateIps.add(addr);
                    }
                }
            }

            if (!candidateIps.isEmpty()) {
                return candidateIps.get(0).getHostAddress();
            }

            // Fallback: standard local host lookup
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost instanceof Inet4Address && !localHost.isLoopbackAddress()) {
                return localHost.getHostAddress();
            }
        } catch (Exception e) {
            logger.warn("Could not determine local LAN IP, defaulting to 127.0.0.1: {}", e.getMessage());
        }

        return "127.0.0.1";
    }

    /**
     * Quick check if a remote TCP host:port is reachable.
     */
    public static boolean isHostReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
