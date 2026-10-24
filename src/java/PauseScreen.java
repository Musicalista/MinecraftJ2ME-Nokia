import javax.microedition.lcdui.Graphics;

/**
 * PauseScreen 0.6.1 (Touch): «Game menu», три кнопки 160 в ширину, слева сверху — звук и вид от третьего лица.
 * Отступление: у хоста кнопка Bluetooth (игра только для J2ME-клиентов) — четвёртой строкой, а если по высоте не
 * влезает — рядом с видимостью сервера; под ними пометка, что с MCPE 0.6.1 это не работает.
 */
public final class PauseScreen extends Screen {
    private final TButton bContinue = new TButton(1, "Back to game"), bQuit = new TButton(2, "Quit to title");
    private final TButton bServerVisibility = new TButton(4, "");
    private final TButton bQuitAndSaveLocally = new TButton(3, "Quit and copy map");
    private final TButton bBluetooth = new TButton(5, "");
    private String btShown;
    private int noteY = -1;
    private final OptionButton bSound = new OptionButton(10), bThirdPerson = new OptionButton(11);
    /** ServerSideNetworkHandler::allowsIncomingConnections (по умолчанию — mp_server_visible_default). */
    static Boolean serverVisible;

    protected void init() {
        if (serverVisible == null) serverVisible = new Boolean(Options.serverVisible);
        // touchgui.png (160,144) 39x31, на экране 2/3 размера; вторая картинка — «включено»
        bSound.setImageDef(new ImageDef(R.touch, 160, 144, 39, 31, 39 * 0.666667f, 31 * 0.666667f), true);
        bThirdPerson.setImageDef(new ImageDef(R.touch, 160, 175, 39, 31, 39 * 0.666667f, 31 * 0.666667f), true);
        bSound.on = Options.sound;
        bThirdPerson.on = Options.thirdPerson;
        updateServerVisibilityText();
        add(bContinue);
        add(bQuit);
        add(bSound);
        add(bThirdPerson);
        // клиент сети — «Quit and copy map», хост — видимость сервера
        if (Net.isClient()) add(bQuitAndSaveLocally);
        else add(bServerVisibility);
        if (Net.isHost()) {
            bBluetooth.msg = btShown = Net.btLabel();
            add(bBluetooth);
        }
    }

    protected void setupPositions() {
        int btnW = Math.min(160, width - 16);
        bContinue.width = bQuit.width = bServerVisibility.width = bQuitAndSaveLocally.width = btnW;
        bContinue.x = bQuit.x = bServerVisibility.x = bQuitAndSaveLocally.x = (width - btnW) / 2;
        int yBase = height < 140 ? 4 : 16;
        int step = height < 140 ? 24 : 32;
        bContinue.y = yBase + step;
        bQuit.y = yBase + step * 2;
        bServerVisibility.y = yBase + step * 3;
        bQuitAndSaveLocally.y = yBase + step * 3;
        bBluetooth.width = btnW;
        bBluetooth.x = bServerVisibility.x;
        noteY = -1;
        if (!Net.isHost()) return;
        if (yBase + 32 * 4 + bBluetooth.height + 12 <= height) {
            bBluetooth.y = yBase + 32 * 4;
            noteY = bBluetooth.y + bBluetooth.height + 3;
        } else {
            // низкий экран (альбомная ориентация телефона): видимость и Bluetooth в одну строку
            int w = Math.min(160, (width - 12) / 2);
            bServerVisibility.width = bBluetooth.width = w;
            bServerVisibility.x = width / 2 - 2 - w;
            bBluetooth.x = width / 2 + 2;
            bBluetooth.y = bServerVisibility.y;
            noteY = bBluetooth.y + bBluetooth.height + 3;
        }
        if (noteY + 8 > height) noteY = -1;
        bSound.y = bThirdPerson.y = 8;
        bSound.x = 4;
        bThirdPerson.x = bSound.x + 4 + bSound.width;
    }

    private void updateServerVisibilityText() {
        bServerVisibility.msg = serverVisible.booleanValue() ? "Server is visible" : "Server is invisible";
    }

    public void render(Graphics g) {
        renderBackground(g);
        R.centered(g, "Game menu", width / 2, 24, 0xFFFFFF);
        String bl = Net.btLabel();
        if (Net.isHost() && !bl.equals(btShown)) bBluetooth.msg = btShown = bl;
        super.render(g);
        if (noteY >= 0) R.centered(g, BtJoinScreen.fit(new String[] { "Bluetooth: only J2ME clients, not MCPE 0.6.1",
                "Bluetooth: J2ME clients only", "BT: J2ME only" }, width - 8), width / 2, noteY, 0x9FD0FF);
    }

    protected void buttonClicked(Button b) {
        if (b == bContinue) mc.setScreen(null);
        if (b == bQuit) mc.quitToTitle();
        if (b == bQuitAndSaveLocally) mc.quitAndCopyMap();
        if (b == bServerVisibility) {
            serverVisible = new Boolean(!serverVisible.booleanValue());
            if (Net.server != null) Net.server.allowIncomingConnections(serverVisible.booleanValue());
            updateServerVisibilityText();
        }
        if (b == bBluetooth) {
            // вкл/выкл; после ошибки — сразу повторный запуск
            boolean on = Net.bt != null && Net.bt.state != BtNet.ERROR;
            Net.btStopHost();
            if (!on) Net.btHost();
            bBluetooth.msg = btShown = Net.btLabel();
        }
        if (b == bSound) {
            Options.sound = bSound.on = !Options.sound;
            Options.save();
        }
        if (b == bThirdPerson) {
            Options.thirdPerson = bThirdPerson.on = !Options.thirdPerson;
            Options.save();
        }
    }
}
