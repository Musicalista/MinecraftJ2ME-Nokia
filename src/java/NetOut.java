/**
 * Запись пакета как RakNet::BitStream: числа big-endian (DoEndianSwap на ARM), uint24 — little-endian,
 * строки RakString — u16 длина + байты. Для SynchedEntityData/подписей (RakDataOutput) — little-endian (*le).
 */
public final class NetOut {
    public byte[] b;
    public int n;

    public NetOut(int cap) {
        b = new byte[cap];
    }

    public NetOut reset() {
        n = 0;
        return this;
    }

    private void need(int k) {
        if (n + k > b.length) {
            byte[] nb = new byte[Math.max(b.length * 2, n + k)];
            System.arraycopy(b, 0, nb, 0, n);
            b = nb;
        }
    }

    public void u8(int v) {
        need(1);
        b[n++] = (byte) v;
    }

    public void bool(boolean v) {
        // BitStream::Write(bool) — один бит; в конце пакета занимает байт (0x80 / 0x00)
        u8(v ? 0x80 : 0);
    }

    public void u16(int v) {
        need(2);
        b[n++] = (byte) (v >> 8);
        b[n++] = (byte) v;
    }

    public void i32(int v) {
        need(4);
        b[n++] = (byte) (v >> 24);
        b[n++] = (byte) (v >> 16);
        b[n++] = (byte) (v >> 8);
        b[n++] = (byte) v;
    }

    public void i64(long v) {
        i32((int) (v >> 32));
        i32((int) v);
    }

    public void f32(float v) {
        i32(Float.floatToIntBits(v));
    }

    public void u24le(int v) {
        need(3);
        b[n++] = (byte) v;
        b[n++] = (byte) (v >> 8);
        b[n++] = (byte) (v >> 16);
    }

    public void i16le(int v) {
        need(2);
        b[n++] = (byte) v;
        b[n++] = (byte) (v >> 8);
    }

    public void i32le(int v) {
        need(4);
        b[n++] = (byte) v;
        b[n++] = (byte) (v >> 8);
        b[n++] = (byte) (v >> 16);
        b[n++] = (byte) (v >> 24);
    }

    public void f32le(float v) {
        i32le(Float.floatToIntBits(v));
    }

    public void str(String s) {
        if (s == null) s = "";
        int len = Math.min(s.length(), 0xFFFF);
        u16(len);
        need(len);
        for (int i = 0; i < len; i++) b[n++] = (byte) s.charAt(i);
    }

    /** RakDataOutput::writeString: длина little-endian short */
    public void strLe(String s) {
        if (s == null) s = "";
        int len = s.length() & 0x7FFF;
        i16le(len);
        need(len);
        for (int i = 0; i < len; i++) b[n++] = (byte) s.charAt(i);
    }

    public void bytes(byte[] src, int off, int len) {
        need(len);
        System.arraycopy(src, off, b, n, len);
        n += len;
    }

    public byte[] toBytes() {
        byte[] r = new byte[n];
        System.arraycopy(b, 0, r, 0, n);
        return r;
    }
}
