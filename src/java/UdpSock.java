/**
 * UDP-сокет сети. Основной путь — javax.microedition.io (datagram://); в J2ME Loader он на каждом пакете делает
 * обратный DNS в Datagram.getAddress (~70 мс), поэтому там, где доступен java.net (Android-рантайм J2ME Loader),
 * берётся UdpJava. Адрес везде — строка "datagram://ip:порт".
 */
public abstract class UdpSock {
    public abstract boolean open(int port);

    public abstract void send(String addr, byte[] b, int len) throws java.io.IOException;

    /** блокирующий приём; длина, адрес — в lastAddr */
    public abstract int receive(byte[] buf) throws java.io.IOException;

    public abstract void close();

    public String lastAddr;
    public String error;

    public static UdpSock create() {
        try {
            Class.forName("java.net.DatagramSocket");
            return (UdpSock) Class.forName("UdpJava").newInstance();
        } catch (Throwable t) {
            return new UdpMidp();
        }
    }
}
