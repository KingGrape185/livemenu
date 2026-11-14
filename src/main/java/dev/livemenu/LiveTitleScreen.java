package dev.livemenu;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A playable title screen: a little 2D Minecraft world you can walk around in.
 * Everything is drawn with plain filled rectangles, so there are no textures to load.
 */
public class LiveTitleScreen extends Screen {

    // ---- world constants -------------------------------------------------
    private static final int B = 16;          // block size in GUI pixels
    private static final int W = 320;         // world width in blocks
    private static final int H = 56;          // world height in blocks

    private static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, COAL = 4,
            LOG = 5, LEAVES = 6, PLANKS = 7, BEDROCK = 8, LAMP = 9;

    private static final int[] BASE = new int[10];
    static {
        BASE[GRASS] = 0xFF5FA03A;
        BASE[DIRT] = 0xFF8B5A2B;
        BASE[STONE] = 0xFF7D7D7D;
        BASE[COAL] = 0xFF7D7D7D;
        BASE[LOG] = 0xFF6B4F2A;
        BASE[LEAVES] = 0xFF2F7A2A;
        BASE[PLANKS] = 0xFFB8945A;
        BASE[BEDROCK] = 0xFF2B2B2B;
        BASE[LAMP] = 0xFFFFD866;
    }

    private static final int[] HOTBAR = {DIRT, STONE, PLANKS, LOG, LEAVES, LAMP};
    private static final String[] HOTBAR_NAMES = {"Dirt", "Stone", "Planks", "Log", "Leaves", "Lamp"};

    private static final String[] SPLASHES = {
            "Click the sun!", "Walk around!", "Pigs are friends!", "Place a lamp at night!",
            "Dig deeper!", "Press P for the menu!", "100x more interactive!", "Coal makes lamps!"
    };

    // ---- small physics body ---------------------------------------------
    private static class Body {
        float x, y, w, h, vx, vy, phase;
        boolean ground, hitX;
        int face = 1;
        Body(float w, float h) { this.w = w; this.h = h; }
    }

    private static class Pig {
        final Body b = new Body(14, 10);
        float timer, love;
        int dir;
    }

    // ---- state ------------------------------------------------------------
    private final Random rnd = new Random();
    private final byte[][] world = new byte[W][H];
    private final int[] surface = new int[W];
    private final int[] inv = new int[10];
    private final Body player = new Body(8, 31);
    private final List<Pig> pigs = new ArrayList<>();
    private final List<float[]> particles = new ArrayList<>(); // x, y, vx, vy, life, color
    private final float[] starX = new float[90], starY = new float[90], starPh = new float[90];
    private final float[] cloudX = new float[7], cloudY = new float[7], cloudW = new float[7], cloudSpd = new float[7];

    private final String splash;
    private int sel = 0;
    private float time = 0.25f;          // 0..1 day cycle, 0.25 = noon, 0.75 = midnight
    private float timeTarget = -1f;
    private float camX, camY;
    private boolean camInit = false;
    private boolean left, right, jump, sprint;
    private boolean menuOpen = false;
    private float menuAnim = 0f;
    private float titleKick = 0f;
    private float clock = 0f;
    private long lastNs = System.nanoTime();
    private int mouseX, mouseY;
    private float sunX, sunY, moonX, moonY;
    private int titleX0, titleY0, titleX1, titleY1;
    private float darkness = 0f;
    private String hint = "";

    private Button playButton;
    private Button[] menuButtons;
    private Button partyButton;
    private static final int PANEL_H = 196;

    public LiveTitleScreen() {
        super(Component.literal("Main Menu"));
        splash = SPLASHES[rnd.nextInt(SPLASHES.length)];
        generateWorld();
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat();
            starPh[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < cloudX.length; i++) {
            cloudX[i] = rnd.nextFloat() * 1500f;
            cloudY[i] = 18 + rnd.nextFloat() * 120f;
            cloudW[i] = 50 + rnd.nextFloat() * 70f;
            cloudSpd[i] = 4 + rnd.nextFloat() * 8f;
        }
        inv[DIRT] = 20; inv[STONE] = 10; inv[PLANKS] = 20; inv[LOG] = 8; inv[LEAVES] = 8; inv[LAMP] = 6;
    }

    // =====================================================================
    //  World generation
    // =====================================================================
    private void generateWorld() {
        double p1 = rnd.nextDouble() * 100, p2 = rnd.nextDouble() * 100;
        for (int x = 0; x < W; x++) {
            int h = (int) (22 + 4 * Math.sin(x * 0.07 + p1) + 2.5 * Math.sin(x * 0.19 + p2) + 1.5 * Math.sin(x * 0.43));
            surface[x] = h;
            for (int y = 0; y < H; y++) {
                int id;
                if (y < h) id = AIR;
                else if (y == h) id = GRASS;
                else if (y < h + 4) id = DIRT;
                else id = (y > h + 5 && rnd.nextFloat() < 0.035f) ? COAL : STONE;
                if (y == H - 1) id = BEDROCK;
                world[x][y] = (byte) id;
            }
        }
        // caves
        for (int i = 0; i < 26; i++) {
            int cx = rnd.nextInt(W), cy = 36 + rnd.nextInt(H - 40), r = 2 + rnd.nextInt(3);
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++) {
                    int x = cx + dx, y = cy + dy;
                    if (dx * dx + dy * dy <= r * r && x >= 0 && x < W && y > 0 && y < H - 1) world[x][y] = AIR;
                }
        }
        // trees
        for (int x = 3; x < W - 3; x += 4 + rnd.nextInt(6)) {
            if (Math.abs(x - W / 2) < 3 || rnd.nextFloat() > 0.75f) continue;
            int h = surface[x], trunk = 4 + rnd.nextInt(2), top = h - trunk;
            for (int t = 1; t <= trunk; t++) world[x][h - t] = LOG;
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -2; dy <= 1; dy++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dy) >= 1 && rnd.nextBoolean();
                    int lx = x + dx, ly = top + dy;
                    if (!corner && lx >= 0 && lx < W && ly >= 0 && world[lx][ly] == AIR) world[lx][ly] = LEAVES;
                }
        }
        // player
        player.x = (W / 2) * B + 4;
        player.y = (surface[W / 2] - 3) * B;
        // pigs
        for (int i = 0; i < 5; i++) {
            Pig p = new Pig();
            int bx = Math.max(4, Math.min(W - 5, W / 2 + (rnd.nextBoolean() ? 1 : -1) * (6 + rnd.nextInt(40))));
            p.b.x = bx * B;
            p.b.y = (surface[bx] - 3) * B;
            pigs.add(p);
        }
    }

    // =====================================================================
    //  Widgets
    // =====================================================================
    @Override
    protected void init() {
        Options o = minecraft.options;
        hint = o.keyLeft.getTranslatedKeyMessage().getString() + "/" + o.keyRight.getTranslatedKeyMessage().getString()
                + " move   " + o.keyJump.getTranslatedKeyMessage().getString() + " jump   "
                + "Click: mine / place   1-6: blocks   ["
                + LiveMenuMod.MENU_KEY.getTranslatedKeyMessage().getString() + "] menu";

        // Play button: bottom right
        playButton = addRenderableWidget(Button.builder(Component.literal("Play"),
                b -> minecraft.gui.setScreen(new SelectWorldScreen(this)))
                .bounds(width - 144, height - 34, 130, 20).build());

        // Hamburger button (top left): friends, party, settings and extras live in the hub
        partyButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> minecraft.gui.setScreen(new HubScreen(this, HubScreen.Tab.FRIENDS))).bounds(8, 8, 22, 20).build());
        refreshWidgets();
        clearKeys();
    }

    private int menuTop() { return height / 2 - PANEL_H / 2; }

    private void refreshWidgets() { }

    /** The menu hotkey opens the hub on its Extras tab (Singleplayer / Multiplayer / Options / Quit). */
    private void toggleMenu() {
        clearKeys();
        minecraft.gui.setScreen(new HubScreen(this, HubScreen.Tab.EXTRAS));
    }

    private void clearKeys() { left = right = jump = sprint = false; }

    @Override
    public void removed() { clearKeys(); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // =====================================================================
    //  Input
    // =====================================================================
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (LiveMenuMod.MENU_KEY.matches(e)) { toggleMenu(); return true; }
        if (menuOpen) {
            if (e.key() == InputConstants.KEY_ESCAPE) { toggleMenu(); return true; } // ESC closes the panel
            return super.keyPressed(e);
        }
        Options o = minecraft.options;
        if (o.keyLeft.matches(e)) { left = true; return true; }
        if (o.keyRight.matches(e)) { right = true; return true; }
        if (o.keyJump.matches(e) || o.keyUp.matches(e)) { jump = true; return true; }
        if (o.keySprint.matches(e)) { sprint = true; return true; }
        for (int i = 0; i < HOTBAR.length; i++) {
            if (o.keyHotbarSlots[i].matches(e)) { sel = i; return true; }
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        Options o = minecraft.options;
        if (o.keyLeft.matches(e)) left = false;
        if (o.keyRight.matches(e)) right = false;
        if (o.keyJump.matches(e) || o.keyUp.matches(e)) jump = false;
        if (o.keySprint.matches(e)) sprint = false;
        return super.keyReleased(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        double mx = e.x(), my = e.y();
        int btn = e.button();

        if (menuOpen) {
            int px0 = width / 2 - 95, py0 = menuTop();
            if (mx < px0 || mx > px0 + 190 || my < py0 || my > py0 + PANEL_H) toggleMenu();
            return true;
        }
        if (btn == 0 && dist(mx, my, sunX, sunY) < 22 && Math.sin(time * 6.2832) > -0.15) {
            timeTarget = Math.sin(time * 6.2832) > 0 ? 0.75f : 0.25f;
            burst((float) mx + camX, (float) my + camY, 0xFFFFE066, 14, 120);
            return true;
        }
        if (btn == 0 && dist(mx, my, moonX, moonY) < 20 && Math.sin(time * 6.2832) < 0.15) {
            timeTarget = 0.25f;
            return true;
        }
        if (btn == 0 && mx >= titleX0 && mx <= titleX1 && my >= titleY0 && my <= titleY1) {
            titleKick = 1f;
            int[] cols = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFFFF55FF};
            for (int i = 0; i < 24; i++) burst((float) mx + camX, (float) my + camY, cols[rnd.nextInt(cols.length)], 1, 160);
            return true;
        }

        float wx = (float) mx + Math.round(camX), wy = (float) my + Math.round(camY);
        for (Pig p : pigs) {
            Body b = p.b;
            if (wx >= b.x - 3 && wx <= b.x + b.w + 3 && wy >= b.y - 4 && wy <= b.y + b.h + 3) {
                b.vy = -150f;
                p.love = 1.4f;
                for (int i = 0; i < 6; i++) burst(b.x + b.w / 2, b.y - 2, 0xFFFF4060, 1, 70);
                return true;
            }
        }
        int bx = (int) Math.floor(wx / B), by = (int) Math.floor(wy / B);
        if (btn == 0) breakBlock(bx, by);
        else if (btn == 1) placeBlock(bx, by);
        return true;
    }

    private static double dist(double ax, double ay, double bx, double by) {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by));
    }

    private boolean inReach(int bx, int by) {
        double dx = (bx + 0.5) * B - (player.x + player.w / 2);
        double dy = (by + 0.5) * B - (player.y + player.h / 2);
        return Math.sqrt(dx * dx + dy * dy) <= 5.5 * B;
    }

    private void breakBlock(int bx, int by) {
        if (bx < 0 || bx >= W || by < 0 || by >= H) return;
        int id = world[bx][by];
        if (id == AIR || id == BEDROCK || !inReach(bx, by)) return;
        world[bx][by] = AIR;
        burst(bx * B + 8, by * B + 8, BASE[id], 12, 100);
        int give = id == GRASS ? DIRT : id == COAL ? LAMP : id;
        inv[give]++;
    }

    private void placeBlock(int bx, int by) {
        if (bx < 0 || bx >= W || by < 0 || by >= H) return;
        int id = HOTBAR[sel];
        if (world[bx][by] != AIR || inv[id] <= 0 || !inReach(bx, by)) return;
        if (overlaps(player, bx, by)) return;
        for (Pig p : pigs) if (overlaps(p.b, bx, by)) return;
        world[bx][by] = (byte) id;
        inv[id]--;
        burst(bx * B + 8, by * B + 8, BASE[id], 5, 60);
    }

    private static boolean overlaps(Body b, int bx, int by) {
        return b.x < (bx + 1) * B && b.x + b.w > bx * B && b.y < (by + 1) * B && b.y + b.h > by * B;
    }

    private void burst(float x, float y, int color, int n, float speed) {
        for (int i = 0; i < n; i++) {
            particles.add(new float[]{x, y,
                    (rnd.nextFloat() - 0.5f) * speed, -rnd.nextFloat() * speed * 0.9f,
                    0.6f + rnd.nextFloat() * 0.6f, Float.intBitsToFloat(color)});
        }
    }

    // =====================================================================
    //  Simulation
    // =====================================================================
    private boolean solid(int bx, int by) {
        if (bx < 0 || bx >= W || by >= H) return true;
        if (by < 0) return false;
        return world[bx][by] != AIR;
    }

    private boolean collides(Body b, float x, float y) {
        int x0 = (int) Math.floor(x / B), x1 = (int) Math.floor((x + b.w - 0.01f) / B);
        int y0 = (int) Math.floor(y / B), y1 = (int) Math.floor((y + b.h - 0.01f) / B);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                if (solid(bx, by)) return true;
        return false;
    }

    private void step(Body b, float dt) {
        b.vy = Math.min(b.vy + 900f * dt, 520f);
        b.hitX = false;
        b.x += b.vx * dt;
        if (collides(b, b.x, b.y)) {
            if (b.vx > 0) b.x = (float) Math.floor((b.x + b.w - 0.01f) / B) * B - b.w - 0.01f;
            else b.x = ((float) Math.floor(b.x / B) + 1) * B + 0.01f;
            b.hitX = true;
            b.vx = 0;
        }
        b.y += b.vy * dt;
        b.ground = false;
        if (collides(b, b.x, b.y)) {
            if (b.vy > 0) {
                b.y = (float) Math.floor((b.y + b.h - 0.01f) / B) * B - b.h - 0.01f;
                b.ground = true;
            } else {
                b.y = ((float) Math.floor(b.y / B) + 1) * B + 0.01f;
            }
            b.vy = 0;
        }
    }

    private void update() {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        clock += dt;

        // time of day
        if (timeTarget >= 0f) {
            float d = timeTarget - time;
            d -= (float) Math.floor(d);
            float stepT = 0.35f * dt;
            if (d <= stepT) { time = timeTarget; timeTarget = -1f; } else time += stepT;
        } else {
            if (LiveMenuConfig.dayNightCycle) time += dt / 120f;
        }
        time -= (float) Math.floor(time);

        menuAnim = Math.max(0f, Math.min(1f, menuAnim + (menuOpen ? 1 : -1) * dt * 6f));
        titleKick = Math.max(0f, titleKick - dt * 2.5f);

        // player
        float dir = menuOpen ? 0 : ((right ? 1 : 0) - (left ? 1 : 0));
        Body p = player;
        float target = dir * (sprint ? 135f : 85f);
        p.vx += (target - p.vx) * Math.min(1f, dt * (p.ground ? 16f : 6f));
        if (dir != 0) p.face = (int) dir;
        if (!menuOpen && ((jump && p.ground) || (p.hitX && p.ground && dir != 0))) p.vy = -215f;
        step(p, dt);
        if (p.ground) p.phase += Math.abs(p.vx) * dt * 0.14f;
        if (p.y > H * B + 200) { p.x = (W / 2) * B + 4; p.y = (surface[W / 2] - 3) * B; p.vy = 0; }

        // pigs
        for (Pig pig : pigs) {
            Body b = pig.b;
            pig.timer -= dt;
            if (pig.timer <= 0) { pig.dir = rnd.nextInt(3) - 1; pig.timer = 1f + rnd.nextFloat() * 3f; }
            pig.love = Math.max(0, pig.love - dt);
            b.vx = pig.love > 0 ? 0 : pig.dir * 24f;
            if (b.vx != 0) b.face = b.vx > 0 ? 1 : -1;
            if (b.hitX && b.ground) b.vy = -190f;
            step(b, dt);
            if (pig.love > 0 && rnd.nextFloat() < dt * 8) burst(b.x + b.w / 2, b.y - 2, 0xFFFF4060, 1, 50);
            if (b.y > H * B + 100) { b.x = (W / 2) * B; b.y = (surface[W / 2] - 3) * B; b.vy = 0; }
        }

        // particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            float[] q = particles.get(i);
            q[3] += 600f * dt;
            q[0] += q[2] * dt;
            q[1] += q[3] * dt;
            q[4] -= dt;
            if (q[4] <= 0) particles.remove(i);
        }

        for (int i = 0; i < cloudX.length; i++) cloudX[i] += cloudSpd[i] * dt;

        // camera
        float tx = p.x + p.w / 2 - width / 2f;
        float ty = p.y + p.h / 2 - height * 0.58f;
        float maxX = Math.max(0, W * B - width), maxY = Math.max(0, H * B - height);
        tx = Math.max(0, Math.min(maxX, tx));
        ty = Math.max(0, Math.min(maxY, ty));
        if (!camInit) { camX = tx; camY = ty; camInit = true; }
        else {
            float k = Math.min(1f, dt * 6f);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
    }

    // =====================================================================
    //  Rendering
    // =====================================================================
    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int shade(int c, int d) {
        int r = Math.max(0, Math.min(255, ((c >> 16) & 255) + d));
        int g = Math.max(0, Math.min(255, ((c >> 8) & 255) + d));
        int b = Math.max(0, Math.min(255, (c & 255) + d));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int rgb, float a) {
        return (((int) (Math.max(0f, Math.min(1f, a)) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    private float daylight() {
        float s = (float) Math.sin(time * Math.PI * 2);
        return Math.max(0f, Math.min(1f, s * 1.6f + 0.5f));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        update();
        int cx = Math.round(camX), cy = Math.round(camY);
        float dl = daylight();

        drawSky(g, dl);
        drawCelestial(g, dl);
        drawClouds(g, dl, cx);
        drawMountains(g, dl, cx, cy);
        drawWorld(g, cx, cy);
        for (Pig p : pigs) drawPig(g, p, cx, cy);
        drawPlayer(g, cx, cy);
        drawParticles(g, cx, cy);
        drawDarkness(g, dl, cy);
        drawLampGlow(g, cx, cy);
        drawTitle(g);
        drawHud(g);
    }

    private void drawSky(GuiGraphicsExtractor g, float dl) {
        float night = 1f - dl;
        float sunset = Math.max(0f, 1f - Math.abs(dl - 0.5f) * 3.5f) * 0.75f;
        int top = mix(0xFF4A86E8, 0xFF050816, night);
        int hor = mix(mix(0xFFA8D0FF, 0xFF141A3A, night), 0xFFFF8A4A, sunset);
        int n = 24;
        for (int i = 0; i < n; i++) {
            int y0 = i * height / n, y1 = (i + 1) * height / n;
            float t = Math.min(1f, (float) i / (n * 0.75f));
            g.fill(0, y0, width, y1, mix(top, hor, t));
        }
        if (dl < 0.95f) {
            for (int i = 0; i < starX.length; i++) {
                float tw = 0.6f + 0.4f * (float) Math.sin(clock * 2f + starPh[i]);
                int sx = (int) (starX[i] * width), sy = (int) (starY[i] * height * 0.6f);
                rect(g, sx, sy, 2, 2, alpha(0xFFFFFF, night * tw));
            }
        }
    }

    private void drawCelestial(GuiGraphicsExtractor g, float dl) {
        double th = time * Math.PI * 2;
        float h0 = height * 0.62f;
        sunX = (float) (width * 0.5 - Math.cos(th) * width * 0.5);
        sunY = (float) (h0 - Math.sin(th) * (h0 - 24));
        moonX = (float) (width * 0.5 + Math.cos(th) * width * 0.5);
        moonY = (float) (h0 + Math.sin(th) * (h0 - 24));
        int sx = Math.round(sunX), sy = Math.round(sunY);
        if (Math.sin(th) > -0.15) {
            rect(g, sx - 24, sy - 24, 48, 48, 0x22FFE080);
            rect(g, sx - 17, sy - 17, 34, 34, 0x33FFE080);
            rect(g, sx - 11, sy - 11, 22, 22, 0xFFFFF3A0);
            rect(g, sx - 7, sy - 7, 14, 14, 0xFFFFFFE0);
        }
        int mx = Math.round(moonX), my = Math.round(moonY);
        if (Math.sin(th) < 0.15) {
            rect(g, mx - 14, my - 14, 28, 28, 0x22C0D0FF);
            rect(g, mx - 9, my - 9, 18, 18, 0xFFE8EAF2);
            rect(g, mx - 5, my - 4, 4, 4, 0xFFC0C4D4);
            rect(g, mx + 2, my + 2, 4, 4, 0xFFC0C4D4);
        }
    }

    private void drawClouds(GuiGraphicsExtractor g, float dl, int cx) {
        int cc = (mix(0xFFFFFFFF, 0xFF3A4260, 1f - dl) & 0xFFFFFF) | 0xB0000000;
        int span = width + 260;
        for (int i = 0; i < cloudX.length; i++) {
            int sx = (((int) (cloudX[i] - cx * 0.15f)) % span + span) % span - 130;
            int y = (int) cloudY[i], w = (int) cloudW[i];
            rect(g, sx, y, w, 10, cc);
            rect(g, sx + 8, y - 6, w - 24, 8, cc);
            rect(g, sx + w / 3, y + 10, w / 2, 4, cc);
        }
    }

    private void drawMountains(GuiGraphicsExtractor g, float dl, int cx, int cy) {
        float night = 1f - dl;
        for (int layer = 0; layer < 2; layer++) {
            float par = layer == 0 ? 0.12f : 0.3f;
            int col = layer == 0 ? mix(0xFF7F9CC4, 0xFF10182E, night) : mix(0xFF5E8A5A, 0xFF0B1A14, night);
            float baseY = 24 * B - cy * (layer == 0 ? 0.45f : 0.6f);
            float amp = layer == 0 ? 1f : 0.6f;
            for (int sx = 0; sx < width; sx += 4) {
                float wx = sx + cx * par + layer * 400;
                double hgt = (70 + 36 * Math.sin(wx * 0.012 + layer * 2) + 22 * Math.sin(wx * 0.031 + layer)
                        + 14 * Math.abs(Math.sin(wx * 0.007))) * amp;
                int top = (int) (baseY - hgt);
                if (top < height) g.fill(sx, Math.max(0, top), sx + 4, height, col);
            }
        }
    }

    private int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h;
    }

    private void drawWorld(GuiGraphicsExtractor g, int cx, int cy) {
        int x0 = Math.max(0, cx / B), x1 = Math.min(W - 1, (cx + width) / B);
        int y0 = Math.max(0, cy / B), y1 = Math.min(H - 1, (cy + height) / B);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                int id = world[bx][by];
                int sx = bx * B - cx, sy = by * B - cy;
                if (id == AIR) {
                    if (by > surface[bx]) rect(g, sx, sy, B, B, 0xFF17171C);
                } else {
                    drawBlock(g, id, sx, sy, bx, by);
                }
            }
        }
        int bottom = H * B - cy;
        if (bottom < height) rect(g, 0, bottom, width, height - bottom, BASE[BEDROCK]);

        // block outline under cursor
        if (!menuOpen) {
            int bx = (int) Math.floor((mouseX + cx) / (float) B), by = (int) Math.floor((mouseY + cy) / (float) B);
            if (bx >= 0 && bx < W && by >= 0 && by < H && inReach(bx, by)) {
                int sx = bx * B - cx, sy = by * B - cy;
                int c = world[bx][by] == AIR ? 0x70FFFFFF : 0xE0FFFFFF;
                rect(g, sx, sy, B, 1, c);
                rect(g, sx, sy + B - 1, B, 1, c);
                rect(g, sx, sy, 1, B, c);
                rect(g, sx + B - 1, sy, 1, B, c);
            }
        }
    }

    private void drawBlock(GuiGraphicsExtractor g, int id, int sx, int sy, int bx, int by) {
        int base = BASE[id];
        int hs = hash(bx, by);
        int a = (hs & 7) * 2, b = ((hs >> 3) & 7) * 2, c = ((hs >> 6) & 7) * 2, d = ((hs >> 9) & 7) * 2;
        switch (id) {
            case GRASS -> {
                rect(g, sx, sy, B, B, BASE[DIRT]);
                rect(g, sx + a, sy + 8, 2, 2, shade(BASE[DIRT], -20));
                rect(g, sx, sy, B, 4, base);
                rect(g, sx + a, sy + 4, 3, 2, base);
                rect(g, sx + c, sy + 4, 3, 1, base);
                rect(g, sx + b, sy + 1, 2, 2, shade(base, 22));
            }
            case LOG -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx + 3, sy, 2, B, shade(base, -22));
                rect(g, sx + 10, sy, 2, B, shade(base, -22));
            }
            case LEAVES -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx + a, sy + b, 3, 3, shade(base, 28));
                rect(g, sx + c, sy + d, 3, 3, shade(base, -25));
            }
            case PLANKS -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx, sy + 7, B, 1, shade(base, -30));
                rect(g, sx, sy + 15, B, 1, shade(base, -30));
                rect(g, sx + 4 + (hs & 7), sy, 1, 7, shade(base, -20));
            }
            case COAL -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx + 2, sy + 3, 3, 3, 0xFF1B1B1B);
                rect(g, sx + 9, sy + 2, 3, 3, 0xFF1B1B1B);
                rect(g, sx + 5, sy + 9, 4, 3, 0xFF1B1B1B);
                rect(g, sx + 11, sy + 10, 3, 3, 0xFF1B1B1B);
            }
            case LAMP -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx + 2, sy + 2, B - 4, B - 4, 0xFFFFEFA0);
                rect(g, sx + 5, sy + 5, 6, 6, 0xFFFFFFE0);
            }
            default -> {
                rect(g, sx, sy, B, B, base);
                rect(g, sx + a, sy + b, 2, 2, shade(base, -18));
                rect(g, sx + c, sy + d, 2, 2, shade(base, 14));
            }
        }
    }

    private void drawPig(GuiGraphicsExtractor g, Pig p, int cx, int cy) {
        Body b = p.b;
        int x = Math.round(b.x) - cx, y = Math.round(b.y) - cy;
        int f = b.face;
        int pink = 0xFFF0A8A0, dark = 0xFFD98880;
        float sw = (float) Math.sin(clock * 10f) * (Math.abs(b.vx) > 1 ? 1.5f : 0f);
        rect(g, x + 2 + Math.round(sw), y + 7, 3, 3, dark);
        rect(g, x + 9 - Math.round(sw), y + 7, 3, 3, dark);
        rect(g, x, y + 1, 14, 7, pink);
        int hx = f > 0 ? x + 10 : x - 2;
        rect(g, hx, y, 6, 6, pink);
        rect(g, f > 0 ? hx + 4 : hx - 0, y + 3, 2, 3, 0xFFE08880);
        rect(g, f > 0 ? hx + 3 : hx + 2, y + 1, 1, 1, 0xFF202020);
    }

    private void drawPlayer(GuiGraphicsExtractor g, int cx, int cy) {
        Body p = player;
        int ox = Math.round(p.x + p.w / 2) - cx;
        int oy = Math.round(p.y + p.h) - cy;   // feet
        int f = p.face;
        float amp = p.ground ? Math.min(1f, Math.abs(p.vx) / 60f) : 0.7f;
        float sw = (float) Math.sin(p.phase) * amp;
        int swing = Math.round(sw * 4);
        int idle = p.ground && amp < 0.05f ? Math.round((float) Math.sin(clock * 2f) * 0.6f) : 0;

        int skin = 0xFFC68E65, shirt = 0xFF00A8A8, pants = 0xFF3A3AA0, shoe = 0xFF6B6B6B;
        // back arm + back leg
        rect(g, ox - 2 - swing * f, oy - 24, 4, 7, shade(shirt, -35));
        rect(g, ox - 2 - swing * f, oy - 17, 4, 5, shade(skin, -35));
        rect(g, ox - 2 + swing * f, oy - 12, 4, 10, shade(pants, -30));
        rect(g, ox - 2 + swing * f, oy - 2, 4, 2, shade(shoe, -25));
        // torso
        rect(g, ox - 3, oy - 24, 6, 12, shirt);
        // front leg
        rect(g, ox - 2 - swing * f, oy - 12, 4, 10, pants);
        rect(g, ox - 2 - swing * f, oy - 2, 4, 2, shoe);
        // front arm
        rect(g, ox - 2 + swing * f + idle, oy - 24, 4, 7, shirt);
        rect(g, ox - 2 + swing * f + idle, oy - 17, 4, 5, skin);
        // head
        rect(g, ox - 4, oy - 32, 8, 8, skin);
        rect(g, ox - 4, oy - 32, 8, 3, 0xFF4A2F1B);
        rect(g, f > 0 ? ox - 4 : ox + 1, oy - 32, 3, 6, 0xFF4A2F1B);
        rect(g, f > 0 ? ox + 1 : ox - 3, oy - 28, 2, 2, 0xFFFFFFFF);
        rect(g, f > 0 ? ox + 2 : ox - 3, oy - 28, 1, 2, 0xFF3B2B8F);
        rect(g, f > 0 ? ox + 1 : ox - 3, oy - 25, 3, 1, 0xFF8A5A3A);
    }

    private void drawParticles(GuiGraphicsExtractor g, int cx, int cy) {
        for (float[] q : particles) {
            int col = Float.floatToRawIntBits(q[5]);
            float a = Math.min(1f, q[4] * 2.5f);
            rect(g, Math.round(q[0]) - cx, Math.round(q[1]) - cy, 3, 3, alpha(col, a));
        }
    }

    private void drawDarkness(GuiGraphicsExtractor g, float dl, int cy) {
        float nightA = (1f - dl) * 0.55f;
        float depthA = Math.max(0f, Math.min(1f, (cy + height * 0.5f - 30f * B) / (14f * B))) * 0.72f;
        darkness = Math.max(nightA, depthA);
        if (darkness > 0.01f) g.fill(0, 0, width, height, alpha(0x050814, darkness));
    }

    private void drawLampGlow(GuiGraphicsExtractor g, int cx, int cy) {
        if (darkness < 0.08f) return;
        int x0 = Math.max(0, cx / B - 4), x1 = Math.min(W - 1, (cx + width) / B + 4);
        int y0 = Math.max(0, cy / B - 4), y1 = Math.min(H - 1, (cy + height) / B + 4);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++) {
                if (world[bx][by] != LAMP) continue;
                int mx = bx * B + 8 - cx, my = by * B + 8 - cy;
                rect(g, mx - 58, my - 58, 116, 116, alpha(0xFFD060, darkness * 0.12f));
                rect(g, mx - 38, my - 38, 76, 76, alpha(0xFFD060, darkness * 0.14f));
                rect(g, mx - 20, my - 20, 40, 40, alpha(0xFFE090, darkness * 0.18f));
                // re-draw the lamp itself at full brightness
                drawBlock(g, LAMP, bx * B - cx, by * B - cy, bx, by);
            }
    }

    private void ctext(GuiGraphicsExtractor g, String s, int cxp, int y, int color) {
        g.text(font, s, cxp - font.width(s) / 2, y, color, true);
    }

    private void drawTitle(GuiGraphicsExtractor g) {
        String t = "MINECRAFT";
        float sc = Math.max(2f, Math.min(5f, width / 150f)) + titleKick * 0.8f;
        float bob = (float) Math.sin(clock * 1.5f) * 2f - titleKick * 6f;
        int fw = font.width(t);
        float ty = 18f + bob;
        titleX0 = (int) (width / 2f - fw * sc / 2f);
        titleX1 = (int) (width / 2f + fw * sc / 2f);
        titleY0 = (int) ty;
        titleY1 = (int) (ty + 9 * sc);

        g.pose().pushMatrix();
        g.pose().translate(width / 2f, ty);
        g.pose().scale(sc, sc);
        g.text(font, t, -fw / 2, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();

        // splash
        float pulse = 1.2f + 0.12f * (float) Math.sin(clock * 5f);
        g.pose().pushMatrix();
        g.pose().translate(width / 2f + fw * sc / 2f - 12f, ty + 9 * sc - 4f);
        g.pose().rotate(-0.35f);
        g.pose().scale(pulse, pulse);
        g.text(font, splash, -font.width(splash) / 2, 0, 0xFFFFFF00, true);
        g.pose().popMatrix();

        String key = LiveMenuMod.MENU_KEY.getTranslatedKeyMessage().getString();
        ctext(g, "Press [" + key + "] for the menu", width / 2, titleY1 + 12, alpha(0xFFFFFF, 0.65f + 0.35f * (float) Math.sin(clock * 3f)));
    }

    private void drawHud(GuiGraphicsExtractor g) {
        PartyManager pm = PartyManager.I;
        if (pm.role != PartyManager.Role.NONE) {
            String t = "Party: " + pm.roster.size() + " member" + (pm.roster.size() == 1 ? "" : "s");
            if (pm.role == PartyManager.Role.MEMBER && !pm.destType.equals("NONE"))
                t += "  |  " + pm.leaderName + " is playing" + (pm.autoJoin ? " (joining...)" : " - open Party to join");
            g.text(font, t, 38, 14, 0xFF55FF55, true);
        }
        if (LiveMenuConfig.showHint) g.text(font, hint, 6, height - 12, 0xCCFFFFFF, true);

        int slot = 24, total = slot * HOTBAR.length;
        int x0 = (width - total) / 2 - 40, y = height - 34;
        for (int i = 0; i < HOTBAR.length; i++) {
            int x = x0 + i * slot;
            rect(g, x, y, slot - 2, slot - 2, 0xAA000000);
            if (i == sel) {
                rect(g, x - 1, y - 1, slot, 2, 0xFFFFFFFF);
                rect(g, x - 1, y + slot - 3, slot, 2, 0xFFFFFFFF);
                rect(g, x - 1, y - 1, 2, slot, 0xFFFFFFFF);
                rect(g, x + slot - 3, y - 1, 2, slot, 0xFFFFFFFF);
            }
            int id = HOTBAR[i];
            rect(g, x + 5, y + 5, 12, 12, id == LAMP ? 0xFFFFEFA0 : BASE[id]);
            rect(g, x + 5, y + 5, 12, 2, shade(BASE[id], 25));
            String n = String.valueOf(inv[id]);
            g.text(font, n, x + slot - 4 - font.width(n), y + slot - 11, inv[id] > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
        }
        ctext(g, HOTBAR_NAMES[sel], x0 + total / 2, y - 11, 0xFFFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        Icons.draw(g, Icons.MENU, 8 + (22 - Icons.w(Icons.MENU, 1)) / 2, 8 + (20 - Icons.h(Icons.MENU, 1)) / 2, 1, 0xFFFFFFFF);
    }
}
