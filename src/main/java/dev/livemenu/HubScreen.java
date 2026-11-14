package dev.livemenu;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Dark three-panel hub (sidebar / content / preview), opened from the hamburger button or the menu hotkey.
 * Immediate-mode: buttons are drawn and registered every frame, so layout is always up to date.
 */
public class HubScreen extends Screen {

    public enum Tab {
        FRIENDS("Friends", 0xFF55CCFF, Icons.FRIENDS),
        PARTY("Party", 0xFF55FF55, Icons.FRIENDS),
        SETTINGS("Settings", 0xFFFFDD55, Icons.GEAR),
        EXTRAS("Extras", 0xFFFFAA33, Icons.MENU);

        final String label;
        final int color;
        final String[] icon;
        Tab(String label, int color, String[] icon) { this.label = label; this.color = color; this.icon = icon; }
    }

    private static final int BG = 0xFF121212, FRAME = 0xFF2A2A2A, PANEL = 0xFF1B1B1B, HEADER = 0xFF202020;
    private static final int BTN = 0xFF2B2B2B, BLUE = 0xFF2F5AA8, RED = 0xFF8A2E2E;
    private static final int HDR = 22, CW = 84, CH = 76;
    private static final int[] SKIN = {0xFFC68E65, 0xFFF1C27D, 0xFF8D5524, 0xFFE0AC69, 0xFFFFDBAC};
    private static final int[] HAIR = {0xFF4A2F1B, 0xFF1B1B1B, 0xFFD9B44A, 0xFFB5651D, 0xFFE8E8E8, 0xFF7A3B2E};
    private static final int[] SHIRT = {0xFFD98A1E, 0xFF00A8A8, 0xFFB22222, 0xFF4C8C3A, 0xFF7A4FB0, 0xFF3A6FB0};
    private static final int[] PANTS = {0xFF2F4F8F, 0xFF3A3AA0, 0xFF222222, 0xFF5A4630};

    private record Btn(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Screen parent;
    private Tab tab;
    private final List<Btn> btns = new ArrayList<>();
    private EditBox searchBox, addBox;
    private String selected = null;
    private boolean adding = false;
    private int scroll = 0, contentH = 0, mx, my;
    private String toastText = "";
    private long toastUntil = 0;

    public HubScreen(Screen parent, Tab tab) {
        super(Component.literal("Menu"));
        this.parent = parent;
        this.tab = tab;
    }

    // ---------------------------------------------------------------- setup
    @Override
    protected void init() {
        searchBox = new EditBox(font, 0, 0, 100, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search"));
        searchBox.setMaxLength(16);
        addRenderableWidget(searchBox);
        addBox = new EditBox(font, 0, 0, 100, 16, Component.literal("Name"));
        addBox.setHint(Component.literal("Friend's name"));
        addBox.setMaxLength(16);
        addRenderableWidget(addBox);
        searchBox.visible = false;
        addBox.visible = false;
    }

    @Override
    public void onClose() {
        LiveMenuConfig.save();
        minecraft.gui.setScreen(parent);
    }

    private void toast(String s) {
        toastText = s;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean typing = getFocused() instanceof EditBox eb && eb.isFocused();
        if (!typing && LiveMenuMod.MENU_KEY.matches(e)) { onClose(); return true; }
        if (adding && typing && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) { submitAdd(); return true; }
        if (!typing && e.key() == InputConstants.KEY_DOWN) { scroll += 40; return true; }                // down
        if (!typing && e.key() == InputConstants.KEY_UP) { scroll = Math.max(0, scroll - 40); return true; } // up
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            for (int i = btns.size() - 1; i >= 0; i--) {
                Btn b = btns.get(i);
                if (b.hit(e.x(), e.y())) {
                    setFocused(null);
                    searchBox.setFocused(false);
                    addBox.setFocused(false);
                    b.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    private void submitAdd() {
        String err = FriendsStore.add(addBox.getValue());
        if (err == null) {
            selected = addBox.getValue().trim();
            toast("Added " + selected);
            addBox.setValue("");
            adding = false;
        } else {
            toast(err);
        }
    }

    // ---------------------------------------------------------------- drawing helpers
    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    private static int lighten(int c, int d) {
        int r = Math.min(255, ((c >> 16) & 255) + d), gr = Math.min(255, ((c >> 8) & 255) + d), b = Math.min(255, (c & 255) + d);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) { g.text(font, s, x, y, color, true); }

    private void ctext(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        g.text(font, s, cx - font.width(s) / 2, y, color, true);
    }

    /** Flat dark button. action == null draws it disabled. */
    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int bg, Runnable action) {
        boolean hover = action != null && mx >= x && mx < x + w && my >= y && my < y + h;
        rect(g, x, y, w, h, hover ? lighten(bg, 22) : bg);
        rect(g, x, y + h - 1, w, 1, 0x40000000);
        ctext(g, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, action == null ? 0xFF777777 : 0xFFFFFFFF);
        if (action != null) btns.add(new Btn(x, y, w, h, action));
    }

    private void hit(int x, int y, int w, int h, Runnable action) { btns.add(new Btn(x, y, w, h, action)); }

    private void avatar(GuiGraphicsExtractor g, String name, int x, int y, int p) {
        Random r = new Random(name.toLowerCase().hashCode());
        int skin = SKIN[r.nextInt(SKIN.length)], hair = HAIR[r.nextInt(HAIR.length)];
        int shirt = SHIRT[r.nextInt(SHIRT.length)], pants = PANTS[r.nextInt(PANTS.length)];
        int eye = r.nextBoolean() ? 0xFF3B6FD0 : 0xFF3A8A3A;
        // legs, torso, arms
        rect(g, x + 4 * p, y + 20 * p, 4 * p, 12 * p, pants);
        rect(g, x + 8 * p, y + 20 * p, 4 * p, 12 * p, lighten(pants, -12));
        rect(g, x + 4 * p, y + 30 * p, 8 * p, 2 * p, 0xFF555555);
        rect(g, x + 4 * p, y + 8 * p, 8 * p, 12 * p, shirt);
        rect(g, x, y + 8 * p, 4 * p, 12 * p, shirt);
        rect(g, x + 12 * p, y + 8 * p, 4 * p, 12 * p, shirt);
        rect(g, x, y + 16 * p, 4 * p, 4 * p, skin);
        rect(g, x + 12 * p, y + 16 * p, 4 * p, 4 * p, skin);
        // head
        rect(g, x + 4 * p, y, 8 * p, 8 * p, skin);
        rect(g, x + 4 * p, y, 8 * p, 3 * p, hair);
        rect(g, x + 4 * p, y + 3 * p, p, 2 * p, hair);
        rect(g, x + 11 * p, y + 3 * p, p, 2 * p, hair);
        rect(g, x + 5 * p, y + 4 * p, 2 * p, p, 0xFFFFFFFF);
        rect(g, x + 9 * p, y + 4 * p, 2 * p, p, 0xFFFFFFFF);
        rect(g, x + 6 * p, y + 4 * p, p, p, eye);
        rect(g, x + 9 * p, y + 4 * p, p, p, eye);
        rect(g, x + 6 * p, y + 6 * p, 4 * p, p, lighten(skin, -45));
    }

    private static final String[] HEART = {"##.##", "#####", "#####", ".###.", "..#.."};

    // ---------------------------------------------------------------- main draw
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        mx = mouseX;
        my = mouseY;
        btns.clear();
        searchBox.visible = false;
        addBox.visible = false;
        rect(g, 0, 0, width, height, BG);

        int x0 = 14, top = 24, pw = width - 28, ph = height - top - 12;
        boolean showRight = tab == Tab.FRIENDS && pw >= 380;
        int sbW = 94, rightW = showRight ? Math.min(170, pw / 3) : 0;
        int midX = x0 + sbW + 3, midW = pw - sbW - 3 - (showRight ? rightW + 3 : 0), rX = midX + midW + 3;

        rect(g, x0 - 3, top - 3, pw + 6, ph + 6, FRAME);
        rect(g, x0, top, sbW, ph, PANEL);
        rect(g, midX, top, midW, ph, PANEL);
        rect(g, x0, top, sbW, HDR, HEADER);
        rect(g, midX, top, midW, HDR, HEADER);
        if (showRight) {
            rect(g, rX, top, rightW, ph, PANEL);
            rect(g, rX, top, rightW, HDR, HEADER);
        }

        button(g, 6, 3, 16, 16, "<", BTN, this::onClose);

        // sidebar
        text(g, "Menu", x0 + 10, top + 7, 0xFFFFFFFF);
        int ty = top + HDR + 6;
        for (Tab t : Tab.values()) {
            final Tab tt = t;
            if (t == tab) rect(g, x0, ty, sbW, 28, 0xFF262626);
            rect(g, x0, ty + 27, sbW, 1, 0xFF141414);
            Icons.draw(g, t.icon, x0 + 9, ty + (28 - Icons.h(t.icon, 1)) / 2, 1, t.color);
            text(g, t.label, x0 + 28, ty + 10, t.color);
            hit(x0, ty, sbW, 28, () -> { tab = tt; scroll = 0; adding = false; });
            ty += 28;
        }

        switch (tab) {
            case FRIENDS -> drawFriends(g, midX, top, midW, ph, rX, rightW, showRight);
            case PARTY -> drawParty(g, midX, top, midW);
            case SETTINGS -> drawSettings(g, midX, top, midW);
            case EXTRAS -> drawExtras(g, midX, top, midW);
        }

        if (System.currentTimeMillis() < toastUntil) {
            int tw = font.width(toastText) + 16;
            rect(g, width / 2 - tw / 2, height - 24, tw, 16, 0xEE000000);
            ctext(g, toastText, width / 2, height - 20, 0xFFFFFFFF);
        }
    }

    // ---------------------------------------------------------------- Friends
    private void drawFriends(GuiGraphicsExtractor g, int midX, int top, int midW, int ph, int rX, int rightW, boolean showRight) {
        List<FriendsStore.Friend> all = new ArrayList<>(FriendsStore.list());
        all.sort(Comparator.comparing((FriendsStore.Friend f) -> !f.fav).thenComparing(f -> f.name.toLowerCase()));
        if (selected == null && !all.isEmpty()) selected = all.get(0).name;
        if (selected != null && all.stream().noneMatch(f -> f.name.equalsIgnoreCase(selected))) {
            selected = all.isEmpty() ? null : all.get(0).name;
        }

        text(g, "Friends [" + all.size() + "]", midX + 10, top + 7, 0xFFFFFFFF);
        int addW = 84, ax = midX + midW - addW - 6;
        button(g, ax, top + 3, addW, 16, "+ Add Friend", BLUE, () -> {
            adding = true;
            addBox.setFocused(true);
            setFocused(addBox);
        });
        searchBox.visible = true;
        searchBox.setX(ax - 106);
        searchBox.setY(top + 3);
        searchBox.setWidth(100);

        int contentTop = top + HDR + 6, contentBot = top + ph - 4;
        int cx = midX + 10, cw = midW - 20;
        int yOff = 0;
        if (adding) {
            addBox.visible = true;
            addBox.setX(cx);
            addBox.setY(contentTop);
            addBox.setWidth(Math.max(60, cw - 130));
            int bx = cx + Math.max(60, cw - 130) + 4;
            button(g, bx, contentTop, 60, 16, "Add", BLUE, this::submitAdd);
            button(g, bx + 64, contentTop, 58, 16, "Cancel", BTN, () -> { adding = false; addBox.setValue(""); });
            yOff = 26;
        }

        String q = searchBox.getValue().toLowerCase();
        List<FriendsStore.Friend> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (FriendsStore.Friend f : all) {
            if (!q.isEmpty() && !f.name.toLowerCase().contains(q)) continue;
            (f.fav ? favs : rest).add(f);
        }

        int cols = Math.max(1, (cw + 6) / (CW + 6));
        int listTop = contentTop + yOff;
        int maxScroll = Math.max(0, contentH - (contentBot - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        int y = listTop - scroll;
        if (!favs.isEmpty()) y = section(g, "Favorites", favs, cx, y, cw, cols, listTop, contentBot);
        if (!rest.isEmpty()) y = section(g, favs.isEmpty() ? "Friends" : "Library", rest, cx, y, cw, cols, listTop, contentBot);
        contentH = y + scroll - listTop;
        if (favs.isEmpty() && rest.isEmpty()) {
            ctext(g, all.isEmpty() ? "No friends yet - click + Add Friend" : "No matches", midX + midW / 2, listTop + 30, 0xFF888888);
        }
        if (LiveMenuConfig.essentialDetected()) {
            text(g, "Essential detected - its Social tab keeps its own list.", cx, top + ph - 14, 0xFF777777);
        }

        if (showRight) drawFriendPreview(g, all, rX, top, rightW, ph);
    }

    private int section(GuiGraphicsExtractor g, String title, List<FriendsStore.Friend> list, int x, int y, int w,
                        int cols, int topLim, int botLim) {
        if (y >= topLim && y + 12 <= botLim) {
            int tw = font.width(title);
            rect(g, x, y + 4, (w - tw) / 2 - 8, 1, 0xFF3A3A3A);
            rect(g, x + (w + tw) / 2 + 8, y + 4, w - (w + tw) / 2 - 8, 1, 0xFF3A3A3A);
            ctext(g, title, x + w / 2, y, 0xFFCCCCCC);
        }
        y += 16;
        for (int i = 0; i < list.size(); i += cols) {
            boolean visible = y >= topLim && y + CH + 14 <= botLim;
            for (int c = 0; c < cols && i + c < list.size(); c++) {
                FriendsStore.Friend f = list.get(i + c);
                int cx = x + c * (CW + 6);
                if (!visible) continue;
                boolean sel = f.name.equalsIgnoreCase(selected);
                rect(g, cx, y, CW, CH, sel ? 0xFF3A3A3A : 0xFF262626);
                rect(g, cx, y + CH - 2, CW, 2, sel ? 0xFFFFFFFF : 0xFF555555);
                avatar(g, f.name, cx + (CW - 32) / 2, y + (CH - 64) / 2 - 1, 2);
                text(g, f.name, cx + 2, y + CH + 3, 0xFFDDDDDD);
                final String name = f.name;
                hit(cx, y, CW, CH, () -> selected = name);
                Icons.draw(g, HEART, cx + CW - 11, y + 4, 1, f.fav ? 0xFFFF70D0 : 0xFF4A4A4A);
                hit(cx + CW - 14, y + 1, 13, 11, () -> FriendsStore.toggleFavorite(name));
            }
            y += CH + 20;
        }
        return y + 4;
    }

    private void drawFriendPreview(GuiGraphicsExtractor g, List<FriendsStore.Friend> all, int rX, int top, int rightW, int ph) {
        if (all.isEmpty() || selected == null) {
            ctext(g, "No friend selected", rX + rightW / 2, top + ph / 2, 0xFF777777);
            return;
        }
        int idx = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).name.equalsIgnoreCase(selected)) idx = i;
        final int cur = idx;
        button(g, rX + 5, top + 3, 16, 16, "<", BTN, () -> selected = all.get((cur - 1 + all.size()) % all.size()).name);
        button(g, rX + rightW - 21, top + 3, 16, 16, ">", BTN, () -> selected = all.get((cur + 1) % all.size()).name);
        ctext(g, selected, rX + rightW / 2, top + 7, 0xFFFFFFFF);

        int bh = 16, gap = 3, buttonsH = 4 * (bh + gap);
        int avail = ph - HDR - buttonsH - 10;
        int p = Math.max(1, Math.min(5, avail / 32));
        avatar(g, selected, rX + (rightW - 16 * p) / 2, top + HDR + 8 + Math.max(0, (avail - 32 * p) / 2), p);

        final String name = selected;
        FriendsStore.Friend f = all.get(cur);
        int bx = rX + 8, bw = rightW - 16, by = top + ph - buttonsH - 2;
        PartyManager pm = PartyManager.I;
        button(g, bx, by, bw, bh, "Invite to party", BLUE, pm.role == PartyManager.Role.LEADER ? () -> {
            minecraft.keyboardHandler.setClipboard("Join my Minecraft party! Code: " + pm.code);
            toast("Invite for " + name + " copied - send it to them");
        } : null);
        by += bh + gap;
        button(g, bx, by, bw, bh, "Copy name", BTN, () -> { minecraft.keyboardHandler.setClipboard(name); toast("Copied " + name); });
        by += bh + gap;
        button(g, bx, by, bw, bh, f.fav ? "Unfavorite" : "Favorite", BTN, () -> FriendsStore.toggleFavorite(name));
        by += bh + gap;
        button(g, bx, by, bw, bh, "Remove", RED, () -> { FriendsStore.remove(name); toast("Removed " + name); });
    }

    // ---------------------------------------------------------------- Party
    private void drawParty(GuiGraphicsExtractor g, int midX, int top, int midW) {
        PartyManager pm = PartyManager.I;
        text(g, "Party", midX + 10, top + 7, 0xFFFFFFFF);
        int x = midX + 14, y = top + HDR + 12;
        String state = switch (pm.role) {
            case NONE -> "You are not in a party.";
            case LEADER -> "You are leading a party (" + pm.roster.size() + " member" + (pm.roster.size() == 1 ? "" : "s") + ").";
            case MEMBER -> "You are in " + pm.leaderName + "'s party (" + pm.roster.size() + " members).";
        };
        text(g, state, x, y, 0xFFFFFFFF);
        y += 14;
        if (pm.role == PartyManager.Role.LEADER) { text(g, "Code: " + pm.code, x, y, 0xFF55FF55); y += 14; }
        if (pm.role != PartyManager.Role.NONE) {
            String d = switch (pm.destType) {
                case "SERVER" -> "Server: " + pm.destValue;
                case "WORLD" -> "Leader's world (LAN port " + pm.destValue + ")";
                default -> "No destination chosen yet";
            };
            text(g, "Destination: " + d, x, y, 0xFF55FFFF);
            y += 14;
            for (String n : pm.roster) { text(g, " - " + n, x, y, 0xFFCCCCCC); y += 11; }
            y += 4;
        }
        button(g, x, y + 4, Math.min(200, midW - 28), 18, "Open party controls", BLUE,
                () -> minecraft.gui.setScreen(new PartyScreen(this)));
    }

    // ---------------------------------------------------------------- Settings
    private void drawSettings(GuiGraphicsExtractor g, int midX, int top, int midW) {
        text(g, "Settings", midX + 10, top + 7, 0xFFFFFFFF);
        int x = midX + 14, y = top + HDR + 12, w = midW - 28;
        toggleRow(g, x, y, w, "Auto-join party leader", PartyManager.I.autoJoin, () -> PartyManager.I.autoJoin = !PartyManager.I.autoJoin);
        y += 24;
        toggleRow(g, x, y, w, "Show controls hint", LiveMenuConfig.showHint, () -> LiveMenuConfig.showHint = !LiveMenuConfig.showHint);
        y += 24;
        toggleRow(g, x, y, w, "Day / night cycle", LiveMenuConfig.dayNightCycle, () -> LiveMenuConfig.dayNightCycle = !LiveMenuConfig.dayNightCycle);
        y += 30;
        button(g, x, y, Math.min(220, w), 18, "Minecraft options / controls...", BTN,
                () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options)));
        y += 26;
        text(g, "Menu key: [" + LiveMenuMod.MENU_KEY.getTranslatedKeyMessage().getString() + "]   Party key: ["
                + LiveMenuMod.PARTY_KEY.getTranslatedKeyMessage().getString() + "]", x, y, 0xFFAAAAAA);
        y += 12;
        text(g, "Rebind them under Options > Controls > Key Binds.", x, y, 0xFF777777);
    }

    private void toggleRow(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean on, Runnable toggle) {
        rect(g, x, y, w, 20, 0xFF222222);
        text(g, label, x + 8, y + 6, 0xFFFFFFFF);
        button(g, x + w - 52, y + 2, 48, 16, on ? "ON" : "OFF", on ? 0xFF2E7A3A : 0xFF4A4A4A, toggle);
    }

    // ---------------------------------------------------------------- Extras
    private void drawExtras(GuiGraphicsExtractor g, int midX, int top, int midW) {
        text(g, "Extras", midX + 10, top + 7, 0xFFFFFFFF);
        int x = midX + 14, y = top + HDR + 12, w = Math.min(220, midW - 28);
        button(g, x, y, w, 20, "Singleplayer", BTN, () -> minecraft.gui.setScreen(new SelectWorldScreen(this)));
        y += 24;
        button(g, x, y, w, 20, "Multiplayer", BTN, () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this)));
        y += 24;
        button(g, x, y, w, 20, "Party", BTN, () -> tab = Tab.PARTY);
        y += 24;
        button(g, x, y, w, 20, "Options", BTN, () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options)));
        y += 30;
        rect(g, x, y, w, 20, RED);
        button(g, x, y, w, 20, "      Quit Game", RED, () -> minecraft.stop());
        Icons.draw(g, Icons.EXIT, x + w / 2 - 38, y + 4, 1, 0xFFFFFFFF);
    }
}
