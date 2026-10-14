import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;

/** UDP через java.net (J2ME Loader на Android): адрес отправителя без обратного DNS. Грузится по имени. */
public final class UdpJava extends UdpSock {
    private DatagramSocket sock;
    private DatagramPacket rx;
    private byte[] rxBuf;
    private final Hashtable hosts = new Hashtable();

    public boolean open(int port) {
        try {
            sock = port > 0 ? new DatagramSocket(port) : new DatagramSocket();
            try { sock.setBroadcast(true); } catch (Throwable t) { }
            return true;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    public void send(String addr, byte[] b, int len) throws java.io.IOException {
        String s = addr.startsWith("datagram://") ? addr.substring(11) : addr;
        int c = s.lastIndexOf(':');
        String host = s.substring(0, c);
        InetAddress a = (InetAddress) hosts.get(host);
        if (a == null) {
            a = InetAddress.getByName(host);
            hosts.put(host, a);
        }
        sock.send(new DatagramPacket(b, len, a, Integer.parseInt(s.substring(c + 1))));
    }

    public int receive(byte[] buf) throws java.io.IOException {
        if (rx == null || rxBuf != buf) {
            rx = new DatagramPacket(buf, buf.length);
            rxBuf = buf;
        }
        rx.setData(buf, 0, buf.length);
        sock.receive(rx);
        lastAddr = "datagram://" + rx.getAddress().getHostAddress() + ":" + rx.getPort();
        return rx.getLength();
    }

    public void close() {
        try { sock.close(); } catch (Throwable t) { }
    }
}
