import javax.microedition.lcdui.Graphics;

/**
 * Touch::InventoryPane 0.6.1 (ScrollingPane): сетка ячеек 32x32 (gui.png 200,46), вертикальная прокрутка с инерцией,
 * затенение краёв, полоса прокрутки, касание ячейки — onSelect, удержание — «зарядка» (сундук/печь).
 */
public final class InventoryPane {
    /** IInventoryPaneCallback. */
    public interface Callback {
        int paneCount(InventoryPane p);
        int paneId(InventoryPane p, int i);
        int paneAux(InventoryPane p, int i);
        int paneCountOf(InventoryPane p, int i);
        boolean isAllowed(InventoryPane p, int i);
        boolean addItem(InventoryPane p, int i);
    }

    private final Callback cb;
    public int x, y, w, h;
    public final int itemSize, border;
    public int columns;
    public int fillMarginX = 2, fillMarginY = 4;
    public boolean renderCounts = true, renderDecorations = true;
    // прокрутка
    private float scroll, scrollO, vel;
    private boolean down, dragging;
    private int downX, downY, lastY, downItem = -1;
    private long downTime;
    private float sbAlpha;
    // отклик
    private int lastItemIndex = -1, lastItemTicks;
    public int markerIndex = -1;
    public float markerShare;

    public InventoryPane(Callback cb, int x, int y, int w, int h, int itemSize, int border) {
        this.cb = cb;
        this.itemSize = itemSize;
        this.border = border;
        setRect(x, y, w, h);
    }

    public void setRect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        columns = Math.max(1, w / itemSize);
        clamp();
    }

    private float maxScroll() {
        int n = cb.paneCount(this);
        int rows = (n + columns - 1) / columns;
        return Math.max(0, rows * itemSize - h);
    }

    private void clamp() {
        float m = maxScroll();
        if (scroll > m) scroll = m;
        if (scroll < 0) scroll = 0;
    }

    /** Область касания: прямоугольник, расширенный на border по бокам и на 6 сверху/снизу. */
    public boolean inside(int px, int py) {
        return px >= x - border && px < x + w + border && py >= y - 6 && py < y + h + 6;
    }

    private int itemAt(int px, int py) {
        if (px < x || px >= x + columns * itemSize || py < y || py >= y + h) return -1;
        int c = (px - x) / itemSize, r = (int) ((py - y + scroll) / itemSize);
        int i = r * columns + c;
        return i >= 0 && i < cb.paneCount(this) ? i : -1;
    }

    public void tick() {
        lastItemTicks--;
        scrollO = scroll;
        if (!down) {
            scroll += vel;
            vel *= 0.85f;
            if (Math.abs(vel) < 0.2f) vel = 0;
            float m = maxScroll();
            if (scroll < 0) { scroll *= 0.5f; vel = 0; if (scroll > -0.5f) scroll = 0; }
            if (scroll > m) { scroll = m + (scroll - m) * 0.5f; vel = 0; if (scroll - m < 0.5f) scroll = m; }
        }
        if (down && dragging || vel != 0) sbAlpha = 1;
        else if (sbAlpha > 0) sbAlpha -= 0.08f;
    }

    // ---- касания (пиксели GUI) ----

    public boolean touchDown(int px, int py) {
        if (!inside(px, py)) return false;
        down = true;
        dragging = false;
        downX = px;
        downY = lastY = py;
        downTime = System.currentTimeMillis();
        downItem = itemAt(px, py);
        vel = 0;
        return true;
    }

    public void touchMove(int px, int py) {
        if (!down) return;
        if (!dragging && (Math.abs(py - downY) > 5 || Math.abs(px - downX) > 5)) dragging = true;
        if (dragging) {
            float d = lastY - py;
            float m = maxScroll();
            if (scroll < 0 || scroll > m) d *= 0.5f;
            scroll += d;
            vel = d;
            downItem = -1;
        }
        lastY = py;
    }

    public void touchUp(int px, int py) {
        if (!down) return;
        down = false;
        if (!dragging) {
            int i = itemAt(px, py);
            if (i >= 0 && i == downItem) select(i);
            vel = 0;
        }
        downItem = -1;
    }

    /** Удержание пальца (или OK) на ячейке без прокрутки (ScrollingPane::queryHoldTime): мс или -1. */
    public int holdMs() {
        if (keyItem >= 0) return (int) (System.currentTimeMillis() - keyTime);
        if (!down || dragging || downItem < 0) return -1;
        return (int) (System.currentTimeMillis() - downTime);
    }

    public int heldItem() {
        if (keyItem >= 0) return keyItem;
        return down && !dragging ? downItem : -1;
    }

    /** Отменить текущее касание (после переноса по удержанию). */
    public void cancelTouch() {
        downItem = -1;
        keyItem = -1;
        keyCancelled = true;
    }

    // ---- клавиши: фокус на ячейке, OK — как касание ----

    private int keyItem = -1;
    private long keyTime;
    private boolean keyCancelled;

    public int count() {
        return cb.paneCount(this);
    }

    public void keyPress(int i) {
        keyItem = i;
        keyTime = System.currentTimeMillis();
        keyCancelled = false;
    }

    public void keyRelease(int i) {
        boolean ok = keyItem == i && !keyCancelled && i >= 0 && i < count();
        keyItem = -1;
        if (ok) select(i);
    }

    /** Сосед по сетке или -1 на краю. */
    public int step(int i, int dir) {
        int n = count();
        if (n == 0 || i < 0 || i >= n) return -1;
        int c = i % columns, j;
        if (dir == Screen.UP) j = i - columns;
        else if (dir == Screen.DOWN) {
            j = i + columns;
            // неполный последний ряд — на последнюю ячейку
            if (j >= n && i / columns < (n - 1) / columns) j = n - 1;
        } else if (dir == Screen.LEFT) j = c > 0 ? i - 1 : -1;
        else j = c < columns - 1 ? i + 1 : -1;
        return j >= 0 && j < n ? j : -1;
    }

    /** Прокрутить так, чтобы ячейка была видна целиком. */
    public void showItem(int i) {
        float top = (i / columns) * itemSize, bot = top + itemSize;
        if (top < scroll) scroll = top;
        else if (bot > scroll + h) scroll = bot - h;
        clamp();
        scrollO = scroll;
        vel = 0;
        sbAlpha = 1;
    }

    private void select(int i) {
        if (cb.isAllowed(this, i) && cb.addItem(this, i)) {
            lastItemIndex = i;
            lastItemTicks = 7;
        }
    }

    /** Положение ячейки i (getGridItemFor_slow) — для летящих предметов. */
    public float itemX(int i) {
        return x + (i % columns) * itemSize;
    }

    public float itemY(int i) {
        return y + (i / columns) * itemSize - scroll;
    }

    // ---- отрисовка (InventoryPane::renderBatch) ----

    public void render(Graphics g, float a) {
        float sc = down ? scroll : scrollO + (scroll - scrollO) * a;
        R.fill(g, x - fillMarginX - 1, y - fillMarginY, x + w + fillMarginX + 1, y + h + fillMarginY, 0xFF333333);
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.clipRect(R.px(x), R.px(y), R.px(x + w) - R.px(x), R.px(y + h) - R.px(y));
        int n = cb.paneCount(this);
        int first = Math.max(0, (int) (sc / itemSize) * columns), last = Math.min(n, (int) ((sc + h) / itemSize + 1) * columns);
        for (int i = first; i < last; i++) {
            float xf = x + (i % columns) * itemSize, yf = y + (i / columns) * itemSize - sc;
            R.blit(g, R.gui, xf, yf, itemSize, itemSize, 200, 46, 16, 16);
        }
        for (int i = first; i < last; i++) {
            int id = cb.paneId(this, i);
            if (id <= 0) continue;
            float xf = x + (i % columns) * itemSize, yf = y + (i / columns) * itemSize - sc;
            int cnt = cb.paneCountOf(this, i);
            int mode = !cb.isAllowed(this, i) ? R.DARK : (cnt <= 0 ? R.FADED : R.NORMAL);
            if (lastItemTicks > 0 && lastItemIndex == i && mode == R.NORMAL) mode = R.DARK;
            R.item(g, id, cb.paneAux(this, i), xf + border + 4, yf + border + 4, mode);
            if (i == markerIndex && markerShare >= 0) {
                float mx = xf + border + 4, my = yf + border + 4 - 5;
                R.fill(g, mx, my, mx + 16, my + 2, 0xFF606060);
                R.fill(g, mx, my, mx + Math.min(1f, markerShare) * 16, my + 2, 0xFF00FF00);
            }
            if (renderCounts) R.slotText(g, cnt, xf + border + 3, yf + border + 3);
            if (renderDecorations) R.decorations(g, id, cb.paneAux(this, i), xf + 8, yf + 12);
        }
        g.setClip(cx, cy, cw, ch);
        R.gradient(g, x - fillMarginX, y, x + w + fillMarginX, y + 20, 0x99000000, 0x00000000);
        R.gradient(g, x - fillMarginX, y + h - 20, x + w + fillMarginX, y + h, 0x00000000, 0x99000000);
        // полоса прокрутки
        float m = maxScroll();
        if (sbAlpha > 0 && m > 0) {
            float total = m + h, sbh = Math.max(8, h * h / total), sby = y + (h - sbh) * Math.max(0, Math.min(1, sc / m));
            R.fill(g, x + w - 2, sby, x + w, sby + sbh, ((int) (255 * Math.min(1, sbAlpha)) << 24) | 0xAAAAAA);
        }
    }
}
