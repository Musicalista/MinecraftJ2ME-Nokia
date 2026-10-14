import javax.microedition.lcdui.Graphics;

/**
 * FurnaceScreen 0.6.1: шапка «Furnace», слева панель с топливом и тем, что можно переплавить, справа слоты
 * «вход» / «топливо» / «результат», огонь и стрелка. Касание слота печи выбирает его, повторное — забирает
 * содержимое; касание предмета в панели кладёт его в выбранный слот (удержание — всю стопку).
 * В портрете слоты сверху, панель снизу.
 */
public final class FurnaceScreen extends Screen implements InventoryPane.Callback {
    private static final int ITEM = 32, BORDER = 4, BX = 10, BY = 6, MIN_CHARGE = 200;
    private static final int SLOT_INGREDIENT = 0, SLOT_FUEL = 1, SLOT_RESULT = 2;
    private final TileEntity furnace;
    private final THeader bHeader = new THeader(5, "Furnace");
    private final ImageButton btnClose = closeButton(4);
    private final Button btnIngredient = new Button(SLOT_INGREDIENT, ""), btnFuel = new Button(SLOT_FUEL, ""), btnResult = new Button(SLOT_RESULT, "");
    private InventoryPane pane;
    private int selectedSlot = SLOT_INGREDIENT;
    private int descWidth = 90, holdOnRelease = -1;
    private int[] slots = new int[0]; // индексы инвентаря в панели

    public FurnaceScreen(TileEntity furnace) {
        this.furnace = furnace;
    }

    protected void init() {
        btnIngredient.visible = btnFuel.visible = btnResult.visible = false;
        add(bHeader);
        add(btnIngredient);
        add(btnFuel);
        add(btnResult);
        add(btnClose);
        updateItems();
    }

    protected void setupPositions() {
        bHeader.x = bHeader.y = 0;
        bHeader.width = width;
        btnClose.x = width - btnClose.width;
        btnClose.y = 0;
        btnIngredient.width = btnFuel.width = btnResult.width = 32;
        btnIngredient.height = btnFuel.height = btnResult.height = 32;
        int cols = (width / 2 - BX - BX) / ITEM;
        int by = 36;
        if (cols >= 3) {
            int realWidth = cols * ITEM;
            int px = BX, py = 26 + BY, ph = height - BY - BY - 28;
            pane = pane(px, py, realWidth, ph);
            int cx = px + realWidth, rightWidth = width - cx;
            int margin = (rightWidth - (2 * 32 + 40)) / 2;
            btnIngredient.x = btnFuel.x = cx + margin;
            btnResult.x = width - margin - 32;
        } else {
            int margin = (width - (2 * 32 + 40)) / 2;
            btnIngredient.x = btnFuel.x = margin;
            btnResult.x = width - margin - 32;
            cols = (width - BX - BX) / ITEM;
            int realWidth = cols * ITEM, py = by + 20 + 64 + 14;
            pane = pane((width - realWidth) / 2, py, realWidth, height - py - BY);
        }
        btnIngredient.y = by;
        btnFuel.y = by + 20 + 32;
        btnResult.y = (btnIngredient.y + btnFuel.y) / 2;
        descWidth = Math.min(90, width - btnResult.x + 24 - 4);
    }

    private InventoryPane pane(int x, int y, int w, int h) {
        if (pane == null) pane = new InventoryPane(this, x, y, w, h, ITEM, BORDER);
        else pane.setRect(x, y, w, h);
        pane.fillMarginX = pane.fillMarginY = 0;
        return pane;
    }

    /** FurnaceScreen::updateItems: в панели только топливо и то, что переплавляется. */
    private void updateItems() {
        Inventory inv = mc.player.inv;
        int n = 0;
        int[] tmp = new int[Inventory.SLOTS];
        for (int i = 0; i < Inventory.SLOTS; i++) {
            int id = inv.id[i];
            if (id == 0) continue;
            if (TileEntity.fuel(id) > 0 || TileEntity.smeltResult(id, inv.aux[i]) != 0) tmp[n++] = i;
        }
        slots = new int[n];
        System.arraycopy(tmp, 0, slots, 0, n);
    }

    public boolean renderGameBehind() {
        return false;
    }

    public boolean pausesGame() {
        return false;
    }

    public void tick() {
        pane.tick();
    }

    public void render(Graphics g) {
        R.nineBg.draw(g, 0, 0, width, height);
        super.render(g);
        // удержание — зарядка стопки
        pane.markerIndex = -1;
        int ms = pane.holdMs(), hi = pane.heldItem();
        if (ms >= 0 && hi >= 0 && paneCountOf(pane, hi) > 1 && canMove(hi)) {
            float share = (ms - MIN_CHARGE) / (700f + 10 * paneCountOf(pane, hi));
            pane.markerIndex = hi;
            pane.markerShare = Math.max(share, 0);
            if (share >= 1) {
                moveOver(hi, 100, ms);
                pane.cancelTouch();
            }
        }
        pane.render(g, mc.partial);
        R.nineFrame.draw(g, pane.x - 1, pane.y - 1, pane.w + 2, pane.h + 2);
        slot(g, SLOT_INGREDIENT, btnIngredient);
        slot(g, SLOT_FUEL, btnFuel);
        slot(g, SLOT_RESULT, btnResult);
        // результат-призрак и описание
        int res = furnace.id[SLOT_RESULT] != 0 ? furnace.id[SLOT_RESULT] | (furnace.aux[SLOT_RESULT] << 16)
                : TileEntity.smeltResult(furnace.id[SLOT_INGREDIENT], furnace.aux[SLOT_INGREDIENT]);
        if (res != 0) {
            int rid = res & 0xFFFF, raux = res >>> 16;
            if (furnace.id[SLOT_RESULT] == 0) R.item(g, rid, raux, btnResult.x + 7, btnResult.y + 8, R.GHOST);
            R.wrapped(g, Lang.description(rid, raux), btnResult.x - 24, btnResult.y + btnResult.height + 6, descWidth, 0xF0F0F0, false);
        }
        // огонь (spritesheet 40,20 / 56,20) и стрелка (72,20 / 72,36)
        int yy = btnResult.y + 8;
        int fpx = (furnace.litDuration > 0 ? furnace.burnTime * 14 / furnace.litDuration : 0) + 2;
        if (furnace.burnTime <= 0) fpx = 0;
        int xx0 = btnIngredient.x + 8;
        R.blit(g, R.sheet, xx0, yy, 40, 20, 16, 16);
        if (fpx > 0) R.blit(g, R.sheet, xx0, yy + 16 - fpx, 56, 20 + 16 - fpx, 16, fpx);
        int bpx = furnace.cookTime * 24 / 200;
        int xx1 = btnIngredient.x + 40;
        R.blit(g, R.sheet, xx1, yy, 72, 20, 24, 16);
        if (bpx > 0) R.blit(g, R.sheet, xx1, yy, 72, 36, bpx, 16);
    }

    /** FurnaceScreen::drawSlotItemAt. */
    private void slot(Graphics g, int s, Button b) {
        R.nineSlot.draw(g, b.x, b.y, 32, 32);
        if (s == selectedSlot) R.nineSlotMarker.draw(g, b.x - 2, b.y - 2, 36, 36);
        if (furnace.id[s] != 0 && furnace.count[s] > 0) {
            R.item(g, furnace.id[s], furnace.aux[s], b.x + 7, b.y + 8);
            if (furnace.count[s] > 1) R.shadow(g, R.countText(furnace.count[s]), b.x + 3, b.y + 3, 0xCCCCCC);
        }
    }

    protected void mouseClicked(int x, int y) {
        if (!pane.touchDown(x, y)) {
            // невидимые кнопки слотов
            btnIngredient.visible = btnFuel.visible = btnResult.visible = true;
            super.mouseClicked(x, y);
            btnIngredient.visible = btnFuel.visible = btnResult.visible = false;
        }
    }

    protected void mouseDragged(int x, int y) {
        pane.touchMove(x, y);
    }

    protected void mouseReleased(int x, int y) {
        holdOnRelease = pane.holdMs();
        pane.touchUp(x, y);
        btnIngredient.visible = btnFuel.visible = btnResult.visible = true;
        super.mouseReleased(x, y);
        btnIngredient.visible = btnFuel.visible = btnResult.visible = false;
    }

    protected void collectFocus(java.util.Vector v) {
        Button[] sl = { btnIngredient, btnFuel, btnResult };
        for (int i = 0; i < 3; i++) addFocus(v, sl[i], 0, sl[i].x, sl[i].y, sl[i].width, sl[i].height);
        addPane(v, pane);
        addButtons(v);
    }

    protected void focusActivate(Object o, int i) {
        if (o instanceof InventoryPane) holdOnRelease = ((InventoryPane) o).holdMs();
        super.focusActivate(o, i);
    }

    protected void buttonClicked(Button b) {
        if (b == btnClose) {
            mc.setScreen(null);
            return;
        }
        int slot = b.id;
        if (b == btnIngredient || b == btnFuel || b == btnResult) {
            int old = selectedSlot;
            if (slot != SLOT_RESULT) selectedSlot = slot;
            if (old == selectedSlot) takeAndClearSlot(slot);
        }
    }

    /** Забрать содержимое слота печи в инвентарь (остаток — выбросить). */
    private void takeAndClearSlot(int s) {
        if (furnace.id[s] == 0) return;
        int left = mc.player.inv.add(furnace.id[s], furnace.count[s], furnace.aux[s]);
        if (left > 0) {
            Player p = mc.player;
            mc.level.addEntity(new ItemEntity(mc.level, p.x, p.y + 1f, p.z, furnace.id[s], left, furnace.aux[s]));
        }
        furnace.id[s] = 0;
        furnace.count[s] = 0;
        furnace.aux[s] = 0;
        changed();
        updateItems();
    }

    private void changed() {
        mc.level.unsaved[(furnace.x >> 4) | ((furnace.z >> 4) << 4)] = true;
    }

    private boolean canMove(int i) {
        if (!isAllowed(pane, i)) return false;
        Inventory inv = mc.player.inv;
        int s = slots[i];
        int fid = furnace.id[selectedSlot];
        if (fid == 0) return true;
        int m = Item.maxStack[fid] <= 0 ? 64 : Item.maxStack[fid];
        return fid == inv.id[s] && furnace.aux[selectedSlot] == inv.aux[s] && furnace.count[selectedSlot] < m;
    }

    /** FurnaceScreen::moveOver: доля стопки (минимум один) — в выбранный слот печи. */
    private boolean moveOver(int i, int percent, int heldMs) {
        if (!canMove(i)) return false;
        Inventory inv = mc.player.inv;
        int s = slots[i];
        int id = inv.id[s], aux = inv.aux[s];
        int wanted = inv.count[s] * percent / 100;
        if (wanted == 0 || heldMs < MIN_CHARGE) wanted = 1;
        int m = Item.maxStack[id] <= 0 ? 64 : Item.maxStack[id];
        wanted = Math.min(wanted, m - furnace.count[selectedSlot]);
        if (wanted <= 0) return false;
        furnace.id[selectedSlot] = id;
        furnace.aux[selectedSlot] = aux;
        furnace.count[selectedSlot] += wanted;
        inv.count[s] -= wanted;
        if (inv.count[s] <= 0) { inv.id[s] = 0; inv.count[s] = 0; inv.aux[s] = 0; updateItems(); }
        changed();
        return true;
    }

    // ---- InventoryPane.Callback ----

    public int paneCount(InventoryPane p) {
        return slots.length;
    }

    public int paneId(InventoryPane p, int i) {
        return mc.player.inv.id[slots[i]];
    }

    public int paneAux(InventoryPane p, int i) {
        return mc.player.inv.aux[slots[i]];
    }

    public int paneCountOf(InventoryPane p, int i) {
        return mc.player.inv.count[slots[i]];
    }

    public boolean isAllowed(InventoryPane p, int i) {
        if (i >= slots.length) return false;
        Inventory inv = mc.player.inv;
        int s = slots[i];
        if (selectedSlot == SLOT_FUEL) return TileEntity.fuel(inv.id[s]) > 0;
        if (selectedSlot == SLOT_INGREDIENT) return TileEntity.smeltResult(inv.id[s], inv.aux[s]) != 0;
        return false;
    }

    public boolean addItem(InventoryPane p, int i) {
        int ms = holdOnRelease, cnt = paneCountOf(p, i);
        int percent = ms < MIN_CHARGE ? 0 : (int) Mth.clamp(100f * (ms - MIN_CHARGE) / (700f + 10 * cnt), 0, 100);
        return moveOver(i, percent, ms);
    }

    /** LocalPlayer::closeContainer: у клиента сети — ContainerClosePacket */
    public void removed() {
        if (Net.client != null) Net.client.containerClosed(furnace);
    }
}
