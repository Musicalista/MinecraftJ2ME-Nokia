import javax.microedition.lcdui.Graphics;

/**
 * Touch::IngameBlockSelectionScreen 0.6.1: шапка «Craft» / «Armor» (в выживании) / «Select blocks» / крестик,
 * панель ячеек в рамке gui/itemframe.png. Касание предмета ставит его в первый слот хотбара (со сдвигом),
 * экран остаётся открытым; хотбар виден под рамкой.
 */
public final class InventoryScreen extends Screen implements InventoryPane.Callback {
    private static final int BX = 10, BY = 6, ITEM = 32, BORDER = 4;
    private final TButton bCraft = new TButton(1, "Craft"), bArmor = new TButton(4, "Armor");
    private final THeader bHeader = new THeader(0, "Select blocks");
    private final ImageButton bDone = closeButton(3);
    private InventoryPane pane;
    private int[] ids, auxs;

    /** Творческий инвентарь 0.6.1 — порядок Inventory::setupDefault() релиза (сверено с libminecraftpe.so). */
    static final int[] CREATIVE = {
            Tile.COBBLE, 0, Tile.STONE_BRICK, 0, Tile.STONE_BRICK, 1, Tile.STONE_BRICK, 2, Tile.MOSSY, 0,
            Tile.PLANKS, 0, Tile.BRICK, 0, Tile.STONE, 0, Tile.DIRT, 0, Tile.GRASS, 0, Tile.CLAY, 0,
            Tile.SANDSTONE, 0, Tile.SANDSTONE, 1, Tile.SANDSTONE, 2, Tile.SAND, 0, Tile.GRAVEL, 0,
            Tile.LOG, 0, Tile.LOG, 1, Tile.LOG, 2, Tile.NETHER_BRICK, 0, Tile.NETHERRACK, 0,
            Tile.STONE_STAIRS, 0, Tile.WOOD_STAIRS, 0, Tile.BRICK_STAIRS, 0, Tile.SANDSTONE_STAIRS, 0,
            Tile.STONE_BRICK_STAIRS, 0, Tile.NETHER_BRICK_STAIRS, 0, Tile.QUARTZ_STAIRS, 0,
            Tile.SLAB, 0, Tile.SLAB, 3, Tile.SLAB, 2, Tile.SLAB, 4, Tile.SLAB, 1, Tile.SLAB, 5,
            Tile.QUARTZ, 0, Tile.QUARTZ, 2, Tile.QUARTZ, 1,
            Tile.COAL_ORE, 0, Tile.IRON_ORE, 0, Tile.GOLD_ORE, 0, Tile.DIAMOND_ORE, 0, Tile.LAPIS_ORE, 0, Tile.REDSTONE_ORE, 0,
            Tile.GOLD_BLOCK, 0, Tile.IRON_BLOCK, 0, Tile.DIAMOND_BLOCK, 0, Tile.LAPIS_BLOCK, 0, Tile.OBSIDIAN, 0,
            Tile.SNOW, 0, Tile.GLASS, 0, Tile.GLOWSTONE, 0, Tile.NETHER_REACTOR, 0,
            Tile.WOOL, 0, Tile.WOOL, 7, Tile.WOOL, 6, Tile.WOOL, 5, Tile.WOOL, 4, Tile.WOOL, 3, Tile.WOOL, 2, Tile.WOOL, 1,
            Tile.WOOL, 15, Tile.WOOL, 14, Tile.WOOL, 13, Tile.WOOL, 12, Tile.WOOL, 11, Tile.WOOL, 10, Tile.WOOL, 9, Tile.WOOL, 8,
            Tile.LADDER, 0, Tile.TORCH, 0, Tile.GLASS_PANE, 0, 324, 0, Tile.TRAPDOOR, 0, Tile.FENCE, 0, Tile.FENCE_GATE, 0,
            355, 0, Tile.BOOKSHELF, 0, 321, 0, Tile.WORKBENCH, 0, Tile.STONECUTTER, 0, Tile.CHEST, 0, Tile.FURNACE, 0, Tile.TNT, 0,
            Tile.FLOWER, 0, Tile.ROSE, 0, Tile.BROWN_MUSHROOM, 0, Tile.RED_MUSHROOM, 0, Tile.CACTUS, 0, Tile.MELON, 0, 338, 0,
            Tile.SAPLING, 0, Tile.SAPLING, 1, Tile.SAPLING, 2, Tile.LEAVES, 0, Tile.LEAVES, 1, Tile.LEAVES, 2,
            295, 0, 362, 0, 351, 15, 292, 0, 267, 0, 261, 0, 323, 0
    };

    private boolean creative() {
        return mc.player.inv.creative;
    }

    protected void init() {
        if (creative()) {
            int n = CREATIVE.length / 2;
            ids = new int[n];
            auxs = new int[n];
            for (int i = 0; i < n; i++) { ids[i] = CREATIVE[i * 2]; auxs[i] = CREATIVE[i * 2 + 1]; }
        }
        add(bHeader);
        add(bDone);
        if (!creative()) {
            add(bCraft);
            add(bArmor);
        }
    }

    protected void setupPositions() {
        int cols = (width - BX - BX) / ITEM;
        int realWidth = cols * ITEM, realBx = (width - realWidth) / 2;
        int top = 24 + BY, h = height - BY - BY - 20 - 24;
        if (pane == null) pane = new InventoryPane(this, realBx, top, realWidth, h, ITEM, BORDER);
        else pane.setRect(realBx, top, realWidth, h);
        pane.fillMarginX = realBx;
        pane.renderCounts = !creative();
        if (width < 140) bHeader.msg = "Inv"; else bHeader.msg = "Select blocks";
        bHeader.y = bDone.y = bCraft.y = bArmor.y = 0;
        bDone.x = width - bDone.width;
        bCraft.x = 0;
        bCraft.width = bArmor.width = 48;
        // узкий экран (176 пикселей GUI): «Select blocks» не помещается между Armor и крестиком — кнопки уже
        if (!creative()) {
            int need = R.width(bHeader.msg) + 8 + bDone.width;
            if (width - 2 * 48 < need) bCraft.width = bArmor.width = Math.max(R.width(bArmor.msg) + 8, (width - need) / 2);
        }
        bArmor.x = bCraft.width;
        if (creative()) {
            bHeader.x = 0;
            bHeader.width = width;
            bHeader.xText = width / 2;
        } else {
            bHeader.x = bCraft.width + bArmor.width;
            bHeader.width = width - bCraft.width - bArmor.width;
            bHeader.xText = bHeader.x + (bHeader.width - bDone.width) / 2;
        }
    }

    public void tick() {
        pane.tick();
    }

    public void render(Graphics g) {
        super.render(g);
        pane.render(g, mc.partial);
        // рамка: gui/itemframe.png (215x128 текселей), растянута на ширину экрана
        R.blit(g, R.frame, 0, pane.y - BY, width, pane.h + BY + BY, 0, 0, 215, 128);
    }

    protected void mouseClicked(int x, int y) {
        if (!pane.touchDown(x, y)) super.mouseClicked(x, y);
    }

    protected void mouseDragged(int x, int y) {
        pane.touchMove(x, y);
    }

    protected void mouseReleased(int x, int y) {
        pane.touchUp(x, y);
        super.mouseReleased(x, y);
    }

    protected void buttonClicked(Button b) {
        if (b == bDone) mc.setScreen(null);
        if (b == bCraft) mc.setScreen(new CraftScreen(CraftScreen.HAND));
        if (b == bArmor) mc.setScreen(new ArmorScreen());
    }

    protected void collectFocus(java.util.Vector v) {
        addPane(v, pane);
        addButtons(v);
    }

    // ---- InventoryPane.Callback ----

    public int paneCount(InventoryPane p) {
        return creative() ? ids.length : Inventory.SLOTS;
    }

    public int paneId(InventoryPane p, int i) {
        return creative() ? ids[i] : mc.player.inv.id[i];
    }

    public int paneAux(InventoryPane p, int i) {
        return creative() ? auxs[i] : mc.player.inv.aux[i];
    }

    public int paneCountOf(InventoryPane p, int i) {
        return creative() ? 1 : mc.player.inv.count[i];
    }

    public boolean isAllowed(InventoryPane p, int i) {
        return true;
    }

    /** IngameBlockSelectionScreen::addItem: в слот 0 хотбара со сдвигом, выбрать его, звук и вспышка. */
    public boolean addItem(InventoryPane p, int i) {
        Inventory inv = mc.player.inv;
        if (creative()) inv.moveToFirstSlotCreative(ids[i], auxs[i]);
        else {
            if (inv.id[i] == 0) return false;
            inv.moveToFirstSlot(i);
        }
        inv.selected = 0;
        mc.playUI("random.pop2");
        mc.gui.flashSlot(inv.selected);
        return true;
    }
}
