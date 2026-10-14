import java.io.InputStream;
import java.util.Hashtable;

/** Строки из lang/en_US.lang (ключ=значение). */
public final class Lang {
    private static final Hashtable map = new Hashtable();

    public static void load() {
        try {
            InputStream in = Lang.class.getResourceAsStream("/lang/en_US.lang");
            if (in == null) return;
            byte[] buf = new byte[64 * 1024];
            int n = 0, r;
            while ((r = in.read(buf, n, buf.length - n)) > 0) {
                n += r;
                if (n == buf.length) {
                    byte[] b = new byte[buf.length * 2];
                    System.arraycopy(buf, 0, b, 0, n);
                    buf = b;
                }
            }
            in.close();
            String s = new String(buf, 0, n, "UTF-8");
            int p = 0;
            while (p < s.length()) {
                int e = s.indexOf('\n', p);
                if (e < 0) e = s.length();
                String line = s.substring(p, e);
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) map.put(line.substring(0, eq), line.substring(eq + 1));
                p = e + 1;
            }
        } catch (Throwable t) {
            Log.e("lang", t);
        }
    }

    public static String get(String key) {
        String v = (String) map.get(key);
        return v != null ? v : key;
    }

    public static String get(String key, String def) {
        String v = (String) map.get(key);
        return v != null ? v : def;
    }

    static final String[] COLOR_DESCS = { "black", "red", "green", "brown", "blue", "purple", "cyan", "silver", "gray", "pink",
            "lime", "yellow", "lightBlue", "magenta", "orange", "white" };
    static final String[] SLAB_NAMES = { "stone", "sand", "wood", "cobble", "brick", "smoothStoneBrick" };

    /** ItemInstance::getDescriptionId 0.6.1 (шерсть, краситель, плиты — с суффиксом по aux). */
    public static String descriptionId(int id, int aux) {
        String n = id < 256 ? Tile.name[id] : Item.name[id];
        if (n == null) return "";
        String k = (id < 256 ? "tile." : "item.") + n;
        if (id == Tile.WOOL) k += "." + COLOR_DESCS[~aux & 15];
        else if (id == 351) k += "." + COLOR_DESCS[aux < 0 ? 0 : (aux > 15 ? 15 : aux)];
        else if (id == Tile.SLAB) k += "." + SLAB_NAMES[aux < 0 || aux >= 6 ? 0 : aux];
        return k;
    }

    /** Название предмета/блока. */
    public static String itemName(int id, int aux) {
        if (id <= 0) return "";
        String k = descriptionId(id, aux);
        String v = (String) map.get(k + ".name");
        if (v != null) return v;
        v = (String) map.get((id < 256 ? "tile." : "item.") + (id < 256 ? Tile.name[id] : Item.name[id]) + ".name");
        return v != null ? v : k;
    }

    /** I18n::getDescriptionString 0.6.1. */
    public static String description(int id, int aux) {
        if (id == Tile.WOOL) return get(aux != 0 ? "desc.wool" : "desc.woolstring");
        if (id == Tile.FENCE_GATE) return get("desc.fence");
        if (id == Tile.SLAB) return get("desc.slab");
        String desc = descriptionId(id, aux);
        String s = desc.toLowerCase();
        if (s.startsWith("tile.")) s = "desc." + s.substring(5);
        else if (s.startsWith("item.")) s = "desc." + s.substring(5);
        String v = (String) map.get(s);
        if (v != null) return v;
        String[] mats = { "wood", "iron", "stone", "diamond", "gold", "brick", "emerald", "lapis", "cloth" };
        for (int i = 0; i < mats.length; i++) {
            int p;
            while ((p = s.indexOf(mats[i])) >= 0) s = s.substring(0, p) + s.substring(p + mats[i].length());
        }
        v = (String) map.get(s);
        if (v != null) return v;
        if (desc.equals("tile.workbench")) {
            v = (String) map.get("desc.craftingtable");
            if (v != null) return v;
        }
        return desc + " : couldn't find desc";
    }
}
