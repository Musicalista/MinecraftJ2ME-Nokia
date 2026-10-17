package javax.microedition.io;
import java.io.*;
public interface DatagramConnection extends Connection {
    int getMaximumLength() throws IOException;
    int getNominalLength() throws IOException;
    Datagram newDatagram(int size) throws IOException;
    Datagram newDatagram(int size, String addr) throws IOException;
    Datagram newDatagram(byte[] buf, int size) throws IOException;
    Datagram newDatagram(byte[] buf, int size, String addr) throws IOException;
    void send(Datagram d) throws IOException;
    void receive(Datagram d) throws IOException;
}
