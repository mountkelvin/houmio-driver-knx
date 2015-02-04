package houmio;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import rx.Observable;
import rx.functions.Action1;
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

public class Main {
    static String knxIp = Optional.ofNullable(System.getenv("HOUMIO_KNX_IP")).orElse("localhost");
    static Optional<String> networkInterface = Optional.ofNullable(System.getenv("HOUMIO_NETWORK_INTERFACE"));
    static KNXNetworkLinkIP knxLink;
    static ProcessCommunicator knxSocket;

    public static void main(String[] args) throws SocketException, UnknownHostException, KNXException, InterruptedException {
        System.out.println("Using HOUMIO_KNX_IP=" + knxIp);
        networkInterface.ifPresent(i -> System.out.println("Using HOUMIO_NETWORK_INTERFACE=" + i));
        knxSocket = createKnxSocket();
        openTcpSocketToBridgeWithProtocolKnx();
    }

    private static void openTcpSocketToBridgeWithProtocolKnx() {
        RxClient<String, String> rxClient = RxNetty.createTcpClient("localhost", 3001, PipelineConfigurators.stringMessageConfigurator());
        Observable<ObservableConnection<String, String>> connectionObservable = rxClient.connect();
        connectionObservable
            .flatMap(c -> {
                Observable<JsonNode> write = c.writeAndFlush(DriverProtocol.driverReadyMessage("knx")).map(x -> Json.mapper.createObjectNode());
                Observable<JsonNode> jsons = c.getInput().flatMap(Json.parseJson);
                return Observable.concat(write, jsons);
            })
            .filter(json -> json.get("command").asText().equals("write"))
            .map(o -> o.get("data"))
            .map(KnxCommandData::fromJson)
            .toBlocking()
            .forEach(writeToKnxBus);
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

    private static ProcessCommunicator createKnxSocket() throws SocketException, KNXException, InterruptedException, UnknownHostException {
        InetAddress localInetAddress = resolveLocalInetAddress();
        knxLink = new KNXNetworkLinkIP(
            KNXNetworkLinkIP.TUNNELING,
            new InetSocketAddress(localInetAddress, 0),
            new InetSocketAddress(
                InetAddress.getByName(knxIp),
                KNXnetIPConnection.DEFAULT_PORT),
            false,
            new TPSettings(false)
        );
        System.out.println(String.format("Connected to KNX IP gateway, from %s to %s", localInetAddress.getHostAddress(), knxIp));
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

    private static InetAddress resolveLocalInetAddress() throws SocketException {
        List<InetAddress> inet4Addresses = NetworkInterfaces.getInet4Addresses(networkInterface);
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
}
