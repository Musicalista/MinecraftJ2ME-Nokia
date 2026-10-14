import javax.microedition.lcdui.Graphics;

/** ConfirmScreen 0.6.1 (Touch): две строки вопроса и кнопки 120x24. */
public abstract class ConfirmScreen extends Screen {
    private final String title1, title2;
    private final TButton yes, no;

    public ConfirmScreen(String title1, String title2, String yesText, String noText) {
        this.title1 = title1;
        this.title2 = title2;
        yes = new TButton(0, yesText);
        no = new TButton(1, noText);
    }

    protected void init() {
        add(yes);
        add(no);
    }

    protected void setupPositions() {
        int bw = Math.min(120, (width - 12) / 2);
        yes.width = no.width = bw;
        yes.height = no.height = 24;
        yes.x = width / 2 - bw - 4;
        no.x = width / 2 + 4;
        yes.y = no.y = Math.max(height / 6 + 72, lineY(70, title2) + 18);
    }

    /** Строка по центру; не влезающая в экран переносится по словам. */
    private int lineY(int y, String t) {
        return y + (R.width(t) > width - 8 ? R.wrapped(null, t, 0, 0, width - 8, 0, true) - 8 : 0);
    }

    private void line(Graphics g, String t, int y) {
        if (R.width(t) <= width - 8) R.centered(g, t, width / 2, y, 0xFFFFFF);
        else R.wrappedCentered(g, t, 4, y, width - 8, 0xFFFFFF);
    }

    public void render(Graphics g) {
        renderBackground(g);
        line(g, title1, 50);
        line(g, title2, lineY(70, title1));
        super.render(g);
    }

    protected void buttonClicked(Button b) {
        postResult(b == yes);
    }

    protected abstract void postResult(boolean ok);

    public void back() {
        postResult(false);
    }
}
