import java.util.Vector;
import javax.microedition.lcdui.Graphics;

/**
 * Крафт — PaneCraftingScreen 0.6.1 (WorkbenchScreen / StonecutterScreen): слева категории, в середине список
 * рецептов категории (ItemPane), справа кнопка крафта с нужными предметами и описание.
 * В портрете категории — строкой сверху, кнопка крафта и описание — снизу.
 */
public final class CraftScreen extends Screen {
    public static final int HAND = 0, WORKBENCH = 1, STONECUTTER = 2;

    static final class CItem {
        Recipes.Recipe recipe;
        String text, sortText;
        int inventoryCount;
        boolean canCraft;
        int[] has;
    }

    private static final int BORDER = 6, DESC_W = 100, ROW = 22;
    private static final int RGB_ACTIVE = 0xF0F0F0, RGB_INACTIVE = 0x635558, RGB_INACTIVE_SHADOW = 0xAAAAAA;
    private static final R.Nine CAT = new R.Nine(R.sheet, 8, 32, 8, 8, 2, 2), CAT_SEL = new R.Nine(R.sheet, 0, 32, 8, 8, 2, 2);
    private static final R.Nine FRAME = new R.Nine(R.sheet, 0, 20, 8, 8, 1, 2).exclude(4);
    private static final R.Nine CRAFT = new R.Nine(R.sheet, 112, 0, 8, 67, 2, 2), CRAFT_SEL = new R.Nine(R.sheet, 120, 0, 8, 67, 2, 2);
    private static final R.Nine ROW_BG = new R.Nine(R.sheet, 20, 32, 8, 8, 2, 2), ROW_SEL = new R.Nine(R.sheet, 28, 32, 8, 8, 2, 2);

    private final int kind;
    private int numCategories = 4;
    private int[] catMask = { 1, 2, 4, 8 }, catIcon = { 0, 1, 2, 3 };
    private Vector[] categories;
    private final Vector items = new Vector();
    private int currentCategory = -1;
    private CItem currentItem;
    private String currentDesc = "";
    private final ImageButton btnClose = closeButton(1);

    // раскладка (пиксели GUI)
    private int catSize, catX0, catY0, catDX, catDY, paneX, paneY, paneW, paneH, craftX, craftY, craftW = 88, craftH = 62, descX, descY, descW;
    // прокрутка списка
    private float scroll, scrollO, vel;
    private int downX, downY, lastY, target = -1, pressedCat = -1;
    private boolean dragging;
    private static final int T_PANE = 1, T_CRAFT = 2, T_CAT = 4;

    public CraftScreen(int kind) {
        this.kind = kind;
        if (kind == STONECUTTER) {
            // setSingleCategoryAndIcon(ItemCategory::Structures, 5)
            numCategories = 1;
            catMask = new int[] { 1 };
            catIcon = new int[] { 5 };
        }
    }

    protected void init() {
        add(btnClose);
        categories = new Vector[numCategories];
        for (int i = 0; i < numCategories; i++) categories[i] = new Vector();
        items.removeAllElements();
        for (int i = 0; i < Recipes.all.size(); i++) {
            Recipes.Recipe r = (Recipes.Recipe) Recipes.all.elementAt(i);
            boolean ok = kind == STONECUTTER ? r.station == 2 : (r.station == 0 || (kind == WORKBENCH && r.station == 1));
            if (ok) addItem(r);
        }
        recheckRecipes();
    }

    protected void setupPositions() {
        btnClose.x = width - btnClose.width;
        btnClose.y = 0;
        int bh0 = (height - 16) / Math.max(numCategories, 4);
        if (width - (bh0 + 2 * BORDER) - DESC_W >= 90) {
            int bh = bh0;
            catSize = bh;
            catX0 = BORDER;
            catY0 = BORDER;
            catDX = 0;
            catDY = bh + 1;
            craftW = 100 - 2 * BORDER;
            craftX = width - DESC_W + (DESC_W - craftW) / 2 - 1;
            craftY = 20;
            paneX = bh + 2 * BORDER;
            paneY = BORDER + 2;
            paneW = width - paneX - DESC_W;
            paneH = height - 2 * BORDER - 4;
            descX = craftX;
            descY = craftY + craftH + 6;
            descW = craftW;
        } else {
            int n = Math.max(numCategories, 4);
            catSize = Math.min(44, (width - 2 * BORDER - btnClose.width - (n - 1)) / n);
            catX0 = BORDER;
            catY0 = BORDER;
            catDX = catSize + 1;
            catDY = 0;
            craftW = 88;
            paneX = BORDER + 1;
            paneY = BORDER + catSize + BORDER;
            paneW = width - 2 * BORDER - 2;
            craftX = BORDER;
            craftY = height - BORDER - craftH;
            paneH = craftY - 8 - paneY;
            descX = craftX + craftW + 6;
            descY = craftY + 2;
            descW = width - descX - BORDER;
        }
        selectCategory(currentCategory < 0 ? 0 : currentCategory);
    }

    private void addItem(Recipes.Recipe r) {
        CItem ci = new CItem();
        ci.recipe = r;
        ci.text = Lang.itemName(r.id, r.aux);
        ci.sortText = ci.text;
        if (r.id == Tile.WOOL) ci.sortText = "Wool " + ci.text;
        if (r.id == 351) ci.sortText = "ZDye " + ci.text;
        items.addElement(ci);
        int c = Item.category[r.id];
        for (int i = 0; i < numCategories; i++) if ((catMask[i] & c) != 0) categories[i].addElement(ci);
    }

    private void recheckRecipes() {
        Inventory inv = mc.player.inv;
        for (int i = 0; i < items.size(); i++) {
            CItem ci = (CItem) items.elementAt(i);
            Recipes.Recipe r = ci.recipe;
            ci.inventoryCount = Recipes.countOf(inv, r.id, r.aux);
            int n = r.ing.length / 3;
            ci.has = new int[n];
            ci.canCraft = true;
            for (int k = 0; k < n; k++) {
                ci.has[k] = Recipes.countOf(inv, r.ing[k * 3], r.ing[k * 3 + 1]);
                if (ci.has[k] < r.ing[k * 3 + 2]) ci.canCraft = false;
            }
        }
        // std::stable_sort по sortText
        for (int c = 0; c < numCategories; c++) {
            Vector v = categories[c];
            for (int i = 1; i < v.size(); i++) {
                Object o = v.elementAt(i);
                String key = ((CItem) o).sortText;
                int j = i - 1;
                while (j >= 0 && ((CItem) v.elementAt(j)).sortText.compareTo(key) > 0) {
                    v.setElementAt(v.elementAt(j), j + 1);
                    j--;
                }
                v.setElementAt(o, j + 1);
            }
        }
    }

    private float maxScroll() {
        return Math.max(0, categories[currentCategory].size() * ROW - paneH);
    }

    private void selectCategory(int c) {
        Vector cat = categories[c];
        if (c != currentCategory) scroll = scrollO = vel = 0;
        currentCategory = c;
        if (cat.size() > 0) selectItem((CItem) cat.elementAt(0));
        else currentItem = null;
    }

    private void selectItem(CItem ci) {
        currentItem = ci;
        currentDesc = Lang.description(ci.recipe.id, ci.recipe.aux);
    }

    private void craftSelected() {
        if (currentItem == null || !currentItem.canCraft) return;
        if (Recipes.craft(mc.player.inv, currentItem.recipe)) mc.playUI("random.pop");
        recheckRecipes();
    }

    public boolean renderGameBehind() {
        return false;
    }

    public void tick() {
        scrollO = scroll;
        if (target != T_PANE) {
            scroll += vel;
            vel *= 0.85f;
            if (Math.abs(vel) < 0.2f) vel = 0;
            float m = maxScroll();
            if (scroll < 0) { scroll *= 0.5f; if (scroll > -0.5f) scroll = 0; vel = 0; }
            if (scroll > m) { scroll = m + (scroll - m) * 0.5f; if (scroll - m < 0.5f) scroll = m; vel = 0; }
        }
    }

    public void render(Graphics g) {
        R.nineBg.draw(g, 0, 0, width, height);
        // категории
        for (int c = 0; c < numCategories; c++) {
            int x = catX0 + c * catDX, y = catY0 + c * catDY;
            boolean sel = c == currentCategory || c == pressedCat;
            (sel ? CAT_SEL : CAT).draw(g, x, y, catSize, catSize);
            int icon = catIcon[c];
            float sh = c == pressedCat ? 0.95f : 1f, d = catSize * (1 - sh) / 2;
            R.blit(g, R.sheet, x + d, y + d, catSize * sh, catSize * sh, 32 * (icon / 2), 64 + (icon & 1) * 32, 32, 32);
        }
        boolean craftDown = target == T_CRAFT && !dragging && touching && inCraft(xm, ym);
        (craftDown ? CRAFT_SEL : CRAFT).draw(g, craftX, craftY, craftW, craftH);
        super.render(g);
        FRAME.draw(g, paneX - 1, paneY - 2, paneW + 2, paneH + 4);
        renderPane(g);
        if (currentItem != null) {
            Recipes.Recipe r = currentItem.recipe;
            float slotW = craftW / 2f, slotH = craftH / 2f;
            float slotBx = craftX + slotW / 2 - 8, slotBy = craftY + slotH / 2 - 9;
            int n = r.ing.length / 3;
            for (int i = 0; i < n && i < 4; i++) {
                float xx = slotBx + slotW * (i % 2), yy = slotBy + slotH * (i / 2);
                int a = r.ing[i * 3 + 1];
                R.item(g, r.ing[i * 3], a < 0 ? 0 : a, xx, yy);
            }
            float sc = 2f / 3f;
            for (int i = 0; i < n && i < 4; i++) {
                float xx = slotBx + slotW * (i % 2) + 4 * sc, yy = slotBy + slotH * (i / 2) + 23 * sc;
                String t = R.countText(currentItem.has[i]) + "/" + R.countText(r.ing[i * 3 + 2]);
                if (currentItem.has[i] >= r.ing[i * 3 + 2]) R.shadow(g, t, xx, yy, RGB_ACTIVE, sc);
                else {
                    R.text(g, t, xx + sc, yy + sc, RGB_INACTIVE_SHADOW, sc);
                    R.text(g, t, xx, yy, RGB_INACTIVE, sc);
                }
            }
            R.wrapped(g, currentDesc, descX, descY, descW, RGB_ACTIVE, true);
        }
    }

    private void renderPane(Graphics g) {
        Vector cat = categories[currentCategory];
        float sc = target == T_PANE ? scroll : scrollO + (scroll - scrollO) * mc.partial;
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.clipRect(R.px(paneX), R.px(paneY), R.px(paneX + paneW) - R.px(paneX), R.px(paneY + paneH) - R.px(paneY));
        float k2 = 2f / 3f;
        for (int i = 0; i < cat.size(); i++) {
            float y = paneY + i * ROW - sc;
            if (y + ROW < paneY || y > paneY + paneH) continue;
            CItem ci = (CItem) cat.elementAt(i);
            float xf = paneX - 1;
            (ci == currentItem ? ROW_SEL : ROW_BG).draw(g, xf, y, paneW + 4, ROW);
            Recipes.Recipe r = ci.recipe;
            R.item(g, r.id, r.aux, paneX + paneW - 16, y + 2);
            String cnt = R.countText(ci.inventoryCount);
            float tx = xf + paneW - cnt.length() * 4, ty = y + ROW - 8;
            if (ci.canCraft) {
                R.shadow(g, ci.text, xf + 2, y + 6, RGB_ACTIVE);
                R.shadow(g, cnt, tx, ty, RGB_ACTIVE, k2);
            } else {
                R.text(g, ci.text, xf + 3, y + 7, RGB_INACTIVE_SHADOW);
                R.text(g, ci.text, xf + 2, y + 6, RGB_INACTIVE);
                R.text(g, cnt, tx, ty, RGB_INACTIVE, k2);
            }
        }
        R.gradient(g, paneX, paneY, paneX + paneW, paneY + 28, 0xBB000000, 0x00000000);
        R.gradient(g, paneX, paneY + paneH - 28, paneX + paneW, paneY + paneH, 0x00000000, 0xBB000000);
        g.setClip(cx, cy, cw, ch);
    }

    private boolean inCraft(int x, int y) {
        return x >= craftX && x < craftX + craftW && y >= craftY && y < craftY + craftH;
    }

    protected void mouseClicked(int x, int y) {
        downX = x;
        downY = lastY = y;
        dragging = false;
        pressedCat = -1;
        target = -1;
        vel = 0;
        if (inCraft(x, y)) target = T_CRAFT;
        else if (x >= paneX && x < paneX + paneW && y >= paneY && y < paneY + paneH) target = T_PANE;
        else {
            for (int c = 0; c < numCategories; c++) {
                int bx = catX0 + c * catDX, by = catY0 + c * catDY;
                if (x >= bx && x < bx + catSize && y >= by && y < by + catSize) {
                    target = T_CAT;
                    pressedCat = c;
                }
            }
        }
        if (target < 0) super.mouseClicked(x, y);
    }

    protected void mouseDragged(int x, int y) {
        if (target == T_PANE) {
            if (Math.abs(y - downY) > 5) dragging = true;
            if (dragging) {
                float d = lastY - y;
                scroll += d;
                vel = d;
            }
        } else if (Math.abs(x - downX) > 10 || Math.abs(y - downY) > 10) dragging = true;
        lastY = y;
    }

    protected void mouseReleased(int x, int y) {
        int t = target;
        target = -1;
        int pc = pressedCat;
        pressedCat = -1;
        if (t < 0) {
            super.mouseReleased(x, y);
            return;
        }
        if (dragging) return;
        vel = 0;
        switch (t) {
            case T_CRAFT:
                if (inCraft(x, y)) craftSelected();
                break;
            case T_CAT:
                if (pc >= 0) {
                    mc.playUI("random.click");
                    selectCategory(pc);
                }
                break;
            case T_PANE: {
                Vector cat = categories[currentCategory];
                int i = (int) ((y - paneY + scroll) / ROW);
                if (i >= 0 && i < cat.size()) selectItem((CItem) cat.elementAt(i));
                break;
            }
        }
    }

    protected void buttonClicked(Button b) {
        if (b == btnClose) mc.setScreen(null);
    }

    // ---- клавиши: рецепты, категории, кнопка крафта ----

    private static final Object F_ROWS = new Object(), F_CATS = new Object(), F_CRAFT = new Object();

    protected void collectFocus(Vector v) {
        Vector cat = categories[currentCategory];
        for (int i = 0; i < cat.size(); i++) {
            float y = paneY + i * ROW - scroll;
            if (y + ROW / 2f < paneY || y + ROW / 2f > paneY + paneH) continue;
            addFocus(v, F_ROWS, i, paneX, y, paneW, ROW);
        }
        for (int c = 0; c < numCategories; c++) addFocus(v, F_CATS, c, catX0 + c * catDX, catY0 + c * catDY, catSize, catSize);
        addFocus(v, F_CRAFT, 0, craftX, craftY, craftW, craftH);
        addButtons(v);
    }

    protected int focusStep(Object o, int i, int dir) {
        if (o != F_ROWS || (dir != UP && dir != DOWN)) return -1;
        int j = dir == UP ? i - 1 : i + 1;
        return j >= 0 && j < categories[currentCategory].size() ? j : -1;
    }

    protected void focusChanged(Object o, int i) {
        if (o == F_ROWS) {
            // как касание строки: выбрать рецепт; прокрутить, чтобы строка была видна
            Vector cat = categories[currentCategory];
            if (i >= cat.size()) return;
            selectItem((CItem) cat.elementAt(i));
            float top = i * ROW, bot = top + ROW;
            if (top < scroll) scroll = top;
            else if (bot > scroll + paneH) scroll = bot - paneH;
            scrollO = scroll;
            vel = 0;
        } else if (o == F_CATS && i != currentCategory) selectCategory(i);
    }

    protected void focusActivate(Object o, int i) {
        if (o == F_ROWS || o == F_CRAFT) craftSelected();
        else if (o == F_CATS) {
            mc.playUI("random.click");
            selectCategory(i);
        } else super.focusActivate(o, i);
    }
}
