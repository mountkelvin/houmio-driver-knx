package houmio;

import com.fasterxml.jackson.core.JsonProcessingException;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.StringObservable;
import tuwien.auto.calimero.CloseEvent;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;
import tuwien.auto.calimero.link.KNXNetworkLinkIP;
import tuwien.auto.calimero.link.NetworkLinkListener;
import tuwien.auto.calimero.link.medium.TPSettings;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Main {
    static Optional<String> DEV = Optional.ofNullable(System.getenv("DEV"));
    static String HOUMIO_KNX_IP = Optional.ofNullable(System.getenv("HOUMIO_KNX_IP")).orElse("localhost");
    static Optional<String> HOUMIO_NETWORK_INTERFACE = Optional.ofNullable(System.getenv("HOUMIO_NETWORK_INTERFACE"));
    static String HOUMIO_KNX_DALI_THROTTLE = Optional.ofNullable(System.getenv("HOUMIO_KNX_DALI_THROTTLE")).orElse("500");
    static KNXNetworkLinkIP knxLink;
    static ProcessCommunicator knxSocket;

    public static void main(String[] args) throws IOException, KNXException, InterruptedException {
        System.out.println("Using HOUMIO_KNX_IP=" + HOUMIO_KNX_IP);
        HOUMIO_NETWORK_INTERFACE.ifPresent(i -> System.out.println("Using HOUMIO_NETWORK_INTERFACE=" + i));
        System.out.println("Using HOUMIO_KNX_DALI_THROTTLE=" + HOUMIO_KNX_DALI_THROTTLE);
        setupKnxSocket();
        setupBridgeSocketKnxDaliThread().start();
        setupBridgeSocketKnxThread().start();
    }

    private static Thread setupBridgeSocketKnxDaliThread() throws IOException {
        return new Thread(() -> {
            try {
                createBridgeSocketCommandObservable("knx/dali")
                    .flatMap(toForceOn)
                    .onBackpressureBuffer()
                    .subscribe(new KnxDaliCommandSubscriber());
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static Func1<KnxCommandData, Observable<? extends KnxCommandData>> toForceOn = d -> {
        if (d.on) {
            KnxCommandData forceOn = new KnxCommandData("binary", true, 0, d.switchAddress, "", "", "");
            return Observable.just(forceOn, d);
        } else {
            return Observable.just(d);
        }
    };

    private static Thread setupBridgeSocketKnxThread() throws IOException {
        return new Thread(() -> {
            try {
                createBridgeSocketCommandObservable("knx").forEach(writeToKnxBus);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static Observable<KnxCommandData> createBridgeSocketCommandObservable(String protocol) throws IOException {
        Socket bridgeSocket = new Socket("localhost", 3001);
        PrintWriter bridgeSocketWriter = new PrintWriter(bridgeSocket.getOutputStream());
        bridgeSocketWriter.write(DriverProtocol.driverReadyMessage(protocol));
        bridgeSocketWriter.flush();
        return StringObservable.from(new BufferedReader(new InputStreamReader(bridgeSocket.getInputStream())))
            .flatMap(Json.parseJson)
            .filter(json -> json.get("command").asText().equals("write"))
            .map(o -> o.get("data"))
            .map(KnxCommandData::fromJson);
    }

    private static void setupKnxSocket() throws SocketException, KNXException, InterruptedException, UnknownHostException {
        if (DEV.isPresent()) {
            writeToKnxBus = knxCommandData -> {
                try {
                    System.out.println(Json.mapper.writeValueAsString(knxCommandData));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            };
        } else {
            knxSocket = createKnxSocket();
        }
    }

    private static ProcessCommunicator createKnxSocket() throws SocketException, KNXException, InterruptedException, UnknownHostException {
        InetAddress localInetAddress = resolveLocalInetAddress();
        knxLink = new KNXNetworkLinkIP(
            KNXNetworkLinkIP.TUNNELING,
            new InetSocketAddress(localInetAddress, 0),
            new InetSocketAddress(
                InetAddress.getByName(HOUMIO_KNX_IP),
                KNXnetIPConnection.DEFAULT_PORT),
            false,
            new TPSettings(false)
        );
        System.out.println(String.format("Connected to KNX IP gateway, from %s to %s", localInetAddress.getHostAddress(), HOUMIO_KNX_IP));
        knxLink.addLinkListener(new LinkClosedListener());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shut down hook execution started");
                System.out.println("Going to close KNX link");
                knxLink.close();
                System.out.println("Shut down hook executed");
            }
        });
        System.out.println("Shut down hook attached");
        return new ProcessCommunicatorImpl(knxLink);
    }

    static Action1<KnxCommandData> writeToKnxBus = data -> {
        try {
            if (!data.on) {
                writeSwitch(knxSocket, data.switchAddress, false);
            }
            if (data.on && "binary".equals(data.type)) {
                writeSwitch(knxSocket, data.switchAddress, true);
            }
            if (data.on && "dimmable".equals(data.type)) {
                writeAbsolute(knxSocket, data.absoluteAddress, data.scaledBri);
            }
        } catch (KNXException e) {
            throw new RuntimeException(e);
        }
    };

    private static void writeAbsolute(ProcessCommunicator knxSocket, String absoluteAddress, int bri) throws KNXException {
        knxSocket.write(new GroupAddress(absoluteAddress), bri, ProcessCommunicator.SCALING);
        System.out.printf("Wrote to KNX, type: %s, address: %s, value: %d\n", "dimmable", absoluteAddress, bri);
    }

    private static void writeSwitch(ProcessCommunicator knxSocket, String switchAddress, boolean on) throws KNXException {
        knxSocket.write(new GroupAddress(switchAddress), on);
        System.out.printf("Wrote to KNX, type: %s, address: %s, value: %b\n", "binary", switchAddress, on);
    }

    private static class LinkClosedListener implements NetworkLinkListener {
        @Override
        public void confirmation(FrameEvent e) {

        }

        @Override
        public void indication(FrameEvent e) {

        }

        @Override
        public void linkClosed(CloseEvent e) {
            System.out.println("Connection closed to KNX IP gateway, reason: " + e.getReason());
            System.exit(0);
        }
    }

    private static class KnxDaliCommandSubscriber extends Subscriber<KnxCommandData> {
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
        public void onNext(KnxCommandData knxCommandData) {
            writeToKnxBus.call(knxCommandData);
            Observable.just(null).delay(Integer.parseInt(HOUMIO_KNX_DALI_THROTTLE), TimeUnit.MILLISECONDS).forEach(i -> {
                request(1);
            });
        }

    }

    private static InetAddress resolveLocalInetAddress() throws SocketException {
        List<InetAddress> inet4Addresses = NetworkInterfaces.getInet4Addresses(HOUMIO_NETWORK_INTERFACE);
        if (inet4Addresses.size() == 1) {
            InetAddress localInetAddress = inet4Addresses.get(0);
            System.out.println("Local inet address is " + localInetAddress.getHostAddress());
            return localInetAddress;
        } else {
            System.err.println("Multiple non-loopback inet v4 addresses present");
            System.err.println("Specify network interface via environment variable HOUMIO_NETWORK_INTERFACE");
            System.exit(0);
            return null;
        }
    }
}
