import java.util.Vector;
import javax.microedition.lcdui.Graphics;

/**
 * Экран 0.6.1 (Screen + Touch-кнопки). Координаты — пиксели GUI: width x height = экран / R.k.
 * Касание: кнопка нажимается при опускании пальца, срабатывает при отпускании над ней (Screen::mouseReleased).
 */
public abstract class Screen {
    public MC mc;
    public int width, height;
    protected final Vector buttons = new Vector();
    private Button clickedButton;
    /** Текущая точка касания в пикселях GUI и признак, что палец на экране (Mouse::isButtonDown). */
    protected int xm = -9999, ym = -9999;
    protected boolean touching;

    // ---- кнопки ----

    /** Button: фон из gui.png (200x20, растянут по высоте), текст по центру. */
    public static class Button {
        public int id, x, y, width = 200, height = 24;
        public String msg;
        public boolean active = true, visible = true, selected;
        boolean down;

        public Button(int id, String msg) {
            this.id = id;
            this.msg = msg;
        }

        public Button(int id, int x, int y, int w, int h, String msg) {
            this(id, msg);
            this.x = x;
            this.y = y;
            width = w;
            height = h;
        }

        public boolean inside(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }

        boolean clicked(int px, int py) {
            return active && visible && inside(px, py);
        }

        boolean hovered(Screen s) {
            return down && s.touching && inside(s.xm, s.ym);
        }

        public void render(Graphics g, Screen s) {
            if (!visible) return;
            renderBg(g, s);
            renderFace(g, s);
        }

        void renderBg(Graphics g, Screen s) {
            int yImage = !active ? 0 : (selected || hovered(s) ? 2 : 1);
            int hw = width / 2;
            R.blit(g, R.gui, x, y, hw, height, 0, 46 + yImage * 20, hw, 20);
            R.blit(g, R.gui, x + hw, y, width - hw, height, 200 - (width - hw), 46 + yImage * 20, width - hw, 20);
        }

        void renderFace(Graphics g, Screen s) {
            int c = !active ? 0xA0A0A0 : (hovered(s) || selected ? 0xFFFFA0 : 0xE0E0E0);
            R.centered(g, msg, x + width / 2, y + (height - 8) / 2, c);
        }
    }

    /** Touch::TButton: фон touchgui.png 66x26 (нажатая — соседняя картинка). */
    public static class TButton extends Button {
        public TButton(int id, String msg) {
            super(id, msg);
            width = 66;
            height = 26;
        }

        void renderBg(Graphics g, Screen s) {
            boolean h = active && hovered(s);
            R.blit(g, R.touch, x, y, width, height, h ? 66 : 0, 0, 66, 26, 255, active ? 255 : 128);
        }
    }

    /** Touch::THeader: неактивная плашка-заголовок touchgui.png (края 2, середина 8, тень 3). */
    public static class THeader extends Button {
        public int xText = -99999;

        public THeader(int id, String msg) {
            super(id, msg);
            active = false;
            width = 66;
            height = 26;
        }

        public void render(Graphics g, Screen s) {
            if (!visible) return;
            R.blit(g, R.touch, x, y, 2, height - 1, 150, 26, 2, 25);
            R.blit(g, R.touch, x + 2, y, width - 3, height - 1, 153, 26, 8, 25);
            R.blit(g, R.touch, x + width - 2, y, 2, height - 1, 162, 26, 2, 25);
            R.blit(g, R.touch, x, y + height - 1, width, 3, 153, 52, 8, 3);
            int xx = xText != -99999 ? xText : x + width / 2;
            R.centered(g, msg, xx, y + (height - 8) / 2, 0xE0E0E0);
        }
    }

    /** ImageDef: текстура, смещение и размер картинки на кнопке, прямоугольник источника. */
    public static final class ImageDef {
        public R.Tex tex;
        public int x, y, sx, sy, sw, sh;
        public float width = 16, height = 16;

        public ImageDef(R.Tex tex, int sx, int sy, int sw, int sh, float w, float h) {
            this.tex = tex;
            this.sx = sx;
            this.sy = sy;
            this.sw = sw;
            this.sh = sh;
            width = w;
            height = h;
        }
    }

    /** ImageButton: картинка (при нажатии — вторая, справа от первой; чуть уменьшается), подпись сверху. */
    public static class ImageButton extends Button {
        public ImageDef def;
        public boolean scaleWhenPressed = true;

        public ImageButton(int id, String msg) {
            super(id, msg);
            width = 48;
            height = 48;
        }

        public void setImageDef(ImageDef d, boolean setSize) {
            def = d;
            if (setSize) {
                width = (int) d.width;
                height = (int) d.height;
            }
        }

        boolean secondImage(boolean hovered) {
            return hovered;
        }

        public void render(Graphics g, Screen s) {
            if (!visible) return;
            boolean h = active && hovered(s);
            if (def != null) {
                float hx = def.width * 0.5f, hy = def.height * 0.5f;
                float cx = x + def.x + hx, cy = y + def.y + hy;
                if (scaleWhenPressed && h) {
                    hx *= 0.95f;
                    hy *= 0.95f;
                }
                int sx = def.sx + (secondImage(h) ? def.sw : 0);
                R.blit(g, def.tex, cx - hx, cy - hy, hx * 2, hy * 2, sx, def.sy, def.sw, def.sh, 255, active ? 255 : 128);
            }
            if (msg.length() == 0) return;
            if (!active) R.centered(g, msg, x + width / 2, y + 16, 0xA0A0A0);
            else if (h || selected) R.centered(g, msg, x + width / 2, y + 17, 0xFFFFA0);
            else R.centered(g, msg, x + width / 2, y + 16, 0xE0E0E0);
        }
    }

    /** OptionButton: картинка-переключатель (вторая картинка — включено). */
    public static class OptionButton extends ImageButton {
        public boolean on;

        public OptionButton(int id) {
            super(id, "");
        }

        boolean secondImage(boolean hovered) {
            return on;
        }
    }

    // ---- жизненный цикл ----

    public void init(MC mc, int w, int h) {
        this.mc = mc;
        width = w;
        height = h;
        buttons.removeAllElements();
        clickedButton = null;
        init();
        setupPositions();
        // кнопочный режим: фокус виден сразу (Screen::updateTabButtonSelection, первая кнопка)
        keyFocus = !mc.touchUI;
        focusOwner = null;
        okOwner = null;
        if (keyFocus) {
            Vector v = collect();
            if (v.size() > 0) {
                Focus f = (Focus) v.elementAt(defaultFocus(v));
                setFocus(f.o, f.i);
            }
        }
        syncSelected();
    }

    /** Смена размера экрана без пересоздания (Screen::setSize). */
    public void setSize(int w, int h) {
        width = w;
        height = h;
        setupPositions();
    }

    protected void init() {
    }

    protected void setupPositions() {
    }

    public void render(Graphics g) {
        for (int i = 0; i < buttons.size(); i++) ((Button) buttons.elementAt(i)).render(g, this);
    }

    public void tick() {
    }

    /** Screen::isPauseScreen — игра стоит, пока экран открыт. */
    public boolean pausesGame() {
        return true;
    }

    /** Screen::renderGameBehind — рисовать мир под экраном. */
    public boolean renderGameBehind() {
        return true;
    }

    /** Экран с полупрозрачным фоном (иначе рисуется только сам экран). */
    public boolean showsWorld() {
        return renderGameBehind() && mc != null && mc.level != null;
    }

    protected void buttonClicked(Button b) {
    }

    /** Кнопка «Назад» (Screen::handleBackEvent). */
    public void back() {
        mc.setScreen(null);
    }

    /** Текстовый символ с клавиатуры (TextEditScreen): 8 — стереть, 10 — следующая строка. */
    public void keyChar(int c) {
    }

    /** Нажатие клавиши до разбора на навигацию (KeysScreen ждёт клавишу); true — съедено. */
    public boolean keyRaw(int k, int ga) {
        return false;
    }

    /** Экран принимает текст с клавиатуры. */
    public boolean wantsText() {
        return false;
    }

    /** Screen::removed — экран закрыт. */
    public void removed() {
    }

    /** Screen::renderBackground: в игре — затемнение, в меню — земля. */
    protected void renderBackground(Graphics g) {
        if (mc.level != null) R.gradient(g, 0, 0, width, height, 0xC0101010, 0xD0101010);
        else R.dirt(g, 0, 0, width, height, 0x40, 0);
    }

    // ---- касания (MC передаёт пиксели экрана) ----

    public final void pointerPressed(int px, int py) {
        if (keyFocus) {
            keyFocus = false;
            syncSelected();
        }
        xm = (int) (px / R.k);
        ym = (int) (py / R.k);
        touching = true;
        mouseClicked(xm, ym);
    }

    public final void pointerDragged(int px, int py) {
        xm = (int) (px / R.k);
        ym = (int) (py / R.k);
        mouseDragged(xm, ym);
    }

    public final void pointerReleased(int px, int py) {
        xm = (int) (px / R.k);
        ym = (int) (py / R.k);
        touching = false;
        mouseReleased(xm, ym);
    }

    protected void mouseClicked(int x, int y) {
        clickedButton = null;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = (Button) buttons.elementAt(i);
            if (b.clicked(x, y)) {
                b.down = true;
                clickedButton = b;
                break;
            }
        }
    }

    protected void mouseDragged(int x, int y) {
    }

    protected void mouseReleased(int x, int y) {
        Button b = clickedButton;
        clickedButton = null;
        if (b == null) return;
        b.down = false;
        if (b.clicked(x, y)) {
            mc.playUI("random.click");
            buttonClicked(b);
        }
    }

    // ---- клавиши (Screen::keyPressed 0.6.1: фокус по кнопкам; здесь — по направлениям, вместе со слотами) ----

    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    /** Элемент с фокусом: владелец (кнопка, панель ячеек или своё) и номер внутри него. */
    protected Object focusOwner;
    protected int focusIdx;
    /** Фокус показан: кнопочный режим или нажата клавиша после касания. */
    protected boolean keyFocus;
    private Object okOwner;
    private int okIdx;

    protected static final class Focus {
        Object o;
        int i;
        float x, y, w, h;
    }

    private Vector collect() {
        Vector v = new Vector();
        collectFocus(v);
        return v;
    }

    /** Элементы, на которые можно поставить фокус (по умолчанию — активные видимые кнопки). */
    protected void collectFocus(Vector v) {
        addButtons(v);
    }

    protected final void addButtons(Vector v) {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = (Button) buttons.elementAt(i);
            if (b.visible && b.active && !(b instanceof THeader)) addFocus(v, b, 0, b.x, b.y, b.width, b.height);
        }
    }

    protected static void addFocus(Vector v, Object o, int i, float x, float y, float w, float h) {
        Focus f = new Focus();
        f.o = o;
        f.i = i;
        f.x = x;
        f.y = y;
        f.w = w;
        f.h = h;
        v.addElement(f);
    }

    /** Видимые ячейки панели (центр ячейки внутри панели). */
    protected static void addPane(Vector v, InventoryPane p) {
        int n = p.count();
        for (int i = 0; i < n; i++) {
            float x = p.itemX(i), y = p.itemY(i), cy = y + p.itemSize / 2f;
            if (cy < p.y || cy > p.y + p.h) continue;
            addFocus(v, p, i, x, y, p.itemSize, p.itemSize);
        }
    }

    /** Номер элемента, получающего фокус первым. */
    protected int defaultFocus(Vector v) {
        return 0;
    }

    /** Шаг внутри элемента (сетка, список): новый номер (тот же — остаться) или -1 — перейти к соседнему элементу. */
    protected int focusStep(Object o, int i, int dir) {
        if (o instanceof InventoryPane) return ((InventoryPane) o).step(i, dir);
        return -1;
    }

    /** Фокус перешёл на элемент. */
    protected void focusChanged(Object o, int i) {
        if (o instanceof InventoryPane) ((InventoryPane) o).showItem(i);
    }

    /** OK нажата на элементе (панель — «зарядка» стопки удержанием). */
    protected void focusPress(Object o, int i) {
        if (o instanceof InventoryPane) ((InventoryPane) o).keyPress(i);
    }

    /** OK отпущена на элементе: нажать кнопку / выбрать ячейку. */
    protected void focusActivate(Object o, int i) {
        if (o instanceof Button) {
            Button b = (Button) o;
            if (b.active) {
                mc.playUI("random.click");
                buttonClicked(b);
            }
        } else if (o instanceof InventoryPane) ((InventoryPane) o).keyRelease(i);
    }

    protected final void setFocus(Object o, int i) {
        focusOwner = o;
        focusIdx = i;
        focusChanged(o, i);
        syncSelected();
    }

    private Focus current(Vector v) {
        for (int k = 0; k < v.size(); k++) {
            Focus f = (Focus) v.elementAt(k);
            if (f.o == focusOwner && f.i == focusIdx) return f;
        }
        return null;
    }

    private void syncSelected() {
        for (int i = 0; i < buttons.size(); i++) {
            Button b = (Button) buttons.elementAt(i);
            b.selected = keyFocus && b == focusOwner;
        }
    }

    /** Клавиша направления. */
    public void keyNav(int dir) {
        Vector v = collect();
        if (v.size() == 0) return;
        Focus cur = current(v);
        if (cur == null && focusOwner != null) {
            // элемент ушёл из видимых (ячейка за краем панели) — шаг внутри него ещё возможен
            int ni = focusStep(focusOwner, focusIdx, dir);
            if (ni >= 0) {
                keyFocus = true;
                setFocus(focusOwner, ni);
                return;
            }
        }
        if (!keyFocus || cur == null) {
            keyFocus = true;
            if (cur == null) {
                Focus f = (Focus) v.elementAt(defaultFocus(v));
                setFocus(f.o, f.i);
            } else syncSelected();
            return;
        }
        int ni = focusStep(cur.o, cur.i, dir);
        if (ni >= 0) {
            if (ni != cur.i) setFocus(cur.o, ni);
            return;
        }
        Focus best = nearest(v, cur, dir);
        if (best != null) setFocus(best.o, best.i);
    }

    /** Ближайший элемент в направлении: расстояние по оси + перекос вбок. */
    private static Focus nearest(Vector v, Focus c, int dir) {
        float cx = c.x + c.w / 2, cy = c.y + c.h / 2;
        Focus best = null;
        float bs = 1e9f;
        for (int k = 0; k < v.size(); k++) {
            Focus f = (Focus) v.elementAt(k);
            if (f.o == c.o && f.i == c.i) continue;
            float fx = f.x + f.w / 2, fy = f.y + f.h / 2, main, side, dc, ds;
            if (dir == LEFT || dir == RIGHT) {
                dc = dir == RIGHT ? fx - cx : cx - fx;
                if (dc <= 1) continue;
                main = Math.max(0, dir == RIGHT ? f.x - (c.x + c.w) : c.x - (f.x + f.w));
                side = gap(c.y, c.y + c.h, f.y, f.y + f.h);
                ds = Math.abs(fy - cy);
            } else {
                dc = dir == DOWN ? fy - cy : cy - fy;
                if (dc <= 1) continue;
                main = Math.max(0, dir == DOWN ? f.y - (c.y + c.h) : c.y - (f.y + f.h));
                side = gap(c.x, c.x + c.w, f.x, f.x + f.w);
                ds = Math.abs(fx - cx);
            }
            float sc = main + dc * 0.25f + side * 2 + ds * 0.1f;
            if (sc < bs) {
                bs = sc;
                best = f;
            }
        }
        return best;
    }

    private static float gap(float a0, float a1, float b0, float b1) {
        if (b1 < a0) return a0 - b1;
        if (b0 > a1) return b0 - a1;
        return 0;
    }

    /** OK (центр джойстика / 5): нажатие и отпускание. */
    public void keyOk(boolean down) {
        if (down) {
            Vector v = collect();
            Focus cur = current(v);
            if (cur == null) {
                if (v.size() == 0) return;
                cur = (Focus) v.elementAt(defaultFocus(v));
                setFocus(cur.o, cur.i);
            }
            if (!keyFocus) {
                keyFocus = true;
                syncSelected();
            }
            okOwner = cur.o;
            okIdx = cur.i;
            focusPress(cur.o, cur.i);
            return;
        }
        Object o = okOwner;
        okOwner = null;
        if (o != null && o == focusOwner && okIdx == focusIdx && mc.screen == this) focusActivate(o, okIdx);
    }

    /** Рамка вокруг элемента с фокусом. */
    public void renderFocus(Graphics g) {
        if (!keyFocus || focusOwner == null) return;
        Focus f = current(collect());
        if (f == null) return;
        float t = 1f, x0 = f.x - t, y0 = f.y - t, x1 = f.x + f.w + t, y1 = f.y + f.h + t;
        int c = 0xFFFFFFA0;
        R.fill(g, x0, y0, x1, y0 + t, c);
        R.fill(g, x0, y1 - t, x1, y1, c);
        R.fill(g, x0, y0 + t, x0 + t, y1 - t, c);
        R.fill(g, x1 - t, y0 + t, x1, y1 - t, c);
    }

    protected Button add(Button b) {
        buttons.addElement(b);
        return b;
    }

    /** Крестик закрытия из spritesheet.png (60,0 18x18), кнопка 19x19 в правом верхнем углу. */
    protected static ImageButton closeButton(int id) {
        ImageButton b = new ImageButton(id, "");
        ImageDef d = new ImageDef(R.sheet, 60, 0, 18, 18, 18, 18);
        d.y = 1;
        b.setImageDef(d, true);
        b.width = b.height = 19;
        b.scaleWhenPressed = false;
        return b;
    }
}
