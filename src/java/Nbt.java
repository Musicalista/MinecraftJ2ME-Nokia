import java.io.*;
import java.util.Hashtable;
import java.util.Vector;

/** NBT в варианте MCPE: little-endian, строки — short длина + байты. */
public final class Nbt {
    public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11;

    public static final class Compound {
        public final Vector keys = new Vector();
        public final Hashtable map = new Hashtable();
        public final Hashtable types = new Hashtable();

        public void put(String k, int type, Object v) {
            if (!map.containsKey(k)) keys.addElement(k);
            map.put(k, v);
            types.put(k, new Integer(type));
        }

        public void putByte(String k, int v) { put(k, BYTE, new Byte((byte) v)); }
        public void putShort(String k, int v) { put(k, SHORT, new Short((short) v)); }
        public void putInt(String k, int v) { put(k, INT, new Integer(v)); }
        public void putLong(String k, long v) { put(k, LONG, new Long(v)); }
        public void putFloat(String k, float v) { put(k, FLOAT, new Float(v)); }
        public void putString(String k, String v) { put(k, STRING, v); }
        public void putCompound(String k, Compound v) { put(k, COMPOUND, v); }
        public void putList(String k, NList v) { put(k, LIST, v); }
        public void putBoolean(String k, boolean v) { putByte(k, v ? 1 : 0); }

        public boolean contains(String k) { return map.containsKey(k); }

        public int typeOf(String k) {
            Integer t = (Integer) types.get(k);
            return t == null ? END : t.intValue();
        }

        public long getLong(String k) {
            Object o = map.get(k);
            if (o instanceof Long) return ((Long) o).longValue();
            if (o instanceof Integer) return ((Integer) o).intValue();
            if (o instanceof Short) return ((Short) o).shortValue();
            if (o instanceof Byte) return ((Byte) o).byteValue();
            if (o instanceof Float) return (long) ((Float) o).floatValue();
            if (o instanceof Double) return (long) ((Double) o).doubleValue();
            return 0;
        }

        public int getInt(String k) { return (int) getLong(k); }
        public int getShort(String k) { return (short) getLong(k); }
        public int getByte(String k) { return (byte) getLong(k); }
        public boolean getBoolean(String k) { return getLong(k) != 0; }

        public float getFloat(String k) {
            Object o = map.get(k);
            if (o instanceof Float) return ((Float) o).floatValue();
            if (o instanceof Double) return (float) ((Double) o).doubleValue();
            return getLong(k);
        }

        public String getString(String k) {
            Object o = map.get(k);
            return o instanceof String ? (String) o : "";
        }

        public Compound getCompound(String k) {
            Object o = map.get(k);
            return o instanceof Compound ? (Compound) o : null;
        }

        public NList getList(String k) {
            Object o = map.get(k);
            return o instanceof NList ? (NList) o : null;
        }
    }

    public static final class NList {
        public int type;
        public final Vector items = new Vector();

        public NList(int type) { this.type = type; }

        public void add(Object o) { items.addElement(o); }
        public int size() { return items.size(); }
        public Object get(int i) { return items.elementAt(i); }

        public float getFloat(int i) {
            Object o = items.elementAt(i);
            if (o instanceof Float) return ((Float) o).floatValue();
            if (o instanceof Double) return (float) ((Double) o).doubleValue();
            return 0;
        }

        public static NList floats(float a, float b, float c) {
            NList l = new NList(FLOAT);
            l.add(new Float(a)); l.add(new Float(b)); l.add(new Float(c));
            return l;
        }

        public static NList floats(float a, float b) {
            NList l = new NList(FLOAT);
            l.add(new Float(a)); l.add(new Float(b));
            return l;
        }
    }

    // ---- ввод/вывод little-endian ----

    public static final class LEOut {
        public final ByteArrayOutputStream bo = new ByteArrayOutputStream();

        public void b(int v) { bo.write(v & 255); }
        public void s(int v) { b(v); b(v >> 8); }
        public void i(int v) { b(v); b(v >> 8); b(v >> 16); b(v >> 24); }
        public void l(long v) { i((int) v); i((int) (v >>> 32)); }
        public void f(float v) { i(Float.floatToIntBits(v)); }
        public void d(double v) { l(Double.doubleToLongBits(v)); }

        public void str(String s) {
            byte[] u;
            try { u = s.getBytes("UTF-8"); } catch (Exception e) { u = s.getBytes(); }
            s(u.length);
            bo.write(u, 0, u.length);
        }

        public byte[] bytes() { return bo.toByteArray(); }
    }

    public static final class LEIn {
        final byte[] buf;
        int p;

        public LEIn(byte[] buf, int off) { this.buf = buf; this.p = off; }

        public int b() { return buf[p++]; }
        public int s() { int v = (buf[p] & 255) | (buf[p + 1] << 8); p += 2; return (short) v; }
        public int i() { int v = (buf[p] & 255) | ((buf[p + 1] & 255) << 8) | ((buf[p + 2] & 255) << 16) | (buf[p + 3] << 24); p += 4; return v; }
        public long l() { long lo = i() & 0xFFFFFFFFL; long hi = i(); return lo | (hi << 32); }
        public float f() { return Float.intBitsToFloat(i()); }
        public double d() { return Double.longBitsToDouble(l()); }

        public String str() {
            int n = s() & 0xFFFF;
            String r;
            try { r = new String(buf, p, n, "UTF-8"); } catch (Exception e) { r = new String(buf, p, n); }
            p += n;
            return r;
        }
    }

    public static Compound read(byte[] data, int off) {
        LEIn in = new LEIn(data, off);
        int type = in.b();
        if (type != COMPOUND) return null;
        in.str();
        return (Compound) readPayload(in, COMPOUND);
    }

    static Object readPayload(LEIn in, int type) {
        switch (type) {
            case BYTE: return new Byte((byte) in.b());
            case SHORT: return new Short((short) in.s());
            case INT: return new Integer(in.i());
            case LONG: return new Long(in.l());
            case FLOAT: return new Float(in.f());
            case DOUBLE: return new Double(in.d());
            case BYTE_ARRAY: {
                int n = in.i();
                byte[] a = new byte[n];
                System.arraycopy(in.buf, in.p, a, 0, n);
                in.p += n;
                return a;
            }
            case STRING: return in.str();
            case LIST: {
                int t = in.b();
                int n = in.i();
                NList l = new NList(t);
                for (int k = 0; k < n; k++) l.add(readPayload(in, t));
                return l;
            }
            case COMPOUND: {
                Compound c = new Compound();
                while (true) {
                    int t = in.b();
                    if (t == END) break;
                    String name = in.str();
                    c.put(name, t, readPayload(in, t));
                }
                return c;
            }
            case INT_ARRAY: {
                int n = in.i();
                int[] a = new int[n];
                for (int k = 0; k < n; k++) a[k] = in.i();
                return a;
            }
        }
        throw new RuntimeException("NBT: bad tag " + type);
    }

    public static byte[] write(Compound c) {
        LEOut o = new LEOut();
        o.b(COMPOUND);
        o.str("");
        writePayload(o, COMPOUND, c);
        return o.bytes();
    }

    static void writePayload(LEOut o, int type, Object v) {
        switch (type) {
            case BYTE: o.b(((Byte) v).byteValue()); break;
            case SHORT: o.s(((Short) v).shortValue()); break;
            case INT: o.i(((Integer) v).intValue()); break;
            case LONG: o.l(((Long) v).longValue()); break;
            case FLOAT: o.f(((Float) v).floatValue()); break;
            case DOUBLE: o.d(((Double) v).doubleValue()); break;
            case BYTE_ARRAY: {
                byte[] a = (byte[]) v;
                o.i(a.length);
                o.bo.write(a, 0, a.length);
                break;
            }
            case STRING: o.str((String) v); break;
            case LIST: {
                NList l = (NList) v;
                int t = l.type;
                if (l.size() == 0 && t == END) t = BYTE;
                o.b(t);
                o.i(l.size());
                for (int k = 0; k < l.size(); k++) writePayload(o, t, l.get(k));
                break;
            }
            case COMPOUND: {
                Compound c = (Compound) v;
                for (int k = 0; k < c.keys.size(); k++) {
                    String name = (String) c.keys.elementAt(k);
                    int t = c.typeOf(name);
                    o.b(t);
                    o.str(name);
                    writePayload(o, t, c.map.get(name));
                }
                o.b(END);
                break;
            }
            case INT_ARRAY: {
                int[] a = (int[]) v;
                o.i(a.length);
                for (int k = 0; k < a.length; k++) o.i(a[k]);
                break;
            }
        }
    }
}
