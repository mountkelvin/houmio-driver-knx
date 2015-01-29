package houmio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DriverProtocol {
    public static String driverReadyMessage(String protocol) {
        try {
            ObjectNode root = Json.mapper.createObjectNode();
            root.put("command", "driverReady");
            root.put("protocol", protocol);
            return Json.mapper.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not form driverReady message", e);
        }
    }
}
