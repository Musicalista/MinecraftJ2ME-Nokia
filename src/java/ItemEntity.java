/** Выпавший предмет (id 64 в entities.dat). */
public final class ItemEntity extends Entity {
    public int item, count, aux;
    public int age, pickupDelay = 10, hp = 5;
    public float bobOffs;

    public ItemEntity(Level l) {
        super(l);
        bbW = 0.25f;
        bbH = 0.25f;
        stepHeight = 0;
        bobOffs = (float) (Mth.random() * Math.PI * 2);
    }

    public ItemEntity(Level l, float x, float y, float z, int item, int count, int aux) {
        this(l);
        // ItemEntity::ItemEntity: y — центр рамки (heightOffset = высота / 2)
        setPos(x, y - bbH / 2, z);
        this.item = item;
        this.count = count;
        this.aux = aux;
        yRot = (float) (Mth.random() * 360);
        xd = (float) (Mth.random() * 0.2 - 0.1);
        yd = 0.2f;
        zd = (float) (Mth.random() * 0.2 - 0.1);
    }

    public int typeId() {
        return 64;
    }

    /** ItemEntity::tick 0.6.1: без слияния стопок; лава подбрасывает с шипением; живёт 5 минут. */
    public void tick() {
        super.tick();
        if (pickupDelay > 0) pickupDelay--;
        xo = x; yo = y; zo = z;
        yd -= 0.04f;
        float cy = y + bbH / 2;
        if (Tile.isLava(level.getTile(Mth.floor(x), Mth.floor(cy), Mth.floor(z)))) {
            java.util.Random r = level.random;
            yd = 0.2f;
            xd = (r.nextFloat() - r.nextFloat()) * 0.2f;
            zd = (r.nextFloat() - r.nextFloat()) * 0.2f;
            if (MC.instance != null) MC.instance.playSoundAt("random.fizz", x, y, z, 0.4f);
        }
        checkInTile(x, cy, z);
        move(xd, yd, zd);
        float fr = 0.98f;
        if (onGround) {
            fr = 0.6f * 0.98f;
            int t = level.getTile(Mth.floor(x), Mth.floor(y) - 1, Mth.floor(z));
            if (t > 0) fr = Tile.friction[t] * 0.98f;
        }
        xd *= fr;
        yd *= 0.98f;
        zd *= fr;
        if (onGround) yd *= -0.5f;
        age++;
        if (age >= 6000) remove();
    }

    /** ItemEntity::isInWater — вся рамка, с течением */
    protected boolean isInWaterNow() {
        float hw = bbW / 2;
        return checkWater(x - hw, y, z - hw, x + hw, y + bbH, z + hw);
    }

    /** ItemEntity::checkInTile: выталкивание из сплошного блока к ближайшей свободной стороне. */
    private void checkInTile(float px, float py, float pz) {
        int xt = Mth.floor(px), yt = Mth.floor(py), zt = Mth.floor(pz);
        if (!Tile.solidT[level.getTile(xt, yt, zt)]) return;
        float xd0 = px - xt, yd0 = py - yt, zd0 = pz - zt;
        boolean west = !Tile.solidT[level.getTile(xt - 1, yt, zt)], east = !Tile.solidT[level.getTile(xt + 1, yt, zt)];
        boolean up = !Tile.solidT[level.getTile(xt, yt - 1, zt)], down = !Tile.solidT[level.getTile(xt, yt + 1, zt)];
        boolean north = !Tile.solidT[level.getTile(xt, yt, zt - 1)], south = !Tile.solidT[level.getTile(xt, yt, zt + 1)];
        int dir = -1;
        float closest = 9999;
        if (west && xd0 < closest) { closest = xd0; dir = 0; }
        if (east && 1 - xd0 < closest) { closest = 1 - xd0; dir = 1; }
        if (up && yd0 < closest) { closest = yd0; dir = 2; }
        if (down && 1 - yd0 < closest) { closest = 1 - yd0; dir = 3; }
        if (north && zd0 < closest) { closest = zd0; dir = 4; }
        if (south && 1 - zd0 < closest) dir = 5;
        float speed = level.random.nextFloat() * 0.2f + 0.1f;
        if (dir == 0) xd = -speed;
        if (dir == 1) xd = speed;
        if (dir == 2) yd = -speed;
        if (dir == 3) yd = speed;
        if (dir == 4) zd = -speed;
        if (dir == 5) zd = speed;
    }

    /** ItemEntity::playerTouch: подбирается, только если влезает целиком (иначе остаток лежит). */
    public void playerTouch(Player p) {
        if (pickupDelay > 0 || removed || p.health <= 0) return;
        int left = p.inv.add(item, count, aux);
        if (p.inv.creative) left = 0;
        if (left == 0) {
            if (MC.instance != null) {
                MC.instance.playSound("random.pop", 0.3f);
                MC.instance.takeAnim(this);
            }
            remove();
        } else count = left;
    }

    public boolean hurt(int dmg, Entity source) {
        hp -= dmg;
        if (hp <= 0) remove();
        return false;
    }

    protected float savedYOffset() {
        return bbH / 2;
    }

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putShort("Health", hp);
        t.putShort("Age", age);
        Nbt.Compound it = new Nbt.Compound();
        it.putShort("id", item);
        it.putByte("Count", count);
        it.putShort("Damage", aux);
        t.putCompound("Item", it);
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        hp = t.getShort("Health");
        if (hp <= 0) hp = 5;
        age = t.getShort("Age");
        Nbt.Compound it = t.getCompound("Item");
        if (it != null) {
            item = it.getShort("id") & 0xFFFF;
            count = it.getByte("Count") & 255;
            aux = it.getShort("Damage");
        }
        if (item <= 0 || item >= Item.N || !Item.exists[item] || count <= 0) remove();
    }
}
