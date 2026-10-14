import javax.microedition.rms.RecordStore;

/**
 * Настройки 0.6.1: то, что на Android задаётся в MainMenuOptionsActivity (preferences.xml), плюс звук и вид от
 * третьего лица из меню паузы. Хранятся в RMS строками «ключ:значение», ключи — как в options.txt оригинала.
 */
public final class Options {
    public static String username = "Steve";
    public static boolean serverVisible = true;
    public static boolean fancyGraphics, lowQuality;
    /** ползунок 0..100 (ctrl_sensitivity), по умолчанию 50 */
    public static int sensitivitySlider = 50;
    public static boolean invertY, lefty;
    public static boolean vibration = true;
    public static boolean peaceful;
    public static boolean sound = true, thirdPerson;
    // ---- раздел Custom (отступление от оригинала) ----
    public static boolean showFps, autoJump;
    /** поворот экрана: 0 — как у устройства, 1 — альбомный, 2 — портретный */
    public static int orientation;
    public static final String[] ORIENTATION_NAME = { "Auto", "Landscape", "Portrait" };
    /** размер крестовины и кнопки паузы, % (50 — прежний размер) */
    public static int ctrlSize = 50;
    /** центры крестовины и паузы в тысячных долях экрана; -1 — место по умолчанию */
    public static int dpadX = -1, dpadY = -1, pauseX = -1, pauseY = -1;
    public static final int CTRL_MIN = 20, CTRL_MAX = 100;

    /** Options::update: sens = 0.3 + (1.1 s)^1.3 * 0.42, s = 0..1. */
    public static float sensitivity() {
        return 0.3f + Mth.pow(1.1f * sensitivitySlider / 100f, 1.3f) * 0.42f;
    }

    /** viewDistance 0.6.1: 2 (256 >> 2 = 64 блока), «низкое качество» — 3 (32 блока). */
    public static int renderDistance() {
        return lowQuality ? 32 : 64;
    }

    public static boolean fancy() {
        return fancyGraphics && !lowQuality;
    }

    public static void load() {
        try {
            RecordStore rs = RecordStore.openRecordStore("mcpe_options2", true);
            try {
                if (rs.getNumRecords() > 0) parse(new String(rs.getRecord(1), "UTF-8"));
            } finally {
                rs.closeRecordStore();
            }
        } catch (Throwable t) {
            Log.e("options load", t);
        }
        Sound.enabled = sound;
    }

    private static void parse(String s) {
        int p = 0;
        while (p < s.length()) {
            int e = s.indexOf('\n', p);
            if (e < 0) e = s.length();
            String line = s.substring(p, e);
            p = e + 1;
            int c = line.indexOf(':');
            if (c < 0) continue;
            String k = line.substring(0, c), v = line.substring(c + 1);
            boolean b = v.equals("true");
            if (k.equals("mp_username")) username = v;
            else if (k.equals("mp_server_visible_default")) serverVisible = b;
            else if (k.equals("gfx_fancygraphics")) fancyGraphics = b;
            else if (k.equals("gfx_lowquality")) lowQuality = b;
            else if (k.equals("ctrl_sensitivity")) {
                try { sensitivitySlider = Mth.clamp(Integer.parseInt(v), 0, 100); } catch (Exception ex) { }
            } else if (k.equals("ctrl_invertmouse")) invertY = b;
            else if (k.equals("ctrl_islefthanded")) lefty = b;
            else if (k.equals("feedback_vibration")) vibration = b;
            else if (k.equals("game_difficultypeaceful")) peaceful = b;
            else if (k.equals("sound")) sound = b;
            else if (k.equals("thirdperson")) thirdPerson = b;
            else if (k.equals("custom_showfps")) showFps = b;
            else if (k.equals("custom_autojump")) autoJump = b;
            else if (k.equals("custom_orientation")) orientation = Mth.clamp(num(v, 0), 0, 2);
            else if (k.equals("custom_ctrlsize")) ctrlSize = Mth.clamp(num(v, 50), CTRL_MIN, CTRL_MAX);
            else if (k.equals("custom_dpadx")) dpadX = Mth.clamp(num(v, -1), -1, 1000);
            else if (k.equals("custom_dpady")) dpadY = Mth.clamp(num(v, -1), -1, 1000);
            else if (k.equals("custom_pausex")) pauseX = Mth.clamp(num(v, -1), -1, 1000);
            else if (k.equals("custom_pausey")) pauseY = Mth.clamp(num(v, -1), -1, 1000);
        }
    }

    private static int num(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static void save() {
        Sound.enabled = sound;
        StringBuffer sb = new StringBuffer();
        sb.append("mp_username:").append(username).append('\n');
        sb.append("mp_server_visible_default:").append(serverVisible).append('\n');
        sb.append("gfx_fancygraphics:").append(fancyGraphics).append('\n');
        sb.append("gfx_lowquality:").append(lowQuality).append('\n');
        sb.append("ctrl_sensitivity:").append(sensitivitySlider).append('\n');
        sb.append("ctrl_invertmouse:").append(invertY).append('\n');
        sb.append("ctrl_islefthanded:").append(lefty).append('\n');
        sb.append("feedback_vibration:").append(vibration).append('\n');
        sb.append("game_difficultypeaceful:").append(peaceful).append('\n');
        sb.append("sound:").append(sound).append('\n');
        sb.append("thirdperson:").append(thirdPerson).append('\n');
        sb.append("custom_showfps:").append(showFps).append('\n');
        sb.append("custom_autojump:").append(autoJump).append('\n');
        sb.append("custom_orientation:").append(orientation).append('\n');
        sb.append("custom_ctrlsize:").append(ctrlSize).append('\n');
        sb.append("custom_dpadx:").append(dpadX).append('\n');
        sb.append("custom_dpady:").append(dpadY).append('\n');
        sb.append("custom_pausex:").append(pauseX).append('\n');
        sb.append("custom_pausey:").append(pauseY).append('\n');
        try {
            byte[] b = sb.toString().getBytes("UTF-8");
            RecordStore rs = RecordStore.openRecordStore("mcpe_options2", true);
            try {
                if (rs.getNumRecords() == 0) rs.addRecord(b, 0, b.length);
                else rs.setRecord(1, b, 0, b.length);
            } finally {
                rs.closeRecordStore();
            }
        } catch (Throwable t) {
            Log.e("options save", t);
        }
    }
}
