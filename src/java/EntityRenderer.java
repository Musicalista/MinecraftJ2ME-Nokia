import java.util.Hashtable;
import javax.microedition.m3g.*;

/** Отрисовка мобов, предметов, стрел и TNT. */
public final class EntityRenderer {
    final Hashtable apps = new Hashtable();
    final Fog fog;
    final Appearance terrainCut;
    Appearance itemsApp;
    Model pig, cow, sheep, sheepFur, chicken, zombie, skeleton, creeper, spider;
    /** игрок (HumanoidModel) и слои брони PlayerRenderer: armorParts1 (g=1), armorParts2 (g=0.5) */
    Model player, armor1, armor2;
    final Transform t = new Transform();

    public EntityRenderer(LevelRenderer lr) {
        fog = lr.fog;
        terrainCut = lr.app[Tile.L_CUTOUT];
        itemsApp = app("/gui/items.png");
        build();
    }

    private Appearance app(String path) {
        Appearance a = (Appearance) apps.get(path);
        if (a != null) return a;
        Texture2D tex = new Texture2D(R.image2D(path));
        tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
        tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
        tex.setBlending(Texture2D.FUNC_MODULATE);
        a = new Appearance();
        a.setTexture(0, tex);
        CompositingMode cm = new CompositingMode();
        cm.setAlphaThreshold(0.5f);
        a.setCompositingMode(cm);
        PolygonMode pm = new PolygonMode();
        pm.setCulling(PolygonMode.CULL_NONE);
        pm.setPerspectiveCorrectionEnable(true);
        a.setPolygonMode(pm);
        a.setFog(fog);
        apps.put(path, a);
        return a;
    }

    private void build() {
        pig = new Model(64, 32);
        pig.part(0, 12, -6).box(-4, -4, -8, 8, 8, 8, 0, 0).box(-2, 0, -9, 4, 3, 1, 16, 16);
        pig.part(0, 11, 2).box(-5, -10, -7, 10, 16, 8, 28, 8).xRot = Mth.PI / 2;
        quadLegs(pig, 6, 18, 3, 7, -5);
        pig.compile();

        cow = new Model(64, 32);
        cow.part(0, 4, -8).box(-4, -4, -6, 8, 8, 6, 0, 0).box(-5, -5, -4, 1, 3, 1, 22, 0).box(4, -5, -4, 1, 3, 1, 22, 0);
        cow.part(0, 5, 2).box(-6, -10, -7, 12, 18, 10, 18, 4).box(-2, 2, -8, 4, 6, 1, 52, 0).xRot = Mth.PI / 2;
        quadLegs(cow, 12, 12, 4, 7, -6);
        cow.compile();

        sheep = new Model(64, 32);
        sheep.part(0, 6, -8).box(-3, -4, -6, 6, 6, 8, 0, 0);
        sheep.part(0, 5, 2).box(-4, -10, -7, 8, 16, 6, 28, 8).xRot = Mth.PI / 2;
        quadLegs(sheep, 12, 12, 3, 7, -5);
        sheep.compile();

        sheepFur = new Model(64, 32);
        sheepFur.part(0, 6, -8).box(-3, -4, -4, 6, 6, 6, 0, 0, 0.6f);
        sheepFur.part(0, 5, 2).box(-4, -10, -7, 8, 16, 6, 28, 8, 1.75f).xRot = Mth.PI / 2;
        for (int i = 0; i < 4; i++) {
            float lx = (i & 1) == 0 ? -3 : 3, lz = i < 2 ? 7 : -5;
            sheepFur.part(lx, 12, lz).box(-2, 0, -2, 4, 6, 4, 0, 16, 0.5f);
        }
        sheepFur.compile();

        chicken = new Model(64, 32);
        chicken.part(0, 15, -4).box(-2, -6, -2, 4, 6, 3, 0, 0).box(-2, -4, -4, 4, 2, 2, 14, 0).box(-1, -2, -3, 2, 2, 2, 14, 4);
        chicken.part(0, 16, 0).box(-3, -4, -3, 6, 8, 6, 0, 9).xRot = Mth.PI / 2;
        chicken.part(-2, 19, 1).box(-1, 0, -3, 3, 5, 3, 26, 0);
        chicken.part(1, 19, 1).box(-1, 0, -3, 3, 5, 3, 26, 0);
        chicken.part(-4, 13, 0).box(0, 0, -3, 1, 4, 6, 24, 13);
        chicken.part(4, 13, 0).box(-1, 0, -3, 1, 4, 6, 24, 13);
        chicken.compile();

        zombie = humanoid(4);
        player = humanoidG(0);
        armor1 = humanoidG(1.0f);
        armor2 = humanoidG(0.5f);
        skeleton = humanoid(2);

        creeper = new Model(64, 32);
        creeper.part(0, 4, 0).box(-4, -8, -4, 8, 8, 8, 0, 0);
        creeper.part(0, 4, 0).box(-4, 0, -2, 8, 12, 4, 16, 16);
        creeper.part(-2, 16, 4).box(-2, 0, -2, 4, 6, 4, 0, 16);
        creeper.part(2, 16, 4).box(-2, 0, -2, 4, 6, 4, 0, 16);
        creeper.part(-2, 16, -4).box(-2, 0, -2, 4, 6, 4, 0, 16);
        creeper.part(2, 16, -4).box(-2, 0, -2, 4, 6, 4, 0, 16);
        creeper.compile();

        spider = new Model(64, 32);
        spider.part(0, 15, -3).box(-4, -4, -8, 8, 8, 8, 32, 4);
        spider.part(0, 15, 0).box(-3, -3, -3, 6, 6, 6, 0, 0);
        spider.part(0, 15, 9).box(-5, -4, -6, 10, 8, 12, 0, 12);
        for (int i = 0; i < 8; i++) {
            boolean left = (i & 1) == 0;
            float z = 2 - (i >> 1);
            Model.Part p = spider.part(left ? -4 : 4, 15, z);
            if (left) p.box(-15, -1, -1, 16, 2, 2, 18, 0);
            else p.box(-1, -1, -1, 16, 2, 2, 18, 0);
        }
        spider.compile();
    }

    private static void quadLegs(Model m, int h, float py, float lx, float back, float front) {
        m.part(-lx, py, back).box(-2, 0, -2, 4, h, 4, 0, 16);
        m.part(lx, py, back).box(-2, 0, -2, 4, h, 4, 0, 16);
        m.part(-lx, py, front).box(-2, 0, -2, 4, h, 4, 0, 16);
        m.part(lx, py, front).box(-2, 0, -2, 4, h, 4, 0, 16);
    }

    private static Model humanoid(int limb) {
        Model m = new Model(64, 32);
        m.part(0, 0, 0).box(-4, -8, -4, 8, 8, 8, 0, 0);
        m.part(0, 0, 0).box(-4, 0, -2, 8, 12, 4, 16, 16);
        int o = limb / 2;
        m.part(-5, 2, 0).box(limb == 4 ? -3 : -1, -2, -o, limb, 12, limb, 40, 16);
        m.part(5, 2, 0).box(-1, -2, -o, limb, 12, limb, 40, 16);
        m.part(-2, 12, 0).box(-o, 0, -o, limb, 12, limb, 0, 16);
        m.part(2, 12, 0).box(-o, 0, -o, limb, 12, limb, 0, 16);
        return m.compile();
    }

    /** HumanoidModel(g): голова, тело, руки, ноги — части 0..5. */
    private static Model humanoidG(float g) {
        Model m = new Model(64, 32);
        m.part(0, 0, 0).box(-4, -8, -4, 8, 8, 8, 0, 0, g);
        m.part(0, 0, 0).box(-4, 0, -2, 8, 12, 4, 16, 16, g);
        m.part(-5, 2, 0).box(-3, -2, -2, 4, 12, 4, 40, 16, g);
        m.part(5, 2, 0).box(-1, -2, -2, 4, 12, 4, 40, 16, g);
        m.part(-2, 12, 0).box(-2, 0, -2, 4, 12, 4, 0, 16, g);
        m.part(2, 12, 0).box(-2, 0, -2, 4, 12, 4, 0, 16, g);
        return m.compile();
    }

    private static final String[] ARMOR_TEX = { "cloth", "chain", "iron", "diamond", "gold" };

    /** HumanoidModel::setupAnim (ходьба) + PlayerRenderer::prepareArmor; base — ноги игрока, лицом к +z мира модели. */
    public void renderHumanoidWithArmor(Graphics3D g, Transform base, int[] armor, float time, float r, float headYaw, float headPitch, int color) {
        renderHumanoidWithArmor(g, base, armor, time, r, headYaw, headPitch, color, 0);
    }

    /** hurt — цвет второго прохода при уроне (0 — нет) */
    public void renderHumanoidWithArmor(Graphics3D g, Transform base, int[] armor, float time, float r, float headYaw, float headPitch, int color, int hurt) {
        float c0 = Mth.cos(time * 0.6662f) * r, c1 = Mth.cos(time * 0.6662f + Mth.PI) * r;
        Model[] ms = { player, armor1, armor2 };
        for (int k = 0; k < 3; k++) {
            Model m = ms[k];
            head(m, 0, headYaw, headPitch);
            part(m, 2).xRot = c1;
            part(m, 3).xRot = c0;
            part(m, 4).xRot = c0 * 1.4f;
            part(m, 5).xRot = c1 * 1.4f;
        }
        player.render(g, base, app("/mob/char.png"), color);
        if (hurt != 0) overlay(g, player, base, hurt);
        for (int layer = 0; layer < 4; layer++) {
            int id = armor[layer];
            if (!Item.isArmor(id)) continue;
            Model m = layer == 2 ? armor2 : armor1;
            part(m, 0).visible = layer == 0;
            part(m, 1).visible = layer == 1 || layer == 2;
            part(m, 2).visible = layer == 1;
            part(m, 3).visible = layer == 1;
            part(m, 4).visible = layer == 2 || layer == 3;
            part(m, 5).visible = layer == 2 || layer == 3;
            m.render(g, base, app("/armor/" + ARMOR_TEX[(id - 298) >> 2] + "_" + (layer == 2 ? 2 : 1) + ".png"), color);
            if (hurt != 0) overlay(g, m, base, hurt);
        }
    }

    // ---- отрисовка ----

    /** EntityRenderDispatcher::playerRotY — поворот камеры (для плоских предметов) */
    public float playerRotY;

    public void render(Graphics3D g, Level level, float a, float camX, float camY, float camZ, Frustum fr, float dist) {
        java.util.Vector v = level.entities;
        float maxD = Math.min(64f, dist);
        // модели мобов — пакетом, одним render(Group) в конце (каждый render() в JL-Mod ~0.3 мс)
        Model.beginBatch(batch);
        try {
            renderEntities(g, level, a, camX, camY, camZ, fr, maxD, v);
        } finally {
            Model.endBatch(g);
        }
    }

    private final Group batch = new Group();

    private void renderEntities(Graphics3D g, Level level, float a, float camX, float camY, float camZ, Frustum fr, float maxD, java.util.Vector v) {
        for (int i = 0; i < v.size(); i++) {
            Entity e = (Entity) v.elementAt(i);
            float ex = e.xo + (e.x - e.xo) * a, ey = e.yo + (e.y - e.yo) * a, ez = e.zo + (e.z - e.zo) * a;
            float dx = ex - camX, dz = ez - camZ;
            float d2 = dx * dx + dz * dz;
            if (d2 > maxD * maxD) continue;
            // отсечение по пирамиде видимости (рамка с запасом на анимацию и модели крупнее рамки)
            float r = e.bbW * 0.5f + 0.75f;
            if (!fr.visible(ex - r, ey - 0.5f, ez - r, ex + r, ey + e.bbH + 0.75f, ez + r, maxD)) continue;
            int br = LevelRenderer.BR[level.getRawBrightness(Mth.floor(ex), Mth.floor(ey + e.bbH * 0.5f), Mth.floor(ez))];
            if (e instanceof RemotePlayer) renderRemote(g, (RemotePlayer) e, ex, ey, ez, a, br, level);
            else if (e instanceof Mob) renderMob(g, (Mob) e, ex, ey, ez, a, br);
            else if (e instanceof ItemEntity) renderItem(g, (ItemEntity) e, ex, ey, ez, a, br, playerRotY);
            else if (e instanceof PrimedTnt) renderTnt(g, (PrimedTnt) e, ex, ey, ez, a, br);
            else if (e instanceof Arrow) renderArrow(g, (Arrow) e, ex, ey, ez, a, br);
            else if (e instanceof Painting) renderPainting(g, (Painting) e, level);
            else if (e instanceof FallingTile) renderFalling(g, (FallingTile) e, ex, ey, ez, br);
            else if (e instanceof ThrownItem) renderThrown(g, (ThrownItem) e, ex, ey, ez, br);
        }
    }

    /** Игрок при виде от третьего лица (HumanoidMobRenderer: тело по взгляду, руки и ноги по шагам). */
    public void renderPlayer(Graphics3D g, Player p, float a, Level level) {
        float x = p.xo + (p.x - p.xo) * a, y = p.yo + (p.y - p.yo) * a, z = p.zo + (p.z - p.zo) * a;
        int br = LevelRenderer.BR[level.getRawBrightness(Mth.floor(x), Mth.floor(y + 1), Mth.floor(z))];
        t.setIdentity();
        if (p.sleeping && p.health > 0) {
            // PlayerRenderer::setupPosition/setupRotations: сдвиг bedOffset 1.8 к ногам, getSleepRotation, лёжа на спине
            int dir = level.getData(p.bedX, p.bedY, p.bedZ) & 3;
            br = LevelRenderer.BR[level.getRawBrightness(p.bedX, p.bedY + 1, p.bedZ)];
            t.postTranslate(x + BED_OFF_X[dir], y, z + BED_OFF_Z[dir]);
            t.postRotate(SLEEP_ROT[dir], 0, 1, 0);
            t.postRotate(90f, 0, 0, 1);
            t.postRotate(270f, 0, 1, 0);
            renderHumanoidWithArmor(g, t, p.armor, 0, 0, 0, 0, gray(br, 0), hurtArgb(p, br));
            return;
        }
        t.postTranslate(x, y, z);
        t.postRotate(180f - p.yRot, 0, 1, 0);
        float walk = (p.walkDistO + (p.walkDist - p.walkDistO) * a) * 2.5f;
        float speed = Math.min(1f, (p.walkDist - p.walkDistO) * 6f);
        renderHumanoidWithArmor(g, t, p.armor, walk, speed, 0, p.xRot * Mth.DEG, gray(br, 0), hurtArgb(p, br));
    }

    /** Игрок сети (PlayerRenderer): тело по шагам, голова по взгляду, броня; во сне — лёжа на кровати. */
    private void renderRemote(Graphics3D g, RemotePlayer p, float x, float y, float z, float a, int br, Level level) {
        int hurt = p.hurtTime > 0 || p.deathTime > 0 ? 0x66000000 | (br << 16) : 0;
        t.setIdentity();
        if (p.sleeping && p.health > 0 && level.getTile(p.bedX, p.bedY, p.bedZ) == Tile.BED) {
            int dir = level.getData(p.bedX, p.bedY, p.bedZ) & 3;
            br = LevelRenderer.BR[level.getRawBrightness(p.bedX, p.bedY + 1, p.bedZ)];
            t.postTranslate(x + BED_OFF_X[dir], y, z + BED_OFF_Z[dir]);
            t.postRotate(SLEEP_ROT[dir], 0, 1, 0);
            t.postRotate(90f, 0, 0, 1);
            t.postRotate(270f, 0, 1, 0);
            renderHumanoidWithArmor(g, t, p.armor, 0, 0, 0, 0, gray(br, 0), hurt);
            return;
        }
        float body = p.bodyRotO + Mth.wrapDegrees(p.bodyRot - p.bodyRotO) * a;
        t.postTranslate(x, y, z);
        t.postRotate(180f - body, 0, 1, 0);
        if (p.deathTime > 0) {
            float f = Math.min(1f, Mth.sqrt(Math.max(0, (p.deathTime + a - 1f) / 20f * 1.6f)));
            t.postRotate(f * 90f, 0, 0, 1);
        }
        float walk = p.walkAnimO + (p.walkAnim - p.walkAnimO) * a;
        float sp = Math.min(1f, p.walkSpeedO + (p.walkSpeed - p.walkSpeedO) * a);
        float headYaw = Mth.wrapDegrees(p.yRot - body) * Mth.DEG;
        renderHumanoidWithArmor(g, t, p.armor, walk, sp, headYaw, p.xRot * Mth.DEG, gray(br, 0), hurt);
    }

    /** MobRenderer::render: при уроне и смерти игрока — второй проход (br, 0, 0, 0.4) по модели и броне. */
    private static int hurtArgb(Player p, int br) {
        return p.hurtTime > 0 || p.deathTime > 0 ? 0x66000000 | (br << 16) : 0;
    }

    static final float[] BED_OFF_X = { 0, 1.8f, 0, -1.8f }, BED_OFF_Z = { -1.8f, 0, 1.8f, 0 }, SLEEP_ROT = { 90, 0, 270, 180 };

    private static int gray(int br, int hurt) {
        if (hurt > 0) return 0xFF000000 | (br << 16) | ((br / 2) << 8) | (br / 2);
        return 0xFF000000 | (br << 16) | (br << 8) | br;
    }

    private Appearance overlayApp;

    /** MobRenderer::render: при уроне и смерти — второй проход цветом (br, 0, 0, 0.4) без текстуры. */
    private void overlay(Graphics3D g, Model md, int argb) {
        overlay(g, md, t, argb);
    }

    private void overlay(Graphics3D g, Model md, Transform base, int argb) {
        if (overlayApp == null) {
            overlayApp = new Appearance();
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            cm.setDepthWriteEnable(false);
            overlayApp.setCompositingMode(cm);
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            overlayApp.setPolygonMode(pm);
            overlayApp.setFog(fog);
        }
        md.renderAgain(g, base, overlayApp, argb);
    }

    private void hurtOverlay(Graphics3D g, Model md, Mob m, int br) {
        if (m.hurtTime > 0 || m.deathTime > 0) overlay(g, md, 0x66000000 | (br << 16));
    }

    private void renderMob(Graphics3D g, Mob m, float x, float y, float z, float a, int br) {
        float body = m.bodyRotO + Mth.wrapDegrees(m.bodyRot - m.bodyRotO) * a;
        float walk = m.walkAnimO + (m.walkAnim - m.walkAnimO) * a;
        float sp = m.walkSpeed;
        float headYaw = Mth.wrapDegrees(m.yRot - body) * Mth.DEG;
        float headPitch = m.xRot * Mth.DEG;
        float swing = Mth.cos(walk * 0.6662f) * 1.4f * sp;
        int col = gray(br, 0);
        t.setIdentity();
        t.postTranslate(x, y, z);
        t.postRotate(180f - body, 0, 1, 0);
        if (m.deathTime > 0) {
            // MobRenderer::setupRotations: fall = sqrt((deathTime + a - 1) / 20 * 1.6)
            float f = Math.min(1f, Mth.sqrt(Math.max(0, (m.deathTime + a - 1f) / 20f * 1.6f)));
            t.postRotate(f * 90f, 0, 0, 1);
        }
        Model md;
        Appearance ap;
        switch (m.typeId()) {
            case Mobs.PIG:
            case Mobs.COW:
            case Mobs.SHEEP: {
                md = m.typeId() == Mobs.PIG ? pig : (m.typeId() == Mobs.COW ? cow : sheep);
                ap = app(m.typeId() == Mobs.PIG ? "/mob/pig.png" : (m.typeId() == Mobs.COW ? "/mob/cow.png" : "/mob/sheep.png"));
                legs(md, 2, swing);
                head(md, 0, headYaw, headPitch);
                // SheepModel::prepareMobModel: голова опускается к траве
                float eatPos = 0, eatPitch = headPitch;
                if (m instanceof Mobs.Sheep) {
                    Mobs.Sheep shp = (Mobs.Sheep) m;
                    eatPos = shp.headEatPos(a);
                    eatPitch = shp.headEatAngle(a, headPitch);
                    part(md, 0).py = 6 + eatPos * 9;
                    part(md, 0).xRot = eatPitch;
                }
                md.render(g, t, ap, col);
                hurtOverlay(g, md, m, br);
                if (m instanceof Mobs.Sheep && !((Mobs.Sheep) m).sheared) {
                    legs(sheepFur, 2, swing);
                    head(sheepFur, 0, headYaw, eatPitch);
                    part(sheepFur, 0).py = 6 + eatPos * 9;
                    int wc = Wool.COLOR[((Mobs.Sheep) m).color];
                    int r = ((wc >> 16) & 255) * br / 255, gg = ((wc >> 8) & 255) * br / 255, b = (wc & 255) * br / 255;
                    sheepFur.render(g, t, app("/mob/sheep_fur.png"), 0xFF000000 | (r << 16) | (gg << 8) | b);
                    hurtOverlay(g, sheepFur, m, br);
                }
                return;
            }
            case Mobs.CHICKEN: {
                md = chicken;
                head(md, 0, headYaw, headPitch);
                part(md, 2).xRot = swing;
                part(md, 3).xRot = -swing;
                Mobs.Chicken c = (Mobs.Chicken) m;
                // ChickenRenderer::getBob
                float fl = c.oFlap + (c.flap - c.oFlap) * a, fs = c.oFlapSpeed + (c.flapSpeed - c.oFlapSpeed) * a;
                float wing = (Mth.sin(fl) + 1f) * fs;
                part(md, 4).zRot = wing;
                part(md, 5).zRot = -wing;
                md.render(g, t, app("/mob/chicken.png"), col);
                hurtOverlay(g, md, m, br);
                return;
            }
            case Mobs.ZOMBIE:
            case Mobs.PIG_ZOMBIE:
            case Mobs.SKELETON: {
                md = m.typeId() == Mobs.SKELETON ? skeleton : zombie;
                ap = app(m.typeId() == Mobs.SKELETON ? "/mob/skeleton.png" : (m.typeId() == Mobs.ZOMBIE ? "/mob/zombie.png" : "/mob/pigzombie.png"));
                head(md, 0, headYaw, headPitch);
                part(md, 2).xRot = -Mth.PI / 2;
                part(md, 3).xRot = -Mth.PI / 2;
                part(md, 2).zRot = 0;
                part(md, 4).xRot = swing;
                part(md, 5).xRot = -swing;
                md.render(g, t, ap, col);
                hurtOverlay(g, md, m, br);
                return;
            }
            case Mobs.CREEPER: {
                Mobs.Creeper c = (Mobs.Creeper) m;
                // CreeperRenderer::scale: раздувание g^4, дрожь sin(g*100)
                float f = (c.fuseO + (c.fuse - c.fuseO) * a) / 28f;
                float wob = 1f + Mth.sin(f * 100f) * f * 0.01f;
                float gq = Mth.clamp(f, 0, 1);
                gq *= gq;
                gq *= gq;
                t.postScale((1f + gq * 0.4f) * wob, (1f + gq * 0.1f) / wob, (1f + gq * 0.4f) * wob);
                head(creeper, 0, headYaw, headPitch);
                part(creeper, 2).xRot = swing;
                part(creeper, 3).xRot = -swing;
                part(creeper, 4).xRot = -swing;
                part(creeper, 5).xRot = swing;
                creeper.render(g, t, app("/mob/creeper.png"), col);
                hurtOverlay(g, creeper, m, br);
                // CreeperRenderer::getOverlayColor: белая вспышка альфой swell * 0.2 через кадр
                if (((int) (f * 10)) % 2 != 0) overlay(g, creeper, ((int) Mth.clamp(f * 0.2f * 255, 0, 255) << 24) | 0xFFFFFF);
                return;
            }
            case Mobs.SPIDER: {
                head(spider, 0, headYaw, headPitch);
                float q = Mth.PI / 4;
                for (int i = 0; i < 8; i++) {
                    Model.Part p = part(spider, 3 + i);
                    boolean left = (i & 1) == 0;
                    int row = i >> 1;
                    float spread = (row == 0 ? -q : (row == 1 ? -q * 0.33f : (row == 2 ? q * 0.33f : q)));
                    float lift = (row == 0 || row == 3) ? q : q * 0.74f;
                    float wiggle = Mth.cos(walk * 0.6662f * 2f + row * Mth.PI / 2 + (left ? 0 : Mth.PI)) * 0.4f * sp;
                    p.yRot = (left ? spread : -spread) + wiggle;
                    p.zRot = left ? -lift : lift;
                }
                spider.render(g, t, app("/mob/spider.png"), col);
                hurtOverlay(g, spider, m, br);
                return;
            }
        }
    }

    private static Model.Part part(Model m, int i) {
        return (Model.Part) m.parts.elementAt(i);
    }

    private static void head(Model m, int i, float yaw, float pitch) {
        Model.Part p = part(m, i);
        p.yRot = yaw;
        p.xRot = pitch;
    }

    private static void legs(Model m, int first, float swing) {
        part(m, first).xRot = swing;
        part(m, first + 1).xRot = -swing;
        part(m, first + 2).xRot = -swing;
        part(m, first + 3).xRot = swing;
    }

    // ---- предметы ----

    private static final class Mesh {
        VertexBuffer vb;
        TriangleStripArray ib;
        boolean block;
    }

    /** ItemRenderer::render: блок — вращается, 1/4 размера; плоский — иконка 1/2 размера лицом к камере. */
    private final java.util.Random copyRnd = new java.util.Random();

    /** TakeAnimationParticle: подобранный предмет за 3 тика летит от места к игроку (Pos игрока - 0.5). */
    public void renderTake(Graphics3D g, Level level, ItemEntity e, int life, float a, Player p) {
        float time = (life + a) / 3f;
        time *= time;
        float xt = p.xo + (p.x - p.xo) * a, yt = p.yo + (p.y - p.yo) * a + Player.EYE - 0.5f, zt = p.zo + (p.z - p.zo) * a;
        float xx = e.x + (xt - e.x) * time, yy = e.y + (yt - e.y) * time, zz = e.z + (zt - e.z) * time;
        int br = LevelRenderer.BR[level.getRawBrightness(Mth.floor(xx), Mth.floor(yy + e.bbH / 2), Mth.floor(zz))];
        renderItem(g, e, xx, yy, zz, a, br, playerRotY);
    }

    private void renderItem(Graphics3D g, ItemEntity e, float x, float y, float z, float a, int br, float playerRotY) {
        copyRnd.setSeed(187);
        float bob = Mth.sin((e.age + a) / 10f + e.bobOffs) * 0.1f + 0.1f;
        int count = e.count > 20 ? 4 : (e.count > 5 ? 3 : (e.count > 1 ? 2 : 1));
        if (ItemMeshes.canRender(e.item)) {
            ItemMeshes.Mesh m = ItemMeshes.block(e.item, e.aux);
            float spin = ((e.age + a) / 20f + e.bobOffs) * 57.29578f;
            if (e.item == Tile.SAND || e.item == Tile.SANDSTONE) br = br * 8 / 10;
            m.vb.setDefaultColor(gray(br, 0));
            for (int i = 0; i < count; i++) {
                t.setIdentity();
                t.postTranslate(x, y + e.bbH / 2 + bob, z);
                t.postRotate(spin, 0, 1, 0);
                t.postScale(0.25f, 0.25f, 0.25f);
                if (i > 0) t.postTranslate((copyRnd.nextFloat() * 2 - 1) * 0.8f, (copyRnd.nextFloat() * 2 - 1) * 0.8f, (copyRnd.nextFloat() * 2 - 1) * 0.8f);
                g.render(m.vb, m.ib, terrainCut, t);
            }
            return;
        }
        ItemMeshes.Mesh m = ItemMeshes.sprite(e.item, e.aux);
        m.vb.setDefaultColor(gray(br, 0));
        for (int i = 0; i < count; i++) {
            t.setIdentity();
            t.postTranslate(x, y + e.bbH / 2 + bob, z);
            t.postScale(0.5f, 0.5f, 0.5f);
            if (i > 0) t.postTranslate((copyRnd.nextFloat() * 2 - 1) * 0.3f, (copyRnd.nextFloat() * 2 - 1) * 0.3f, (copyRnd.nextFloat() * 2 - 1) * 0.3f);
            t.postRotate(180 - playerRotY, 0, 1, 0);
            g.render(m.vb, m.ib, m.terrain ? terrainCut : itemsApp, t);
        }
    }

    /** FallingTileRenderer: блок целиком, по центру рамки. */
    private void renderFalling(Graphics3D g, FallingTile e, float x, float y, float z, int br) {
        if (!ItemMeshes.canRender(e.tile)) return;
        ItemMeshes.Mesh m = ItemMeshes.block(e.tile, e.data);
        t.setIdentity();
        t.postTranslate(x, y + 0.49f, z);
        m.vb.setDefaultColor(gray(br, 0));
        g.render(m.vb, m.ib, terrainCut, t);
    }

    /** ItemSpriteRenderer: снежок/яйцо — иконка 1/2 блока лицом к камере. */
    private void renderThrown(Graphics3D g, ThrownItem e, float x, float y, float z, int br) {
        ItemMeshes.Mesh m = ItemMeshes.sprite(e.type == 81 ? 332 : 344, 0);
        m.vb.setDefaultColor(gray(br, 0));
        t.setIdentity();
        t.postTranslate(x, y, z);
        t.postScale(0.5f, 0.5f, 0.5f);
        t.postRotate(180 - playerRotY, 0, 1, 0);
        g.render(m.vb, m.ib, m.terrain ? terrainCut : itemsApp, t);
    }

    private Appearance flashApp;

    /** TntRenderer: за 10 тиков до взрыва раздувается до 1.3; через каждые 5 тиков — белая вспышка (аддитивно). */
    private void renderTnt(Graphics3D g, PrimedTnt e, float x, float y, float z, float a, int br) {
        ItemMeshes.Mesh m = ItemMeshes.block(Tile.TNT, 0);
        t.setIdentity();
        t.postTranslate(x, y + 0.49f, z);
        float life = e.fuse - a + 1;
        if (life < 10) {
            float gg = Mth.clamp(1 - life / 10f, 0, 1);
            gg = gg * gg;
            gg = gg * gg;
            float s = 1 + gg * 0.3f;
            t.postScale(s, s, s);
        }
        m.vb.setDefaultColor(gray(br, 0));
        g.render(m.vb, m.ib, terrainCut, t);
        if (((e.fuse / 5) & 1) == 0) {
            if (flashApp == null) {
                flashApp = new Appearance();
                CompositingMode cm = new CompositingMode();
                cm.setBlending(CompositingMode.ALPHA_ADD);
                cm.setDepthWriteEnable(false);
                flashApp.setCompositingMode(cm);
                flashApp.setFog(fog);
            }
            int al = (int) (Mth.clamp((1 - life / 100f) * 0.8f, 0, 1) * 255);
            m.vb.setDefaultColor((al << 24) | 0xFFFFFF);
            g.render(m.vb, m.ib, flashApp, t);
        }
    }

    // ---- картины (PaintingRenderer 0.6.1) ----

    private final Mesh[] paintingMeshes = new Mesh[Painting.NAME.length];

    /** Меш картины в половинках пикселя (масштаб 1/32 блока): лицо на -z, задник на +z, рамка толщиной 1/16. */
    private Mesh paintingMesh(int mo) {
        Mesh m = paintingMeshes[mo];
        if (m != null) return m;
        m = new Mesh();
        MeshBuilder b = new MeshBuilder();
        int w = Painting.W[mo], h = Painting.H[mo], uo = Painting.U[mo], vo = Painting.V[mo];
        int c = 0xFFFFFFFF, e = 1;
        int bu0 = 192, bu1 = 208, bv0 = 0, bv1 = 16;
        for (int xs = 0; xs < w / 16; xs++)
            for (int ys = 0; ys < h / 16; ys++) {
                int x0 = -w + (xs + 1) * 32, x1 = -w + xs * 32;
                int y0 = -h + (ys + 1) * 32, y1 = -h + ys * 32;
                int fu0 = uo + w - xs * 16, fu1 = uo + w - (xs + 1) * 16;
                int fv0 = vo + h - ys * 16, fv1 = vo + h - (ys + 1) * 16;
                b.quad(x0, y1, -e, fu1, fv0, c, x1, y1, -e, fu0, fv0, c, x1, y0, -e, fu0, fv1, c, x0, y0, -e, fu1, fv1, c);
                b.quad(x0, y0, e, bu0, bv0, c, x1, y0, e, bu1, bv0, c, x1, y1, e, bu1, bv1, c, x0, y1, e, bu0, bv1, c);
                b.quad(x0, y0, -e, bu0, 0, c, x1, y0, -e, bu1, 0, c, x1, y0, e, bu1, 1, c, x0, y0, e, bu0, 1, c);
                b.quad(x0, y1, e, bu0, 0, c, x1, y1, e, bu1, 0, c, x1, y1, -e, bu1, 1, c, x0, y1, -e, bu0, 1, c);
                b.quad(x0, y0, e, bu0 + 1, bv0, c, x0, y1, e, bu0 + 1, bv1, c, x0, y1, -e, bu0, bv1, c, x0, y0, -e, bu0, bv0, c);
                b.quad(x1, y0, -e, bu0 + 1, bv0, c, x1, y1, -e, bu0 + 1, bv1, c, x1, y1, e, bu0, bv1, c, x1, y0, e, bu0, bv0, c);
            }
        m.vb = b.build(0, 0, 0);
        m.ib = MeshBuilder.strips(b.verts);
        paintingMeshes[mo] = m;
        return m;
    }

    private void renderPainting(Graphics3D g, Painting p, Level level) {
        Mesh m = paintingMesh(p.motive);
        int br = LevelRenderer.BR[level.getRawBrightness(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z))];
        t.setIdentity();
        t.postTranslate(p.x, p.y, p.z);
        t.postRotate(p.yRot, 0, 1, 0);
        t.postScale(0.5f, 0.5f, 0.5f);
        m.vb.setColors(null);
        m.vb.setDefaultColor(gray(br, 0));
        g.render(m.vb, m.ib, app("/art/kz.png"), t);
    }

    private Mesh arrowMesh;

    /** Модель ArrowRenderer: два перекрещенных пера 16x4 и торец 4x4, текстура item/arrows.png 32x32 (сдвиг -4 вшит). */
    private Mesh arrowMesh() {
        if (arrowMesh != null) return arrowMesh;
        Mesh m = new Mesh();
        MeshBuilder b = new MeshBuilder();
        int c = 0xFFFFFFFF;
        b.quad(-11, -2, -2, 0, 5, c, -11, -2, 2, 5, 5, c, -11, 2, 2, 5, 10, c, -11, 2, -2, 0, 10, c);
        b.quad(-12, -2, 0, 0, 0, c, 4, -2, 0, 16, 0, c, 4, 2, 0, 16, 5, c, -12, 2, 0, 0, 5, c);
        b.quad(-12, 0, -2, 0, 0, c, 4, 0, -2, 16, 0, c, 4, 0, 2, 16, 5, c, -12, 0, 2, 0, 5, c);
        m.vb = b.build(0, 0, 0);
        m.vb.setTexCoords(0, m.vb.getTexCoords(0, null), 1f / 32f, null);
        m.vb.setColors(null);
        m.ib = MeshBuilder.strips(b.verts);
        arrowMesh = m;
        return m;
    }

    /** ArrowRenderer::render: поворот по полёту, дрожь после попадания (shakeTime), наклон 45°, масштаб 0.9/16. */
    private void renderArrow(Graphics3D g, Arrow e, float x, float y, float z, float a, int br) {
        Mesh m = arrowMesh();
        t.setIdentity();
        t.postTranslate(x, y, z);
        t.postRotate(e.yRotO + (e.yRot - e.yRotO) * a - 90f, 0, 1, 0);
        t.postRotate(e.xRotO + (e.xRot - e.xRotO) * a, 0, 0, 1);
        float shake = e.shakeTime - a;
        if (shake > 0) t.postRotate(-Mth.sin(shake * 3f) * shake, 0, 0, 1);
        t.postRotate(45f, 1, 0, 0);
        t.postScale(0.9f, 0.9f, 0.9f);
        m.vb.setDefaultColor(gray(br, 0));
        g.render(m.vb, m.ib, app("/item/arrows.png"), t);
    }
}
