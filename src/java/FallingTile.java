/** Падающий песок/гравий (id 66) — FallingTile 0.6.1. Pos в файле — центр (heightOffset = высота/2). */
public final class FallingTile extends Entity {
    public int tile, data, time;

    public FallingTile(Level l) {
        super(l);
        bbW = bbH = 0.98f;
        stepHeight = 0;
    }

    /** x, y, z — центр блока */
    public FallingTile(Level l, float x, float y, float z, int tile, int data) {
        this(l);
        this.tile = tile;
        this.data = data;
        setPos(x, y - bbH / 2, z);
    }

    public int typeId() {
        return 66;
    }

    public boolean blocksBuilding() {
        return true;
    }

    public boolean isPickable() {
        return !removed;
    }

    protected float savedYOffset() {
        return bbH / 2;
    }

    public void tick() {
        if (tile == 0) {
            remove();
            return;
        }
        xo = x; yo = y; zo = z;
        time++;
        yd -= 0.04f;
        move(xd, yd, zd);
        xd *= 0.98f; yd *= 0.98f; zd *= 0.98f;
        int xt = Mth.floor(x), yt = Mth.floor(y + bbH / 2), zt = Mth.floor(z);
        if (time == 1) {
            if (level.getTile(xt, yt, zt) == tile) level.setTile(xt, yt, zt, 0);
            else {
                remove();
                return;
            }
        }
        if (onGround) {
            xd *= 0.7f; zd *= 0.7f; yd *= -0.5f;
            remove();
            // FallingTile::tick: не поместился — пропадает (в 0.6.1 предмет не выпадает)
            if (TileLogic.levelMayPlace(level, tile, xt, yt, zt, true, 1)) level.setTileAndData(xt, yt, zt, tile, data);
        } else if (time > 20 * 5) remove();
    }

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putByte("Tile", tile);
        t.putByte("Data", data);
        t.putByte("Time", time);
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        tile = t.getByte("Tile") & 255;
        data = t.getByte("Data");
        time = t.getByte("Time");
    }
}
