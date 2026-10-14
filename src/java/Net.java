/**
 * Сетевая игра MCPE 0.6.1 (протокол 9): хост (ServerSideNetworkHandler) и клиент (ClientSideNetworkHandler).
 * Всё, что трогает мир, выполняется в потоке игры из tick(); сокет и надёжная доставка — в потоках Raknet.
 */
public final class Net {
    public static final int PROTOCOL = 9;
    // MinecraftPacketIds: ID_USER_PACKET_ENUM (0x81) + номер
    static final int LOGIN = 0x82, LOGINSTATUS = 0x83, READY = 0x84, MESSAGE = 0x85, SETTIME = 0x86, STARTGAME = 0x87,
            ADDMOB = 0x88, ADDPLAYER = 0x89, REMOVEPLAYER = 0x8a, TELEPORTENTITY = 0x8b, ADDENTITY = 0x8c, REMOVEENTITY = 0x8d,
            ADDITEMENTITY = 0x8e, TAKEITEMENTITY = 0x8f, MOVEENTITY = 0x90, MOVEENTITY_POS = 0x91, MOVEENTITY_ROT = 0x92,
            MOVEENTITY_POSROT = 0x93, MOVEPLAYER = 0x94, PLACEBLOCK = 0x95, REMOVEBLOCK = 0x96, UPDATEBLOCK = 0x97,
            ADDPAINTING = 0x98, EXPLODE = 0x99, LEVELEVENT = 0x9a, TILEEVENT = 0x9b, ENTITYEVENT = 0x9c, REQUESTCHUNK = 0x9d,
            CHUNKDATA = 0x9e, PLAYEREQUIPMENT = 0x9f, PLAYERARMOREQUIPMENT = 0xa0, INTERACT = 0xa1, USEITEM = 0xa2,
            PLAYERACTION = 0xa3, UPDATEARMOR = 0xa4, HURTARMOR = 0xa5, SETENTITYDATA = 0xa6, SETENTITYMOTION = 0xa7,
            SETHEALTH = 0xa8, SETSPAWNPOSITION = 0xa9, ANIMATE = 0xaa, RESPAWN = 0xab, SENDINVENTORY = 0xac, DROPITEM = 0xad,
            CONTAINEROPEN = 0xae, CONTAINERCLOSE = 0xaf, CONTAINERSETSLOT = 0xb0, CONTAINERSETDATA = 0xb1,
            CONTAINERSETCONTENT = 0xb2, CONTAINERACK = 0xb3, CHAT = 0xb4, SIGNUPDATE = 0xb5, ADVENTURESETTINGS = 0xb6;

    // ReadyPacket, InteractPacket, PlayerActionPacket, AnimatePacket, EntityEvent, LevelEvent
    static final int READY_CLIENTGENERATION = 1, READY_REQUESTEDCHUNKS = 2;
    static final int INTERACT_INTERACT = 1, INTERACT_ATTACK = 2;
    static final int ACTION_RELEASE_USE_ITEM = 5, ACTION_STOP_SLEEPING = 6;
    static final int ANIM_SWING = 1, ANIM_WAKE_UP = 3;
    static final int EV_HURT = 2, EV_DEATH = 3, EV_USE_ITEM_COMPLETE = 9, EV_EAT_GRASS = 10;
    static final int LEVEL_SOUND_OPEN_DOOR = 1003, LEVEL_ALL_PLAYERS_SLEEPING = 9800;
    static final int CT_CONTAINER = 0, CT_FURNACE = 2;
    static final int HEALTH_MODIFY_OFFSET = -64;

    public static Raknet rak;
    static final int[] rxCount = new int[256];
    static int statTicks;
    public static NetServer server;
    public static NetClient client;
    /** Bluetooth (только J2ME): у хоста — служба, у клиента — соединение с хостом */
    public static BtNet bt;
    /** пакеты собираются только в потоке игры */
    static final NetOut out = new NetOut(4096);

    public static boolean isClient() {
        return client != null;
    }

    public static boolean isHost() {
        return server != null;
    }

    /** Мир не ставится на паузу меню: идёт сетевая игра (клиент или к хосту кто-то подключён). */
    public static boolean keepsRunning() {
        return client != null || (server != null && server.clientCount() > 0);
    }

    /** Minecraft::hostMultiplayer: каждый открытый мир — сервер на 19132 (до 4 клиентов). */
    public static void host(MC mc, Level l, Player p) {
        stop();
        Raknet r = new Raknet();
        if (!r.host(19132)) {
            Log.i("NET host failed: " + r.error);
            return;
        }
        rak = r;
        server = new NetServer(mc, r, l, p);
        l.net = server;
        server.allowIncomingConnections(PauseScreen.serverVisible == null ? Options.serverVisible : PauseScreen.serverVisible.booleanValue());
        Log.i("NET host port " + r.port + " guid " + r.guid);
    }

    /** Minecraft::locateMultiplayer: клиентский сокет и поиск игр в сети. */
    public static NetClient locate(MC mc) {
        stop();
        Raknet r = new Raknet();
        if (!r.open()) {
            Log.i("NET open failed: " + r.error);
            return null;
        }
        rak = r;
        client = new NetClient(mc, r);
        r.startPing();
        return client;
    }

    /** Хост открывает мир ещё и по Bluetooth (кнопка в меню паузы). */
    public static void btHost() {
        if (server == null || rak == null || bt != null) return;
        BtNet b = new BtNet(rak);
        bt = b;
        rak.bt = b;
        b.startHost();
    }

    /** Закрыть Bluetooth у хоста: игроки по Bluetooth отключаются, Wi-Fi-игра остаётся. */
    public static void btStopHost() {
        BtNet b = bt;
        if (b == null || server == null) return;
        bt = null;
        b.shutdown();
        if (rak != null) rak.bt = null;
    }

    /** Подпись кнопки Bluetooth в меню паузы хоста. */
    public static String btLabel() {
        BtNet b = bt;
        if (b == null) return "Bluetooth: off";
        switch (b.state) {
            case BtNet.STARTING: return "Bluetooth: ...";
            case BtNet.HOSTING: {
                int n = b.linkCount();
                return n > 0 ? "Bluetooth: on (" + n + ")" : "Bluetooth: on";
            }
            case BtNet.ERROR: return "Bluetooth: error";
        }
        return "Bluetooth: off";
    }

    /** Клиент по Bluetooth: соединение уже установлено (BtJoinScreen), дальше — обычный вход RakNet. */
    public static NetClient joinBt(MC mc, javax.microedition.io.StreamConnection sc, java.io.DataInputStream in, java.io.DataOutputStream out) {
        stop();
        Raknet r = new Raknet();
        BtNet b = new BtNet(r);
        r.openBt(b);
        b.attachHost(sc, in, out);
        rak = r;
        bt = b;
        client = new NetClient(mc, r);
        client.join(BtNet.HOST_ADDR);
        Log.i("NET bluetooth join");
        return client;
    }

    /** Minecraft::leaveGame / disconnect */
    public static void stop() {
        if (server != null) server.shutdown();
        if (client != null) client.shutdown();
        if (rak != null) rak.shutdown();
        if (bt != null) bt.shutdown();
        bt = null;
        rak = null;
        server = null;
        client = null;
    }

    /** В начале каждого тика игры (и в меню подключения): события сети и периодические пакеты. */
    public static void tick(MC mc) {
        Raknet r = rak;
        if (r == null) return;
        pump(mc);
        if (rak != r) return;
        if (bt != null) {
            String m = bt.takeMessage();
            if (m != null) message(mc, m);
        }
        if (mc.debug && ++statTicks % 100 == 0) {
            StringBuffer sb = new StringBuffer("NET rx");
            for (int i = 0; i < 256; i++) if (rxCount[i] > 0) { sb.append(' ').append(Integer.toHexString(i)).append(':').append(rxCount[i]); rxCount[i] = 0; }
            Log.i(sb.toString());
        }
        try {
            if (server != null) server.tick();
            else if (client != null) client.tick();
        } catch (Throwable t) {
            Log.e("NET tick", t);
        }
    }

    /** Каждый кадр: пришедшие пакеты (ответ на запрос чанка не ждёт тика игры). */
    public static void pump(MC mc) {
        Raknet r = rak;
        if (r == null) return;
        Raknet.Ev e;
        int n = 0;
        while ((e = r.poll()) != null) {
            if (e.data != null && e.data.length > 0) rxCount[e.data[0] & 255]++;
            try {
                if (server != null) server.event(e);
                else if (client != null) client.event(e);
            } catch (Throwable t) {
                Log.e("NET packet " + (e.data != null && e.data.length > 0 ? Integer.toHexString(e.data[0] & 255) : "-"), t);
            }
            if (rak != r) return;
            // не задерживать кадр: остальное — в следующем кадре
            if (++n >= 200) break;
        }
    }

    /** LocalPlayer::swing: AnimatePacket хосту/всем */
    public static void localSwing() {
        if (server != null) server.localSwing();
        else if (client != null && client.state == NetClient.S_PLAYING) client.swing();
    }

    /** TextEditScreen закрыт: SignUpdatePacket */
    public static void signEdited(TileEntity te) {
        if (te == null || te.text == null) return;
        if (server != null) server.signChanged(te);
        else if (client != null) client.signChanged(te);
    }

    // ---- общие пакеты ----

    static void writeItem(NetOut o, int id, int count, int aux) {
        o.u16(id);
        o.u8(count);
        o.u16(aux);
    }

    /** PacketUtil::Rot_degreesToChar */
    static int rotToByte(float rot) {
        return (int) (rot / 360f * 256f);
    }

    static float byteToRot(int b) {
        return (byte) b / 256f * 360f;
    }

    /** SynchedEntityData::packAll — то, что объявляют Mob/Sheep/Creeper/Spider/Player в 0.6.1. */
    static void packEntityData(NetOut o, Entity e) {
        int flags = 0;
        if (e.fire > 0) flags |= 1;
        if (e instanceof Player && ((Player) e).sneaking) flags |= 2;
        o.u8(0x00);
        o.u8(flags);
        o.u8(0x21);
        o.i16le(Math.max(0, Math.min(300, e.air)));
        if (e instanceof Mobs.Sheep) {
            Mobs.Sheep s = (Mobs.Sheep) e;
            o.u8(0x10);
            o.u8((s.color & 15) | (s.sheared ? 0x10 : 0));
        } else if (e instanceof Mobs.Creeper) {
            o.u8(0x10);
            o.u8(((Mobs.Creeper) e).swellDir);
        } else if (e instanceof Mobs.Spider) {
            o.u8(0x10);
            o.u8(e.horizontalCollision ? 1 : 0);
        } else if (e instanceof Player || e instanceof RemotePlayer) {
            boolean sl;
            int bx, by, bz;
            if (e instanceof Player) {
                Player p = (Player) e;
                sl = p.sleeping; bx = p.bedX; by = p.bedY; bz = p.bedZ;
            } else {
                RemotePlayer p = (RemotePlayer) e;
                sl = p.sleeping; bx = p.bedX; by = p.bedY; bz = p.bedZ;
            }
            o.u8(0x10);
            o.u8(sl ? 2 : 0);
            o.u8(0xD1);
            o.i32le(sl ? bx : 0);
            o.i32le(sl ? by : 0);
            o.i32le(sl ? bz : 0);
        }
        o.u8(0x7F);
    }

    /** Хэш синхронизируемых данных: изменился — SetEntityDataPacket. */
    static int dataHash(Entity e) {
        int h = e.fire > 0 ? 1 : 0;
        if (e instanceof Mobs.Sheep) h |= (((Mobs.Sheep) e).color << 1) | (((Mobs.Sheep) e).sheared ? 64 : 0);
        else if (e instanceof Mobs.Creeper) h |= (((Mobs.Creeper) e).swellDir + 1) << 1;
        else if (e instanceof RemotePlayer) {
            RemotePlayer p = (RemotePlayer) e;
            if (p.sleeping) h |= 2 | (p.bedX << 2) ^ (p.bedZ << 10) ^ (p.bedY << 18);
        } else if (e instanceof Player) {
            Player p = (Player) e;
            if (p.sleeping) h |= 2 | (p.bedX << 2) ^ (p.bedZ << 10) ^ (p.bedY << 18);
            if (p.sneaking) h |= 1 << 30;
        }
        return h;
    }

    /** SynchedEntityData::unpack + assignValues для известных нам полей. */
    static void readEntityData(NetIn in, Entity e) {
        while (in.remaining() > 0) {
            int h = in.u8();
            if (h == 0x7F) break;
            int type = (h & 0xE0) >> 5, id = h & 0x1F;
            int v = 0;
            int px = 0, py = 0, pz = 0;
            switch (type) {
                case 0: v = in.s8(); break;
                case 1: v = in.s16le(); break;
                case 2: v = in.i32le(); break;
                case 3: in.f32le(); break;
                case 4: in.strLe(); break;
                case 5: in.s16le(); in.u8(); in.s16le(); break;
                case 6: px = in.i32le(); py = in.i32le(); pz = in.i32le(); break;
                default: return;
            }
            if (e == null) continue;
            if (id == 0 && type == 0) {
                if ((v & 1) != 0) { if (e.fire <= 0) e.fire = 1; } else if (!(e instanceof Player)) e.fire = 0;
            } else if (id == 1 && type == 1) {
                if (!(e instanceof Player)) e.air = v;
            } else if (id == 16 && type == 0) {
                if (e instanceof Mobs.Sheep) {
                    ((Mobs.Sheep) e).color = v & 15;
                    ((Mobs.Sheep) e).sheared = (v & 0x10) != 0;
                } else if (e instanceof Mobs.Creeper) ((Mobs.Creeper) e).swellDir = v;
                else if (e instanceof RemotePlayer) ((RemotePlayer) e).sleeping = (v & 2) != 0;
                else if (e instanceof Player && client != null) client.ownSleepFlag((v & 2) != 0);
            } else if (id == 17 && type == 6) {
                if (e instanceof RemotePlayer) {
                    RemotePlayer p = (RemotePlayer) e;
                    p.bedX = px; p.bedY = py; p.bedZ = pz;
                } else if (e instanceof Player && client != null) client.ownBedPos(px, py, pz);
            }
        }
    }

    /** Текст сообщения в чат (Gui::addMessage / displayClientMessage). */
    static void message(MC mc, String s) {
        if (mc.gui != null) mc.gui.message(s);
    }
}
