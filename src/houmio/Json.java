package houmio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;

import java.io.IOException;

public class Json {
    public static ObjectMapper mapper = new ObjectMapper();

    public static Stream<JsonNode> parseJson(String s) {
        try {
            JsonNode value = mapper.readTree(s);
            return Stream.of(value);
        } catch (IOException e) {
            return Stream.empty();
        }
    };
}
