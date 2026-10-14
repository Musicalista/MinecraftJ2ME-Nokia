import javax.microedition.lcdui.Graphics;

/** DeathScreen 0.6.1 (Touch): красный градиент, «You died!» вдвое крупнее, кнопки через 30 тиков. */
public final class DeathScreen extends Screen {
    private final TButton bRespawn = new TButton(1, "Respawn!"), bTitle = new TButton(2, "Main menu");
    private int ticks;

    protected void init() {
        add(bRespawn);
        add(bTitle);
    }

    protected void setupPositions() {
        int bw = width / 4;
        // узкий экран: кнопки шире подписей
        bw = Math.max(bw, Math.max(R.width(bRespawn.msg), R.width(bTitle.msg)) + 12);
        bRespawn.width = bTitle.width = bw;
        if (2 * bw + 20 <= width) {
            bRespawn.y = bTitle.y = height / 2;
            bRespawn.x = width / 2 - bw - 10;
            bTitle.x = width / 2 + 10;
        } else {
            bRespawn.x = bTitle.x = (width - bw) / 2;
            bRespawn.y = height / 2;
            bTitle.y = height / 2 + bRespawn.height + 8;
        }
    }

    public void tick() {
        ticks++;
    }

    public void render(Graphics g) {
        R.gradient(g, 0, 0, width, height, 0x60500000, 0xA0803030);
        String t = "You died!";
        R.shadow(g, t, width / 2 - R.width(t), height / 4, 0xFFFFFF, 2f);
        if (ticks >= 30) super.render(g);
    }

    protected void buttonClicked(Button b) {
        if (ticks < 30) return;
        if (b == bRespawn) {
            mc.player.respawn();
            mc.netRespawned();
            mc.setScreen(null);
        }
        if (b == bTitle) mc.quitToTitle();
    }

    public void back() {
    }
}
