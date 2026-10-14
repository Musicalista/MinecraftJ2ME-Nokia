import javax.microedition.lcdui.Canvas;

/**
 * Custom (нет в оригинале): назначение клавиш в игре. У каждого действия две клавиши — кнопочная (джойстик, цифры)
 * и QWERTY; работают обе сразу. Код назначения: буква — строчная латинская (по месту на клавиатуре, см. MC.qwerty),
 * джойстик — GA - getGameAction (у разных телефонов коды стрелок разные), прочее — код клавиши; 0 — не назначено.
 * Backspace / C / Esc не назначаются никогда — это всегда «Назад» (пауза).
 */
final class Keys {
    static final int FWD = 0, BACK = 1, LEFT = 2, RIGHT = 3, JUMP = 4, DESTROY = 5, USE = 6, LOOKUP = 7, LOOKDOWN = 8,
            TURNL = 9, TURNR = 10, SNEAK = 11, PREV = 12, NEXT = 13, INV = 14, CRAFT = 15, PAUSE = 16, N = 17;
    static final String[] NAME = { "Walk forward", "Walk back", "Strafe left", "Strafe right", "Jump", "Destroy / attack",
            "Use / place", "Look up", "Look down", "Turn left", "Turn right", "Sneak", "Previous slot", "Next slot",
            "Inventory", "Craft", "Pause" };
    /** ключи в настройках */
    static final String[] ID = { "fwd", "back", "left", "right", "jump", "destroy", "use", "lookup", "lookdown",
            "turnl", "turnr", "sneak", "prev", "next", "inv", "craft", "pause" };
    static final int GA = -1000;
    static final int[] PAD_DEF = { GA - Canvas.UP, GA - Canvas.DOWN, GA - Canvas.LEFT, GA - Canvas.RIGHT, GA - Canvas.FIRE,
            Canvas.KEY_NUM5, Canvas.KEY_NUM1, Canvas.KEY_NUM2, Canvas.KEY_NUM8, Canvas.KEY_NUM4, Canvas.KEY_NUM6,
            Canvas.KEY_STAR, Canvas.KEY_NUM7, Canvas.KEY_NUM9, Canvas.KEY_NUM0, Canvas.KEY_POUND, 0 };
    static final int[] QW_DEF = { 'w', 's', 'a', 'd', ' ', 'e', 'q', 'i', 'k', 'j', 'l', 'c', 'z', 'x', 'r', 'f', 'p' };
    /** назначения: [0] — кнопочные, [1] — QWERTY */
    static final int[][] bind = { new int[N], new int[N] };

    static {
        reset();
    }

    static void reset() {
        System.arraycopy(PAD_DEF, 0, bind[0], 0, N);
        System.arraycopy(QW_DEF, 0, bind[1], 0, N);
    }

    /** Клавиша не назначается: всегда «Назад». */
    static boolean reserved(int k) {
        return k == 8 || k == -8 || k == 27;
    }

    /** Код назначения для нажатой клавиши. */
    static int codeOf(int k, int ga) {
        int q = MC.qwerty(k);
        if (q != 0) return q;
        if (k < 0 && ga != 0) return GA - ga;
        return k;
    }

    private static boolean matches(int b, int k, int ga) {
        if (b == 0) return false;
        if (b <= GA) return ga != 0 && ga == GA - b;
        if (b >= 'a' && b <= 'z') return MC.qwerty(k) == b;
        return k == b;
    }

    /** Действие нажатой клавиши или -1. */
    static int find(int k, int ga) {
        for (int s = 0; s < 2; s++)
            for (int i = 0; i < N; i++) if (matches(bind[s][i], k, ga)) return i;
        return -1;
    }

    /** Назначить; та же клавиша у других действий снимается, чтобы не было двойных назначений. */
    static void set(int scheme, int action, int code) {
        if (code != 0)
            for (int s = 0; s < 2; s++)
                for (int i = 0; i < N; i++) if (bind[s][i] == code) bind[s][i] = 0;
        bind[scheme][action] = code;
    }

    static String name(int b) {
        if (b == 0) return "-";
        if (b <= GA) {
            switch (GA - b) {
                case Canvas.UP: return "Up";
                case Canvas.DOWN: return "Down";
                case Canvas.LEFT: return "Left";
                case Canvas.RIGHT: return "Right";
                case Canvas.FIRE: return "OK";
                case Canvas.GAME_A: return "Game A";
                case Canvas.GAME_B: return "Game B";
                case Canvas.GAME_C: return "Game C";
                case Canvas.GAME_D: return "Game D";
            }
            return "Joy " + (GA - b);
        }
        if (b >= 'a' && b <= 'z') return String.valueOf((char) (b - 32));
        switch (b) {
            case ' ': return "Space";
            case 10: case 13: return "Enter";
            case 9: return "Tab";
            case -6: return "Left soft";
            case -7: return "Right soft";
        }
        if (b > 32 && b < 127) return String.valueOf((char) b);
        return "Key " + b;
    }
}
