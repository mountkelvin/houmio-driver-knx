package houmio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;

public class Json {
    public static ObjectMapper mapper = new ObjectMapper();

    public static Func1<String, Observable<JsonNode>> parseJson = s -> {
        try {
            JsonNode value = mapper.readTree(s);
            return Observable.just(value);
        } catch (IOException e) {
            return Observable.empty();
        }
    };
}
