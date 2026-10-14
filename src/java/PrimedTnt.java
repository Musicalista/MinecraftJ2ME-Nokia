/** Подожжённый TNT (id 65, поле Fuse) — PrimedTnt 0.6.1. Pos в файле — центр. */
public final class PrimedTnt extends Entity {
    public int fuse = 80;

    public PrimedTnt(Level l) {
        super(l);
        bbW = 0.98f;
        bbH = 0.98f;
        stepHeight = 0;
    }

    /** y — низ рамки (центр на 0.49 выше) */
    public PrimedTnt(Level l, float x, float y, float z) {
        this(l);
        setPos(x, y, z);
        // в оригинале угол (радианы) ещё раз переводится в радианы — толчок почти нулевой
        float rot = Mth.random() * Mth.PI * 2 * Mth.DEG;
        xd = Mth.sin(rot) * -0.02f;
        yd = 0.2f;
        zd = Mth.cos(rot) * -0.02f;
    }

    public int typeId() {
        return 65;
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
        xo = x; yo = y; zo = z;
        tickCount++;
        yd -= 0.04f;
        move(xd, yd, zd);
        xd *= 0.98f; yd *= 0.98f; zd *= 0.98f;
        if (onGround) {
            xd *= 0.7f; zd *= 0.7f; yd *= -0.5f;
        }
        if (--fuse <= 0) {
            remove();
            level.explode(null, x, y + bbH / 2, z, 3.1f);
        } else if (MC.instance != null) MC.instance.particles.smoke(x, y + bbH / 2 + 0.5f, z, 0, 0, 0, 1);
    }

    public void save(Nbt.Compound t) {
        saveBase(t);
        t.putByte("Fuse", fuse);
    }

    public void load(Nbt.Compound t) {
        loadBase(t);
        fuse = t.getByte("Fuse");
    }
}
