/**
 * Блок-сущности: Chest, Furnace, Sign (строковые id как в entities.dat MCPE).
 * Прочие типы хранятся как исходный NBT и записываются обратно без изменений.
 */
public final class TileEntity {
    public String type;
    public int x, y, z;
    public int[] id, count, aux;
    public String[] text;
    public int burnTime, cookTime, litDuration;
    public Nbt.Compound raw;

    public TileEntity(String type, int x, int y, int z) {
        this.type = type;
        this.x = x; this.y = y; this.z = z;
        int n = 0;
        if ("Chest".equals(type)) n = 27;
        else if ("Furnace".equals(type)) n = 3;
        id = new int[n];
        count = new int[n];
        aux = new int[n];
        if ("Sign".equals(type)) text = new String[] { "", "", "", "" };
    }

    public boolean isFurnace() {
        return "Furnace".equals(type);
    }

    public void tick(Level l) {
        if (!isFurnace()) return;
        boolean wasLit = burnTime > 0;
        if (burnTime > 0) burnTime--;
        int result = smeltResult(id[0], aux[0]);
        boolean canSmelt = result != 0 && count[0] > 0
                && (id[2] == 0 || (id[2] == (result & 0xFFFF) && aux[2] == (result >>> 16) && count[2] < 64));
        if (burnTime == 0 && canSmelt) {
            int f = fuel(id[1]);
            if (f > 0) {
                burnTime = litDuration = f;
                if (--count[1] <= 0) { id[1] = 0; count[1] = 0; aux[1] = 0; }
            }
        }
        if (burnTime > 0 && canSmelt) {
            if (++cookTime >= 200) {
                cookTime = 0;
                if (id[2] == 0) { id[2] = result & 0xFFFF; aux[2] = result >>> 16; count[2] = 1; }
                else count[2]++;
                if (--count[0] <= 0) { id[0] = 0; count[0] = 0; aux[0] = 0; }
            }
        } else cookTime = 0;
        boolean lit = burnTime > 0;
        if (lit != wasLit) {
            TileLogic.setFurnaceLit(l, lit, x, y, z);
        }
    }

    /** Результат переплавки (FurnaceRecipes 0.6.1, по id без учёта data): id | aux<<16, 0 — нельзя. */
    public static int smeltResult(int item, int a) {
        switch (item) {
            case Tile.IRON_ORE: return 265;
            case Tile.GOLD_ORE: return 266;
            case Tile.DIAMOND_ORE: return 264;
            case Tile.SAND: return Tile.GLASS;
            case 319: return 320;
            case 363: return 364;
            case 365: return 366;
            case Tile.COBBLE: return Tile.STONE;
            case 337: return 336;
            case Tile.CACTUS: return 351 | (2 << 16);
            case Tile.RED_MUSHROOM: return 351 | (1 << 16);
            case Tile.LOG: return 263 | (1 << 16);
            case Tile.NETHERRACK: return 405;
        }
        return 0;
    }

    /** FurnaceTileEntity::getBurnDuration 0.6.1: деревянные блоки, палка, уголь. Саженцы и инструменты не горят. */
    public static int fuel(int item) {
        if (item > 0 && item < 256 && Tile.exists[item] && Tile.mat[item] == Tile.MT_WOOD) return 300;
        if (item == 280) return 100;
        if (item == 263) return 1600;
        return 0;
    }

    public Nbt.Compound save() {
        if (raw != null && text == null && id.length == 0) return raw;
        Nbt.Compound t = new Nbt.Compound();
        t.putString("id", type);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        if (text != null) {
            for (int i = 0; i < 4; i++) t.putString("Text" + (i + 1), text[i]);
        }
        if (id.length > 0) {
            Nbt.NList items = new Nbt.NList(Nbt.COMPOUND);
            for (int i = 0; i < id.length; i++) {
                if (id[i] == 0 || count[i] <= 0) continue;
                Nbt.Compound it = new Nbt.Compound();
                it.putByte("Slot", i);
                it.putShort("id", id[i]);
                it.putByte("Count", count[i]);
                it.putShort("Damage", aux[i]);
                items.add(it);
            }
            t.putList("Items", items);
        }
        if (isFurnace()) {
            t.putShort("BurnTime", burnTime);
            t.putShort("CookTime", cookTime);
        }
        return t;
    }

    public static TileEntity load(Nbt.Compound t) {
        String type = t.getString("id");
        TileEntity te = new TileEntity(type, t.getInt("x"), t.getInt("y"), t.getInt("z"));
        if (te.text == null && te.id.length == 0) {
            te.raw = t;
            return te;
        }
        if (te.text != null) {
            for (int i = 0; i < 4; i++) te.text[i] = t.getString("Text" + (i + 1));
        }
        Nbt.NList items = t.getList("Items");
        if (items != null) {
            for (int k = 0; k < items.size(); k++) {
                Object o = items.get(k);
                if (!(o instanceof Nbt.Compound)) continue;
                Nbt.Compound it = (Nbt.Compound) o;
                int slot = it.getByte("Slot") & 255;
                if (slot >= te.id.length) continue;
                te.id[slot] = it.getShort("id") & 0xFFFF;
                te.count[slot] = it.getByte("Count") & 255;
                te.aux[slot] = it.getShort("Damage");
            }
        }
        if (te.isFurnace()) {
            te.burnTime = t.getShort("BurnTime");
            te.cookTime = t.getShort("CookTime");
            te.litDuration = fuel(te.id[1]);
            if (te.litDuration == 0) te.litDuration = 1600;
        }
        return te;
    }
}
