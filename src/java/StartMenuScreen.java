import javax.microedition.lcdui.Graphics;

/** Touch::StartMenuScreen 0.6.1: заголовок, версия, три квадратные кнопки, копирайт. */
public final class StartMenuScreen extends Screen {
    private final ImageButton bHost = new ImageButton(2, "Start Game");
    private final ImageButton bJoin = new ImageButton(3, "Join Game");
    private final ImageButton bOptions = new ImageButton(4, "Options");
    private static final String VERSION = "v0.6.1 alpha", COPYRIGHT = "ÿMojang AB";

    protected void init() {
        // touchgui.png: настройки (0,26), игра (0,101), сеть (0,176), по 75x75; нажатая — справа
        bOptions.setImageDef(new ImageDef(R.touch, 0, 26, 75, 75, 75, 75), true);
        bHost.setImageDef(new ImageDef(R.touch, 0, 101, 75, 75, 75, 75), true);
        bJoin.setImageDef(new ImageDef(R.touch, 0, 176, 75, 75, 75, 75), true);
        add(bHost);
        add(bJoin);
        add(bOptions);
    }

    protected void setupPositions() {
        int bw = bHost.width;
        if (width >= 3 * bw + 12) {
            int yBase = 2 + height / 3;
            float spacing = (width - 3f * bw) / 4;
            bHost.y = bJoin.y = bOptions.y = yBase;
            bJoin.x = (int) spacing;
            bHost.x = bw + (int) (2 * spacing);
            bOptions.x = 2 * bw + (int) (3 * spacing);
        } else {
            // портрет: те же кнопки столбиком, «Start Game» сверху
            int top = 4 + titleHeight() + 12;
            int free = height - 12 - top;
            int gap = Math.max(4, Math.min(16, (free - 3 * bw) / 4));
            int x = (width - bw) / 2;
            if (3 * bw + 2 * gap <= free) {
                int y = top + (free - 3 * bw - 2 * gap) / 2;
                bHost.x = bJoin.x = bOptions.x = x;
                bHost.y = y;
                bJoin.y = y + bw + gap;
                bOptions.y = y + 2 * (bw + gap);
            } else {
                // не влезает столбиком: «Start Game» сверху, две кнопки под ней
                gap = Math.max(4, Math.min(12, (free - 2 * bw) / 3));
                int y = top + Math.max(0, (free - 2 * bw - gap) / 2);
                int sp = Math.max(4, (width - 2 * bw) / 3);
                bHost.x = x;
                bHost.y = y;
                bJoin.x = sp;
                bOptions.x = width - sp - bw;
                bJoin.y = bOptions.y = y + bw + gap;
            }
        }
    }

    private float titleWidth() {
        return Math.min(width / 2f, R.title.w / 2f);
    }

    private int titleHeight() {
        return (int) (titleWidth() * R.title.h / R.title.w);
    }

    public void render(Graphics g) {
        renderBackground(g);
        float tw = titleWidth(), th = tw * R.title.h / R.title.w;
        float y = 4;
        R.blit(g, R.title, width / 2f - tw / 2, y, tw, th, 0, 0, R.title.w, R.title.h);
        R.shadow(g, VERSION, (width - R.width(VERSION)) / 2, (int) (y + th) + 2, 0xCCCCCC);
        R.shadow(g, COPYRIGHT, width - R.width(COPYRIGHT) - 1, height - 10, 0xFFFFFF);
        super.render(g);
    }

    protected void buttonClicked(Button b) {
        if (b == bHost) mc.setScreen(new SelectWorldScreen());
        // Minecraft::locateMultiplayer + SCREEN_JOINGAME
        if (b == bJoin) mc.setScreen(new JoinGameScreen());
        if (b == bOptions) mc.setScreen(new OptionsScreen());
    }

    public void back() {
        mc.midlet.notifyDestroyed();
    }
}
