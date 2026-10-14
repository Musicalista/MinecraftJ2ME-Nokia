/** Animal 0.6.1: бродит к траве, после удара 3 с бежит (скорость x2), не исчезает вдали. */
public abstract class Animal extends PathfinderMob {
    public int age;
    public boolean noAi;

    protected Animal(Level l) {
        super(l);
    }

    public int maxHealth() {
        return 10;
    }

    protected int ambientInterval() {
        return 12 * 20;
    }

    protected boolean removeWhenFarAway() {
        return false;
    }

    protected void updateAi() {
        if (noAi) { moveForward = moveStrafe = 0; jumping = false; return; }
        super.updateAi();
    }

    /** Animal::getWalkTargetValue: трава — 10, иначе яркость - 0.5 */
    public float walkTargetValue(int x, int y, int z) {
        if (level.getTile(x, y - 1, z) == Tile.GRASS) return 10;
        return level.getBrightness(x, y, z) - 0.5f;
    }

    public boolean hurt(int dmg, Entity source) {
        fleeTime = 3 * 20;
        attackTarget = null;
        return super.hurt(dmg, source);
    }

    public void save(Nbt.Compound t) {
        saveMob(t);
        t.putInt("Age", age);
    }

    public void load(Nbt.Compound t) {
        loadMob(t);
        age = t.getInt("Age");
    }
}
