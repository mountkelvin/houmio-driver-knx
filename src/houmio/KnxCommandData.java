package houmio;

import com.fasterxml.jackson.databind.JsonNode;

public class KnxCommandData {
    public final String type;
    public final boolean on;
    public final int scaledBri;
    public final String switchAddress;
    public final String switchConfirmationAddress;
    public final String absoluteAddress;
    public final String absoluteConfirmationAddress;

    public KnxCommandData(String type, boolean on, int scaledBri, String switchAddress, String switchConfirmationAddress, String absoluteAddress, String absoluteConfirmationAddress) {
        this.type = type;
        this.on = on;
        this.scaledBri = scaledBri;
        this.switchAddress = switchAddress;
        this.switchConfirmationAddress = switchConfirmationAddress;
        this.absoluteAddress = absoluteAddress;
        this.absoluteConfirmationAddress = absoluteConfirmationAddress;
    }

    public static KnxCommandData fromJson(JsonNode n) {
        String type = n.get("type").asText();
        boolean on = n.get("on").asBoolean();
        int bri = n.get("bri").asInt();
        int knxScaledBri = Scale.scale(0, 0xff, 0, 100, bri);
        String protocolAddress = n.get("protocolAddress").asText();
        String[] groupAddresses = protocolAddress.split("\\.");
        String switchAddress = groupAddresses[0];
        String switchConfirmationAddress = groupAddresses[1];
        String absoluteAddress = "";
        String absoluteConfirmationAddress = "";
        if("dimmable".equals(type)) {
            absoluteAddress = groupAddresses[2];
            absoluteConfirmationAddress = groupAddresses[3];
        }
        return new KnxCommandData(type, on, knxScaledBri, switchAddress, switchConfirmationAddress, absoluteAddress, absoluteConfirmationAddress);
    }
}
