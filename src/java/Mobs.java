/** Конкретные мобы MCPE 0.6.1 и фабрика по числовому id из entities.dat. */
public final class Mobs {
    public static final int CHICKEN = 10, COW = 11, PIG = 12, SHEEP = 13,
            ZOMBIE = 32, CREEPER = 33, SKELETON = 34, SPIDER = 35, PIG_ZOMBIE = 36;

    public static Entity create(int id, Level l) {
        switch (id) {
            case CHICKEN: return new Chicken(l);
            case COW: return new Cow(l);
            case PIG: return new Pig(l);
            case SHEEP: return new Sheep(l);
            case ZOMBIE: return new Zombie(l);
            case CREEPER: return new Creeper(l);
            case SKELETON: return new Skeleton(l);
            case SPIDER: return new Spider(l);
            case PIG_ZOMBIE: return new PigZombie(l);
            case 64: return new ItemEntity(l);
            case 65: return new PrimedTnt(l);
            case 66: return new FallingTile(l);
            case 80: return new Arrow(l);
            case 81: return new ThrownItem(l, 81);
            case 82: return new ThrownItem(l, 82);
            case 83: return new Painting(l);
        }
        return null;
    }

    // ---- животные ----

    public static final class Pig extends Animal {
        public Pig(Level l) { super(l); bbW = 0.9f; bbH = 0.9f; }
        public int typeId() { return PIG; }
        protected String ambientSound() { return "mob.pig"; }
        protected String hurtSound() { return "mob.pig"; }
        protected String deathSound() { return "mob.pigdeath"; }
        protected void dropLoot() { drop(319, rnd.nextInt(3), 0); }
    }

    public static final class Cow extends Animal {
        public Cow(Level l) { super(l); bbW = 0.9f; bbH = 1.3f; }
        public int typeId() { return COW; }
        protected String ambientSound() { return "mob.cow"; }
        protected String hurtSound() { return "mob.cowhurt"; }
        protected String deathSound() { return "mob.cowhurt"; }
        protected float soundVolume() { return 0.4f; }
        protected void dropLoot() {
            drop(334, rnd.nextInt(3), 0);
            drop(363, rnd.nextInt(3) + 1, 0);
        }
    }

    /** Курица 0.6.1: яиц не несёт (в Chicken::aiStep релиза кладка вырезана). */
    public static final class Chicken extends Animal {
        public float flap, oFlap, flapSpeed, oFlapSpeed, flapping = 1;
        public Chicken(Level l) { super(l); bbW = 0.3f; bbH = 0.7f; }
        public int typeId() { return CHICKEN; }
        protected String ambientSound() { return "mob.chicken"; }
        protected String hurtSound() { return "mob.chickenhurt"; }
        protected String deathSound() { return "mob.chickenhurt"; }
        public int maxHealth() { return 4; }
        protected void causeFall(float d) { }
        /** Chicken::aiStep */
        protected void aiStep() {
            super.aiStep();
            oFlap = flap;
            oFlapSpeed = flapSpeed;
            flapSpeed += (onGround ? -1 : 4) * 0.3f;
            if (flapSpeed < 0) flapSpeed = 0;
            if (flapSpeed > 1) flapSpeed = 1;
            if (!onGround && flapping < 1) flapping = 1;
            flapping *= 0.9f;
            if (!onGround && yd < 0) yd *= 0.6f;
            flap += flapping * 2;
        }
        protected void dropLoot() {
            drop(288, rnd.nextInt(3), 0);
            drop(365, 1, 0);
        }
    }

    public static final class Sheep extends Animal {
        static final int EAT_TICKS = 40;
        public int color;
        public boolean sheared;
        public int eatTime;
        public Sheep(Level l) { super(l); bbW = 0.9f; bbH = 1.3f; }
        /** Sheep::getSheepColor (MobSpawner::finalizeMobSettings) */
        public static int randomColor(java.util.Random r) {
            int n = r.nextInt(100);
            if (n < 5) return 15;
            if (n < 10) return 7;
            if (n < 15) return 8;
            if (n < 18) return 12;
            if (r.nextInt(500) == 0) return 6;
            return 0;
        }
        public int typeId() { return SHEEP; }
        public int maxHealth() { return 8; }
        protected String ambientSound() { return "mob.sheep"; }
        protected String hurtSound() { return "mob.sheep"; }
        protected String deathSound() { return "mob.sheep"; }
        protected boolean shouldHoldGround() { return eatTime > 0; }
        protected void jumpFromGround() { if (eatTime <= 0) super.jumpFromGround(); }
        protected void aiStep() {
            super.aiStep();
            if (eatTime > 0) eatTime--;
        }
        /** Sheep::updateAi: изредка щиплет траву (трава -> земля, шерсть отрастает). */
        protected void updateAi() {
            super.updateAi();
            if (noAi) return;
            int xx = Mth.floor(x), yy = Mth.floor(y), zz = Mth.floor(z);
            if (!isPathFinding() && eatTime <= 0 && rnd.nextInt(1000) == 0) {
                if (level.getTile(xx, yy - 1, zz) == Tile.GRASS) eatTime = EAT_TICKS;
            } else if (eatTime == 4) {
                if (level.getTile(xx, yy - 1, zz) == Tile.GRASS) {
                    if (MC.instance != null) MC.instance.particles.destroy(xx, yy - 1, zz, Tile.GRASS, 0);
                    level.setTile(xx, yy - 1, zz, Tile.DIRT);
                    sheared = false;
                }
            }
        }
        /** Sheep::getHeadEatPositionScale */
        public float headEatPos(float a) {
            if (eatTime <= 0) return 0;
            if (eatTime >= 4 && eatTime <= EAT_TICKS - 4) return 1;
            if (eatTime < 4) return (eatTime - a) / 4f;
            return -((eatTime - EAT_TICKS) - a) / 4f;
        }
        /** Sheep::getHeadEatAngleScale (null — обычный наклон головы) */
        public float headEatAngle(float a, float normal) {
            if (eatTime > 4 && eatTime <= EAT_TICKS - 4) {
                float sc = ((eatTime - 4) - a) / (float) (EAT_TICKS - 8);
                return Mth.PI * 0.2f + Mth.PI * 0.07f * Mth.sin(sc * 28.7f);
            }
            if (eatTime > 0) return Mth.PI * 0.2f;
            return normal;
        }
        /** Sheep::interact: ножницы — 1..3 шерсти. */
        public boolean shear() {
            if (sheared || health <= 0) return false;
            sheared = true;
            int n = 1 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                ItemEntity e = new ItemEntity(level, x, y + 1f, z, Tile.WOOL, 1, color);
                e.yd += rnd.nextFloat() * 0.05f;
                e.xd += (rnd.nextFloat() - rnd.nextFloat()) * 0.1f;
                e.zd += (rnd.nextFloat() - rnd.nextFloat()) * 0.1f;
                level.addEntity(e);
            }
            return true;
        }
        protected void dropLoot() { if (!sheared) drop(Tile.WOOL, 1, color); }
        public void save(Nbt.Compound t) {
            super.save(t);
            t.putBoolean("Sheared", sheared);
            t.putByte("Color", color);
        }
        public void load(Nbt.Compound t) {
            super.load(t);
            sheared = t.getBoolean("Sheared");
            color = t.getByte("Color") & 15;
        }
    }

    // ---- монстры ----

    public static class Zombie extends Monster {
        public Zombie(Level l) { super(l); attackDamage = 4; runSpeed = 0.5f; }
        public int typeId() { return ZOMBIE; }
        public int maxHealth() { return 12; }
        protected String ambientSound() { return "mob.zombie"; }
        protected String hurtSound() { return "mob.zombiehurt"; }
        protected String deathSound() { return "mob.zombiedeath"; }
        public int armorValue() { return 2; }
        private int fireCheckTick;
        protected void aiStep() {
            if ((++fireCheckTick & 1) != 0) sunHurt();
            super.aiStep();
        }
        /** Zombie::die: перо с шансом 1/4 */
        protected void dropLoot() {
            if (rnd.nextInt(4) == 0) drop(288, 1, 0);
        }
    }

    /** PigZombie 0.6.1 (наследник Zombie): злится от удара игрока и зовёт соседей. */
    public static final class PigZombie extends Zombie {
        int anger, playAngrySoundIn, stunned = 3 * 20;
        public PigZombie(Level l) { super(l); attackDamage = 5; runSpeed = 0.7f; }
        public int typeId() { return PIG_ZOMBIE; }
        protected String ambientSound() { return "mob.zombiepig.zpig"; }
        protected String hurtSound() { return "mob.zombiepig.zpighurt"; }
        protected String deathSound() { return "mob.zombiepig.zpigdeath"; }
        protected boolean fireImmune() { return true; }
        protected int attackTimeTicks() { return 40; }
        public void tick() {
            if (stunned > 0) stunned--;
            if (playAngrySoundIn > 0 && --playAngrySoundIn == 0 && MC.instance != null)
                MC.instance.playSoundAt("mob.zombiepig.zpigangry", x, y, z, soundVolume() * 2);
            super.tick();
        }
        protected Entity findAttackTarget() {
            if (stunned != 0) return null;
            if (anger == 0) {
                Entity t = super.findAttackTarget();
                if (t != null && distTo(t) < 5) return t;
                return null;
            }
            return super.findAttackTarget();
        }
        public boolean hurt(int dmg, Entity source) {
            Entity attacker = null;
            if (source instanceof Player) attacker = source;
            else if (source instanceof Arrow && ((Arrow) source).owner instanceof Player) attacker = ((Arrow) source).owner;
            if (attacker != null) {
                java.util.Vector v = level.entities;
                for (int i = 0; i < v.size(); i++) {
                    Object o = v.elementAt(i);
                    if (!(o instanceof PigZombie)) continue;
                    PigZombie pz = (PigZombie) o;
                    if (Math.abs(pz.x - x) < 12 + bbW && Math.abs(pz.y - y) < 12 + bbH && Math.abs(pz.z - z) < 12 + bbW) pz.alert(source);
                }
            }
            return super.hurt(dmg, source);
        }
        void alert(Entity t) {
            attackTarget = t;
            anger = 20 * 20 * rnd.nextInt(20 * 20);
            playAngrySoundIn = rnd.nextInt(20 * 2);
        }
        protected void dropLoot() {
            drop(266, rnd.nextInt(2), 0);
            super.dropLoot(); // Zombie::die: перо 1/4 и у свинозомби
        }
        public void save(Nbt.Compound t) { super.save(t); t.putShort("Anger", anger); }
        public void load(Nbt.Compound t) { super.load(t); anger = t.getShort("Anger"); }
    }

    public static final class Creeper extends Monster {
        static final int MAX_SWELL = 30;
        public int fuse, fuseO, swellDir = -1;
        public Creeper(Level l) { super(l); }
        public int typeId() { return CREEPER; }
        public int maxHealth() { return 16; }
        protected String hurtSound() { return "mob.creeper"; }
        protected String deathSound() { return "mob.creeperdeath"; }
        public void tick() {
            fuseO = fuse;
            super.tick();
            if (level.isClientSide) {
                // Creeper::tick у клиента: раздувание по swellDir от хоста
                fuse += swellDir;
                if (fuse < 0) fuse = 0;
                if (fuse >= MAX_SWELL) fuse = MAX_SWELL;
                return;
            }
            if (attackTarget == null && fuse > 0) {
                swellDir = -1;
                fuse--;
                if (fuse < 0) fuse = 0;
            }
        }
        protected void checkCantSeeTarget(Entity t, float d) {
            if (fuse > 0) {
                swellDir = -1;
                fuse--;
                if (fuse < 0) fuse = 0;
            }
        }
        /** Creeper::checkHurtTarget: шипит ближе 3, не сбрасывает до 7 */
        protected void checkHurtTarget(Entity t, float d) {
            if ((swellDir <= 0 && d < 3) || (swellDir > 0 && d < 7)) {
                if (fuse == 0 && MC.instance != null) MC.instance.playSoundAt("random.fuse", x, y, z, 1f);
                swellDir = 1;
                if (++fuse >= MAX_SWELL) {
                    level.explode(this, x, y, z, 2.4f);
                    remove();
                }
                holdGround = true;
            } else {
                swellDir = -1;
                if (--fuse < 0) fuse = 0;
            }
        }
        protected void dropLoot() { drop(289, rnd.nextInt(3), 0); }
    }

    public static final class Skeleton extends Monster {
        public Skeleton(Level l) { super(l); }
        public int typeId() { return SKELETON; }
        public int maxHealth() { return 10; }
        protected String ambientSound() { return "mob.skeleton"; }
        protected String hurtSound() { return "mob.skeletonhurt"; }
        protected String deathSound() { return "mob.skeletonhurt"; }
        private int fireCheckTick;
        protected void aiStep() {
            if ((++fireCheckTick & 1) != 0) sunHurt();
            super.aiStep();
        }
        /** Skeleton::checkHurtTarget: ближе 10 — стоит и стреляет раз в 3 с */
        protected void checkHurtTarget(Entity t, float d) {
            if (d >= 10) return;
            float dx = t.x - x, dz = t.z - z;
            if (attackTime == 0) {
                Arrow a = new Arrow(level, this);
                float dy = Level.origY(t) + (t instanceof Player || t instanceof RemotePlayer ? 0.12f : t.bbH * 0.85f) - 0.7f - a.y;
                float yo = Mth.sqrt(dx * dx + dz * dz) * 0.2f;
                if (MC.instance != null) MC.instance.playSoundAt("random.bow", x, y, z, 1f);
                level.addEntity(a);
                a.shoot(dx, dy + yo, dz, 1.6f, 32f);
                attackTime = 20 * 3;
            }
            yRot = Mth.atan2(dz, dx) * 180f / Mth.PI - 90;
            holdGround = true;
        }
        protected void dropLoot() {
            drop(262, rnd.nextInt(3), 0);
            drop(352, rnd.nextInt(3), 0);
        }
    }

    public static final class Spider extends Monster {
        boolean climbing;
        public Spider(Level l) { super(l); bbW = 1.4f; bbH = 0.9f; runSpeed = 0.5f; }
        public int typeId() { return SPIDER; }
        protected String ambientSound() { return "mob.spider"; }
        protected String hurtSound() { return "mob.spider"; }
        protected String deathSound() { return "mob.spiderdeath"; }
        public int maxHealth() { return 8; }
        protected boolean makeStepSound() { return false; }
        public void tick() {
            super.tick();
            climbing = horizontalCollision;
        }
        protected boolean onLadder() { return climbing; }
        /** Spider::findAttackTarget: только в темноте, видеть не обязательно */
        protected Entity findAttackTarget() {
            if (brightness() < 0.5f) return nearestPlayer(16);
            return null;
        }
        protected void checkHurtTarget(Entity t, float d) {
            if (brightness() > 0.5f && rnd.nextInt(100) == 0) {
                attackTarget = null;
                return;
            }
            if (d > 2 && d < 6 && rnd.nextInt(10) == 0) {
                if (onGround) {
                    float xdd = t.x - x, zdd = t.z - z;
                    float dd = Mth.sqrt(xdd * xdd + zdd * zdd);
                    xd = (xdd / dd * 0.5f) * 0.8f + xd * 0.2f;
                    zd = (zdd / dd * 0.5f) * 0.8f + zd * 0.2f;
                    yd = 0.4f;
                }
            } else super.checkHurtTarget(t, d);
        }
        protected void dropLoot() { drop(287, rnd.nextInt(3), 0); }
    }
}
