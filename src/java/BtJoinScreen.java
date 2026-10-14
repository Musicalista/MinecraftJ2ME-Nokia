import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.microedition.lcdui.Graphics;
import javax.microedition.io.StreamConnection;

/**
 * Вход по Bluetooth (отступление от оригинала, только J2ME). Вид как у JoinGameScreen: «Back», заголовок,
 * список устройств строками по 34 (сопряжённые сразу, остальные — поиском), внизу подсказка. Касание устройства —
 * соединение RFCOMM с его игрой; удалось — обычный вход RakNet и экран хода подключения.
 */
public final class BtJoinScreen extends Screen {
    private final TButton bBack = new TButton(3, "Back");
    private final THeader bHeader = new THeader(0, "");
    private static final int ROW = 34, TOP = 28;
    private BtNet.Finder finder;
    private float yo;
    private int pressed = -1, downY, lastY, keySel = -1;
    private boolean dragged;
    private final long t0 = System.currentTimeMillis();
    // подключение (поток) — результат забирает tick
    private volatile boolean connecting, cancelled;
    private volatile String connError;
    private String connName;
    private volatile StreamConnection rSc;
    private volatile DataInputStream rIn;
    private volatile DataOutputStream rOut;

    protected void init() {
        add(bBack);
        add(bHeader);
        if (finder == null) {
            finder = new BtNet.Finder();
            finder.start();
        }
    }

    protected void setupPositions() {
        bBack.x = 0;
        bBack.y = bHeader.y = 0;
        bHeader.x = bBack.width;
        bHeader.width = width - bHeader.x;
    }

    public boolean renderGameBehind() {
        return false;
    }

    private int footH() {
        return 24;
    }

    /** строки: устройства, затем «Search again» / «Searching...» (или ошибка) */
    private int rows() {
        return finder.devs.size() + 1;
    }

    private void choose(int i) {
        int n = finder.devs.size();
        if (i == n) {
            if (finder.error != null) {
                finder = new BtNet.Finder();
                finder.start();
            } else finder.search();
            return;
        }
        if (i < 0 || i > n) return;
        final BtNet.Dev d = (BtNet.Dev) finder.devs.elementAt(i);
        finder.stop();
        connName = d.name;
        connError = null;
        cancelled = false;
        connecting = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    DataInputStream[] in = new DataInputStream[1];
                    DataOutputStream[] out = new DataOutputStream[1];
                    StreamConnection sc = BtNet.connect(d, in, out);
                    if (cancelled) {
                        sc.close();
                        return;
                    }
                    rIn = in[0];
                    rOut = out[0];
                    rSc = sc;
                } catch (Throwable t) {
                    Log.i("BT connect: " + t);
                    connError = BtNet.errorText(t);
                }
            }
        }).start();
    }

    public void tick() {
        StreamConnection sc = rSc;
        if (sc != null) {
            rSc = null;
            connecting = false;
            NetClient c = Net.joinBt(mc, sc, rIn, rOut);
            mc.setScreen(new JoinGameScreen(c));
        }
    }

    public void render(Graphics g) {
        renderBackground(g);
        if (connecting) {
            renderConnecting(g);
            super.render(g);
            R.centered(g, "Bluetooth games", bHeader.x + bHeader.width / 2, 8, 0xFFFFFF);
            return;
        }
        int listH = height - TOP - footH();
        int n = rows();
        float max = Math.max(0, n * ROW - listH);
        if (yo > max) yo = max;
        if (yo < 0) yo = 0;
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.clipRect(0, R.px(TOP), R.px(width), R.px(TOP + listH) - R.px(TOP));
        for (int i = 0; i < n; i++) {
            int y = TOP + i * ROW - (int) yo;
            if (y + ROW < TOP || y > TOP + listH) continue;
            boolean hl = (pressed == i && touching) || keySel == i;
            if (i < finder.devs.size()) {
                if (hl) R.fill(g, 0, y, width, y + ROW, 0x809E684F);
                BtNet.Dev d = (BtNet.Dev) finder.devs.elementAt(i);
                R.shadow(g, d.name, 24, y + 6, 0xFFFFB0);
                R.shadow(g, d.paired ? "Paired" : d.name.equals(BtNet.macOf(d.addr)) ? "Nearby" : "Nearby  " + BtNet.macOf(d.addr), 24, y + 18, 0xA0A0A0);
            } else {
                renderActionRow(g, y, hl);
            }
        }
        g.setClip(cx, cy, cw, ch);
        renderFooter(g);
        super.render(g);
        int baseX = bHeader.x + bHeader.width / 2;
        String s = "Bluetooth games";
        R.centered(g, s, baseX, 8, 0xFFFFFF);
        if (finder.searching) R.centered(g, spinner(), baseX + R.width(s) / 2 + 6, 8, 0xFFFFFF);
    }

    private void renderActionRow(Graphics g, int y, boolean hl) {
        String a, b;
        boolean active;
        if (finder.error != null) {
            a = finder.error;
            b = "Tap to try again";
            active = true;
        } else if (!finder.ready) {
            a = finder.status[0];
            b = "";
            active = false;
        } else if (finder.searching) {
            a = "Searching for devices...";
            b = finder.devs.isEmpty() ? "" : "Tap a device to join its game";
            active = false;
        } else {
            a = "Search again";
            b = finder.devs.isEmpty() ? "No devices found" : "Tap a device to join its game";
            active = true;
        }
        if (hl && active) R.fill(g, 0, y, width, y + ROW, 0x809E684F);
        R.centered(g, a, width / 2, y + 6, active ? 0xFFFFFF : 0xA0A0A0);
        if (b.length() > 0) R.centered(g, b, width / 2, y + 18, 0xA0A0A0);
    }

    /** Подсказка внизу: только J2ME, как открыть игру. Длинный вариант, если влезает. */
    private void renderFooter(Graphics g) {
        int y = height - footH();
        R.fill(g, 0, y, width, height, 0x80000000);
        int w = width - 8;
        R.centered(g, fit(new String[] { "Bluetooth works only between J2ME clients, not with MCPE 0.6.1",
                "J2ME clients only, not MCPE 0.6.1", "J2ME clients only" }, w), width / 2, y + 3, 0x9FD0FF);
        R.centered(g, fit(new String[] { "Host: open a world, Pause > Bluetooth. Not listed? Pair the phones",
                "Host: Pause > Bluetooth. Not listed? Pair phones", "Host: Pause > Bluetooth" }, w), width / 2, y + 13, 0xA0A0A0);
    }

    static String fit(String[] v, int w) {
        for (int i = 0; i < v.length; i++) if (R.width(v[i]) <= w) return v[i];
        return v[v.length - 1];
    }

    private String spinner() {
        String[] spin = { "-", "\\", "|", "/" };
        return spin[(int) (5.5f * (System.currentTimeMillis() - t0) / 1000f) % 4];
    }

    private void renderConnecting(Graphics g) {
        int cy = height / 2;
        if (connError != null) {
            R.centered(g, connError, width / 2, cy - 12, 0xFFFFFF);
            R.centered(g, connName, width / 2, cy + 2, 0xA0A0A0);
            R.centered(g, "Tap to continue", width / 2, cy + 20, 0xA0A0A0);
            return;
        }
        String t = "Connecting via Bluetooth";
        R.centered(g, t, width / 2, cy - 12, 0xFFFFFF);
        R.centered(g, spinner(), width / 2 + R.width(t) / 2 + 6, cy - 12, 0xFFFFFF);
        R.centered(g, connName, width / 2, cy + 2, 0xFFFFB0);
    }

    /** Строка Bluetooth в JoinGameScreen: значок и пометка, что это только для J2ME. */
    static void renderBtRow(Graphics g, int x, int y, int width) {
        renderIcon(g, 6, y + 9);
        R.shadow(g, "Bluetooth game", x, y + 6, 0x9FD0FF);
        R.shadow(g, fit(new String[] { "Only between J2ME clients, not in MCPE 0.6.1", "J2ME only, not in MCPE 0.6.1", "J2ME only" },
                width - x - 4), x, y + 18, 0xA0A0A0);
    }

    /** Руна Bluetooth 5x9 на синей плашке. */
    private static final String[] RUNE = { "..X..", "..XX.", "X.X.X", ".XXX.", "..X..", ".XXX.", "X.X.X", "..XX.", "..X.." };

    static void renderIcon(Graphics g, float x, float y) {
        float p = 1.5f;
        R.fill(g, x - 3, y - 2, x + 5 * p + 3, y + 9 * p + 2, 0xFF2A62C9);
        for (int r = 0; r < RUNE.length; r++)
            for (int c = 0; c < 5; c++)
                if (RUNE[r].charAt(c) == 'X') R.fill(g, x + c * p, y + r * p, x + (c + 1) * p, y + (r + 1) * p, 0xFFFFFFFF);
    }

    protected void mouseClicked(int x, int y) {
        super.mouseClicked(x, y);
        if (connecting) return;
        downY = lastY = y;
        dragged = false;
        pressed = y >= TOP && y < height - footH() ? (int) ((y - TOP + yo) / ROW) : -1;
        keySel = -1;
    }

    protected void mouseDragged(int x, int y) {
        if (connecting) return;
        if (Math.abs(y - downY) > 6) dragged = true;
        if (dragged) yo -= y - lastY;
        lastY = y;
    }

    protected void mouseReleased(int x, int y) {
        super.mouseReleased(x, y);
        if (connecting) {
            if (connError != null && y >= TOP) connecting = false;
            return;
        }
        int i = y >= TOP && y < height - footH() ? (int) ((y - TOP + yo) / ROW) : -1;
        if (!dragged && i == pressed && i >= 0 && i < rows()) choose(i);
        pressed = -1;
    }

    protected void buttonClicked(Button b) {
        if (b == bBack) back();
    }

    public void back() {
        if (connecting && connError == null) {
            // соединение ещё идёт: бросить его (поток закроет сокет сам), вернуться к списку
            cancelled = true;
            connecting = false;
            return;
        }
        if (connecting) {
            connecting = false;
            return;
        }
        finder.stop();
        mc.setScreen(new JoinGameScreen());
    }

    public void removed() {
        if (finder != null) finder.stop();
    }

    public void keyNav(int dir) {
        if (connecting) return;
        int n = rows();
        if (dir == DOWN) keySel = Math.min(n - 1, keySel + 1);
        else if (dir == UP) keySel = Math.max(-1, keySel - 1);
        if (keySel < 0) super.keyNav(dir);
        else {
            // выбранная строка — в видимой части
            int listH = height - TOP - footH();
            if (keySel * ROW < yo) yo = keySel * ROW;
            if ((keySel + 1) * ROW > yo + listH) yo = (keySel + 1) * ROW - listH;
        }
    }

    public void keyOk(boolean down) {
        if (connecting) {
            if (!down && connError != null) connecting = false;
            return;
        }
        if (keySel >= 0) {
            if (!down) choose(keySel);
            return;
        }
        super.keyOk(down);
    }
}
