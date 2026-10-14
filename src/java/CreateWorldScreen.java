import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Создание мира. На Android 0.6.1 это диалог MainActivity (layout create_new_world.xml): шапка из картинок
 * «Back» / «World name» / «Start!», поле имени, кнопка режима, поле сида; фон bg32, текст моноширинный.
 */
public final class CreateWorldScreen extends Screen {
    private static R.Tex texCancel0, texCancel1, texCreate0, texCreate1, texName, texCreative, texSurvival, texBg;
    private String name = "Unnamed world", seed = "";
    private boolean survival;
    private final Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    // раскладка (пиксели GUI)
    private int fx0, fy0, fx1, fy1, cx0, cw, hy, hh, bw, nameW, nameX, nameY, fh, modeX, modeY, modeW, modeH, descY, seedLabelY, seedY, infoY;
    private int pressed = -1; // 0 back, 1 start, 2 mode

    protected void init() {
        if (texCancel0 == null) {
            texCancel0 = R.tex("/gui/nw_cancel_0_1.png");
            texCancel1 = R.tex("/gui/nw_cancel_1_1.png");
            texCreate0 = R.tex("/gui/nw_create_0_1.png");
            texCreate1 = R.tex("/gui/nw_create_1_1.png");
            texName = R.tex("/gui/nw_worldname.png");
            texCreative = R.tex("/gui/nw_creative_3.png");
            texSurvival = R.tex("/gui/nw_survival_3.png");
            texBg = R.tex("/gui/bg32.png");
        }
    }

    protected void setupPositions() {
        float k = R.k;
        fh = (int) Math.ceil(font.getHeight() / k);
        fx0 = 4;
        fy0 = 4;
        fx1 = width - 4;
        fy1 = height - 4;
        cx0 = fx0 + 3;
        cw = fx1 - 3 - cx0;
        hy = fy0 + 3;
        hh = 26;
        bw = cw * 3 / 13;
        // android:layout_weight 0.5; в портрете поле шире, чтобы имя помещалось
        nameW = width >= height ? cw / 2 : cw * 17 / 20;
        nameX = cx0 + (cw - nameW) / 2;
        nameY = hy + hh + 2;
        modeH = 24;
        modeW = modeH * 286 / 78;
        modeX = cx0 + (cw - modeW) / 2;
        // мало места по высоте (320x240, 176x220): сжать отступы, затем убрать нижнюю подсказку (то же сказано над
        // полем сида), затем описание режима
        if (!layoutBody(false, true, true) && !layoutBody(true, true, true) && !layoutBody(true, false, true))
            layoutBody(true, false, false);
    }

    private boolean showInfo = true, showDesc = true;

    private boolean layoutBody(boolean compact, boolean info, boolean desc) {
        showInfo = info;
        showDesc = desc;
        int y = nameY + fh + 8 + (compact ? 4 : 10);
        modeY = y + 1;
        y += 26;
        descY = y + 2;
        if (desc) y = descY + fh * Math.max(lines(DESC_SURVIVAL), lines(DESC_CREATIVE)) + (compact ? 4 : 10);
        seedLabelY = y;
        seedY = y + fh * lines(SEED_LABEL) + 2;
        infoY = seedY + fh + 8 + 2;
        int bottom = info ? infoY + fh * lines(SEED_INFO) + 2 : seedY + fh + 8 + 2;
        return bottom <= fy1 - 3;
    }


    public boolean renderGameBehind() {
        return false;
    }

    public void render(Graphics g) {
        // под диалогом — экран выбора мира, затемнённый
        R.dirt(g, 0, 0, width, height, 0x20, 0);
        // рамка диалога
        R.fill(g, fx0 - 0.5f, fy0 - 0.5f, fx1 + 0.5f, fy1 + 0.5f, 0xFF6A6A6A);
        R.fill(g, fx0, fy0, fx1, fy1, 0xFF000000);
        // фон bg32 (тайл 32 пикселя GUI)
        int x0 = R.px(cx0), y0 = R.px(hy), x1 = R.px(cx0 + cw), y1 = R.px(fy1 - 3);
        int cx = g.getClipX(), cy = g.getClipY(), cwc = g.getClipWidth(), chc = g.getClipHeight();
        g.clipRect(x0, y0, x1 - x0, y1 - y0);
        for (int ty = hy; ty < fy1; ty += 32)
            for (int tx = cx0; tx < cx0 + cw; tx += 32) R.blit(g, texBg, tx, ty, 0, 0, 32, 32);
        g.setClip(cx, cy, cwc, chc);
        // шапка: картинки, растянутые по весам 3 / 7 / 3, между ними — чёрные щели
        R.fill(g, cx0, hy, cx0 + cw, hy + hh, 0xFF000000);
        int gap = 1;
        img(g, pressed == 0 ? texCancel1 : texCancel0, cx0, hy, bw - gap, hh);
        img(g, texName, cx0 + bw + gap, hy, cw - 2 * bw - 2 * gap, hh);
        img(g, pressed == 1 ? texCreate1 : texCreate0, cx0 + cw - bw + gap, hy, bw - gap, hh);
        // поле имени (в фокусе — оранжевая рамка)
        field(g, nameX, nameY, nameW, name, true);
        // кнопка режима
        R.Tex m = survival ? texSurvival : texCreative;
        float sh = pressed == 2 ? 0.95f : 1f;
        float mw = modeW * sh, mh = modeH * sh;
        R.blit(g, m, modeX + (modeW - mw) / 2, modeY + (modeH - mh) / 2, mw, mh, 0, 0, m.w, m.h);
        if (showDesc) label(g, survival ? DESC_SURVIVAL : DESC_CREATIVE, descY);
        label(g, SEED_LABEL, seedLabelY);
        field(g, nameX, seedY, nameW, seed, false);
        if (showInfo) label(g, SEED_INFO, infoY);
    }

    private void img(Graphics g, R.Tex t, int x, int y, int w, int h) {
        R.blit(g, t, x, y, w, h, 0, 0, t.w, t.h);
    }

    private void field(Graphics g, int x, int y, int w, String text, boolean focus) {
        int h = fh + 8;
        if (focus) R.fill(g, x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFF8A00);
        R.fill(g, x, y, x + w, y + h, 0xFFF4F4F4);
        g.setFont(font);
        g.setColor(0x101010);
        int X0 = R.px(x), W0 = R.px(x + w) - X0;
        int cx = g.getClipX(), cy = g.getClipY(), cwc = g.getClipWidth(), chc = g.getClipHeight();
        g.clipRect(X0 + 2, R.px(y), W0 - 4, R.px(y + h) - R.px(y));
        g.drawString(text, X0 + W0 / 2, R.px(y + 4), Graphics.TOP | Graphics.HCENTER);
        g.setClip(cx, cy, cwc, chc);
    }

    private static final String DESC_SURVIVAL = "Mobs, health and gather resources", DESC_CREATIVE = "Unlimited resources and flying",
            SEED_LABEL = "World Generator seed. Leave blank for random.", SEED_INFO = "Leave blank for random seed";

    /** TextView: перенос по словам в ширину содержимого. */
    private java.util.Vector wrap(String t) {
        java.util.Vector v = new java.util.Vector();
        int maxW = R.px(cw) - 8, start = 0;
        while (start < t.length()) {
            int end = t.length();
            while (end > start && font.stringWidth(t.substring(start, end)) > maxW) {
                int sp = t.lastIndexOf(' ', end - 1);
                end = sp > start ? sp : end - 1;
            }
            if (end <= start) end = start + 1;
            v.addElement(t.substring(start, end).trim());
            start = end;
            while (start < t.length() && t.charAt(start) == ' ') start++;
        }
        return v;
    }

    private int lines(String t) {
        return wrap(t).size();
    }

    private void label(Graphics g, String text, int y) {
        g.setFont(font);
        g.setColor(0xE8E8E8);
        java.util.Vector v = wrap(text);
        for (int i = 0; i < v.size(); i++)
            g.drawString((String) v.elementAt(i), R.px(cx0 + cw / 2f), R.px(y) + i * font.getHeight(), Graphics.TOP | Graphics.HCENTER);
    }

    private int hit(int x, int y) {
        if (y >= hy && y < hy + hh) {
            if (x >= cx0 && x < cx0 + bw) return 0;
            if (x >= cx0 + cw - bw && x < cx0 + cw) return 1;
        }
        if (x >= modeX && x < modeX + modeW && y >= modeY && y < modeY + modeH) return 2;
        if (x >= nameX && x < nameX + nameW) {
            if (y >= nameY && y < nameY + fh + 8) return 3;
            if (y >= seedY && y < seedY + fh + 8) return 4;
        }
        return -1;
    }

    protected void mouseClicked(int x, int y) {
        pressed = hit(x, y);
    }

    protected void mouseReleased(int x, int y) {
        int p = pressed;
        pressed = -1;
        if (p < 0 || hit(x, y) != p) return;
        activate(p);
    }

    // ---- клавиши: «Start!», имя, режим, сид, «Back» ----

    protected void collectFocus(java.util.Vector v) {
        addFocus(v, this, 1, cx0 + cw - bw, hy, bw, hh);
        addFocus(v, this, 3, nameX, nameY, nameW, fh + 8);
        addFocus(v, this, 2, modeX, modeY, modeW, modeH);
        addFocus(v, this, 4, nameX, seedY, nameW, fh + 8);
        addFocus(v, this, 0, cx0, hy, bw, hh);
    }

    protected void focusActivate(Object o, int i) {
        if (o == this) activate(i);
    }

    private void activate(int p) {
        switch (p) {
            case 0:
                back();
                break;
            case 1: {
                // SelectWorldScreen::tick: имя без лишних пробелов, сид — sscanf("%d") или hashCode
                String n = name.trim();
                String s = seed.trim();
                mc.createWorld(n, s.length() == 0 ? (int) (System.currentTimeMillis() / 1000) : seedOf(s), survival ? 0 : 1);
                break;
            }
            case 2:
                survival = !survival;
                break;
            case 3:
                mc.editText("World name", name, 64, new MC.TextResult() {
                    public void done(String text) {
                        if (text != null) name = text;
                    }
                });
                break;
            case 4:
                mc.editText("World Generator seed", seed, 64, new MC.TextResult() {
                    public void done(String text) {
                        if (text != null) seed = text;
                    }
                });
                break;
        }
    }

    /** Как SelectWorldScreen 0.6.1: sscanf("%d") — число в начале строки, иначе Util::hashCode по байтам UTF-8. */
    static int seedOf(String t) {
        int i = 0;
        boolean neg = false;
        if (t.charAt(0) == '-' || t.charAt(0) == '+') { neg = t.charAt(0) == '-'; i = 1; }
        int start = i;
        long v = 0;
        while (i < t.length() && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
            v = v * 10 + (t.charAt(i) - '0');
            if (v > 0x7FFFFFFFL) v = 0x7FFFFFFFL + (neg ? 1 : 0);
            i++;
        }
        if (i > start) return (int) (neg ? -v : v);
        byte[] u;
        try { u = t.getBytes("UTF-8"); } catch (Exception e) { u = t.getBytes(); }
        int h = 0;
        for (int k = 0; k < u.length; k++) h = ((h << 5) - h) + (u[k] & 255);
        return h;
    }

    public void back() {
        mc.setScreen(new SelectWorldScreen());
    }
}
