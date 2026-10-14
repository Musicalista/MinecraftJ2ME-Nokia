import java.util.Vector;
import javax.microedition.lcdui.Graphics;

/**
 * ChestScreen 0.6.1: шапки «Inventory» / «Chest», две панели ячеек. Касание переносит один предмет в другую
 * панель, удержание «заряжает» стопку (зелёная полоска) и переносит её целиком. В портрете панели одна под другой.
 */
public final class ChestScreen extends Screen implements InventoryPane.Callback {
    private static final int ITEM = 32, BORDER = 4, BX = 10, BY = 6, MIN_CHARGE = 200;
    private final TileEntity chest;
    private final THeader bHeader = new THeader(5, "Inventory"), bHeaderChest = new THeader(6, "Chest");
    private final ImageButton btnClose = closeButton(4);
    private InventoryPane invPane, chestPane;
    private final Vector flying = new Vector(); // {id, aux, sx, sy, dx, dy, t0 - base}
    private final long base = System.currentTimeMillis();

    public ChestScreen(TileEntity chest) {
        this.chest = chest;
    }

    protected void init() {
        add(bHeader);
        add(bHeaderChest);
        add(btnClose);
    }

    protected void setupPositions() {
        bHeader.x = 0;
        bHeader.y = bHeaderChest.y = 0;
        bHeader.width = bHeaderChest.width = width / 2;
        bHeaderChest.x = bHeader.width;
        btnClose.x = width - btnClose.width;
        btnClose.y = 0;
        int cols = (width / 2 - BX / 2) / ITEM;
        int top = 24 + BY;
        if (cols >= 3) {
            int realWidth = cols * ITEM, realBx = (width / 2 - realWidth) / 2, h = height - BY - BY - 24;
            invPane = pane(invPane, realBx, top, realWidth, h);
            chestPane = pane(chestPane, realBx + width / 2, top, realWidth, h);
        } else {
            // портрет: инвентарь сверху, сундук снизу, на всю ширину
            cols = (width - BX - BX) / ITEM;
            int realWidth = cols * ITEM, realBx = (width - realWidth) / 2;
            int h = (height - top - BY * 3) / 2;
            invPane = pane(invPane, realBx, top, realWidth, h);
            chestPane = pane(chestPane, realBx, top + h + BY * 2, realWidth, h);
        }
    }

    private InventoryPane pane(InventoryPane p, int x, int y, int w, int h) {
        if (p == null) p = new InventoryPane(this, x, y, w, h, ITEM, BORDER);
        else p.setRect(x, y, w, h);
        p.fillMarginX = p.fillMarginY = 0;
        return p;
    }

    public boolean renderGameBehind() {
        return false;
    }

    public boolean pausesGame() {
        return false;
    }

    public void tick() {
        invPane.tick();
        chestPane.tick();
    }

    public void render(Graphics g) {
        R.nineBg.draw(g, 0, 0, width, height);
        super.render(g);
        renderPane(g, invPane);
        renderPane(g, chestPane);
        // летящие предметы (0.3 с, квадратичное ускорение)
        long now = System.currentTimeMillis();
        for (int i = flying.size() - 1; i >= 0; i--) {
            float[] f = (float[]) flying.elementAt(i);
            float t = (now - base - f[6]) / 300f;
            if (t > 1) { flying.removeElementAt(i); continue; }
            t *= t;
            R.item(g, (int) f[0], (int) f[1], f[2] + (f[4] - f[2]) * t + 7, f[3] + (f[5] - f[3]) * t + 8, R.GHOST);
        }
    }

    private void renderPane(Graphics g, InventoryPane p) {
        // ChestScreen::handleRenderPane: удержание заряжает перенос стопки
        p.markerIndex = -1;
        int ms = p.holdMs(), i = p.heldItem();
        if (ms >= 0 && i >= 0) {
            int cnt = paneCountOf(p, i);
            if (cnt > 1) {
                float share = (ms - MIN_CHARGE) / (700f + 10 * cnt);
                p.markerIndex = i;
                p.markerShare = Math.max(share, 0);
                if (share >= 1) {
                    transfer(p, i, 100, ms);
                    p.cancelTouch();
                }
            }
        }
        p.render(g, mc.partial);
        R.nineFrame.draw(g, p.x - 1, p.y - 1, p.w + 2, p.h + 2);
    }

    protected void mouseClicked(int x, int y) {
        if (!invPane.touchDown(x, y) && !chestPane.touchDown(x, y)) super.mouseClicked(x, y);
    }

    protected void mouseDragged(int x, int y) {
        invPane.touchMove(x, y);
        chestPane.touchMove(x, y);
    }

    protected void mouseReleased(int x, int y) {
        holdOnRelease = Math.max(invPane.holdMs(), chestPane.holdMs());
        invPane.touchUp(x, y);
        chestPane.touchUp(x, y);
        super.mouseReleased(x, y);
    }

    private int holdOnRelease = -1;

    protected void collectFocus(Vector v) {
        addPane(v, invPane);
        addPane(v, chestPane);
        addButtons(v);
    }

    protected void focusActivate(Object o, int i) {
        if (o instanceof InventoryPane) holdOnRelease = ((InventoryPane) o).holdMs();
        super.focusActivate(o, i);
    }

    protected void buttonClicked(Button b) {
        if (b == btnClose) mc.setScreen(null);
    }

    // ---- InventoryPane.Callback ----

    public int paneCount(InventoryPane p) {
        return p == invPane ? Inventory.SLOTS : chest.id.length;
    }

    public int paneId(InventoryPane p, int i) {
        return p == invPane ? mc.player.inv.id[i] : chest.id[i];
    }

    public int paneAux(InventoryPane p, int i) {
        return p == invPane ? mc.player.inv.aux[i] : chest.aux[i];
    }

    public int paneCountOf(InventoryPane p, int i) {
        return p == invPane ? mc.player.inv.count[i] : chest.count[i];
    }

    public boolean isAllowed(InventoryPane p, int i) {
        return true;
    }

    public boolean addItem(InventoryPane p, int i) {
        int ms = holdOnRelease;
        int cnt = paneCountOf(p, i);
        int percent = ms < MIN_CHARGE ? 0 : (int) Mth.clamp(100f * (ms - MIN_CHARGE) / (700f + 10 * cnt), 0, 100);
        return transfer(p, i, percent, ms);
    }

    /** ChestScreen::handleAddItem: перенести долю стопки (минимум один предмет) в другую панель. */
    private boolean transfer(InventoryPane from, int i, int percent, int heldMs) {
        Inventory inv = mc.player.inv;
        boolean fromChest = from == chestPane;
        int[] sId = fromChest ? chest.id : inv.id, sCnt = fromChest ? chest.count : inv.count, sAux = fromChest ? chest.aux : inv.aux;
        int id = sId[i], aux = sAux[i], cnt = sCnt[i];
        if (id == 0 || cnt <= 0) return false;
        int wanted = cnt * percent / 100;
        if (wanted == 0 || heldMs < MIN_CHARGE) wanted = 1;
        InventoryPane to = fromChest ? invPane : chestPane;
        int target = fromChest ? findTarget(inv.id, inv.count, inv.aux, id, aux) : findTarget(chest.id, chest.count, chest.aux, id, aux);
        int left = fromChest ? inv.add(id, wanted, aux) : addToChest(id, wanted, aux);
        int moved = wanted - left;
        if (moved <= 0) return false;
        sCnt[i] -= moved;
        if (sCnt[i] <= 0) { sId[i] = 0; sCnt[i] = 0; sAux[i] = 0; }
        if (target >= 0)
            flying.addElement(new float[] { id, aux, from.itemX(i), from.itemY(i), to.itemX(target), to.itemY(target), System.currentTimeMillis() - base });
        mc.level.unsaved[(chest.x >> 4) | ((chest.z >> 4) << 4)] = true;
        return true;
    }

    private static int max(int id) {
        return Item.maxStack[id] <= 0 ? 64 : Item.maxStack[id];
    }

    private static int findTarget(int[] ids, int[] cnt, int[] aux, int id, int a) {
        for (int k = 0; k < ids.length; k++) if (ids[k] == id && aux[k] == a && cnt[k] < max(id)) return k;
        for (int k = 0; k < ids.length; k++) if (ids[k] == 0) return k;
        return -1;
    }

    /** FillingContainer::add для сундука: дополнить стопки, затем пустые ячейки; возвращает остаток. */
    private int addToChest(int id, int n, int a) {
        int m = max(id);
        for (int k = 0; k < chest.id.length && n > 0; k++) {
            if (chest.id[k] == id && chest.aux[k] == a && chest.count[k] < m) {
                int d = Math.min(n, m - chest.count[k]);
                chest.count[k] += d;
                n -= d;
            }
        }
        for (int k = 0; k < chest.id.length && n > 0; k++) {
            if (chest.id[k] == 0) {
                int d = Math.min(n, m);
                chest.id[k] = id;
                chest.aux[k] = a;
                chest.count[k] = d;
                n -= d;
            }
        }
        return n;
    }

    /** LocalPlayer::closeContainer: у клиента сети — ContainerClosePacket */
    public void removed() {
        if (Net.client != null) Net.client.containerClosed(chest);
    }
}
