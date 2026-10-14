import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Своя реализация той части RakNet 4.036 (RAKNET_PROTOCOL_VERSION 5), что использует MCPE 0.6.1: офлайн-пинг,
 * рукопожатие OPEN_CONNECTION_REQUEST/REPLY 1/2, слой надёжности (ACK/NAK, номера, порядок, split), CONNECTION_REQUEST/
 * ACCEPTED, NEW_INCOMING_CONNECTION, пинги и отключение. Форматы — как в ReliabilityLayer.cpp/RakPeer.cpp исходников.
 * Два потока: приём (блокирующий receive) и обслуживание (ACK, повторы, отправка, тайм-ауты, раз в 10 мс).
 * Игра забирает события через poll() в своём потоке.
 */
public final class Raknet {
    static final byte[] MAGIC = { 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
            (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, 0x12, 0x34, 0x56, 0x78 };
    static final int PROTOCOL = 5;
    static final int UDP_HEADER = 28, MAX_MTU = 1492;
    static final int[] MTU_TRY = { 1492, 1200, 576 };

    // MessageIdentifiers.h
    static final int ID_CONNECTED_PING = 0x00, ID_UNCONNECTED_PING = 0x01, ID_UNCONNECTED_PING_OPEN = 0x02, ID_CONNECTED_PONG = 0x03,
            ID_DETECT_LOST = 0x04, ID_OCR1 = 0x05, ID_OCREPLY1 = 0x06, ID_OCR2 = 0x07, ID_OCREPLY2 = 0x08, ID_CONNECTION_REQUEST = 0x09,
            ID_CONNECTION_ACCEPTED = 0x10, ID_ATTEMPT_FAILED = 0x11, ID_ALREADY_CONNECTED = 0x12, ID_NEW_INCOMING = 0x13,
            ID_NO_FREE = 0x14, ID_DISCONNECT = 0x15, ID_LOST = 0x16, ID_BANNED = 0x17, ID_INCOMPATIBLE = 0x19, ID_RECENTLY = 0x1a,
            ID_UNCONNECTED_PONG = 0x1c, ID_USER = 0x81;

    // PacketReliability
    public static final int UNRELIABLE = 0, UNRELIABLE_SEQ = 1, RELIABLE = 2, RELIABLE_ORDERED = 3, RELIABLE_SEQ = 4;

    // события для игры
    public static final int EV_PACKET = 1, EV_NEW_CLIENT = 2, EV_CONNECTED = 3, EV_DISCONNECT = 4, EV_FAILED = 5, EV_PONG = 6;

    public static final class Ev {
        public int type;
        public Conn conn;
        public byte[] data;
        public String addr, name;
        public long guid;
    }

    /** Состояние соединения (RemoteSystemStruct + ReliabilityLayer). */
    public static final class Conn {
        public String addr;
        public long guid;
        int mtu = 576;
        static final int ST_UNVERIFIED = 0, ST_REQUESTED = 1, ST_HANDLING = 2, ST_CONNECTED = 3, ST_CLOSING = 4;
        int state;
        boolean weInitiated;
        long lastRecv, lastPing, created, closeAt;
        public Object user;
        // отправка
        int sendDgram, sendRel, sendSplitId;
        final int[] sendOrder = new int[32], sendSeq = new int[32];
        final Vector queue = new Vector();          // ещё не отправленные IP
        final Hashtable inFlight = new Hashtable(); // номер надёжного -> IP
        final Hashtable dgrams = new Hashtable();   // номер датаграммы -> int[] надёжных номеров
        final Vector resend = new Vector();         // IP к повтору
        // приём
        int expectDgram;
        final Vector acks = new Vector(), naks = new Vector(); // Integer номера датаграмм
        int relBase;
        final Hashtable relSeen = new Hashtable();
        final int[] ordExpect = new int[32];
        final Hashtable[] ordHeld = new Hashtable[32];
        final int[] seqHighest = new int[32];
        final Hashtable splits = new Hashtable();
        int rtt = 100;

        public boolean connected() {
            return state == ST_CONNECTED;
        }
    }

    /** InternalPacket */
    static final class IP {
        int rel, relNum, seqIdx, ordIdx, chan;
        int splitCount, splitId, splitIdx;
        byte[] data;
        long sentAt;
        int sends;
    }

    static final class Split {
        byte[][] parts;
        int got, rel, ordIdx, chan, seqIdx;
    }

    private UdpSock sock;
    public int port;
    public final long guid;
    public boolean server;
    public int maxConn = 4;
    private volatile boolean running;
    private final Vector conns = new Vector();
    private final Vector events = new Vector();
    /** офлайн-ответ на пинг (RakPeer::SetOfflinePingResponse) */
    private byte[] pongData = new byte[0];
    // исходящее подключение (RequestedConnectionStruct)
    private String connAddr;
    private int connTries;
    private long connNext;
    // поиск игр
    private boolean pinging;
    private long pingNext;
    public boolean broadcastFailed;
    public String error;
    /** Bluetooth: адреса BtNet.PREFIX... идут не в сокет, а в RFCOMM */
    public BtNet bt;

    public Raknet() {
        java.util.Random r = new java.util.Random();
        guid = r.nextLong() ^ System.currentTimeMillis();
    }

    // ---- запуск/остановка ----

    /** Хост: порт 19132, занят — 19133..19135 (клиенты MCPE пингуют basePort..basePort+3). */
    public boolean host(int basePort) {
        server = true;
        for (int i = 0; i < 4; i++) {
            sock = UdpSock.create();
            if (sock.open(basePort + i)) {
                port = basePort + i;
                start();
                return true;
            }
            error = sock.error;
        }
        return false;
    }

    /** Клиент: свободный порт. */
    public boolean open() {
        server = false;
        sock = UdpSock.create();
        if (!sock.open(0)) {
            error = sock.error;
            return false;
        }
        Log.i("NET socket " + sock.getClass().getName());
        start();
        return true;
    }

    /** Клиент по Bluetooth: без UDP-сокета, датаграммы приносит BtNet (inject). */
    public boolean openBt(BtNet b) {
        server = false;
        bt = b;
        start();
        return true;
    }

    private void start() {
        running = true;
        if (sock == null) {
            new Thread(new Runnable() {
                public void run() {
                    updateLoop();
                }
            }).start();
            return;
        }
        Thread rx = new Thread(new Runnable() {
            public void run() {
                recvLoop();
            }
        });
        Thread up = new Thread(new Runnable() {
            public void run() {
                updateLoop();
            }
        });
        rx.start();
        up.start();
    }

    /** Отключение: всем ID_DISCONNECTION_NOTIFICATION, короткое ожидание отправки, закрытие сокета. */
    public void shutdown() {
        if (!running) return;
        synchronized (this) {
            for (int i = 0; i < conns.size(); i++) {
                Conn c = (Conn) conns.elementAt(i);
                if (c.state == Conn.ST_CONNECTED) sendRaw(c, new byte[] { (byte) ID_DISCONNECT }, 1, RELIABLE_ORDERED);
            }
            flushAll(System.currentTimeMillis());
        }
        try { Thread.sleep(60); } catch (InterruptedException e) { }
        synchronized (this) {
            flushAll(System.currentTimeMillis());
            running = false;
            notifyAll();
        }
        if (sock != null) sock.close();
    }

    public boolean running() {
        return running;
    }

    public synchronized void setPong(String data) {
        // RakNetInstance::announceServer: BitStream с RakString — u16 длина + байты
        NetOut o = new NetOut(64);
        o.str(data);
        pongData = o.toBytes();
    }

    public synchronized int connCount() {
        int n = 0;
        for (int i = 0; i < conns.size(); i++) if (((Conn) conns.elementAt(i)).state != Conn.ST_CLOSING) n++;
        return n;
    }

    // ---- поиск игр и подключение ----

    public synchronized void startPing() {
        pinging = true;
        pingNext = 0;
        notifyAll();
    }

    public synchronized void stopPing() {
        pinging = false;
    }

    /** RakPeer::Connect: OPEN_CONNECTION_REQUEST_1 до 12 раз через 500 мс с MTU 1492/1200/576. */
    public synchronized void connect(String host, int port) {
        connAddr = "datagram://" + host + ":" + port;
        connTries = 0;
        connNext = 0;
        notifyAll();
    }

    public synchronized void close(Conn c, boolean notify) {
        if (c.state == Conn.ST_CLOSING) return;
        if (notify && c.state == Conn.ST_CONNECTED) sendRaw(c, new byte[] { (byte) ID_DISCONNECT }, 1, RELIABLE_ORDERED);
        c.state = Conn.ST_CLOSING;
        c.closeAt = System.currentTimeMillis() + 300;
        notifyAll();
    }

    // ---- события ----

    public Ev poll() {
        synchronized (events) {
            if (events.isEmpty()) return null;
            Ev e = (Ev) events.elementAt(0);
            events.removeElementAt(0);
            return e;
        }
    }

    private void event(int type, Conn c, byte[] data) {
        Ev e = new Ev();
        e.type = type;
        e.conn = c;
        e.data = data;
        synchronized (events) {
            events.addElement(e);
        }
    }

    // ---- отправка из игры ----

    /** Пакет игры (первый байт — id) в соединение; разбивка на части — по MTU. */
    public synchronized void send(Conn c, byte[] data, int len, int rel) {
        if (c == null || c.state == Conn.ST_CLOSING) return;
        sendRaw(c, data, len, rel);
        notifyAll();
    }

    private void sendRaw(Conn c, byte[] data, int len, int rel) {
        int maxData = c.mtu - UDP_HEADER - 9 - 23;
        boolean ordered = rel == RELIABLE_ORDERED || rel == RELIABLE_SEQ || rel == UNRELIABLE_SEQ;
        int ord = 0, seq = 0;
        if (rel == RELIABLE_ORDERED) ord = c.sendOrder[0]++;
        else if (rel == RELIABLE_SEQ || rel == UNRELIABLE_SEQ) { ord = c.sendOrder[0]; seq = c.sendSeq[0]++; }
        if (len > maxData) {
            // split: каждая часть надёжная (ненадёжное делается надёжным, как в ReliabilityLayer::SplitPacket)
            if (rel == UNRELIABLE) rel = RELIABLE;
            if (rel == UNRELIABLE_SEQ) rel = RELIABLE_SEQ;
            int count = (len + maxData - 1) / maxData;
            int id = c.sendSplitId++ & 0xFFFF;
            for (int i = 0; i < count; i++) {
                int off = i * maxData, n = Math.min(maxData, len - off);
                IP p = new IP();
                p.rel = rel;
                p.data = new byte[n];
                System.arraycopy(data, off, p.data, 0, n);
                p.ordIdx = ord;
                p.seqIdx = seq;
                p.chan = 0;
                p.splitCount = count;
                p.splitId = id;
                p.splitIdx = i;
                if (rel != UNRELIABLE && rel != UNRELIABLE_SEQ) p.relNum = c.sendRel++ & 0xFFFFFF;
                c.queue.addElement(p);
            }
            return;
        }
        IP p = new IP();
        p.rel = rel;
        p.data = new byte[len];
        System.arraycopy(data, 0, p.data, 0, len);
        p.ordIdx = ord;
        p.seqIdx = seq;
        if (ordered) p.chan = 0;
        if (rel == RELIABLE || rel == RELIABLE_ORDERED || rel == RELIABLE_SEQ) p.relNum = c.sendRel++ & 0xFFFFFF;
        c.queue.addElement(p);
    }

    // ---- сокет ----

    private final NetOut raw = new NetOut(MAX_MTU);

    private void sendTo(String addr, byte[] b, int len) {
        BtNet bn = bt;
        if (bn != null && BtNet.isBt(addr)) {
            bn.send(addr, b, len);
            return;
        }
        if (sock == null) return;
        try {
            sock.send(addr, b, len);
        } catch (Throwable t) {
            if (addr.indexOf("255.255.255.255") >= 0) broadcastFailed = true;
            else if (Prof.on) Log.i("NET send " + addr + ": " + t);
        }
    }

    private void recvLoop() {
        byte[] buf = new byte[2048];
        while (running) {
            try {
                int len = sock.receive(buf);
                String addr = sock.lastAddr;
                synchronized (this) {
                    handleDatagram(addr, buf, len);
                }
            } catch (Throwable t) {
                if (!running) break;
                Log.i("NET recv: " + t);
                try { Thread.sleep(5); } catch (InterruptedException e) { }
            }
        }
    }

    /** Датаграмма, пришедшая по Bluetooth (поток чтения BtNet). */
    public void inject(String addr, byte[] b, int len) {
        synchronized (this) {
            if (running) handleDatagram(addr, b, len);
        }
    }

    /** Bluetooth-соединение порвалось: сразу отключение, без 20-секундного тайм-аута. */
    public synchronized void dropAddr(String addr) {
        if (!running) return;
        if (!server && addr.equals(connAddr)) {
            connAddr = null;
            event(EV_FAILED, null, null);
        }
        Conn c = find(addr);
        if (c == null) return;
        conns.removeElement(c);
        boolean was = c.state == Conn.ST_CONNECTED;
        c.state = Conn.ST_CLOSING;
        if (was) event(EV_DISCONNECT, c, null);
        else if (c.weInitiated) event(EV_FAILED, null, null);
    }

    private void updateLoop() {
        while (running) {
            synchronized (this) {
                try {
                    long now = System.currentTimeMillis();
                    update(now);
                    wait(10);
                } catch (Throwable t) {
                    Log.e("NET update", t);
                }
            }
        }
    }

    private Conn find(String addr) {
        for (int i = 0; i < conns.size(); i++) {
            Conn c = (Conn) conns.elementAt(i);
            if (c.addr.equals(addr)) return c;
        }
        return null;
    }

    // ---- приём ----

    private void handleDatagram(String addr, byte[] b, int len) {
        if (len < 1) return;
        int id = b[0] & 255;
        Conn c = find(addr);
        if ((id & 0x80) != 0 && c != null) {
            if (c.state != Conn.ST_CLOSING) handleConnected(c, b, len);
            return;
        }
        handleOffline(addr, b, len, c);
    }

    private static boolean magicAt(byte[] b, int off, int len) {
        if (off + 16 > len) return false;
        for (int i = 0; i < 16; i++) if (b[off + i] != MAGIC[i]) return false;
        return true;
    }

    private void handleOffline(String addr, byte[] b, int len, Conn existing) {
        int id = b[0] & 255;
        NetIn in = new NetIn(b, 0, len);
        in.p = 1;
        long now = System.currentTimeMillis();
        switch (id) {
            case ID_UNCONNECTED_PING:
            case ID_UNCONNECTED_PING_OPEN: {
                if (!server || len != 1 + 8 + 16 || !magicAt(b, 9, len)) return;
                if (id == ID_UNCONNECTED_PING_OPEN && connCount() >= maxConn) return;
                long t = in.i64();
                raw.reset();
                raw.u8(ID_UNCONNECTED_PONG);
                raw.i64(t);
                raw.i64(guid);
                raw.bytes(MAGIC, 0, 16);
                raw.bytes(pongData, 0, pongData.length);
                sendTo(addr, raw.b, raw.n);
                return;
            }
            case ID_UNCONNECTED_PONG: {
                if (server || len < 1 + 8 + 8 + 16 || !magicAt(b, 17, len)) return;
                in.i64();
                long g = in.i64();
                in.p += 16;
                String data = in.remaining() >= 2 ? in.str() : "";
                Ev e = new Ev();
                e.type = EV_PONG;
                e.addr = addr;
                e.name = data;
                e.guid = g;
                synchronized (events) {
                    events.addElement(e);
                }
                return;
            }
            case ID_OCR1: {
                if (!server || len <= 17 || !magicAt(b, 1, len)) return;
                raw.reset();
                if ((b[17] & 255) != PROTOCOL) {
                    raw.u8(ID_INCOMPATIBLE);
                    raw.u8(PROTOCOL);
                    raw.bytes(MAGIC, 0, 16);
                    raw.i64(guid);
                } else {
                    raw.u8(ID_OCREPLY1);
                    raw.bytes(MAGIC, 0, 16);
                    raw.i64(guid);
                    raw.u8(0); // без защиты
                    raw.u16(Math.min(MAX_MTU, len + UDP_HEADER));
                }
                sendTo(addr, raw.b, raw.n);
                return;
            }
            case ID_OCR2: {
                if (!server || len < 1 + 16 + 7 + 2 + 8 || !magicAt(b, 1, len)) return;
                in.p = 17;
                readAddress(in);
                int mtu = in.u16();
                long g = in.i64();
                if (existing != null && existing.guid != g) {
                    conns.removeElement(existing);
                    existing = null;
                }
                if (existing == null) {
                    if (connCount() >= maxConn) {
                        raw.reset();
                        raw.u8(ID_NO_FREE);
                        raw.bytes(MAGIC, 0, 16);
                        raw.i64(g);
                        sendTo(addr, raw.b, raw.n);
                        return;
                    }
                    existing = newConn(addr, g, mtu, Conn.ST_UNVERIFIED);
                }
                raw.reset();
                raw.u8(ID_OCREPLY2);
                raw.bytes(MAGIC, 0, 16);
                raw.i64(guid);
                writeAddress(raw, addr);
                raw.u16(existing.mtu);
                raw.u8(0); // requiresSecurityOfThisClient (1 бит)
                sendTo(addr, raw.b, raw.n);
                return;
            }
            case ID_OCREPLY1: {
                if (server || connAddr == null || len < 1 + 16 + 8 + 1 + 2 || !magicAt(b, 1, len)) return;
                in.p = 17;
                in.i64();
                int sec = in.u8();
                if (sec != 0) in.i32();
                int mtu = in.u16();
                connAddr = addr; // ответ мог прийти с другим именем адреса — запоминаем его
                raw.reset();
                raw.u8(ID_OCR2);
                raw.bytes(MAGIC, 0, 16);
                writeAddress(raw, addr);
                raw.u16(mtu);
                raw.i64(guid);
                sendTo(addr, raw.b, raw.n);
                return;
            }
            case ID_OCREPLY2: {
                if (server || connAddr == null || len < 1 + 16 + 8 + 7 + 2 || !magicAt(b, 1, len)) return;
                in.p = 17;
                long g = in.i64();
                readAddress(in);
                int mtu = in.u16();
                if (existing != null) return;
                Conn c = newConn(addr, g, mtu, Conn.ST_REQUESTED);
                c.weInitiated = true;
                connAddr = null;
                // ID_CONNECTION_REQUEST: guid, время, без защиты, без пароля — RELIABLE
                raw.reset();
                raw.u8(ID_CONNECTION_REQUEST);
                raw.i64(guid);
                raw.i64(now);
                raw.u8(0);
                sendRaw(c, raw.b, raw.n, RELIABLE);
                return;
            }
            case ID_ATTEMPT_FAILED:
            case ID_ALREADY_CONNECTED:
            case ID_NO_FREE:
            case ID_BANNED:
            case ID_RECENTLY:
            case ID_INCOMPATIBLE:
                if (!server && connAddr != null) {
                    connAddr = null;
                    event(EV_FAILED, null, null);
                }
                return;
        }
    }

    private Conn newConn(String addr, long g, int mtu, int state) {
        Conn c = new Conn();
        c.addr = addr;
        c.guid = g;
        c.mtu = Math.max(576, Math.min(MAX_MTU, mtu));
        c.state = state;
        c.lastRecv = c.created = c.lastPing = System.currentTimeMillis();
        for (int i = 0; i < 32; i++) c.ordHeld[i] = new Hashtable();
        conns.addElement(c);
        return c;
    }

    /** SystemAddress: версия 4, инвертированный IPv4, порт (big-endian). */
    static void writeAddress(NetOut o, String addr) {
        int[] ip = parseIp(addr);
        o.u8(4);
        for (int i = 0; i < 4; i++) o.u8(~ip[i] & 255);
        o.u16(ip[4]);
    }

    static void readAddress(NetIn in) {
        int v = in.u8();
        if (v == 4) in.p += 6;
        else in.p += 26;
    }

    /** "datagram://a.b.c.d:port" -> {a,b,c,d,port}; имя хоста — 127.0.0.1 для localhost, иначе нули. */
    static int[] parseIp(String addr) {
        int[] r = new int[5];
        String s = addr.startsWith("datagram://") ? addr.substring(11) : addr;
        int c = s.lastIndexOf(':');
        String host = c >= 0 ? s.substring(0, c) : s;
        try { r[4] = c >= 0 ? Integer.parseInt(s.substring(c + 1)) : 0; } catch (Exception e) { }
        if (host.equals("localhost")) { r[0] = 127; r[3] = 1; return r; }
        int k = 0, v = 0;
        boolean digit = false;
        for (int i = 0; i <= host.length(); i++) {
            char ch = i < host.length() ? host.charAt(i) : '.';
            if (ch >= '0' && ch <= '9') { v = v * 10 + (ch - '0'); digit = true; }
            else if (ch == '.' && digit && k < 4) { r[k++] = v & 255; v = 0; digit = false; }
            else { r[0] = r[1] = r[2] = r[3] = 0; return r; }
        }
        return r;
    }

    /** Строка адреса для показа: хост без протокола и порта. */
    public static String hostOf(String addr) {
        String s = addr.startsWith("datagram://") ? addr.substring(11) : addr;
        int c = s.lastIndexOf(':');
        return c >= 0 ? s.substring(0, c) : s;
    }

    public static int portOf(String addr) {
        int c = addr.lastIndexOf(':');
        try { return Integer.parseInt(addr.substring(c + 1)); } catch (Exception e) { return 19132; }
    }

    private void handleConnected(Conn c, byte[] b, int len) {
        c.lastRecv = System.currentTimeMillis();
        int flags = b[0] & 255;
        NetIn in = new NetIn(b, 0, len);
        in.p = 1;
        if ((flags & 0x40) != 0) {
            // ACK: (hasBAndAS -> float AS), RangeList
            if ((flags & 0x20) != 0) in.p += 4;
            int n = in.u16();
            for (int i = 0; i < n && in.remaining() >= 4; i++) {
                boolean single = in.u8() != 0;
                int min = in.u24le(), max = single ? min : in.u24le();
                if (max - min > 10000) break;
                for (int d = min; d <= max; d++) acked(c, d);
            }
            return;
        }
        if ((flags & 0x20) != 0) {
            // NAK
            int n = in.u16();
            for (int i = 0; i < n && in.remaining() >= 4; i++) {
                boolean single = in.u8() != 0;
                int min = in.u24le(), max = single ? min : in.u24le();
                if (max - min > 10000) break;
                for (int d = min; d <= max; d++) naked(c, d);
            }
            return;
        }
        int dnum = in.u24le();
        // CCRakNetSlidingWindow::OnGotPacket: пропущенные номера — в NAK
        if (dnum == c.expectDgram) c.expectDgram = (dnum + 1) & 0xFFFFFF;
        else if (((dnum - c.expectDgram) & 0xFFFFFF) < 0x800000) {
            int skipped = (dnum - c.expectDgram) & 0xFFFFFF;
            if (skipped > 1000) skipped = 1000;
            for (int k = skipped; k > 0; k--) c.naks.addElement(new Integer((dnum - k) & 0xFFFFFF));
            c.expectDgram = (dnum + 1) & 0xFFFFFF;
        }
        c.acks.addElement(new Integer(dnum));
        while (in.remaining() >= 3) {
            int h = in.u8();
            int rel = (h >> 5) & 7;
            boolean split = (h & 0x10) != 0;
            int bits = in.u16();
            int bytes = (bits + 7) >> 3;
            IP p = new IP();
            p.rel = rel;
            if (rel == RELIABLE || rel == RELIABLE_ORDERED || rel == RELIABLE_SEQ) p.relNum = in.u24le();
            else p.relNum = -1;
            if (rel == UNRELIABLE_SEQ || rel == RELIABLE_SEQ) p.seqIdx = in.u24le();
            if (rel == UNRELIABLE_SEQ || rel == RELIABLE_SEQ || rel == RELIABLE_ORDERED) {
                p.ordIdx = in.u24le();
                p.chan = in.u8();
            }
            if (split) {
                p.splitCount = in.i32();
                p.splitId = in.u16();
                p.splitIdx = in.i32();
            }
            if (bytes == 0 || bytes > in.remaining() || p.chan >= 32 || (split && (p.splitIdx < 0 || p.splitIdx >= p.splitCount))) break;
            p.data = new byte[bytes];
            System.arraycopy(b, in.p, p.data, 0, bytes);
            in.p += bytes;
            receivedPacket(c, p);
        }
    }

    private void receivedPacket(Conn c, IP p) {
        // повторы надёжных — по номеру (hasReceivedPacketQueue)
        if (p.relNum >= 0) {
            int hole = (p.relNum - c.relBase) & 0xFFFFFF;
            if (hole >= 0x800000) return;
            if (hole == 0) {
                c.relBase = (c.relBase + 1) & 0xFFFFFF;
                while (c.relSeen.remove(new Integer(c.relBase)) != null) c.relBase = (c.relBase + 1) & 0xFFFFFF;
            } else {
                Integer k = new Integer(p.relNum);
                if (c.relSeen.containsKey(k)) return;
                if (hole > 100000) return;
                c.relSeen.put(k, k);
            }
        }
        if (p.splitCount > 0) {
            if (p.splitCount > 4096) return;
            Integer key = new Integer(p.splitId);
            Split s = (Split) c.splits.get(key);
            if (s == null) {
                s = new Split();
                s.parts = new byte[p.splitCount][];
                s.rel = p.rel;
                s.ordIdx = p.ordIdx;
                s.chan = p.chan;
                s.seqIdx = p.seqIdx;
                c.splits.put(key, s);
            }
            if (p.splitIdx >= s.parts.length || s.parts[p.splitIdx] != null) return;
            s.parts[p.splitIdx] = p.data;
            if (++s.got < s.parts.length) return;
            c.splits.remove(key);
            int total = 0;
            for (int i = 0; i < s.parts.length; i++) total += s.parts[i].length;
            byte[] all = new byte[total];
            int off = 0;
            for (int i = 0; i < s.parts.length; i++) {
                System.arraycopy(s.parts[i], 0, all, off, s.parts[i].length);
                off += s.parts[i].length;
            }
            p.data = all;
            p.splitCount = 0;
        }
        if (p.rel == RELIABLE_ORDERED) {
            int ch = p.chan;
            if (p.ordIdx == c.ordExpect[ch]) {
                deliver(c, p.data);
                c.ordExpect[ch] = (c.ordExpect[ch] + 1) & 0xFFFFFF;
                byte[] next;
                while ((next = (byte[]) c.ordHeld[ch].remove(new Integer(c.ordExpect[ch]))) != null) {
                    deliver(c, next);
                    c.ordExpect[ch] = (c.ordExpect[ch] + 1) & 0xFFFFFF;
                }
            } else if (((p.ordIdx - c.ordExpect[ch]) & 0xFFFFFF) < 0x800000) {
                c.ordHeld[ch].put(new Integer(p.ordIdx), p.data);
            }
            return;
        }
        if (p.rel == RELIABLE_SEQ || p.rel == UNRELIABLE_SEQ) {
            int ch = p.chan;
            if (((p.seqIdx - c.seqHighest[ch]) & 0xFFFFFF) >= 0x800000) return;
            c.seqHighest[ch] = p.seqIdx;
        }
        deliver(c, p.data);
    }

    /** Сообщения уровня соединения (RakPeer::RunUpdateCycle) и пакеты игры. */
    private void deliver(Conn c, byte[] d) {
        int id = d[0] & 255;
        long now = System.currentTimeMillis();
        NetIn in = new NetIn(d, 0, d.length);
        in.p = 1;
        switch (id) {
            case ID_CONNECTED_PING:
                if (d.length == 9) {
                    long t = in.i64();
                    raw.reset();
                    raw.u8(ID_CONNECTED_PONG);
                    raw.i64(t);
                    raw.i64(now);
                    sendRaw(c, raw.b, raw.n, UNRELIABLE);
                }
                return;
            case ID_CONNECTED_PONG:
                if (d.length == 17) {
                    long t = in.i64();
                    int r = (int) (now - t);
                    if (r >= 0 && r < 5000) c.rtt = (c.rtt * 3 + r) / 4;
                }
                return;
            case ID_DETECT_LOST:
                return;
            case ID_CONNECTION_REQUEST: {
                if (!server) return;
                in.i64();
                long t = in.i64();
                if (c.state == Conn.ST_UNVERIFIED) c.state = Conn.ST_HANDLING;
                // RakPeer::OnConnectionRequest
                raw.reset();
                raw.u8(ID_CONNECTION_ACCEPTED);
                writeAddress(raw, c.addr);
                raw.u16(conns.indexOf(c) & 0xFFFF);
                for (int i = 0; i < 10; i++) writeAddress(raw, i == 0 ? "datagram://127.0.0.1:" + port : "datagram://255.255.255.255:65535");
                raw.i64(t);
                raw.i64(now);
                sendRaw(c, raw.b, raw.n, RELIABLE_ORDERED);
                return;
            }
            case ID_NEW_INCOMING:
                if (server && c.state == Conn.ST_HANDLING) {
                    c.state = Conn.ST_CONNECTED;
                    event(EV_NEW_CLIENT, c, null);
                }
                return;
            case ID_CONNECTION_ACCEPTED: {
                if (server || c.state != Conn.ST_REQUESTED) return;
                readAddress(in);
                in.u16();
                for (int i = 0; i < 10; i++) readAddress(in);
                in.i64();
                long pong = in.remaining() >= 8 ? in.i64() : now;
                c.state = Conn.ST_CONNECTED;
                raw.reset();
                raw.u8(ID_NEW_INCOMING);
                writeAddress(raw, c.addr);
                for (int i = 0; i < 10; i++) writeAddress(raw, i == 0 ? "datagram://127.0.0.1:" + port : "datagram://255.255.255.255:65535");
                raw.i64(pong);
                raw.i64(now);
                sendRaw(c, raw.b, raw.n, RELIABLE_ORDERED);
                event(EV_CONNECTED, c, null);
                return;
            }
            case ID_DISCONNECT:
                if (c.state != Conn.ST_CLOSING) {
                    boolean was = c.state == Conn.ST_CONNECTED;
                    c.state = Conn.ST_CLOSING;
                    c.closeAt = now + 300;
                    if (was) event(EV_DISCONNECT, c, null);
                }
                return;
        }
        if (id >= ID_USER && c.state == Conn.ST_CONNECTED) event(EV_PACKET, c, d);
    }

    private void acked(Conn c, int d) {
        int[] rels = (int[]) c.dgrams.remove(new Integer(d));
        if (rels == null) return;
        for (int i = 0; i < rels.length; i++) {
            IP p = (IP) c.inFlight.remove(new Integer(rels[i]));
            if (p != null && p.sends == 1) {
                int r = (int) (System.currentTimeMillis() - p.sentAt);
                if (r >= 0 && r < 5000) c.rtt = (c.rtt * 7 + r) / 8;
            }
        }
    }

    private void naked(Conn c, int d) {
        int[] rels = (int[]) c.dgrams.remove(new Integer(d));
        if (rels == null) return;
        for (int i = 0; i < rels.length; i++) {
            IP p = (IP) c.inFlight.get(new Integer(rels[i]));
            if (p != null && !c.resend.contains(p)) c.resend.addElement(p);
        }
    }

    // ---- обслуживание ----

    private void update(long now) {
        // подключение (RakPeer::RunUpdateCycle, requestedConnectionQueue)
        if (!server && connAddr != null && now >= connNext) {
            if (connTries >= 12) {
                connAddr = null;
                event(EV_FAILED, null, null);
            } else {
                int mtu = MTU_TRY[Math.min(2, connTries / 4)];
                connTries++;
                connNext = now + 500;
                raw.reset();
                raw.u8(ID_OCR1);
                raw.bytes(MAGIC, 0, 16);
                raw.u8(PROTOCOL);
                while (raw.n < mtu - UDP_HEADER) raw.u8(0);
                sendTo(connAddr, raw.b, raw.n);
            }
        }
        // поиск игр: раз в секунду ID_UNCONNECTED_PING_OPEN_CONNECTIONS на 19132..19135 (и на себя — та же машина)
        if (pinging && now >= pingNext) {
            pingNext = now + 1000;
            raw.reset();
            raw.u8(ID_UNCONNECTED_PING_OPEN);
            raw.i64(now);
            raw.bytes(MAGIC, 0, 16);
            for (int i = 0; i < 4; i++) {
                sendTo("datagram://255.255.255.255:" + (19132 + i), raw.b, raw.n);
                sendTo("datagram://127.0.0.1:" + (19132 + i), raw.b, raw.n);
            }
        }
        for (int i = conns.size() - 1; i >= 0; i--) {
            Conn c = (Conn) conns.elementAt(i);
            if (c.state == Conn.ST_CLOSING) {
                flush(c, now);
                if (now >= c.closeAt) conns.removeElementAt(i);
                continue;
            }
            // тайм-аут: 20 с без датаграмм (SetTimeoutTime(20000)); незавершённое подключение — 10 с
            boolean pending = c.state != Conn.ST_CONNECTED;
            if (now - c.lastRecv > 20000 || (pending && now - c.created > 10000)) {
                c.state = Conn.ST_CLOSING;
                c.closeAt = now;
                conns.removeElementAt(i);
                if (!pending) event(EV_DISCONNECT, c, null);
                else if (c.weInitiated) event(EV_FAILED, null, null);
                continue;
            }
            if (c.state == Conn.ST_CONNECTED && now - c.lastPing > 5000) {
                c.lastPing = now;
                raw.reset();
                raw.u8(ID_CONNECTED_PING);
                raw.i64(now);
                sendRaw(c, raw.b, raw.n, UNRELIABLE);
            }
            flush(c, now);
        }
    }

    private void flushAll(long now) {
        for (int i = 0; i < conns.size(); i++) flush((Conn) conns.elementAt(i), now);
    }

    private final Vector dgramRel = new Vector();

    /** ACK/NAK, повторы по тайм-ауту и упаковка очереди в датаграммы. */
    private void flush(Conn c, long now) {
        if (!c.acks.isEmpty()) sendRanges(c, c.acks, 0xC0);
        if (!c.naks.isEmpty()) sendRanges(c, c.naks, 0xA0);
        int rto = Math.max(150, Math.min(2000, c.rtt * 2 + 50));
        if (!c.inFlight.isEmpty()) {
            for (Enumeration en = c.inFlight.elements(); en.hasMoreElements(); ) {
                IP p = (IP) en.nextElement();
                if (now - p.sentAt > rto && !c.resend.contains(p)) c.resend.addElement(p);
            }
        }
        int maxPayload = c.mtu - UDP_HEADER;
        // окно: не больше 512 надёжных без подтверждения (повторы идут всегда)
        while (!c.resend.isEmpty() || (!c.queue.isEmpty() && c.inFlight.size() < 512)) {
            raw.reset();
            raw.u8(0x80);
            int dn = c.sendDgram;
            c.sendDgram = (c.sendDgram + 1) & 0xFFFFFF;
            raw.u24le(dn);
            dgramRel.removeAllElements();
            int count = 0;
            while (true) {
                IP p;
                boolean fromResend = !c.resend.isEmpty();
                if (fromResend) p = (IP) c.resend.elementAt(0);
                else if (!c.queue.isEmpty() && c.inFlight.size() < 512) p = (IP) c.queue.elementAt(0);
                else break;
                if (fromResend && !c.inFlight.containsKey(new Integer(p.relNum))) {
                    c.resend.removeElementAt(0);
                    continue;
                }
                int need = headerLen(p) + p.data.length;
                if (count > 0 && raw.n + need > maxPayload) break;
                if (fromResend) c.resend.removeElementAt(0);
                else c.queue.removeElementAt(0);
                writeIP(raw, p);
                count++;
                if (p.rel == RELIABLE || p.rel == RELIABLE_ORDERED || p.rel == RELIABLE_SEQ) {
                    p.sentAt = now;
                    p.sends++;
                    Integer k = new Integer(p.relNum);
                    c.inFlight.put(k, p);
                    dgramRel.addElement(k);
                }
            }
            if (count == 0) break;
            if (!dgramRel.isEmpty()) {
                int[] r = new int[dgramRel.size()];
                for (int i = 0; i < r.length; i++) r[i] = ((Integer) dgramRel.elementAt(i)).intValue();
                c.dgrams.put(new Integer(dn), r);
            }
            sendTo(c.addr, raw.b, raw.n);
        }
        // старые записи датаграмм, по которым всё уже подтверждено другими путями
        if (c.dgrams.size() > 4096) c.dgrams.clear();
    }

    private static int headerLen(IP p) {
        int n = 3;
        if (p.rel == RELIABLE || p.rel == RELIABLE_ORDERED || p.rel == RELIABLE_SEQ) n += 3;
        if (p.rel == UNRELIABLE_SEQ || p.rel == RELIABLE_SEQ) n += 3;
        if (p.rel == UNRELIABLE_SEQ || p.rel == RELIABLE_SEQ || p.rel == RELIABLE_ORDERED) n += 4;
        if (p.splitCount > 0) n += 10;
        return n;
    }

    /** ReliabilityLayer::WriteToBitStreamFromInternalPacket */
    private static void writeIP(NetOut o, IP p) {
        o.u8((p.rel << 5) | (p.splitCount > 0 ? 0x10 : 0));
        o.u16(p.data.length * 8);
        if (p.rel == RELIABLE || p.rel == RELIABLE_ORDERED || p.rel == RELIABLE_SEQ) o.u24le(p.relNum);
        if (p.rel == UNRELIABLE_SEQ || p.rel == RELIABLE_SEQ) o.u24le(p.seqIdx);
        if (p.rel == UNRELIABLE_SEQ || p.rel == RELIABLE_SEQ || p.rel == RELIABLE_ORDERED) {
            o.u24le(p.ordIdx);
            o.u8(p.chan);
        }
        if (p.splitCount > 0) {
            o.i32(p.splitCount);
            o.u16(p.splitId);
            o.i32(p.splitIdx);
        }
        o.bytes(p.data, 0, p.data.length);
    }

    /** DatagramHeaderFormat (ACK 0xC0 / NAK 0xA0) + RangeList<uint24>::Serialize. */
    private void sendRanges(Conn c, Vector list, int head) {
        int n = list.size();
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = ((Integer) list.elementAt(i)).intValue();
        list.removeAllElements();
        // сортировка и склейка в диапазоны
        for (int i = 1; i < n; i++) {
            int x = v[i], j = i - 1;
            while (j >= 0 && v[j] > x) { v[j + 1] = v[j]; j--; }
            v[j + 1] = x;
        }
        int i = 0;
        while (i < n) {
            raw.reset();
            raw.u8(head);
            int countPos = raw.n;
            raw.u16(0);
            int ranges = 0;
            while (i < n && raw.n + 7 <= c.mtu - UDP_HEADER) {
                int min = v[i], max = min;
                i++;
                while (i < n && (v[i] == max || v[i] == max + 1)) { max = v[i]; i++; }
                raw.u8(min == max ? 1 : 0);
                raw.u24le(min);
                if (min != max) raw.u24le(max);
                ranges++;
            }
            raw.b[countPos] = (byte) (ranges >> 8);
            raw.b[countPos + 1] = (byte) ranges;
            sendTo(c.addr, raw.b, raw.n);
        }
    }
}
