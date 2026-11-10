package com.vku.pulseoj.backend.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * [TV4] UDP LAN Auto-Discovery Client.
 * Broadcasts UDP probes across the subnet to dynamically discover the Master Server within 2 seconds.
 */
public class UdpDiscoveryClient {

    private static final Logger logger = LoggerFactory.getLogger(UdpDiscoveryClient.class);

    public record DiscoveryResult(boolean found, String masterHost, int masterRmiPort, String message) {}

    public static DiscoveryResult discoverMaster(int timeoutMs) {
        logger.info("Broadcasting UDP discovery probe on port {} (timeout {} ms)...",
                UdpDiscoveryServer.DISCOVERY_PORT, timeoutMs);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);

            byte[] requestBytes = UdpDiscoveryServer.MAGIC_REQUEST.getBytes(StandardCharsets.UTF_8);
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");

            DatagramPacket requestPacket = new DatagramPacket(
                    requestBytes, requestBytes.length, broadcastAddr, UdpDiscoveryServer.DISCOVERY_PORT
            );
            socket.send(requestPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8).trim();
            logger.info("Received UDP discovery response: {}", response);

            if (response.startsWith(UdpDiscoveryServer.MAGIC_RESPONSE_PREFIX)) {
                String[] parts = response.split("\\|");
                if (parts.length >= 3) {
                    String masterIp = parts[1];
                    int rmiPort = Integer.parseInt(parts[2]);
                    return new DiscoveryResult(true, masterIp, rmiPort, "Discovered Master at " + masterIp + ":" + rmiPort);
                }
            }
            return new DiscoveryResult(false, null, 0, "Unexpected discovery packet format: " + response);
        } catch (SocketTimeoutException te) {
            logger.warn("Discovery timeout: No Master Server responded within {} ms.", timeoutMs);
            return new DiscoveryResult(false, null, 0, "No Master Server found on LAN.");
        } catch (Exception e) {
            logger.error("Discovery error: {}", e.getMessage());
            return new DiscoveryResult(false, null, 0, "Discovery failed: " + e.getMessage());
        }
    }
}
