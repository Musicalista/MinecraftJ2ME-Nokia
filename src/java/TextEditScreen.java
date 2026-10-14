import javax.microedition.lcdui.Graphics;

/**
 * TextEditScreen 0.6.1: доска таблички (item/sign.png) крупно сверху, четыре строки чёрным, текущая — «> текст <»,
 * крестик закрытия. На Android текст вводился системной клавиатурой; здесь — касание строки открывает поле ввода
 * (до 15 символов), клавиши телефона тоже работают (Enter — следующая строка, стирание на пустой — предыдущая).
 */
public final class TextEditScreen extends Screen {
    final TileEntity sign;
    private final ImageButton btnClose = closeButton(1);
    private int line;

    public TextEditScreen(TileEntity sign) {
        this.sign = sign;
    }

    protected void init() {
        add(btnClose);
    }

    protected void setupPositions() {
        btnClose.x = width - btnClose.width;
        btnClose.y = 0;
    }

    public boolean wantsText() {
        return true;
    }

    /** Масштаб доски: (высота экрана / 2 / 32) * 0.9 пикселей экрана на единицу (доска 64x32 единицы). */
    private float scale() {
        float s = (mc.H / 2 / 32) * 0.9f / R.k;
        float maxW = (width - 2 * btnClose.width) / 64f;
        return Math.min(s, maxW);
    }

    public void render(Graphics g) {
        renderBackground(g);
        float s = scale();
        float x0 = width / 2f - 32 * s, y0 = 5 / R.k;
        // доска: UV 0.03126..0.39063 x 0.06249..0.4374 текстуры 64x32
        R.blit(g, R.sign, x0, y0, 64 * s, 32 * s, 2, 2, 23, 12);
        float ts = s * 8f / 11f;
        for (int i = 0; i < 4; i++) {
            String msg = sign.text[i];
            if (i == line && msg.length() < 14) msg = "> " + msg + " <";
            R.text(g, msg, width / 2f - R.width(msg) * ts / 2f, y0 + s * 2 + ts * 10 * i, 0x000000, ts);
        }
        super.render(g);
    }

    private int lineAt(int x, int y) {
        float s = scale(), y0 = 5 / R.k;
        if (x < width / 2f - 32 * s || x > width / 2f + 32 * s || y < y0 || y > y0 + 32 * s) return -1;
        float ts = s * 8f / 11f;
        int l = (int) ((y - y0 - s * 2) / (ts * 10));
        return Mth.clamp(l, 0, 3);
    }

    protected void mouseReleased(int x, int y) {
        super.mouseReleased(x, y);
        int l = lineAt(x, y);
        if (l < 0 || mc.screen != this) return;
        edit(l);
    }

    /** Поле ввода для строки l. Enter в поле — переход на следующую строку (как в Android-версии). */
    private void edit(final int l) {
        line = l;
        mc.editText("Edit sign message", sign.text[l], 64, new MC.TextResult() {
            public void done(String text) {
                if (text == null) return;
                int cur = l;
                StringBuffer sb = new StringBuffer();
                boolean nextLine = false;
                for (int i = 0; i <= text.length(); i++) {
                    char c = i < text.length() ? text.charAt(i) : 0;
                    if (c == '\n' || c == '\r' || c == 0) {
                        if (cur < 4) sign.text[cur] = sb.toString();
                        sb.setLength(0);
                        if (c == 0) break;
                        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                        cur++;
                        nextLine = true;
                        continue;
                    }
                    if (c >= 32 && c < 256 && sb.length() < 15) sb.append(c);
                }
                changed();
                // Enter/OK — как клавиатура Android в TextEditScreen: курсор на следующую строку и ввод продолжается
                int next = nextLine ? cur : l + 1;
                line = Math.min(next, 3);
                if (next < 4) edit(next);
            }
        });
    }

    public void keyChar(int c) {
        String m = sign.text[line];
        if (c == 8) {
            if (m.length() > 0) sign.text[line] = m.substring(0, m.length() - 1);
            else line = (line + 3) % 4;
        } else if (c == 10 || c == 13) line = (line + 1) % 4;
        else if (c >= 32 && c < 256 && m.length() + 1 < 16) sign.text[line] = m + (char) c;
        changed();
    }

    private void changed() {
        mc.level.unsaved[(sign.x >> 4) | ((sign.z >> 4) << 4)] = true;
    }

    // ---- клавиши: строки таблички (OK — ввод строки) и крестик ----

    protected void collectFocus(java.util.Vector v) {
        float s = scale(), x0 = width / 2f - 32 * s, y0 = 5 / R.k, ts = s * 8f / 11f;
        for (int i = 0; i < 4; i++) addFocus(v, this, i, x0, y0 + s * 2 + ts * 10 * i, 64 * s, ts * 10);
        addButtons(v);
    }

    protected void focusChanged(Object o, int i) {
        if (o == this) line = i;
    }

    protected void focusActivate(Object o, int i) {
        if (o == this) edit(i);
        else super.focusActivate(o, i);
    }

    protected void buttonClicked(Button b) {
        if (b == btnClose) back();
    }

    public void back() {
        changed();
        mc.setScreen(null);
    }

    public void removed() {
        Net.signEdited(sign);
    }
}
