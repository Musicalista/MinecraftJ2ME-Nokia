import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

/**
 * Bluetooth-мультиплеер — отступление от оригинала: в MCPE 0.6.1 его нет, играть так можно только между J2ME-клиентами.
 * Тот же протокол 0.6.1 и RakNet, что по Wi-Fi, только датаграммы идут по RFCOMM (JSR-82, btspp://): кадр —
 * u16 длина + байты. Каждое соединение для Raknet выглядит как адрес PREFIX..., отправка туда уходит сюда (send),
 * принятое отдаётся в Raknet.inject. Все вызовы JSR-82 — в своих потоках: загрузчик спрашивает разрешения и
 * включение Bluetooth системными окнами и ждёт ответа внутри LocalDevice.getLocalDevice.
 */
public final class BtNet {
    /** UUID службы игры (32 hex без дефисов) */
    static final String SERVICE = "6d63706a326d65303631627400000001";
    static final String HELLO = "MCJ2ME-BT", VERSION = "1";
    public static final String PREFIX = "datagram://127.66.";
    /** адрес хоста для клиента */
    static final String HOST_ADDR = PREFIX + "1.1:19132";
    static final int GIAC = 0x9E8B33, CACHED = 0, PREKNOWN = 1, MAX_DGRAM = 2048, MAX_QUEUE = 256;
    /** ServiceRecord.NOAUTHENTICATE_NOENCRYPT, DiscoveryListener.SERVICE_SEARCH_COMPLETED */
    static final int NOAUTH = 0, SEARCH_COMPLETED = 1;

    public static final int OFF = 0, STARTING = 1, HOSTING = 2, CONNECTED = 3, ERROR = 4;
    public volatile int state = OFF;
    /** что показать игроку (ошибка или ход запуска) */
    public volatile String status = "";
    /** сообщение в чат хоста; забирает поток игры (takeMessage) */
    private String message;
    private volatile boolean stopped;
    private Raknet rak;
    private StreamConnectionNotifier notifier;
    private final Vector links = new Vector();
    private int nextAddr = 1;

    BtNet(Raknet rak) {
        this.rak = rak;
    }

    public static boolean isBt(String addr) {
        return addr != null && addr.startsWith(PREFIX);
    }

    synchronized String takeMessage() {
        String s = message;
        message = null;
        return s;
    }

    private synchronized void say(String s) {
        message = s;
    }

    public synchronized int linkCount() {
        return links.size();
    }

    // ---- LocalDevice ----

    /**
     * LocalDevice.getLocalDevice в J2ME Loader: нет разрешения — запрашивает его и сразу бросает исключение (ждём и
     * пробуем снова); Bluetooth выключен — спрашивает включение и ждёт ответа (отказ — исключение после паузы).
     */
    static LocalDevice local(BtNet owner, String[] statusOut) throws IOException {
        long end = System.currentTimeMillis() + 40000;
        while (true) {
            long t = System.currentTimeMillis();
            try {
                return LocalDevice.getLocalDevice();
            } catch (BluetoothStateException e) {
                long took = System.currentTimeMillis() - t;
                if (took > 400) throw new IOException("Bluetooth is off");
                if (System.currentTimeMillis() > end || (owner != null && owner.stopped)) throw new IOException("No Bluetooth permission");
                if (statusOut != null) statusOut[0] = "Waiting for Bluetooth permission...";
                if (owner != null) owner.status = "Waiting for permission";
                try { Thread.sleep(700); } catch (InterruptedException ie) { }
            }
        }
    }

    static String errorText(Throwable t) {
        if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) return "Bluetooth is not supported";
        String m = t.getMessage();
        if (t instanceof IOException && m != null && m.length() > 0 && m.length() < 40) return m;
        // без подробностей на телефоне без logcat не разобраться — короткое имя класса исключения
        String c = t.getClass().getName();
        return "Bluetooth error: " + c.substring(c.lastIndexOf('.') + 1);
    }

    private static int loader = -1;

    /**
     * Под J2ME Loader (Android): там btspp://адрес:UUID — нестандартная, но рабочая запись, а поиск служб (SDP)
     * ненадёжен. На настоящих телефонах (Nokia и др.) после адреса — номер канала RFCOMM, его даёт только SDP.
     */
    static boolean onLoader() {
        if (loader < 0) {
            try {
                Class.forName("android.os.Build");
                loader = 1;
            } catch (Throwable t) {
                loader = 0;
            }
        }
        return loader == 1;
    }

    // ---- хост ----

    /** Открыть игру по Bluetooth: служба RFCOMM, видимость устройства, приём игроков. */
    void startHost() {
        state = STARTING;
        status = "Starting";
        new Thread(new Runnable() {
            public void run() {
                hostLoop();
            }
        }).start();
    }

    private void hostLoop() {
        try {
            LocalDevice ld = local(this, null);
            if (stopped) return;
            String url = "btspp://localhost:" + SERVICE + ";name=MinecraftJ2ME;authenticate=false;encrypt=false";
            notifier = (StreamConnectionNotifier) Connector.open(url);
            if (stopped) { notifier.close(); return; }
            state = HOSTING;
            status = "";
            String name = null;
            try { name = ld.getFriendlyName(); } catch (Throwable t) { }
            say("Bluetooth game is open" + (name != null && name.length() > 0 ? " as \"" + name + "\"" : "") + " (J2ME only)");
            Log.i("BT host " + name);
            // без видимости телефон находят только уже сопряжённые с ним
            try { ld.setDiscoverable(GIAC); } catch (Throwable t) { Log.i("BT discoverable: " + t); }
            while (!stopped) {
                final StreamConnection sc = notifier.acceptAndOpen();
                if (stopped) { try { sc.close(); } catch (Throwable t) { } break; }
                new Thread(new Runnable() {
                    public void run() {
                        accept(sc);
                    }
                }).start();
            }
        } catch (Throwable t) {
            if (stopped) return;
            Log.i("BT host: " + t);
            state = ERROR;
            status = errorText(t);
            say("Bluetooth: " + status);
        }
    }

    private void accept(StreamConnection sc) {
        try {
            DataInputStream in = sc.openDataInputStream();
            DataOutputStream out = sc.openDataOutputStream();
            String hello = in.readUTF(), ver = in.readUTF();
            boolean ok = HELLO.equals(hello) && VERSION.equals(ver);
            out.writeUTF(HELLO);
            out.writeUTF(ok ? VERSION : "?");
            out.flush();
            if (!ok) {
                sc.close();
                return;
            }
            String addr;
            synchronized (this) {
                if (stopped) { sc.close(); return; }
                addr = PREFIX + "0." + (nextAddr++ & 255) + ":19132";
            }
            addLink(addr, sc, in, out);
            Log.i("BT client " + addr);
        } catch (Throwable t) {
            Log.i("BT accept: " + t);
            try { sc.close(); } catch (Throwable t2) { }
        }
    }

    // ---- клиент ----

    /** Подключение к хосту (в потоке экрана): соединение и приветствие; ошибка — текст в IOException. */
    static StreamConnection connect(Dev d, DataInputStream[] in, DataOutputStream[] out) throws IOException {
        String url = onLoader() ? "btspp://" + d.addr + ":" + SERVICE + ";authenticate=false;encrypt=false" : serviceUrl(d);
        if (url == null) throw new IOException("No Bluetooth game on this device");
        Log.i("BT connect " + url);
        StreamConnection sc = (StreamConnection) Connector.open(url);
        try {
            DataOutputStream o = sc.openDataOutputStream();
            DataInputStream i = sc.openDataInputStream();
            o.writeUTF(HELLO);
            o.writeUTF(VERSION);
            o.flush();
            if (!HELLO.equals(i.readUTF())) throw new IOException("Not a Minecraft J2ME game");
            if (!VERSION.equals(i.readUTF())) throw new IOException("Different game version");
            in[0] = i;
            out[0] = o;
            return sc;
        } catch (IOException e) {
            try { sc.close(); } catch (Throwable t) { }
            String m = e.getMessage();
            if (m != null && (m.startsWith("Not a") || m.startsWith("Different"))) throw e;
            throw new IOException("No Bluetooth game on this device");
        }
    }

    /**
     * Поиск службы игры на устройстве (SDP) — URL с каналом RFCOMM или null, если игры там нет. Поиск устройств
     * к этому моменту отменён, но стек может ещё не освободиться (bluetooth.sd.trans.max = 1) — несколько попыток.
     */
    static String serviceUrl(Dev d) throws IOException {
        final String[] url = new String[1];
        final boolean[] done = new boolean[1];
        DiscoveryListener l = new DiscoveryListener() {
            public void servicesDiscovered(int transID, ServiceRecord[] rec) {
                for (int i = 0; rec != null && i < rec.length && url[0] == null; i++) {
                    try { url[0] = rec[i].getConnectionURL(NOAUTH, false); } catch (Throwable t) { }
                }
            }

            public void serviceSearchCompleted(int transID, int respCode) {
                Log.i("BT sdp done " + respCode);
                synchronized (done) {
                    done[0] = true;
                    done.notifyAll();
                }
            }

            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
            }

            public void inquiryCompleted(int discType) {
            }
        };
        DiscoveryAgent agent = LocalDevice.getLocalDevice().getDiscoveryAgent();
        RemoteDevice rd = (RemoteDevice) d.rd;
        if (rd == null) throw new IOException("Search for the device again");
        UUID[] set = { new UUID(SERVICE, false) };
        for (int tries = 0; ; tries++) {
            try {
                agent.searchServices(null, set, rd, l);
                break;
            } catch (BluetoothStateException e) {
                if (tries >= 5) throw e;
                try { Thread.sleep(800); } catch (InterruptedException ie) { }
            }
        }
        long end = System.currentTimeMillis() + 30000;
        synchronized (done) {
            while (!done[0] && System.currentTimeMillis() < end) {
                try { done.wait(500); } catch (InterruptedException e) { }
            }
        }
        return url[0];
    }

    /** Клиент: готовое соединение с хостом становится адресом HOST_ADDR. */
    void attachHost(StreamConnection sc, DataInputStream in, DataOutputStream out) {
        state = CONNECTED;
        addLink(HOST_ADDR, sc, in, out);
    }

    // ---- соединения ----

    private final class Link {
        String addr;
        StreamConnection sc;
        DataInputStream in;
        DataOutputStream out;
        final Vector q = new Vector();
        volatile boolean alive = true;
    }

    private void addLink(String addr, StreamConnection sc, DataInputStream in, DataOutputStream out) {
        final Link l = new Link();
        l.addr = addr;
        l.sc = sc;
        l.in = in;
        l.out = out;
        synchronized (this) {
            links.addElement(l);
        }
        new Thread(new Runnable() {
            public void run() {
                readLoop(l);
            }
        }).start();
        new Thread(new Runnable() {
            public void run() {
                writeLoop(l);
            }
        }).start();
    }

    private synchronized Link find(String addr) {
        for (int i = 0; i < links.size(); i++) {
            Link l = (Link) links.elementAt(i);
            if (l.addr.equals(addr)) return l;
        }
        return null;
    }

    /** Из Raknet.sendTo: как UDP — копия в очередь соединения, переполнение — потеря (надёжное RakNet повторит). */
    void send(String addr, byte[] b, int len) {
        Link l = find(addr);
        if (l == null || !l.alive || len > MAX_DGRAM) return;
        byte[] d = new byte[len];
        System.arraycopy(b, 0, d, 0, len);
        synchronized (l.q) {
            if (l.q.size() >= MAX_QUEUE) return;
            l.q.addElement(d);
            l.q.notify();
        }
    }

    private void writeLoop(Link l) {
        try {
            while (l.alive) {
                byte[] d;
                synchronized (l.q) {
                    while (l.alive && l.q.isEmpty()) l.q.wait(1000);
                    if (!l.alive) break;
                    d = (byte[]) l.q.elementAt(0);
                    l.q.removeElementAt(0);
                }
                l.out.writeShort(d.length);
                l.out.write(d, 0, d.length);
                boolean more;
                synchronized (l.q) {
                    more = !l.q.isEmpty();
                }
                if (!more) l.out.flush();
            }
        } catch (Throwable t) {
            if (l.alive) Log.i("BT write " + l.addr + ": " + t);
        }
        drop(l);
    }

    private void readLoop(Link l) {
        byte[] buf = new byte[MAX_DGRAM];
        try {
            while (l.alive) {
                int n = l.in.readUnsignedShort();
                if (n > MAX_DGRAM) throw new IOException("frame " + n);
                l.in.readFully(buf, 0, n);
                Raknet r = rak;
                if (r != null) r.inject(l.addr, buf, n);
            }
        } catch (Throwable t) {
            if (l.alive) Log.i("BT read " + l.addr + ": " + t);
        }
        drop(l);
    }

    private void drop(Link l) {
        synchronized (this) {
            if (!links.removeElement(l)) return;
        }
        l.alive = false;
        synchronized (l.q) {
            l.q.notifyAll();
        }
        try { l.sc.close(); } catch (Throwable t) { }
        Raknet r = rak;
        if (r != null) r.dropAddr(l.addr);
        if (state == CONNECTED) {
            state = OFF;
            status = "Bluetooth connection lost";
        }
    }

    /** Закрыть службу и все соединения (Raknet к этому моменту уже разослал отключение). */
    void shutdown() {
        stopped = true;
        try { if (notifier != null) notifier.close(); } catch (Throwable t) { }
        Vector v;
        synchronized (this) {
            v = new Vector();
            for (int i = 0; i < links.size(); i++) v.addElement(links.elementAt(i));
        }
        for (int i = 0; i < v.size(); i++) {
            Link l = (Link) v.elementAt(i);
            // дать writeLoop отправить уведомление об отключении
            long end = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < end) {
                synchronized (l.q) {
                    if (l.q.isEmpty()) break;
                }
                try { Thread.sleep(10); } catch (InterruptedException e) { }
            }
            drop(l);
        }
        rak = null;
        state = OFF;
    }

    // ---- поиск устройств ----

    /** Найденное устройство: адрес без двоеточий (как в JSR-82), имя, сопряжено ли. */
    public static final class Dev {
        public String addr, name;
        public boolean paired;
        /** RemoteDevice — для поиска службы */
        Object rd;
    }

    /** Сопряжённые устройства сразу, остальные — поиском (inquiry ~12 с). Только для экрана подключения. */
    public static final class Finder implements DiscoveryListener {
        public final Vector devs = new Vector();
        public volatile boolean searching, ready;
        public volatile String error;
        public final String[] status = { "" };
        private DiscoveryAgent agent;
        private volatile boolean stopped;

        public void start() {
            searching = true;
            error = null;
            status[0] = "Starting Bluetooth...";
            new Thread(new Runnable() {
                public void run() {
                    try {
                        LocalDevice ld = local(null, status);
                        if (stopped) return;
                        agent = ld.getDiscoveryAgent();
                        addAll(agent.retrieveDevices(PREKNOWN), true);
                        addAll(agent.retrieveDevices(CACHED), false);
                        ready = true;
                        startNames();
                        inquiry();
                    } catch (Throwable t) {
                        Log.i("BT find: " + t);
                        error = errorText(t);
                        searching = false;
                    }
                }
            }).start();
        }

        /** Повторный поиск (после окончания предыдущего). */
        public void search() {
            if (searching) return;
            stopped = false;
            if (!ready) { start(); return; }
            searching = true;
            startNames();
            new Thread(new Runnable() {
                public void run() {
                    inquiry();
                }
            }).start();
        }

        private void inquiry() {
            try {
                if (stopped) return;
                if (!agent.startInquiry(GIAC, this)) {
                    // занято предыдущим поиском — подождать и попробовать ещё раз
                    Thread.sleep(1500);
                    if (stopped || !agent.startInquiry(GIAC, this)) searching = false;
                }
            } catch (Throwable t) {
                Log.i("BT inquiry: " + t);
                searching = false;
            }
        }

        public void stop() {
            stopped = true;
            try { if (agent != null && searching) agent.cancelInquiry(this); } catch (Throwable t) { }
            searching = false;
        }

        private void addAll(RemoteDevice[] a, boolean paired) {
            Log.i("BT " + (paired ? "paired " : "cached ") + (a == null ? -1 : a.length));
            for (int i = 0; a != null && i < a.length; i++) add(a[i], paired);
        }

        /**
         * Устройство — в список сразу (имя пока MAC): getFriendlyName на настоящих телефонах — запрос по радио на
         * секунды, а внутри deviceDiscovered его звать нельзя. Имена дописывает поток startNames.
         */
        private void add(RemoteDevice rd, boolean paired) {
            Dev d = new Dev();
            d.rd = rd;
            try { d.addr = rd.getBluetoothAddress(); } catch (Throwable t) { return; }
            d.name = macOf(d.addr);
            d.paired = paired || isTrusted(rd);
            synchronized (devs) {
                for (int i = 0; i < devs.size(); i++) {
                    Dev o = (Dev) devs.elementAt(i);
                    if (o.addr.equals(d.addr)) {
                        if (d.paired) o.paired = true;
                        return;
                    }
                }
                devs.addElement(d);
                devs.notifyAll();
            }
        }

        private static boolean isTrusted(RemoteDevice rd) {
            try { return rd.isTrustedDevice(); } catch (Throwable t) { return false; }
        }

        private boolean naming;

        private void startNames() {
            synchronized (devs) {
                if (naming) return;
                naming = true;
            }
            new Thread(new Runnable() {
                public void run() {
                    int done = 0;
                    while (!stopped) {
                        Dev d = null;
                        synchronized (devs) {
                            if (done < devs.size()) d = (Dev) devs.elementAt(done++);
                            else try { devs.wait(1000); } catch (InterruptedException e) { }
                        }
                        if (d == null) continue;
                        try {
                            String n = ((RemoteDevice) d.rd).getFriendlyName(false);
                            if (n != null && n.length() > 0) d.name = n;
                        } catch (Throwable t) { }
                    }
                    synchronized (devs) {
                        naming = false;
                    }
                }
            }).start();
        }

        public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
            if (!stopped) add(btDevice, false);
        }

        public void inquiryCompleted(int discType) {
            searching = false;
            if (stopped) return;
            new Thread(new Runnable() {
                public void run() {
                    refreshKnown();
                }
            }).start();
        }

        /** Поиск закончен — список устройств заново (часть телефонов отдаёт сопряжённые только после него). */
        private void refreshKnown() {
            try {
                addAll(agent.retrieveDevices(PREKNOWN), true);
                addAll(agent.retrieveDevices(CACHED), false);
            } catch (Throwable t) { }
        }

        public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
        }

        public void serviceSearchCompleted(int transID, int respCode) {
        }
    }

    /** "0123456789AB" -> "01:23:45:67:89:AB" */
    public static String macOf(String a) {
        if (a == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < a.length(); i++) {
            if (i > 0 && i % 2 == 0) sb.append(':');
            sb.append(a.charAt(i));
        }
        return sb.toString().toUpperCase();
    }
}
