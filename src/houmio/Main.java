package houmio;

import tuwien.auto.calimero.CloseEvent;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.exception.KNXTimeoutException;
import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;
import tuwien.auto.calimero.link.KNXNetworkLinkIP;
import tuwien.auto.calimero.link.NetworkLinkListener;
import tuwien.auto.calimero.link.medium.TPSettings;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    static String HOUMIO_KNX_IP = Optional.ofNullable(System.getenv("HOUMIO_KNX_IP")).orElse("localhost");
    static Optional<String> HOUMIO_NETWORK_INTERFACE = Optional.ofNullable(System.getenv("HOUMIO_NETWORK_INTERFACE"));
    static KNXNetworkLinkIP knxLink;
    static ProcessCommunicator knxSocket;
    static Socket bridgeSocket;

    public static void main(String[] args) throws IOException, KNXException, InterruptedException {
        try {
            System.out.println("Using HOUMIO_KNX_IP=" + HOUMIO_KNX_IP);
            HOUMIO_NETWORK_INTERFACE.ifPresent(i -> System.out.println("Using HOUMIO_NETWORK_INTERFACE=" + i));
            knxSocket = createKnxSocket();
            createBridgeSocketCommandObservable("knx").forEach(Main::writeToKnxBus);
        } catch (KNXTimeoutException e) {
            System.out.println("Error while connecting to KNX ip gateway: " + e.getMessage());
        } catch (ConnectException e) {
            System.out.println("Error while connecting to bridge: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shut down hook execution started");
                if (knxLink != null) {
                    System.out.println("Going to close KNX link");
                    knxLink.close();
                    System.out.println("KNX link closed");
                }
                System.out.println("Shut down hook executed");
            }
        });
    }

    private static Stream<KnxCommandData> createBridgeSocketCommandObservable(String protocol) throws IOException {
        bridgeSocket = new Socket("localhost", 3001);
        System.out.println("Connected to bridge at localhost:3001");
        PrintWriter bridgeSocketWriter = new PrintWriter(bridgeSocket.getOutputStream());
        bridgeSocketWriter.write(DriverProtocol.driverReadyMessage(protocol));
        bridgeSocketWriter.flush();
        BufferedReader bridgeSocketReader = new BufferedReader(new InputStreamReader(bridgeSocket.getInputStream()));
        return bridgeSocketReader.lines()
            .map(String::trim)
            .flatMap(Json::parseJson)
            .filter(json -> json.get("command").asText().equals("write"))
            .map(o -> o.get("data"))
            .map(KnxCommandData::fromJson);
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
        return new ProcessCommunicatorImpl(knxLink);
    }

    static void writeToKnxBus(KnxCommandData data) {
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
    }

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
            try {
                bridgeSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
                System.exit(1);
            }
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
