/** Чтение пакета (пара к NetOut). За концом данных — нули, без исключений. */
public final class NetIn {
    public final byte[] b;
    public int p;
    public final int end;

    public NetIn(byte[] b, int off, int len) {
        this.b = b;
        this.p = off;
        this.end = off + len;
    }

    public int remaining() {
        return end - p;
    }

    public int u8() {
        return p < end ? b[p++] & 255 : 0;
    }

    public int s8() {
        return p < end ? b[p++] : 0;
    }

    public boolean bool() {
        return (u8() & 0x80) != 0;
    }

    public int u16() {
        return (u8() << 8) | u8();
    }

    public int s16() {
        return (short) u16();
    }

    public int i32() {
        return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
    }

    public long i64() {
        long hi = i32() & 0xFFFFFFFFL;
        return (hi << 32) | (i32() & 0xFFFFFFFFL);
    }

    public float f32() {
        return Float.intBitsToFloat(i32());
    }

    public int u24le() {
        return u8() | (u8() << 8) | (u8() << 16);
    }

    public int s16le() {
        return (short) (u8() | (u8() << 8));
    }

    public int i32le() {
        return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24);
    }

    public float f32le() {
        return Float.intBitsToFloat(i32le());
    }

    public String str() {
        int len = u16();
        if (len > remaining()) len = remaining();
        char[] c = new char[len];
        for (int i = 0; i < len; i++) c[i] = (char) (b[p++] & 255);
        return new String(c);
    }

    public String strLe() {
        int len = s16le();
        if (len < 0) len = 0;
        if (len > remaining()) len = remaining();
        char[] c = new char[len];
        for (int i = 0; i < len; i++) c[i] = (char) (b[p++] & 255);
        return new String(c);
    }
}
