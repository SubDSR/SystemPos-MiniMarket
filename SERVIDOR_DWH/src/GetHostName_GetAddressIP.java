import java.net.InetAddress;

public class GetHostName_GetAddressIP {
    public static void main(String[] args) throws Exception {
        InetAddress ia = InetAddress.getLocalHost();
        System.out.println("- Hostname  : " + ia.getHostName());
        System.out.println("- IP Address: " + ia.getHostAddress());
    }
}
