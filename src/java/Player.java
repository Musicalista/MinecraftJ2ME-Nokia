/** Игрок: ввод, полёт, инвентарь, сохранение в NBT (поле Player в level.dat). */
public final class Player extends Mob {
    public static final float EYE = 1.62f;
    public boolean flying, mayfly, invulnerable, instabuild, sneaking;
    /** IMoveInput::wantUp/wantDown — подъём/спуск в полёте (крестовина). */
    public boolean wantUp, wantDown;
    /** Mob::lastHealth — для мигания сердец. */
    public int lastHealth = 20;
    /** Player::sleepCounter / playerIsSleeping. */
    public int sleepTimer;
    public boolean sleeping;
    public int bedX, bedY, bedZ;
    public static final int SLEEP_DURATION = 100, WAKE_UP_DURATION = 10;
    public float bob, bobO, tilt, tiltO;
    public int spawnX, spawnY, spawnZ;
    public boolean hasSpawn;
    public Inventory inv;
    /** RakNetGUID владельца (сетевая игра) */
    public long owner;
    /** броня: слоты 0 голова, 1 тело, 2 ноги, 3 ступни (id, Damage) */
    public final int[] armor = new int[4], armorDmg = new int[4];

    public Player(Level level, boolean creative) {
        super(level);
        bbW = 0.6f;
        bbH = 1.8f;
        inv = new Inventory(creative);
        mayfly = creative;
        invulnerable = creative;
        instabuild = creative;
        health = 20;
    }

    public int typeId() {
        return 63;
    }

    public int maxHealth() {
        return 20;
    }

    public float headHeight() {
        return EYE;
    }

    protected boolean avoidEdges() {
        return sneaking && !flying;
    }

    /** Player::getHeadHeight: 0.12 над глазами */
    protected float headHeightOrig() {
        return 0.12f;
    }

    public void tick() {
        // Entity::baseTick + Mob::baseTick: вода, огонь, лава, удушье в блоке, утопление
        baseTick();
        bobO = bob;
        tiltO = tilt;
        updateAttackAnim();
        if (health <= 0) return;
        // Player::tick: счётчик сна (в кровати — до 100, после пробуждения — затухание до 110)
        if (sleeping) {
            if (++sleepTimer > SLEEP_DURATION) sleepTimer = SLEEP_DURATION;
            xd = yd = zd = 0;
            return;
        } else if (sleepTimer > 0 && ++sleepTimer >= SLEEP_DURATION + WAKE_UP_DURATION) sleepTimer = 0;
        // Player::tick: SimpleFoodData::tick — съеденное лечит сразу
        if (foodLevel > 0 && health < 20) {
            health = Math.min(20, health + foodLevel);
            foodLevel = 0;
        }
        tickUsingItem();
        // Player::aiStep: мирный режим — +1 здоровья раз в 12 секунд
        if (Options.peaceful && health < 20 && tickCount % (12 * 20) == 0) heal(1);

        float fwd = moveForward, str = moveStrafe;
        if (sneaking && !flying) { fwd *= 0.3f; str *= 0.3f; }

        if (flying) {
            // LocalPlayer::aiStep: wantUp/wantDown
            if (wantUp) yd += 0.15f;
            if (wantDown || sneaking) yd -= 0.15f;
        } else if (jumping) {
            // Mob::aiStep: прыжок без задержки
            if (inWater || inLava) yd += 0.04f;
            else if (onGround) yd = 0.42f;
        } else if (autoJumpNext && onGround && !inWater && !inLava) yd = 0.42f;
        autoJumpNext = false;

        if (inWater && !flying) {
            moveRelative(str, fwd, 0.02f);
            move(xd, yd, zd);
            xd *= 0.8f; yd *= 0.8f; zd *= 0.8f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f, zd)) yd = 0.3f;
        } else if (inLava && !flying) {
            moveRelative(str, fwd, 0.02f);
            move(xd, yd, zd);
            xd *= 0.5f; yd *= 0.5f; zd *= 0.5f;
            yd -= 0.02f;
            if (horizontalCollision && isFree(xd, yd + 0.6f, zd)) yd = 0.3f;
        } else {
            float friction = 0.91f;
            if (onGround) friction = groundFriction();
            float accel = onGround ? 0.1f * (0.16277136f / (friction * friction * friction)) : 0.02f;
            if (flying) accel = 0.05f;
            moveRelative(str, fwd, accel);
            boolean ladder = level.getTile(Mth.floor(x), Mth.floor(y), Mth.floor(z)) == Tile.LADDER;
            if (ladder) {
                float m = 0.15f;
                if (xd < -m) xd = -m; if (xd > m) xd = m;
                if (zd < -m) zd = -m; if (zd > m) zd = m;
                fallDistance = 0;
                if (yd < -0.15f) yd = -0.15f;
                if (sneaking && yd < 0) yd = 0;
            }
            move(xd, yd, zd);
            if (horizontalCollision && ladder) yd = 0.2f;
            else if (horizontalCollision && Options.autoJump && onGround && !flying && !sneaking) checkAutoJump(str, fwd);
            if (flying) yd *= 0.6f;
            else {
                yd -= 0.08f;
                yd *= 0.98f;
            }
            xd *= friction;
            zd *= friction;
        }
        if (flying && onGround && !jumping) flying = false;

        float dx = x - xo, dz = z - zo;
        float dist = Mth.sqrt(dx * dx + dz * dz);
        pushEntities();
        float tb = onGround && !flying ? Math.min(0.1f, dist) : 0f;
        bob += (tb - bob) * 0.4f;
        // Player::aiStep: наклон камеры в полёте/падении
        float tt = onGround ? 0 : Mth.atan2(-yd * 0.2f, 1f) * 15f;
        tilt += (tt - tilt) * 0.8f;
        bodyRot = yRot;
    }

    /** Custom: автопрыжок — при ходьбе в препятствие высотой в блок, над которым свободно, прыжок на следующем тике. */
    private boolean autoJumpNext;

    private void checkAutoJump(float str, float fwd) {
        float d = Mth.sqrt(str * str + fwd * fwd);
        if (d < 0.01f) return;
        float sn = Mth.sin(yRot * Mth.DEG), cs = Mth.cos(yRot * Mth.DEG);
        float dx = (str * cs - fwd * sn) / d * 0.3f, dz = (fwd * cs + str * sn) / d * 0.3f;
        if (!isFree(dx, 0.01f, dz) && isFree(dx, 1.05f, dz) && isFree(0, 1.05f, 0)) autoJumpNext = true;
    }

    protected boolean makeStepSound() {
        return true;
    }

    protected boolean isSneakingNow() {
        return sneaking;
    }

    /** Mob::heal */
    public void heal(int n) {
        if (health <= 0) return;
        health += n;
        if (health > 20) health = 20;
        invulnerableTime = 10;
    }

    protected void causeFall(float dist) {
        if (mayfly || invulnerable) return;
        if (level.isClientSide) {
            // LocalPlayer::causeFallDamage: урон считает хост (SetHealthPacket -64 + урон)
            int dmg = (int) Math.ceil(dist - 3f);
            if (dmg <= 0) return;
            if (MC.instance != null) MC.instance.playSoundAt(dmg > 4 ? "damage.fallbig" : "damage.fallsmall", x, y, z, 0.75f);
            if (Net.client != null) Net.client.fallDamage(dmg);
            return;
        }
        super.causeFall(dist);
    }

    /** LocalPlayer::hurtTo: здоровье от хоста, при уменьшении — анимация удара без брони. */
    public void hurtTo(int h) {
        int dmg = health - h;
        if (dmg <= 0) {
            health = h;
            return;
        }
        lastHurt = dmg;
        lastHealth = health;
        invulnerableTime = 20;
        health = h;
        hurtTime = 10;
        hurtDir = 0;
        if (sleeping && MC.instance != null) MC.instance.stopSleep(true);
        sound(hurtSound());
        if (health <= 0) die(null);
    }

    /** HurtArmorPacket: износ своей брони */
    public void hurtArmorNet(int dmg) {
        hurtArmor(dmg);
    }

    /** ответ хоста на EntityEvent(USE_ITEM_COMPLETE) */
    public void completeUsingItemNet() {
        if (useItemId != 0) completeUsingItem();
    }

    /** клиент: положение от хоста — только выйти из блоков, если надо */
    public void resetPosNet() {
        xo = x; yo = y; zo = z;
        xd = yd = zd = 0;
        fallDistance = 0;
    }

    // ---- использование предмета (Player::startUsingItem, LocalPlayer::tick) ----

    /** Player::useItem: id/aux предмета и сколько тиков осталось; SimpleFoodData::foodLevel */
    public int useItemId, useItemAux, useItemDuration, foodLevel;
    public static final int EAT_DURATION = 32, BOW_DURATION = 20 * 60 * 60;

    public boolean isUsingItem() {
        return useItemId != 0;
    }

    public void startUsingItem(int id, int aux, int duration) {
        if (useItemId == id && useItemAux == aux) return;
        useItemId = id;
        useItemAux = aux;
        useItemDuration = duration;
    }

    public void stopUsingItem() {
        useItemId = 0;
        useItemDuration = 0;
    }

    /** Сколько тиков предмет уже используется (натяжение лука). */
    public int getTicksUsingItem() {
        return isUsingItem() ? (useItemId == 261 ? BOW_DURATION : EAT_DURATION) - useItemDuration : 0;
    }

    /** LocalPlayer::tick: крошки и звук еды в последние 25 тиков, по окончании — съесть. */
    private void tickUsingItem() {
        if (useItemId == 0) return;
        if (inv.selId() == useItemId && inv.selCount() > 0) {
            if (useItemDuration <= 25 && useItemDuration % 4 == 0) spawnEatParticles(5);
            if (--useItemDuration == 0) {
                // LocalPlayer::tick: клиент сети доедает только по ответу хоста
                if (level.isClientSide && Net.client != null) Net.client.useItemComplete();
                else completeUsingItem();
            }
        } else stopUsingItem();
    }

    /** Player::spawnEatParticles: крошки иконки перед лицом, random.eat. */
    private void spawnEatParticles(int n) {
        if (Item.food[useItemId] <= 0 || MC.instance == null) return;
        float xx = -xRot * Mth.DEG, yy = -yRot * Mth.DEG;
        float cxr = Mth.cos(xx), sxr = Mth.sin(xx), cyr = Mth.cos(yy), syr = Mth.sin(yy);
        java.util.Random r = level.random;
        for (int i = 0; i < n; i++) {
            // Vec3::xRot затем Vec3::yRot
            float dx = (r.nextFloat() - 0.5f) * 0.1f, dy = Mth.random() * 0.1f + 0.1f, dz = 0;
            float ty = dy * cxr + dz * sxr, tz = dz * cxr - dy * sxr;
            dy = ty; dz = tz;
            float tx = dx * cyr + dz * syr; tz = dz * cyr - dx * syr;
            dx = tx; dz = tz;
            float px = (r.nextFloat() - 0.5f) * 0.3f, py = -r.nextFloat() * 0.6f - 0.3f, pz = 0.6f;
            ty = py * cxr + pz * sxr; tz = pz * cxr - py * sxr;
            py = ty; pz = tz;
            tx = px * cyr + pz * syr; tz = pz * cyr - px * syr;
            px = tx; pz = tz;
            MC.instance.particles.iconCrack(x + px, y + EYE + 0.12f + py, z + pz, dx, dy + 0.05f, dz, useItemId);
        }
        MC.instance.playSound("random.eat", 0.5f + 0.5f * r.nextInt(2));
    }

    /** Player::completeUsingItem + FoodItem::useTimeDepleted: -1 шт., пища в foodLevel, суп оставляет миску. */
    private void completeUsingItem() {
        spawnEatParticles(10);
        int id = useItemId;
        if (Item.food[id] > 0) {
            if (id == 282) inv.replaceSelected(281, 1, 0);
            else inv.useSelected();
            foodLevel = Math.min(Item.food[id] + foodLevel, 20);
            if (MC.instance != null) MC.instance.playSound("random.burp", 0.5f);
        }
        stopUsingItem();
    }

    /** Player::releaseUsingItem: лук стреляет (BowItem::releaseUsing), еда — отмена. */
    public void releaseUsingItem() {
        if (level.isClientSide && Net.client != null) Net.client.playerAction(Net.ACTION_RELEASE_USE_ITEM);
        if (useItemId == 261) {
            float pow = getTicksUsingItem() / 20f;
            pow = (pow * pow + pow * 2) / 3;
            if (pow >= 0.1f) {
                if (pow > 1) pow = 1;
                inv.damageSelected(1);
                if (MC.instance != null) MC.instance.playSound("random.bow", 1f);
                inv.removeResource(262);
                // BowItem::releaseUsing: стрелу создаёт хост
                if (!level.isClientSide) {
                    Arrow a = Arrow.fromPlayer(level, this, pow * 2f);
                    a.crit = pow == 1;
                    level.addEntity(a);
                }
            }
        }
        stopUsingItem();
    }

    public int armorValue() {
        int v = 0;
        for (int i = 0; i < 4; i++) v += Item.armorDefense(armor[i]);
        return v;
    }

    /** Player::hurtArmor */
    protected void hurtArmor(int dmg) {
        dmg = Math.max(1, dmg / 4);
        for (int i = 0; i < 4; i++) {
            if (!Item.isArmor(armor[i])) continue;
            armorDmg[i] += dmg;
            if (armorDmg[i] > Item.maxDamage[armor[i]]) {
                armor[i] = 0;
                armorDmg[i] = 0;
            }
        }
    }

    /** Player::hurt 0.6.1: неуязвимость, пробуждение, мирный режим гасит урон монстров и стрел. */
    public boolean hurt(int dmg, Entity source) {
        if (invulnerable || level.isClientSide) return false;
        noActionTime = 0;
        if (health <= 0) return false;
        if (sleeping && MC.instance != null) MC.instance.stopSleep(true);
        if (source != null && (source instanceof Monster || source instanceof Arrow) && Options.peaceful) dmg = 0;
        if (dmg == 0) return false;
        return super.hurt(dmg, source);
    }

    /** Player::die: отлететь от удара */
    protected void die(Entity source) {
        yd = 0.1f;
        if (source != null) {
            xd = -Mth.cos((hurtDir + yRot) * Mth.DEG) * 0.1f;
            zd = -Mth.sin((hurtDir + yRot) * Mth.DEG) * 0.1f;
        } else xd = zd = 0;
    }

    protected void outOfWorld() {
        int old = health;
        hurt(4, null);
        if (health >= old) actuallyHurt(4);
    }

    public boolean isDead() {
        return health <= 0;
    }

    public boolean isPickable() {
        return false;
    }

    public void respawn() {
        health = 20;
        deathTime = 0;
        fallDistance = 0;
        xd = yd = zd = 0;
        fire = 0;
        air = 300;
        hurtTime = 0;
        if (hasSpawn) setPos(spawnX + 0.5f, spawnY + 1f, spawnZ + 0.5f);
        else {
            // Level::getTopTileY: первый пустой блок над 63 — под кроной дерева, а не на ней
            int sx = level.spawnX, sz = level.spawnZ, y = 63;
            while (y < Level.HEIGHT && level.getTile(sx, y + 1, sz) != 0) y++;
            setPos(sx + 0.5f, y + 1, sz + 0.5f);
        }
        resetPos();
    }

    /** Entity::resetPos: поднять вверх, пока стоим внутри блоков. */
    public void resetPos() {
        while (y > 0 && y < Level.HEIGHT + 2 && !isFree(0, 0, 0)) setPos(x, y + 1, z);
        xd = yd = zd = 0;
    }

    public float getEyeY(float a) {
        // в кровати камера лежит (GameRenderer: heightOffset спящего)
        // Pos спящего = ноги + 0.2; камера: -(heightOffset-1.62+1) по y и сдвиг 0.3 вниз
        if (sleeping) return y + 0.2f + 0.12f;
        return yo + (y - yo) * a + EYE;
    }

    // ---- NBT ----

    protected float savedYOffset() {
        return EYE; // в MCPE в Pos игрока лежит позиция глаз
    }

    public void save(Nbt.Compound t) {
        saveTo(t);
    }

    public Nbt.Compound saveTo(Nbt.Compound t) {
        if (t == null) t = new Nbt.Compound();
        saveMob(t);
        t.map.remove("id");
        t.keys.removeElement("id");
        t.putList("Inventory", inv.save());
        Nbt.NList al = new Nbt.NList(Nbt.COMPOUND);
        for (int i = 0; i < 4; i++) {
            Nbt.Compound a = new Nbt.Compound();
            a.putByte("Count", armor[i] != 0 ? 1 : 0);
            a.putShort("Damage", armor[i] != 0 ? armorDmg[i] : 0);
            a.putShort("id", armor[i]);
            al.add(a);
        }
        t.putList("Armor", al);
        if (!t.contains("Score")) t.putInt("Score", 0);
        t.putInt("Dimension", 0);
        // Player::addAdditonalSaveData: состояние сна и кровать
        t.putBoolean("Sleeping", sleeping);
        t.putShort("SleepTimer", sleepTimer);
        t.putInt("BedPositionX", bedX);
        t.putInt("BedPositionY", bedY);
        t.putInt("BedPositionZ", bedZ);
        t.putInt("SpawnX", hasSpawn ? spawnX : 0);
        t.putInt("SpawnY", hasSpawn ? spawnY : -1);
        t.putInt("SpawnZ", hasSpawn ? spawnZ : 0);
        Nbt.Compound ab = new Nbt.Compound();
        ab.putBoolean("invulnerable", invulnerable);
        ab.putBoolean("flying", flying);
        ab.putBoolean("mayfly", mayfly);
        ab.putBoolean("instabuild", instabuild);
        t.putCompound("abilities", ab);
        return t;
    }

    public void load(Nbt.Compound t) {
        loadMob(t);
        if (health <= 0) health = 20;
        if (t.contains("SpawnX")) {
            spawnX = t.getInt("SpawnX");
            spawnY = t.getInt("SpawnY");
            spawnZ = t.getInt("SpawnZ");
            hasSpawn = spawnY >= 0 && !(spawnX == 0 && spawnY == 0 && spawnZ == 0);
        }
        inv.load(t.getList("Inventory"));
        Nbt.NList al = t.getList("Armor");
        if (al != null) {
            for (int i = 0; i < 4 && i < al.size(); i++) {
                Object o = al.get(i);
                if (!(o instanceof Nbt.Compound)) continue;
                Nbt.Compound a = (Nbt.Compound) o;
                int id = a.getShort("id") & 0xFFFF;
                if (Item.isArmor(id) && (a.getByte("Count") & 255) > 0) {
                    armor[i] = id;
                    armorDmg[i] = a.getShort("Damage");
                }
            }
        }
        Nbt.Compound ab = t.getCompound("abilities");
        if (ab != null) flying = ab.getBoolean("flying") && mayfly;
        // Player::readAdditionalSaveData: спал — снова лечь в ту же кровать (startSleepInBed с проверками)
        loadSleeping = false;
        if (t.contains("Sleeping") && t.contains("SleepTimer") && t.contains("BedPositionX")) {
            loadSleeping = t.getBoolean("Sleeping");
            sleepTimer = t.getShort("SleepTimer");
            bedX = t.getInt("BedPositionX");
            bedY = t.getInt("BedPositionY");
            bedZ = t.getInt("BedPositionZ");
        }
    }

    /** спал при сохранении — MC уложит после загрузки */
    public boolean loadSleeping;
}
