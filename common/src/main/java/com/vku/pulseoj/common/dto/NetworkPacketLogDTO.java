package com.vku.pulseoj.common.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Audit log entry capturing an intercepted or observed UDP/RMI network packet.
 */
public class NetworkPacketLogDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private long timestamp;
    private String protocol; // "UDP-DISCOVERY", "UDP-HEARTBEAT", "RMI-REQ", "RMI-RESP", "RMI-CALLBACK"
    private String source;
    private String destination;
    private int packetSizeBytes;
    private String summary;
    private String payloadSnippet;

    public NetworkPacketLogDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public NetworkPacketLogDTO(String protocol, String source, String destination,
                               int packetSizeBytes, String summary, String payloadSnippet) {
        this.timestamp = System.currentTimeMillis();
        this.protocol = protocol;
        this.source = source;
        this.destination = destination;
        this.packetSizeBytes = packetSizeBytes;
        this.summary = summary;
        this.payloadSnippet = payloadSnippet;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getPacketSizeBytes() {
        return packetSizeBytes;
    }

    public void setPacketSizeBytes(int packetSizeBytes) {
        this.packetSizeBytes = packetSizeBytes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPayloadSnippet() {
        return payloadSnippet;
    }

    public void setPayloadSnippet(String payloadSnippet) {
        this.payloadSnippet = payloadSnippet;
    }
}
