package houmio;

import com.fasterxml.jackson.databind.JsonNode;

public class KnxCommandData {
    public final String type;
    public final boolean on;
    public final int scaledBri;
    public final String switchAddress;
    public final String absoluteAddress;

    public KnxCommandData(String type, boolean on, int scaledBri, String switchAddress, String absoluteAddress) {
        this.type = type;
        this.on = on;
        this.scaledBri = scaledBri;
        this.switchAddress = switchAddress;
        this.absoluteAddress = absoluteAddress;
    }

    public static KnxCommandData fromJson(JsonNode n) {
        String type = n.get("type").asText();
        boolean on = n.get("on").asBoolean();
        int bri = n.get("bri").asInt();
        int knxScaledBri = Scale.scale(0, 0xff, 0, 100, bri);
        String protocolAddress = n.get("protocolAddress").asText();
        String[] groupAddresses = protocolAddress.split(":");
        String switchAddress = "";
        String absoluteAddress = "";
        if (groupAddresses.length > 0) {
            switchAddress = groupAddresses[0];
        }
        if (groupAddresses.length > 1) {
            absoluteAddress = groupAddresses[1];
        }
        return new KnxCommandData(type, on, knxScaledBri, switchAddress, absoluteAddress);
    }
}
