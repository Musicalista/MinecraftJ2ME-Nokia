import javax.microedition.lcdui.Graphics;

/** InBedScreen 0.6.1: одна кнопка «Leave Bed» (половина ширины, 1/5 высоты) внизу; игра идёт, экран темнеет. */
public final class InBedScreen extends Screen {
    private final TButton bWakeUp = new TButton(1, "Leave Bed");

    protected void init() {
        add(bWakeUp);
    }

    protected void setupPositions() {
        bWakeUp.width = width / 2;
        bWakeUp.height = (int) (height * 0.2f);
        bWakeUp.y = height - (int) (bWakeUp.height * 1.5);
        bWakeUp.x = width / 2 - bWakeUp.width / 2;
    }

    public boolean pausesGame() {
        return false;
    }

    protected void buttonClicked(Button b) {
        if (b == bWakeUp) mc.stopSleep(true);
        mc.setScreen(null);
    }

    public void back() {
        mc.stopSleep(true);
        mc.setScreen(null);
    }
}
