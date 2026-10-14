/**
 * Инвентарь: 36 обычных слотов (в NBT это Slot 9..44) и 9 слотов выбора (хотбар),
 * которые ссылаются на обычные слоты. Последний слот выбора на сенсорном экране — «Ещё».
 */
public final class Inventory {
    public static final int SEL = 9, SLOTS = 36;
    public final int[] id = new int[SLOTS];
    public final int[] count = new int[SLOTS];
    public final int[] aux = new int[SLOTS];
    /** ссылка слота выбора на индекс обычного слота (0..35), -1 — пусто */
    public final int[] link = new int[SEL];
    public int selected;
    public boolean creative;

    // в творческом режиме хотбар — прямые предметы
    public final int[] cId = new int[SEL];
    public final int[] cAux = new int[SEL];

    public Inventory(boolean creative) {
        this.creative = creative;
        for (int i = 0; i < SEL; i++) link[i] = -1;
        if (creative) {
            // Sel[] из Inventory::setupDefault() 0.6.1
            int[] def = { Tile.STONE, Tile.COBBLE, Tile.BRICK, Tile.TORCH, Tile.DIRT, Tile.PLANKS, Tile.BRICK_STAIRS, Tile.LOG, Tile.SAPLING };
            for (int i = 0; i < SEL; i++) cId[i] = def[i];
        }
    }

    public int selId() {
        if (creative) return cId[selected];
        int s = link[selected];
        return s < 0 ? 0 : id[s];
    }

    public int selAux() {
        if (creative) return cAux[selected];
        int s = link[selected];
        return s < 0 ? 0 : aux[s];
    }

    public int selCount() {
        if (creative) return cId[selected] == 0 ? 0 : 1;
        int s = link[selected];
        return s < 0 ? 0 : count[s];
    }

    public int slotId(int sel) {
        if (creative) return cId[sel];
        int s = link[sel];
        return s < 0 ? 0 : id[s];
    }

    public int slotAux(int sel) {
        if (creative) return cAux[sel];
        int s = link[sel];
        return s < 0 ? 0 : aux[s];
    }

    public int slotCount(int sel) {
        if (creative) return cId[sel] == 0 ? 0 : 1;
        int s = link[sel];
        return s < 0 ? 0 : count[s];
    }

    /** Израсходовать один предмет из выбранного слота. */
    public void useSelected() {
        if (creative) return;
        int s = link[selected];
        if (s < 0) return;
        if (--count[s] <= 0) {
            id[s] = 0; count[s] = 0; aux[s] = 0;
        }
    }

    /** Inventory::hasResource / removeResource (стрелы для лука). */
    public boolean hasResource(int item) {
        for (int i = 0; i < SLOTS; i++) if (id[i] == item && count[i] > 0) return true;
        return false;
    }

    public void removeResource(int item) {
        if (creative) return;
        for (int i = 0; i < SLOTS; i++)
            if (id[i] == item && count[i] > 0) {
                if (--count[i] <= 0) { id[i] = 0; count[i] = 0; aux[i] = 0; }
                return;
            }
    }

    /** Заменить предмет в выбранном слоте (миска после супа). */
    public void replaceSelected(int item, int n, int a) {
        if (creative) return;
        int s = link[selected];
        if (s < 0) return;
        id[s] = item; count[s] = n; aux[s] = a;
    }

    public void damageSelected(int amount) {
        if (creative) return;
        int s = link[selected];
        if (s < 0 || id[s] < 256 || Item.maxDamage[id[s]] == 0) return;
        aux[s] += amount;
        if (aux[s] > Item.maxDamage[id[s]]) {
            id[s] = 0; count[s] = 0; aux[s] = 0;
        }
    }

    /** Добавить предмет; возвращает остаток. */
    public int add(int item, int n, int a) {
        if (creative) return 0;
        int max = Item.maxStack[item] <= 0 ? 64 : Item.maxStack[item];
        for (int i = 0; i < SLOTS && n > 0; i++) {
            if (id[i] == item && aux[i] == a && count[i] < max) {
                int k = Math.min(n, max - count[i]);
                count[i] += k;
                n -= k;
            }
        }
        for (int i = 0; i < SLOTS && n > 0; i++) {
            if (id[i] == 0) {
                int k = Math.min(n, max);
                id[i] = item; aux[i] = a; count[i] = k;
                n -= k;
                linkIfFree(i);
            }
        }
        return n;
    }

    private void linkIfFree(int slot) {
        for (int i = 0; i < SEL - 1; i++) if (link[i] == slot) return;
        for (int i = 0; i < SEL - 1; i++) {
            if (link[i] < 0 || id[link[i]] == 0) {
                link[i] = slot;
                return;
            }
        }
    }

    /** Выбрать слот инвентаря в хотбар (как в MCPE: выбранный предмет попадает в текущий слот выбора). */
    public void selectSlot(int slot) {
        for (int i = 0; i < SEL - 1; i++) {
            if (link[i] == slot) {
                link[i] = link[selected];
                break;
            }
        }
        link[selected] = slot;
    }

    /**
     * FillingContainer::linkSlot(0, slot, propagate = true): предмет — в первый слот хотбара, остальные сдвигаются
     * вправо до прежнего места этого предмета. В творческом слот — номер в списке блоков (item, a).
     */
    public void moveToFirstSlot(int slot) {
        int i = 0;
        for (; i < SEL - 1; i++) if (link[i] == slot) break;
        if (i == 0 && link[0] == slot) return;
        for (; i > 0; i--) link[i] = link[i - 1];
        link[0] = slot;
    }

    public void moveToFirstSlotCreative(int item, int a) {
        int i = 0;
        for (; i < SEL - 1; i++) if (cId[i] == item && cAux[i] == a) break;
        if (i == 0) return;
        for (; i > 0; i--) { cId[i] = cId[i - 1]; cAux[i] = cAux[i - 1]; }
        cId[0] = item;
        cAux[0] = a;
    }

    public void setCreativeSel(int item, int a) {
        for (int i = 0; i < SEL - 1; i++) {
            if (cId[i] == item && cAux[i] == a) {
                cId[i] = cId[selected];
                cAux[i] = cAux[selected];
                break;
            }
        }
        cId[selected] = item;
        cAux[selected] = a;
    }

    // ---- NBT ----

    public Nbt.NList save() {
        Nbt.NList l = new Nbt.NList(Nbt.COMPOUND);
        if (creative) return l;
        for (int i = 0; i < SEL; i++) {
            int s = link[i];
            Nbt.Compound t = new Nbt.Compound();
            t.putByte("Slot", i);
            t.putShort("id", 255);
            t.putByte("Count", 255);
            t.putShort("Damage", s < 0 ? -1 : s + SEL);
            l.add(t);
        }
        for (int i = 0; i < SLOTS; i++) {
            if (id[i] == 0) continue;
            Nbt.Compound t = new Nbt.Compound();
            t.putByte("Slot", i + SEL);
            t.putShort("id", id[i]);
            t.putByte("Count", Math.min(255, count[i]));
            t.putShort("Damage", aux[i]);
            l.add(t);
        }
        return l;
    }

    public void load(Nbt.NList l) {
        if (l == null || creative) return;
        for (int i = 0; i < SLOTS; i++) { id[i] = 0; count[i] = 0; aux[i] = 0; }
        for (int i = 0; i < SEL; i++) link[i] = -1;
        for (int k = 0; k < l.size(); k++) {
            Object o = l.get(k);
            if (!(o instanceof Nbt.Compound)) continue;
            Nbt.Compound t = (Nbt.Compound) o;
            int slot = t.getByte("Slot") & 255;
            int iid = t.getShort("id") & 0xFFFF;
            int cnt = t.getByte("Count") & 255;
            int dmg = t.getShort("Damage");
            if (slot < SEL) {
                if (iid == 255 && cnt == 255 && dmg >= SEL && dmg < SEL + SLOTS) link[slot] = dmg - SEL;
                continue;
            }
            int s = slot - SEL;
            if (s >= 0 && s < SLOTS && cnt > 0 && iid > 0 && iid < Item.N && Item.exists[iid]) {
                id[s] = iid; count[s] = cnt; aux[s] = dmg;
            }
        }
    }
}
