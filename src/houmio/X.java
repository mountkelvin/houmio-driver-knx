package houmio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class X {
    static ObjectMapper mapper = new ObjectMapper();

    static Func1<String, Observable<JsonNode>> parseJson = s -> {
        try {
            JsonNode value = mapper.readTree(s);
            return Observable.just(value);
        } catch (IOException e) {
            return Observable.empty();
        }
    };

    private static String driverReadyMessage(String protocol) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("command", "driverReady");
            root.put("protocol", protocol);
            return mapper.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            System.err.println("Could not form driverReady message");
            e.printStackTrace();
            System.exit(1);
            return "";
        }
    }

    static Func1<Long, Observable<String>> toBusEventMessage = e -> {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("command", "driverData");
            root.put("protocol", "knx");
            root.put("data", e);
            String s = mapper.writeValueAsString(root) + "\n";
            return Observable.just(s);
        } catch (JsonProcessingException e1) {
            return Observable.empty();
        }
    };

    public static void main(String[] args) {
        RxClient<String, String> rxClient = RxNetty.createTcpClient("localhost", 3001, PipelineConfigurators.stringMessageConfigurator());
        Observable<ObservableConnection<String, String>> connectionObservable = rxClient.connect();
        PublishSubject<Long> busEvents = PublishSubject.create();
        Observable<String> busEventMessages = busEvents.flatMap(toBusEventMessage);
        Observable.interval(1, TimeUnit.SECONDS).subscribe(i -> {
            busEvents.onNext(i);
        });
        connectionObservable
            .flatMap(c -> busEventMessages.flatMap(m -> c.writeAndFlush(m).materialize()))
            .forEach(o -> {
            });
        Iterable<JsonNode> jsonNodes = connectionObservable
            .flatMap(c -> {
                Observable<JsonNode> write = c.writeAndFlush(driverReadyMessage("knx")).map(x -> mapper.createObjectNode());
                Observable<JsonNode> jsons = c.getInput().flatMap(parseJson);
                return Observable.concat(write, jsons);
            })
            .filter(json -> json.get("command").asText().equals("write"))
            .toBlocking()
            .toIterable();
        Observable.from(jsonNodes)
            .onBackpressureBuffer()
            .subscribe(new JsonNodeSubscriber());
    }

    private static class JsonNodeSubscriber extends Subscriber<JsonNode> {
        @Override
        public void onStart() {
            request(1);
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onNext(JsonNode jsonNode) {
            System.out.println(jsonNode);
            Observable.just(null).delay(1, TimeUnit.SECONDS).forEach(x -> request(1));
        }
    }
}