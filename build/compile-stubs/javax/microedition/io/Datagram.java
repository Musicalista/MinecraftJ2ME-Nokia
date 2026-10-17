package javax.microedition.io;
public interface Datagram {
    int getLength();
    byte[] getData();
    int getOffset();
    void setLength(int len);
    String getAddress();
    void setData(byte[] buf, int off, int len);
}
