import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Custom (нет в оригинале): расположение и размер крестовины и кнопки паузы. Кнопки стоят в игровой разметке
 * и перетаскиваются пальцем (крестовина целиком); общий ползунок размера — неподвижный блок вверху.
 * Вид — как у OptionsScreen: пиксели экрана, системный шрифт.
 */
public final class ControlsScreen extends Screen {
    private static final int ROW_SIZE = 0, ROW_RESET = 1, ROW_DONE = 2;
    /** что тащит палец: панель (строка) или кнопка управления */
    private static final int DRAG_NONE = -1, DRAG_DPAD = 10, DRAG_PAUSE = 11;
    private final Font fTitle = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
    private final Font fBtn = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    private int W, H, pad, panX, panY, panW, panH, barX, barW, btnH, sizeY, btnY;
    private int down = DRAG_NONE, focusRow = ROW_SIZE;
    /** смещение пальца от центра перетаскиваемой кнопки */
    private float grabX, grabY;

    protected void setupPositions() {
        W = mc.W;
        H = mc.H;
        pad = Math.max(6, fBtn.getHeight() / 2);
        panW = Math.min(W - 4 * pad, Math.max(W / 3, 220));
        int lineH = fTitle.getHeight();
        btnH = fBtn.getHeight() + 2 * pad;
        // «размер + ползунок», кнопки
        panH = pad + lineH + 3 * pad + btnH + pad;
        // блок неподвижен: вверху, слева от места паузы по умолчанию (у правого края) с отступом
        panX = Mth.clamp(W - 4 - (int) Gui.defaultPause(W, H) - 3 * pad - panW, 0, Math.max(0, W - panW));
        panY = pad;
        barX = panX + 3 * pad;
        barW = panW - 6 * pad;
        sizeY = panY + pad;
        btnY = panY + panH - pad - btnH;
        relayout();
    }

    private void relayout() {
        mc.gui.layout(W, H);
    }

    public boolean renderGameBehind() {
        return false;
    }

    private int barY() {
        return sizeY + fTitle.getHeight() + pad + pad / 2;
    }

    private void setSize(int v) {
        Options.ctrlSize = Mth.clamp(v, Options.CTRL_MIN, Options.CTRL_MAX);
        relayout();
    }

    public void render(Graphics g) {
        g.setColor(0x303030);
        g.fillRect(0, 0, W, H);
        Gui gui = mc.gui;
        gui.renderToolBarFrame(g);
        boolean f = keyFocus && focusRow == ROW_SIZE;
        g.setFont(fTitle);
        g.setColor(f ? 0xFFFFA0 : 0xE0E0E0);
        g.drawString("Button size", panX + 2 * pad, sizeY, Graphics.TOP | Graphics.LEFT);
        g.drawString(Options.ctrlSize + "%", panX + panW - 2 * pad, sizeY, Graphics.TOP | Graphics.RIGHT);
        int by = barY();
        g.setColor(0x606060);
        g.fillRect(barX, by - 1, barW, 3);
        int tx = barX + barW * (Options.ctrlSize - Options.CTRL_MIN) / (Options.CTRL_MAX - Options.CTRL_MIN);
        g.setColor(0x8BC34A);
        g.fillRect(barX, by - 1, tx - barX, 3);
        g.fillArc(tx - pad, by - pad, 2 * pad, 2 * pad, 0, 360);
        button(g, ROW_RESET, "RESET", panX + 2 * pad);
        button(g, ROW_DONE, "DONE", panX + panW - 2 * pad - fBtn.stringWidth("DONE"));
        // кнопки управления — поверх панели, чтобы их можно было достать откуда угодно
        gui.renderControls(g);
        if (down == DRAG_DPAD) frame(g, gui.baseX, gui.baseY, 3 * gui.bw);
        else if (down == DRAG_PAUSE) frame(g, gui.pauseX, gui.pauseY, gui.pauseSize);
    }

    private static void frame(Graphics g, float x, float y, float s) {
        g.setColor(0xFFFFA0);
        g.drawRect((int) x - 1, (int) y - 1, (int) s + 1, (int) s + 1);
    }

    private void button(Graphics g, int row, String text, int x) {
        boolean f = (keyFocus && focusRow == row) || down == row;
        g.setFont(fBtn);
        g.setColor(down == row ? 0x606060 : (f ? 0xFFFFA0 : 0x8BC34A));
        g.drawString(text, x, btnY + (btnH - fBtn.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
    }

    private int hit(int x, int y) {
        Gui gui = mc.gui;
        // кнопки управления лежат сверху — у них приоритет
        if (x >= gui.pauseX && x < gui.pauseX + gui.pauseSize && y >= gui.pauseY && y < gui.pauseY + gui.pauseSize) return DRAG_PAUSE;
        float d = 3 * gui.bw;
        if (x >= gui.baseX && x < gui.baseX + d && y >= gui.baseY && y < gui.baseY + d) return DRAG_DPAD;
        int by = barY();
        if (y >= sizeY && y <= by + 2 * pad && x >= barX - 2 * pad && x <= barX + barW + 2 * pad) return ROW_SIZE;
        if (y >= btnY && y < btnY + btnH && x >= panX && x < panX + panW) return x >= panX + panW / 2 ? ROW_DONE : ROW_RESET;
        return DRAG_NONE;
    }

    private void slide(int x) {
        setSize(Options.CTRL_MIN + (x - barX) * (Options.CTRL_MAX - Options.CTRL_MIN) / Math.max(1, barW));
    }

    /** Поставить центр кнопки под палец (в тысячных долях экрана, Gui.layout сам удержит в пределах экрана). */
    private void move(int x, int y) {
        int cx = Mth.clamp((int) ((x - grabX) * 1000 / W), 0, 1000), cy = Mth.clamp((int) ((y - grabY) * 1000 / H), 0, 1000);
        if (down == DRAG_DPAD) {
            Options.dpadX = cx;
            Options.dpadY = cy;
        } else {
            Options.pauseX = cx;
            Options.pauseY = cy;
        }
        relayout();
    }

    // ---- касания: пиксели GUI -> пиксели экрана ----

    protected void mouseClicked(int gx, int gy) {
        int x = (int) (gx * R.k), y = (int) (gy * R.k);
        down = hit(x, y);
        Gui gui = mc.gui;
        if (down == ROW_SIZE) slide(x);
        else if (down == DRAG_DPAD) {
            grabX = x - (gui.baseX + 1.5f * gui.bw);
            grabY = y - (gui.baseY + 1.5f * gui.bw);
        } else if (down == DRAG_PAUSE) {
            grabX = x - (gui.pauseX + gui.pauseSize / 2);
            grabY = y - (gui.pauseY + gui.pauseSize / 2);
        }
    }

    protected void mouseDragged(int gx, int gy) {
        int x = (int) (gx * R.k), y = (int) (gy * R.k);
        if (down == ROW_SIZE) slide(x);
        else if (down == DRAG_DPAD || down == DRAG_PAUSE) move(x, y);
    }

    protected void mouseReleased(int gx, int gy) {
        int x = (int) (gx * R.k), y = (int) (gy * R.k);
        int d = down;
        down = DRAG_NONE;
        if ((d == ROW_RESET || d == ROW_DONE) && hit(x, y) == d) activate(d);
    }

    private void activate(int row) {
        if (row == ROW_RESET) {
            Options.ctrlSize = 50;
            Options.dpadX = Options.dpadY = Options.pauseX = Options.pauseY = -1;
            relayout();
        } else if (row == ROW_DONE) back();
    }

    // ---- клавиши: вверх/вниз — строка, влево/вправо — размер по 5%, OK — кнопка ----

    public void keyNav(int dir) {
        keyFocus = true;
        if (dir == UP) focusRow = ROW_SIZE;
        else if (dir == DOWN) focusRow = focusRow == ROW_SIZE ? ROW_RESET : focusRow;
        else if (focusRow == ROW_SIZE) setSize(Options.ctrlSize + (dir == RIGHT ? 5 : -5));
        else focusRow = dir == RIGHT ? ROW_DONE : ROW_RESET;
    }

    public void keyOk(boolean down) {
        if (!down) return;
        if (focusRow != ROW_SIZE) activate(focusRow);
        else keyNav(DOWN);
    }

    public void renderFocus(Graphics g) {
    }

    public void back() {
        Options.save();
        OptionsScreen o = new OptionsScreen();
        o.scrollToEnd = true;
        mc.setScreen(o);
    }
}
