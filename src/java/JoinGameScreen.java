import javax.microedition.lcdui.Graphics;

/**
 * Touch::JoinGameScreen 0.6.1: сверху «Back» и заголовок «Scanning for WiFi Games...» с вертушкой, ниже список
 * найденных игр (AvailableGamesList, строки по 34: имя и адрес). Касание игры — сразу подключение (ProgressScreen).
 * Отступление: первая строка списка — вход по Bluetooth (BtJoinScreen, только между J2ME-клиентами).
 */
public final class JoinGameScreen extends Screen {
    private final TButton bBack = new TButton(3, "Back");
    private final THeader bHeader = new THeader(0, "");
    private NetClient client;
    private static final int ROW = 34, TOP = 24;
    private float yo;
    private int pressed = -1, downY, lastY;
    private boolean dragged;
    private int keySel = -1;
    private long t0 = System.currentTimeMillis();
    /** подключение начато с экрана Bluetooth: «назад» ведёт туда */
    private final boolean viaBt;

    public JoinGameScreen() {
        viaBt = false;
    }

    /** Ход входа по Bluetooth: клиент уже создан (Net.joinBt). */
    JoinGameScreen(NetClient c) {
        client = c;
        viaBt = true;
    }

    protected void init() {
        add(bBack);
        add(bHeader);
        if (client == null) client = Net.locate(mc);
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

    private boolean joining() {
        return client != null && client.state != NetClient.S_SEARCH;
    }

    /** строки списка: 0 — Bluetooth, дальше найденные игры */
    private int rows() {
        return 1 + (client == null ? 0 : client.games.size());
    }

    private void join(int i) {
        if (i == 0) {
            Net.stop();
            mc.setScreen(new BtJoinScreen());
            return;
        }
        i--;
        if (client == null || i < 0 || i >= client.games.size()) return;
        NetClient.Game g = (NetClient.Game) client.games.elementAt(i);
        client.join(g.addr);
    }

    public void tick() {
        if (client == null) return;
        if (client.state == NetClient.S_PLAYING && mc.level != null) mc.setScreen(null);
    }

    public void render(Graphics g) {
        renderBackground(g);
        if (joining()) {
            renderProgress(g);
            return;
        }
        int n = rows();
        float max = Math.max(0, n * ROW - (height - TOP));
        if (yo > max) yo = max;
        if (yo < 0) yo = 0;
        for (int i = 0; i < n; i++) {
            int y = TOP + i * ROW - (int) yo;
            if (y + ROW < TOP || y > height) continue;
            if ((pressed == i && touching) || keySel == i) R.fill(g, 0, y, width, y + ROW, 0x809E684F);
            if (i == 0) {
                BtJoinScreen.renderBtRow(g, 24, y, width);
                continue;
            }
            NetClient.Game gm = (NetClient.Game) client.games.elementAt(i - 1);
            R.shadow(g, gm.name, 24, y + 6, 0xFFFFB0);
            R.shadow(g, Raknet.hostOf(gm.addr), 24, y + 18, 0xFFFFA0);
        }
        super.render(g);
        int baseX = bHeader.x + bHeader.width / 2;
        String s = client == null ? "WiFi is disabled" : "Scanning for WiFi Games...";
        R.centered(g, s, baseX, 8, 0xFFFFFF);
        if (client != null) {
            String[] spin = { "-", "\\", "|", "/" };
            int k = (int) (5.5f * (System.currentTimeMillis() - t0) / 1000f) % 4;
            R.centered(g, spin[k], baseX + R.width(s) / 2 + 6, 8, 0xFFFFFF);
        }
    }

    /** ProgressScreen: стадии подключения; ошибка — сообщение (DisconnectionScreen) и возврат касанием. */
    private void renderProgress(Graphics g) {
        // Minecraft::progressMessages 0.6.1: до StartGame — «Locating server» со спиннером, потом prepareLevel
        String t;
        boolean locating = false;
        switch (client.state) {
            case NetClient.S_CONNECTING: case NetClient.S_LOGIN: t = "Locating server"; locating = true; break;
            case NetClient.S_GENERATING: case NetClient.S_CHUNKS: t = "Building terrain"; break;
            case NetClient.S_FAILED: t = client.failMessage; break;
            default: t = "Preparing";
        }
        R.centered(g, client.state == NetClient.S_FAILED ? "" : "Generating world", width / 2, height / 2 - 20, 0xFFFFFF);
        R.centered(g, t, width / 2, height / 2 - 4 + 8, 0xFFFFFF);
        if (locating) {
            String[] spin = { "-", "\\", "|", "/" };
            int k = (int) (5.5f * (System.currentTimeMillis() - t0) / 1000f) % 4;
            R.centered(g, spin[k], width / 2 + R.width(t) / 2 + 6, height / 2 - 4 + 8, 0xFFFFFF);
        }
        if (client.state == NetClient.S_CHUNKS || locating || client.state == NetClient.S_GENERATING) {
            int bw = 100, x = width / 2 - bw / 2, y = height / 2 + 16;
            R.fill(g, x, y, x + bw, y + 2, 0xFF808080);
            if (client.state == NetClient.S_CHUNKS) R.fill(g, x, y, x + client.chunksDone * bw / 256, y + 2, 0xFF80FF80);
        }
    }

    protected void mouseClicked(int x, int y) {
        if (joining()) return;
        super.mouseClicked(x, y);
        downY = lastY = y;
        dragged = false;
        pressed = y >= TOP ? (int) ((y - TOP + yo) / ROW) : -1;
        keySel = -1;
    }

    protected void mouseDragged(int x, int y) {
        if (joining()) return;
        if (Math.abs(y - downY) > 6) dragged = true;
        if (dragged) yo -= y - lastY;
        lastY = y;
    }

    protected void mouseReleased(int x, int y) {
        if (joining()) {
            if (client.state == NetClient.S_FAILED) back();
            return;
        }
        super.mouseReleased(x, y);
        if (y < TOP) { pressed = -1; return; }
        int i = y >= TOP ? (int) ((y - TOP + yo) / ROW) : -1;
        if (!dragged && i == pressed && i >= 0) join(i);
        pressed = -1;
    }

    protected void buttonClicked(Button b) {
        if (b == bBack) back();
    }

    /** cancelLocateMultiplayer */
    public void back() {
        Net.stop();
        mc.level = null;
        mc.player = null;
        mc.setScreen(viaBt ? (Screen) new BtJoinScreen() : new StartMenuScreen());
    }

    // клавиши: вверх/вниз по списку, OK — подключиться
    public void keyNav(int dir) {
        if (joining()) return;
        int n = rows();
        if (dir == DOWN) keySel = Math.min(n - 1, keySel + 1);
        else if (dir == UP) keySel = Math.max(-1, keySel - 1);
        if (keySel < 0) super.keyNav(dir);
    }

    public void keyOk(boolean down) {
        if (joining()) {
            if (!down && client.state == NetClient.S_FAILED) back();
            return;
        }
        if (keySel >= 0) {
            if (!down) join(keySel);
            return;
        }
        super.keyOk(down);
    }
}
