import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Custom (нет в оригинале): переназначение клавиш (Keys). Список действий с двумя столбцами — кнопочная клавиша
 * и QWERTY; выбор ячейки ждёт нажатия, Backspace / C снимает назначение. Вид — как у OptionsScreen.
 */
public final class KeysScreen extends Screen {
    private final Font fName = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
    private final Font fKey = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    private final Font fCat = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    /** строки: 0..N-1 — действия, N — сброс */
    private static final int RESET = Keys.N;
    private int W, H, pad, barH, headH, rowH, colW, col0X, scroll, maxScroll;
    private int focusRow, focusCol;
    private int downX, downY, downScroll, pressRow = -1, pressCol;
    private boolean dragging, backDown;
    /** ждём клавишу для ячейки (wait >= 0) */
    private int wait = -1, waitCol;

    protected void setupPositions() {
        W = mc.W;
        H = mc.H;
        pad = Math.max(6, fKey.getHeight() / 2);
        barH = fCat.getHeight() + pad + 4;
        headH = fCat.getHeight() + 6;
        rowH = Math.max(fName.getHeight(), fKey.getHeight()) + 2 * pad;
        colW = Math.max(fKey.stringWidth("Right soft") + pad, Math.min(W / 4, fKey.stringWidth("Right soft") * 3 / 2));
        col0X = W - pad - 2 * colW;
        maxScroll = Math.max(0, barH + headH + (Keys.N + 1) * rowH - H);
        if (scroll > maxScroll) scroll = maxScroll;
    }

    private int rowY(int i) {
        return barH + headH + i * rowH - scroll;
    }

    public boolean renderGameBehind() {
        return false;
    }

    public void render(Graphics g) {
        g.setColor(0);
        g.fillRect(0, 0, W, H);
        // шапка столбцов
        int hy = barH - scroll;
        g.setColor(0x262626);
        g.fillRect(0, hy, W, headH);
        g.setFont(fCat);
        g.setColor(0xC8C8C8);
        g.drawString("Action", pad, hy + 3, Graphics.TOP | Graphics.LEFT);
        g.drawString("Keypad", col0X + colW / 2, hy + 3, Graphics.TOP | Graphics.HCENTER);
        g.drawString("QWERTY", col0X + colW * 3 / 2, hy + 3, Graphics.TOP | Graphics.HCENTER);
        for (int i = 0; i <= RESET; i++) {
            int y = rowY(i);
            if (y + rowH < barH || y > H) continue;
            renderRow(g, i, y);
        }
        renderBar(g);
        if (wait >= 0) renderWait(g);
    }

    private void renderRow(Graphics g, int i, int y) {
        boolean kf = keyFocus && focusRow == i;
        if (i == RESET) {
            if ((pressRow == i && !dragging) || kf) {
                g.setColor(kf ? 0x3A4A5A : 0x303030);
                g.fillRect(0, y, W, rowH);
            }
            g.setFont(fName);
            g.setColor(0x8BC34A);
            g.drawString("Reset to defaults", W / 2, y + (rowH - fName.getHeight()) / 2, Graphics.TOP | Graphics.HCENTER);
            return;
        }
        g.setFont(fName);
        g.setColor(0xFFFFFF);
        String name = Keys.NAME[i];
        int nw = col0X - 2 * pad;
        while (name.length() > 1 && fName.stringWidth(name) > nw) name = name.substring(0, name.length() - 1);
        g.drawString(name, pad, y + (rowH - fName.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
        for (int c = 0; c < 2; c++) {
            int cx = col0X + c * colW;
            boolean f = (kf && focusCol == c) || (pressRow == i && pressCol == c && !dragging) || (wait == i && waitCol == c);
            g.setColor(f ? 0x3A4A5A : 0x1E1E1E);
            g.fillRoundRect(cx + 2, y + 3, colW - 4, rowH - 6, pad, pad);
            if (f) {
                g.setColor(0xFFFFA0);
                g.drawRoundRect(cx + 2, y + 3, colW - 5, rowH - 7, pad, pad);
            }
            int b = Keys.bind[c][i];
            g.setFont(fKey);
            g.setColor(b == 0 ? 0x707070 : 0xE0E0E0);
            g.drawString(Keys.name(b), cx + colW / 2, y + (rowH - fKey.getHeight()) / 2, Graphics.TOP | Graphics.HCENTER);
        }
        g.setColor(0x262626);
        g.drawLine(pad, y + rowH - 1, W - pad, y + rowH - 1);
    }

    private void renderBar(Graphics g) {
        g.setColor(0x9A9A9A);
        g.fillRect(0, 0, W, barH);
        g.setColor(0xB4B4B4);
        g.fillRect(0, 0, W, barH / 2);
        int ax = pad, ay = barH / 2, as = barH / 3;
        g.setColor(backDown ? 0x606060 : 0xFFFFFF);
        g.fillTriangle(ax, ay, ax + as, ay - as, ax + as, ay + as);
        g.fillRect(ax + as - 1, ay - as / 4, as, as / 2 + 1);
        g.setColor(0xFFFFFF);
        g.setFont(fCat);
        g.drawString("Keys", ax + 2 * as + pad, (barH - fCat.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
    }

    private void renderWait(Graphics g) {
        MC.fillAlpha(g, 0, 0, W, H, 0x99000000);
        String l1 = Keys.NAME[wait] + " (" + (waitCol == 0 ? "Keypad" : "QWERTY") + ")";
        String l2 = "Press a key", l3 = "Backspace / C - clear, tap - cancel";
        int dw = Math.min(W - 2 * pad, Math.max(Math.max(fName.stringWidth(l1), fKey.stringWidth(l3)) + 4 * pad, W * 2 / 3));
        int lh = fName.getHeight();
        int dh = 3 * lh + 3 * pad;
        int dx = (W - dw) / 2, dy = (H - dh) / 2;
        g.setColor(0x2B2B2B);
        g.fillRoundRect(dx, dy, dw, dh, pad, pad);
        g.setFont(fName);
        g.setColor(0xFFFFFF);
        g.drawString(l1, W / 2, dy + pad, Graphics.TOP | Graphics.HCENTER);
        g.setColor(0x8BC34A);
        g.drawString(l2, W / 2, dy + pad + lh + pad / 2, Graphics.TOP | Graphics.HCENTER);
        g.setFont(fKey);
        g.setColor(0xB0B0B0);
        g.drawString(l3, W / 2, dy + pad + 2 * lh + pad, Graphics.TOP | Graphics.HCENTER);
    }

    // ---- ожидание клавиши ----

    public boolean keyRaw(int k, int ga) {
        if (wait < 0) return false;
        Keys.set(waitCol, wait, Keys.reserved(k) ? 0 : Keys.codeOf(k, ga));
        Options.save();
        keyFocus = true;
        focusRow = wait;
        focusCol = waitCol;
        wait = -1;
        return true;
    }

    private void activate(int row, int col) {
        if (row == RESET) {
            Keys.reset();
            Options.save();
            return;
        }
        wait = row;
        waitCol = col;
    }

    // ---- касания: пиксели GUI -> пиксели экрана ----

    protected void mouseClicked(int gx, int gy) {
        int x = (int) (gx * R.k), y = (int) (gy * R.k);
        if (wait >= 0) {
            wait = -1;
            pressRow = -1;
            backDown = false;
            dragging = true;
            return;
        }
        downX = x;
        downY = y;
        downScroll = scroll;
        dragging = false;
        backDown = y < barH && x < barH * 2;
        pressRow = -1;
        if (!backDown && y >= barH) {
            int r = (y - barH - headH + scroll) / rowH;
            if (y - barH - headH + scroll >= 0 && r <= RESET) {
                if (r == RESET) pressRow = r;
                else if (x >= col0X) {
                    pressRow = r;
                    pressCol = x >= col0X + colW ? 1 : 0;
                }
            }
        }
    }

    protected void mouseDragged(int gx, int gy) {
        int y = (int) (gy * R.k);
        if (wait >= 0) return;
        if (Math.abs(y - downY) > pad) dragging = true;
        if (dragging) scroll = Mth.clamp(downScroll - (y - downY), 0, maxScroll);
    }

    protected void mouseReleased(int gx, int gy) {
        int x = (int) (gx * R.k), y = (int) (gy * R.k);
        if (backDown) {
            backDown = false;
            if (y < barH && x < barH * 2) back();
            return;
        }
        int r = pressRow;
        pressRow = -1;
        if (dragging || r < 0) return;
        if (y < rowY(r) || y >= rowY(r) + rowH) return;
        activate(r, pressCol);
    }

    // ---- клавиши: вверх/вниз — строка, влево/вправо — столбец, OK — назначить ----

    public void keyNav(int dir) {
        if (keyFocus) {
            if (dir == UP && focusRow > 0) focusRow--;
            else if (dir == DOWN && focusRow < RESET) focusRow++;
            else if (dir == LEFT) focusCol = 0;
            else if (dir == RIGHT) focusCol = 1;
        }
        keyFocus = true;
        int top = rowY(focusRow) - barH - (focusRow == 0 ? headH : 0), bot = rowY(focusRow) + rowH - H;
        if (top < 0) scroll = Math.max(0, scroll + top);
        if (bot > 0) scroll = Math.min(maxScroll, scroll + bot);
    }

    public void keyOk(boolean down) {
        if (!down) return;
        if (!keyFocus) {
            keyNav(DOWN);
            return;
        }
        activate(focusRow, focusCol);
    }

    public void renderFocus(Graphics g) {
    }

    public void back() {
        wait = -1;
        Options.save();
        OptionsScreen o = new OptionsScreen();
        o.scrollToEnd = true;
        mc.setScreen(o);
    }
}
