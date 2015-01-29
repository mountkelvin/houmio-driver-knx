package houmio;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NetworkInterfaces {

    private static Predicate<? super NetworkInterface> isNotLoopback = networkInterface -> {
        try {
            return !networkInterface.isLoopback();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    };

    private static Function<NetworkInterface, Stream<? extends InetAddress>> inetAddresses = networkInterface -> Collections.list(networkInterface.getInetAddresses()).stream();

    private static Predicate<InetAddress> isInet4Address = o -> o instanceof Inet4Address;

    public static List<InetAddress> getInet4Addresses(Optional<String> nameCriterion) throws SocketException {
        List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        return networkInterfaces.stream()
            .filter(networkInterface -> nameCriterion.map(c -> c.equals(networkInterface.getName())).orElse(true))
            .filter(isNotLoopback)
            .flatMap(inetAddresses)
            .filter(isInet4Address)
            .collect(Collectors.toList());
    }

    public static void main(String[] args) throws SocketException {
        displayInterfaceInformation();
    }

    static void displayInterfaceInformation() throws SocketException {
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                System.out.printf("Display name: %s\n", networkInterface.getDisplayName());
                System.out.printf("Name: %s\n", networkInterface.getName());
                System.out.println("Loopback: " + networkInterface.isLoopback());
                System.out.println("IPV4: " + (inetAddress instanceof Inet4Address));
                System.out.println("Host address: " + inetAddress.getHostAddress());
                System.out.printf("\n");
            }
        }
    }
}