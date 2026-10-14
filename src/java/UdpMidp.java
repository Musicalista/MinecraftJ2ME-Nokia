import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.DatagramConnection;

/** UDP через Generic Connection Framework (телефоны с MIDP). */
final class UdpMidp extends UdpSock {
    private DatagramConnection sock;
    private Datagram rx;
    private byte[] rxBuf;

    public boolean open(int port) {
        try {
            sock = (DatagramConnection) Connector.open(port > 0 ? "datagram://:" + port : "datagram://:");
            return true;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    public void send(String addr, byte[] b, int len) throws java.io.IOException {
        sock.send(sock.newDatagram(b, len, addr));
    }

    public int receive(byte[] buf) throws java.io.IOException {
        if (rx == null || rxBuf != buf) {
            // у сокета "datagram://:порт" адрес по умолчанию ":" — без явного адреса newDatagram падает
            rx = sock.newDatagram(buf, buf.length, "datagram://127.0.0.1:19132");
            rxBuf = buf;
        }
        rx.setData(buf, 0, buf.length);
        sock.receive(rx);
        lastAddr = rx.getAddress();
        return rx.getLength();
    }

    public void close() {
        try { sock.close(); } catch (Throwable t) { }
    }
}
