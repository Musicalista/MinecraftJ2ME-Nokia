import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.*;

/**
 * ArmorScreen 0.6.1: шапка «Armor», слева броня из инвентаря (касание — надеть), 4 слота (касание — снять),
 * справа игрок в броне. В портрете слоты и игрок сверху, панель снизу.
 */
public final class ArmorScreen extends Screen implements InventoryPane.Callback {
    private static final int BX = 10, BY = 6, ITEM = 32, BORDER = 4;
    private final THeader bHeader = new THeader(0, "Armor");
    private final ImageButton btnClose = closeButton(10);
    private final Button[] armorButtons = new Button[4];
    private final Vector armorSlots = new Vector(); // индексы слотов инвентаря с бронёй
    private InventoryPane pane;
    private int bgX, bgY, bgW, bgH;
    private final long t0 = System.currentTimeMillis();

    private static final Background depthOnly = new Background();
    private static final Camera cam = new Camera();
    private static final Transform camT = new Transform(), base = new Transform();

    static {
        depthOnly.setColorClearEnable(false);
        depthOnly.setDepthClearEnable(true);
    }

    protected void init() {
        add(bHeader);
        add(btnClose);
        for (int i = 0; i < 4; i++) {
            armorButtons[i] = new Button(i, "");
            armorButtons[i].visible = false;
            add(armorButtons[i]);
        }
        updateItems();
    }

    protected void setupPositions() {
        bHeader.x = bHeader.y = 0;
        bHeader.width = width;
        btnClose.x = width - btnClose.width;
        btnClose.y = 0;
        if (width >= height) {
            int maxWidth = (int) (width / 1.8f) - BX - BX;
            int cols = Math.max(1, maxWidth / ITEM);
            int realWidth = cols * ITEM, paneWidth = realWidth + BX + BX, realBx = (paneWidth - realWidth) / 2;
            pane(realBx, 26 + BY, realWidth, height - BY - BY - 28);
            for (int i = 0; i < 4; i++) {
                armorButtons[i].x = paneWidth;
                armorButtons[i].y = pane.y + 24 * i;
            }
            int xx = paneWidth + 20, xw = width - xx;
            bgX = xx + xw / 10;
            bgW = xw - (xw / 10) * 2;
            bgY = pane.y;
            bgH = pane.h;
        } else {
            // портрет: слоты и игрок сверху, панель снизу
            int top = 26 + BY, h = Math.min(4 * 24, (height - top) / 2 - BY);
            for (int i = 0; i < 4; i++) {
                armorButtons[i].x = BX;
                armorButtons[i].y = top + 24 * i * h / (4 * 24);
            }
            bgX = BX + 30;
            bgW = width - bgX - BX;
            bgY = top;
            bgH = h;
            int cols = (width - BX - BX) / ITEM, realWidth = cols * ITEM, py = top + h + BY * 2;
            pane((width - realWidth) / 2, py, realWidth, height - py - BY);
        }
        for (int i = 0; i < 4; i++) armorButtons[i].width = armorButtons[i].height = 20;
    }

    private void pane(int x, int y, int w, int h) {
        if (pane == null) pane = new InventoryPane(this, x, y, w, h, ITEM, BORDER);
        else pane.setRect(x, y, w, h);
        pane.fillMarginX = pane.fillMarginY = 0;
        pane.renderCounts = false;
    }

    private void updateItems() {
        armorSlots.removeAllElements();
        Inventory inv = mc.player.inv;
        for (int i = 0; i < Inventory.SLOTS; i++) if (Item.isArmor(inv.id[i]) && inv.count[i] > 0) armorSlots.addElement(new Integer(i));
    }

    public boolean renderGameBehind() {
        return false;
    }

    public void tick() {
        pane.tick();
    }

    public void render(Graphics g) {
        R.nineBg.draw(g, 0, 0, width, height);
        super.render(g);
        pane.render(g, mc.partial);
        R.nineFrame.draw(g, pane.x - 1, pane.y - 1, pane.w + 2, pane.h + 2);
        R.ninePlayerBg.draw(g, bgX, bgY, bgW, bgH);
        renderPlayer(g, bgX + bgW / 2f, width >= height ? 0.85f * height : bgY + bgH * 0.9f, width >= height ? 1f : bgH / 96f);
        Player p = mc.player;
        for (int i = 0; i < 4; i++) {
            int x = armorButtons[i].x, y = armorButtons[i].y;
            R.nineSlot.draw(g, x, y, 20, 20);
            if (p.armor[i] != 0) {
                R.item(g, p.armor[i], 0, x + 2, y);
                R.decorations(g, p.armor[i], p.armorDmg[i], x + 2, y + 3);
            } else R.blit(g, R.items, x + 2, y, 15 * 16, i * 16, 16, 16);
        }
    }

    /** ArmorScreen::renderPlayer: 45 пикселей GUI на блок, лёгкое покачивание, медленная ходьба. */
    private void renderPlayer(Graphics g, float cxg, float feetYg, float scale) {
        EntityRenderer er = mc.er;
        if (er == null) return;
        int W = mc.W, H = mc.H;
        float cx = cxg * R.k, feetY = feetYg * R.k;
        Graphics3D g3 = Graphics3D.getInstance();
        try {
            g3.bindTarget(g, true, 0);
            g3.setViewport(0, 0, W, H);
            g3.clear(depthOnly);
            float k = 45f * R.k * Math.min(1f, scale); // пикселей экрана на блок
            cam.setParallel(H / k, (float) W / H, 1f, 40f);
            camT.setIdentity();
            camT.postTranslate(0, 0, 20f);
            g3.setCamera(cam, camT);
            g3.resetLights();
            // туман мира мог быть ещё не настроен (экран открыт до первого кадра) — игрок вне тумана
            mc.lr.fog.setLinear(100f, 200f);
            float t = (System.currentTimeMillis() - t0) / 1000f;
            float xd = 10 * Mth.sin(t), yd = 10 * Mth.cos(t * 0.05f);
            float xtan = atan(xd / 40.0f) * 20, ytan = atan(yd / 40.0f) * -20;
            base.setIdentity();
            base.postTranslate((cx - W / 2f) / k, (H / 2f - feetY) / k, 0);
            base.postRotate(ytan, 1, 0, 0);
            base.postRotate(180f + xtan, 0, 1, 0);
            float walkSpeed = 0.25f, walkPos = t * walkSpeed * 20;
            er.renderHumanoidWithArmor(g3, base, mc.player.armor, walkPos, walkSpeed, xtan * Mth.DEG, ytan * Mth.DEG, 0xFFFFFFFF);
        } catch (Throwable e) {
            Log.e("armor3d", e);
        } finally {
            g3.releaseTarget();
        }
    }

    private static float atan(float x) {
        return x - x * x * x / 3 + x * x * x * x * x / 5;
    }

    protected void mouseClicked(int x, int y) {
        if (pane.touchDown(x, y)) return;
        for (int i = 0; i < 4; i++) armorButtons[i].visible = true;
        super.mouseClicked(x, y);
        for (int i = 0; i < 4; i++) armorButtons[i].visible = false;
    }

    protected void mouseDragged(int x, int y) {
        pane.touchMove(x, y);
    }

    protected void mouseReleased(int x, int y) {
        pane.touchUp(x, y);
        for (int i = 0; i < 4; i++) armorButtons[i].visible = true;
        super.mouseReleased(x, y);
        for (int i = 0; i < 4; i++) armorButtons[i].visible = false;
    }

    protected void collectFocus(Vector v) {
        addPane(v, pane);
        for (int i = 0; i < 4; i++) {
            Button b = armorButtons[i];
            addFocus(v, b, 0, b.x, b.y, b.width, b.height);
        }
        addButtons(v);
    }

    protected void buttonClicked(Button b) {
        if (b == btnClose) {
            mc.setScreen(null);
            return;
        }
        int t = b.id;
        if (t < 0 || t > 3) return;
        // takeAndClearSlot
        Player p = mc.player;
        if (p.armor[t] == 0) return;
        int left = p.inv.add(p.armor[t], 1, p.armorDmg[t]);
        if (left > 0) drop(p.armor[t], p.armorDmg[t]);
        p.armor[t] = 0;
        p.armorDmg[t] = 0;
        updateItems();
    }

    private void drop(int id, int dmg) {
        Player p = mc.player;
        mc.level.addEntity(new ItemEntity(mc.level, p.x, p.y + 1f, p.z, id, 1, dmg));
    }

    // ---- InventoryPane.Callback ----

    private int slot(int i) {
        return ((Integer) armorSlots.elementAt(i)).intValue();
    }

    public int paneCount(InventoryPane p) {
        return armorSlots.size();
    }

    public int paneId(InventoryPane p, int i) {
        return mc.player.inv.id[slot(i)];
    }

    public int paneAux(InventoryPane p, int i) {
        return mc.player.inv.aux[slot(i)];
    }

    public int paneCountOf(InventoryPane p, int i) {
        return mc.player.inv.count[slot(i)];
    }

    public boolean isAllowed(InventoryPane p, int i) {
        return true;
    }

    /** ArmorScreen::addItem: надеть, прежнюю броню — в инвентарь. */
    public boolean addItem(InventoryPane p, int i) {
        Player pl = mc.player;
        Inventory inv = pl.inv;
        int sl = slot(i);
        int id = inv.id[sl], dmg = inv.aux[sl];
        if (!Item.isArmor(id)) return false;
        int s = Item.armorSlot(id);
        int oldId = pl.armor[s], oldDmg = pl.armorDmg[s];
        pl.armor[s] = id;
        pl.armorDmg[s] = dmg;
        inv.id[sl] = 0;
        inv.count[sl] = 0;
        inv.aux[sl] = 0;
        if (oldId != 0 && inv.add(oldId, 1, oldDmg) > 0) drop(oldId, oldDmg);
        updateItems();
        return true;
    }
}
