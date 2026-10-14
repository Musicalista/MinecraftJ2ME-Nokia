import java.util.Calendar;
import java.util.Date;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;

/**
 * Touch::SelectWorldScreen 0.6.1: сверху «Back» / «Select world» / «Create new», лента миров
 * (TouchWorldSelectionList — RolledSelectionListH с доводкой к центру), внизу крестик удаления.
 * Касание соседнего мира — прокрутка к нему, касание выбранного — играть; последний пункт — «Create new».
 */
public final class SelectWorldScreen extends Screen {
    private final TButton bBack = new TButton(3, "Back"), bCreate = new TButton(2, "Create new");
    private final THeader bHeader = new THeader(0, "Select world");
    private final ImageButton bDelete = new ImageButton(1, "");
    private Vector levels;
    private String[][] desc;

    // RolledSelectionListH
    private static final int ITEM_W = 120, HALF_H = 48;
    private int y0, y1;
    private float xo, xoo, xInertia, lastXoo, xinertia;
    private int selectedItem = -1;
    private int dragState = -1; // -1 нет, 0 палец на ленте
    private float xDrag;
    private long lastSelectionTime;
    private int lastSelection = -1, selectionX;
    private boolean newWorldSelected, mouseHasBeenUp;
    // твин доводки (quadraticInOut)
    private int mode;
    private float tdStart, tdStop, tdCur, tdDur;

    protected void init() {
        levels = Storage.listWorlds();
        desc = new String[levels.size()][];
        for (int i = 0; i < levels.size(); i++) {
            Storage.WorldInfo w = (Storage.WorldInfo) levels.elementAt(i);
            desc[i] = new String[] { w.name, date(w.lastPlayed), w.folder, w.gameType == 1 ? "Creative" : "Survival" };
        }
        bDelete.setImageDef(new ImageDef(R.touch, 150, 0, 34, 26, 34, 26), true);
        bDelete.active = false;
        add(bDelete);
        add(bCreate);
        add(bBack);
        add(bHeader);
        xo = xoo = lastXoo = (ITEM_W - width) * 0.5f;
        selectedItem = levels.size() > 0 ? 0 : -1;
        if (selectedItem < 0) selectedItem = 0;
        mouseHasBeenUp = !touching;
    }

    protected void setupPositions() {
        boolean narrow = width < 2 * bBack.width + R.width(bHeader.msg) + 12;
        if (!narrow) {
            bBack.x = 0;
            bBack.y = bCreate.y = bHeader.y = 0;
            bCreate.x = width - bCreate.width;
            bHeader.x = bBack.width;
            bHeader.width = width - (bBack.width + bCreate.width);
            bHeader.height = bCreate.height;
            y0 = 24;
        } else {
            // портрет: заголовок отдельной строкой, кнопки под ним
            bHeader.x = 0;
            bHeader.y = 0;
            bHeader.width = width;
            bHeader.height = 26;
            bBack.y = bCreate.y = 26;
            bBack.x = 0;
            bCreate.x = width - bCreate.width;
            y0 = 50;
        }
        bDelete.x = (width - bDelete.width) / 2;
        bDelete.y = height - 30;
        y1 = height - 32;
        if (mode == 0) xo = xoo = lastXoo = (ITEM_W - width) * 0.5f + Math.max(0, selectedItem) * ITEM_W;
    }

    /** AppPlatform::getDateString (Android, формат устройства). */
    private static String date(long sec) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(sec * 1000L));
        return two(c.get(Calendar.DAY_OF_MONTH)) + "." + two(c.get(Calendar.MONTH) + 1) + "." + c.get(Calendar.YEAR) + " "
                + two(c.get(Calendar.HOUR_OF_DAY)) + ":" + two(c.get(Calendar.MINUTE));
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private int count() {
        return levels.size() + 1;
    }

    private int rowY() {
        return height / 2 - HALF_H + 8;
    }

    private int itemAt(int x, int y) {
        if (y < y0 || y > y1) return -1;
        return itemAtRaw((int) (x + (int) xo - 4));
    }

    private int itemAtRaw(int x) {
        int slot = x / ITEM_W;
        return slot >= 0 && x >= 0 && slot < count() ? slot : -1;
    }

    private boolean capX() {
        float min = (ITEM_W - width) / 2f, max = min + (count() - 1) * ITEM_W;
        if (xo < min) { xo = min; xInertia = 0; mode = 0; return true; }
        if (xo > max) { xo = max; xInertia = 0; mode = 0; return true; }
        return false;
    }

    private static float quadraticInOut(float t, float dur, float start, float stop) {
        float delta = stop - start, T = (t / dur) * 2f;
        if (T < 1) return 0.5f * delta * T * T + start;
        return -0.5f * delta * ((T - 1) * (T - 3) - 1) + start;
    }

    private void tweenInited() {
        float a = quadraticInOut(tdCur, tdDur, tdStart, tdStop), b = quadraticInOut(tdCur + 1, tdDur, tdStart, tdStop);
        xinertia = 0;
        xInertia = a - b;
    }

    private void step(int dir) {
        int xoffset = (int) (xo - (selectedItem * ITEM_W + (ITEM_W - width) * 0.5f));
        tdStart = xo;
        tdStop = xo + dir * ITEM_W - xoffset;
        tdCur = 0;
        tdDur = 8;
        mode = 1;
        tweenInited();
    }

    public void tick() {
        // RolledSelectionListH::tick
        xinertia = lastXoo - xoo;
        lastXoo = xoo;
        xoo = xo - xInertia;
        // TouchWorldSelectionList::tick
        if (touching || dragState == 0) return;
        selectedItem = -1;
        if (mode == 1) {
            if (++tdCur == tdDur) {
                mode = 0;
                xInertia = 0;
                xoo = xo = tdStop;
                selectedItem = itemAt(width / 2, height / 2);
            } else tweenInited();
        } else {
            float speed = Math.abs(xInertia);
            if (speed >= 5f) xInertia *= 0.9f;
            else {
                xInertia *= 0.8f;
                if (speed < 1) {
                    int offsetx = (width - ITEM_W) / 2;
                    float pxo = xo + offsetx;
                    int index = itemAtRaw((int) (pxo - 10 * xInertia));
                    int indexPos = index * ITEM_W;
                    float diff = indexPos - pxo;
                    if (diff < -ITEM_W / 2) diff += ITEM_W;
                    if (Math.abs(diff) < 1 && speed < 0.1f) selectedItem = itemAt(width / 2, height / 2);
                    else {
                        tdStart = xo;
                        tdStop = xo + diff;
                        tdCur = 0;
                        tdDur = Math.min(7, 1 + (int) (Math.abs(diff) * 0.25f));
                        mode = 1;
                        tweenInited();
                    }
                }
            }
        }
        bDelete.active = selectedItem >= 0 && selectedItem < levels.size();
    }

    private float pos(float a) {
        if (mode != 1) return xoo - xInertia * a;
        float x0 = quadraticInOut(tdCur, tdDur, tdStart, tdStop), x1 = quadraticInOut(tdCur + 1, tdDur, tdStart, tdStop);
        return x0 + (x1 - x0) * a;
    }

    protected void mouseClicked(int x, int y) {
        super.mouseClicked(x, y);
        mouseHasBeenUp = true;
        if (y >= y0 && y <= y1) {
            lastSelectionTime = System.currentTimeMillis();
            lastSelection = itemAt(x, height / 2);
            if (selectedItem == levels.size() && lastSelection == selectedItem) newWorldSelected = true;
            selectionX = x;
            dragState = 0;
            mode = 0;
            xDrag = x;
        }
    }

    protected void mouseDragged(int x, int y) {
        if (dragState == 0) {
            xo -= x - xDrag;
            xoo = xo;
            xDrag = x;
            capX();
        }
    }

    protected void mouseReleased(int x, int y) {
        super.mouseReleased(x, y);
        if (dragState != 0) return;
        dragState = -1;
        xInertia = xinertia < 0 ? Math.max(-20f, xinertia) : Math.min(20f, xinertia);
        if (Math.abs(xInertia) <= 2.0001f) xInertia = 0;
        boolean picked = false;
        if (Math.abs(xInertia) <= 10 && System.currentTimeMillis() - lastSelectionTime < 300) {
            int slot = itemAt(x, height / 2);
            if (slot >= 0 && slot == lastSelection && Math.abs(selectionX - x) < 10) {
                picked = true;
                selectItem(slot);
            }
        }
        newWorldSelected = false;
        if (!picked) xo = pos(0);
        capX();
    }

    /** TouchWorldSelectionList::selectItem. */
    private void selectItem(int item) {
        if (selectedItem < 0) return;
        int delta = item - selectedItem;
        if (delta == -1 && selectedItem > 0) step(-1);
        else if (delta == 1 && selectedItem < count() - 1) step(1);
        else if (delta == 0) {
            if (item == levels.size()) mc.setScreen(new CreateWorldScreen());
            else {
                Storage.WorldInfo w = (Storage.WorldInfo) levels.elementAt(item);
                mc.loadWorld(w.folder);
            }
        }
    }

    public void render(Graphics g) {
        renderBackground(g);
        float a = mc.partial;
        if (dragState != 0) xo = pos(a);
        // фон ленты (0x202020) прокручивается вместе с ней
        R.dirt(g, 0, y0, width, height, 0x20, (int) xo);
        int rowY = rowY(), rowBaseX = -(int) xo;
        for (int i = 0; i < count(); i++) {
            float x = rowBaseX + i * ITEM_W;
            if (x > width || x + ITEM_W < 0) continue;
            if (i == selectedItem) {
                float sy0 = height / 2f - HALF_H - 4, sy1 = height / 2f + HALF_H - 4;
                R.fill(g, x - 1, sy0, x + ITEM_W + 1, sy1, 0xFF808080);
                R.fill(g, x, sy0 + 1, x + ITEM_W, sy1 - 1, 0xFF000000);
            }
            renderItem(g, i, (int) x, rowY);
        }
        // верхняя кромка (renderHoleBackground 0x505050)
        R.dirt(g, 0, 0, width, y0, 0x50, 0);
        super.render(g);
    }

    private void renderItem(Graphics g, int i, int x, int y) {
        int centerx = x + ITEM_W / 2;
        float a0 = Math.max(1.1f - Math.abs(width / 2 - centerx) * 0.0055f, 0.2f);
        if (a0 > 1) a0 = 1;
        int v1 = (int) (255 * a0), v2 = (int) (140 * a0);
        int c1 = v1 * 0x010101, c2 = v2 * 0x010101;
        int TX = centerx - ITEM_W / 2 + 5, TY = y + 44;
        if (i < levels.size()) {
            String[] d = desc[i];
            R.shadow(g, d[0], TX, TY, c1);
            R.shadow(g, d[1], TX, TY + 10, c2);
            R.shadow(g, d[2], TX, TY + 20, c2);
            R.shadow(g, d[3], TX, TY + 30, c2);
            // gui/default_world.png 64x48, по высоте 0.125..0.875, яркость как у текста
            int mul = Math.min(255, (v1 + 8) / 16 * 16);
            R.blit(g, R.worldThumb, centerx - 32, y - 8, 64, 48, 0, 16, 128, 96, 255, mul);
        } else {
            R.centered(g, "Create new", centerx, TY + 12, c1);
            int mul = Math.min(255, (v1 + 8) / 16 * 16);
            R.blit(g, R.touch, centerx - 27, y, 54, 54, 168, newWorldSelected ? 86 : 32, 54, 54, 255, mul);
        }
    }

    protected void buttonClicked(Button b) {
        if (b == bCreate) mc.setScreen(new CreateWorldScreen());
        if (b == bBack) mc.setScreen(new StartMenuScreen());
        if (b == bDelete && selectedItem >= 0 && selectedItem < levels.size()) {
            final Storage.WorldInfo w = (Storage.WorldInfo) levels.elementAt(selectedItem);
            mc.setScreen(new ConfirmScreen("Are you sure you want to delete this world?", "'" + w.name + "' will be lost forever!",
                    "Delete", "Cancel") {
                protected void postResult(boolean ok) {
                    if (ok) Storage.deleteWorld(w.folder);
                    mc.setScreen(new SelectWorldScreen());
                }
            });
        }
    }

    public void back() {
        mc.setScreen(new StartMenuScreen());
    }

    // ---- клавиши: лента миров (SelectWorldScreen::keyPressed — влево/вправо листают, OK — играть) ----

    private static final Object F_LIST = new Object();

    protected void collectFocus(Vector v) {
        addFocus(v, F_LIST, 0, (width - ITEM_W) / 2f, height / 2f - HALF_H - 4, ITEM_W, 2 * HALF_H);
        addButtons(v);
    }

    protected int focusStep(Object o, int i, int dir) {
        if (o != F_LIST || (dir != LEFT && dir != RIGHT)) return -1;
        if (selectedItem >= 0 && !touching && dragState != 0) {
            if (dir == LEFT && selectedItem > 0) step(-1);
            if (dir == RIGHT && selectedItem < count() - 1) step(1);
        }
        return 0;
    }

    protected void focusActivate(Object o, int i) {
        if (o != F_LIST) {
            super.focusActivate(o, i);
            return;
        }
        if (selectedItem >= 0 && mode == 0) {
            mc.playUI("random.click");
            selectItem(selectedItem);
        }
    }
}
