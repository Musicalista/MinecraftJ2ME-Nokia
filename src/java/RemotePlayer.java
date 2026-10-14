/**
 * Другой игрок сетевой игры. На хосте — как ServerPlayer: здоровье, урон, броня, сон, открытый сундук/печь,
 * положение приходит пакетами MovePlayer; на клиенте — RemotePlayer: только сглаживание движения и анимации.
 * Поддельный инвентарь (как hasFakeInventory) — творческий, в слоте 0 лежит то, что игрок держит в руке.
 */
public final class RemotePlayer extends Mob {
    public String name = "";
    /** RakNetGUID владельца */
    public long owner;
    /** хост: соединение владельца */
    public Raknet.Conn conn;
    public final Inventory inv = new Inventory(true);
    public final int[] armor = new int[4], armorDmg = new int[4];
    public boolean sleeping;
    public int bedX, bedY, bedZ, sleepTimer;
    /** хост: предмет в использовании (еда, лук) и с какого тика */
    public int useItemId, useItemStart;
    public int sentHealth = -999;
    /** хост: открытый контейнер (ContainerMenu) и его номер */
    public TileEntity container;
    public int containerId, containerCounter;
    final int[] cId = new int[27], cCount = new int[27], cAux = new int[27];
    int cCook = -1, cBurn = -1, cLit = -1;
    /** Mob::lerpTo */
    float lx, ly, lz, lyr, lxr;
    int lSteps;
    float walkDist, walkDistO;

    public RemotePlayer(Level l) {
        super(l);
        bbW = 0.6f;
        bbH = 1.8f;
        health = 20;
        lastHealth = 20;
        yRot = 0;
        stepHeight = 0;
    }

    public int typeId() {
        return 63;
    }

    public int maxHealth() {
        return 20;
    }

    public int heldId() {
        return inv.cId[0];
    }

    public int heldAux() {
        return inv.cAux[0];
    }

    public void setHeld(int id, int aux) {
        inv.selected = 0;
        inv.cId[0] = id;
        inv.cAux[0] = aux;
    }

    public float headHeight() {
        return Player.EYE;
    }

    /** Player::getHeadHeight: 0.12 над глазами (Pos игрока — глаза) */
    protected float headHeightOrig() {
        return 0.12f;
    }

    protected float savedYOffset() {
        return Player.EYE;
    }

    public boolean isPushable() {
        return !removed && !sleeping;
    }

    public boolean isPickable() {
        return !removed && health > 0;
    }

    public boolean isPlayerLike() {
        return true;
    }

    /** Mob::lerpTo: ноги + 3 шага сглаживания */
    public void lerpTo(float x, float y, float z, float yRot, float xRot) {
        lx = x;
        ly = y;
        lz = z;
        lyr = yRot;
        lxr = xRot;
        lSteps = 3;
    }

    public void moveTo(float x, float y, float z, float yRot, float xRot) {
        setPos(x, y, z);
        this.yRot = yRotO = yRot;
        this.xRot = xRotO = xRot;
        bodyRot = bodyRotO = yRot;
        lSteps = 0;
    }

    public void tick() {
        // Entity::baseTick + Mob::baseTick: вода, огонь, лава, удушье (на клиенте урон не проходит — его считает хост)
        baseTick();
        updateAttackAnim();
        walkDistO = walkDist;
        if (lSteps > 0) {
            float nx = x + (lx - x) / lSteps, ny = y + (ly - y) / lSteps, nz = z + (lz - z) / lSteps;
            yRot += Mth.wrapDegrees(lyr - yRot) / lSteps;
            xRot += (lxr - xRot) / lSteps;
            lSteps--;
            x = nx;
            y = ny;
            z = nz;
        }
        if (sleeping) {
            if (++sleepTimer > Player.SLEEP_DURATION) sleepTimer = Player.SLEEP_DURATION;
        } else if (sleepTimer > 0 && ++sleepTimer >= Player.SLEEP_DURATION + Player.WAKE_UP_DURATION) sleepTimer = 0;
        float dx = x - xo, dz = z - zo;
        float side = Mth.sqrt(dx * dx + dz * dz);
        walkDist += side * 0.6f;
        walkSpeedO = walkSpeed;
        walkAnimO = walkAnim;
        float wst = side * 4;
        if (wst > 1) wst = 1;
        walkSpeed += (wst - walkSpeed) * 0.4f;
        walkAnim += walkSpeed;
        // HumanoidMobRenderer: тело к направлению шага, голова не дальше 75°
        float target = bodyRot;
        if (side > 0.05f) target = Mth.atan2(dz, dx) * 180f / Mth.PI - 90;
        if (attackAnim > 0) target = yRot;
        bodyRot += Mth.wrapDegrees(target - bodyRot) * 0.3f;
        float head = Mth.wrapDegrees(yRot - bodyRot);
        if (head < -75) head = -75;
        if (head >= 75) head = 75;
        bodyRot = yRot - head;
        if (head * head > 50 * 50) bodyRot += head * 0.2f;
        tickCount++;
    }

    /** Player::hurt на хосте: творческий — неуязвим, сон прерывается. */
    public boolean hurt(int dmg, Entity source) {
        if (level.isClientSide) return false;
        if (level.gameType == 1) return false;
        if (health <= 0) return false;
        if (sleeping && Net.server != null) Net.server.wakeUp(this);
        if (source != null && (source instanceof Monster || source instanceof Arrow) && Options.peaceful) dmg = 0;
        if (dmg == 0) return false;
        if (MC.instance != null && MC.instance.debug) Log.i("NET rp hurt " + dmg + " by " + (source == null ? (isInWall() ? "wall" : "null") : source.getClass().getName()) + " y " + y + " fire " + fire + " air " + air);
        return super.hurt(dmg, source);
    }

    public void heal(int n) {
        if (health <= 0) return;
        health += n;
        if (health > 20) health = 20;
        invulnerableTime = 10;
    }

    public int armorValue() {
        int v = 0;
        for (int i = 0; i < 4; i++) v += Item.armorDefense(armor[i]);
        return v;
    }

    /** ServerPlayer::hurtArmor: владельцу — HurtArmorPacket, броню изнашивает клиент сам. */
    protected void hurtArmor(int dmg) {
        if (Net.server != null) Net.server.hurtArmor(this, dmg);
    }

    /** у игрока лута нет: вещи выбрасывает клиент пакетом SendInventory */
    protected void die(Entity source) {
        yd = 0.1f;
    }

    protected void outOfWorld() {
        hurt(4, null);
    }

    protected boolean makeStepSound() {
        return false;
    }

    public void save(Nbt.Compound t) {
    }

    public void load(Nbt.Compound t) {
    }
}
