package dev.livemenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public class PartyScreen extends Screen {
    private final Screen parent;
    private final PartyManager pm = PartyManager.I;

    // text typed into boxes survives widget rebuilds
    private String hostText = PartyManager.guessAddress();
    private String codeText = "";
    private String serverText = "";
    private int savedIdx = -1;
    private int seen = -1;

    private EditBox hostBox, codeBox, serverBox;

    public PartyScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
    }

    private EditBox box(int x, int y, int w, String value, int max, java.util.function.Consumer<String> sink) {
        EditBox e = new EditBox(font, x, y, w, 20, Component.empty());
        e.setMaxLength(max);
        e.setValue(value);
        e.setResponder(sink);
        return addRenderableWidget(e);
    }

    @Override
    protected void init() {
        seen = pm.version;
        int cx = width / 2, w = 240, x = cx - w / 2;
        int y = 44;
        String me = minecraft.getUser().getName();

        switch (pm.role) {
            case NONE -> {
                hostBox = box(x, y + 12, w, hostText, 64, s -> hostText = s);
                addRenderableWidget(Button.builder(Component.literal("Create party (I'm the leader)"), b ->
                        pm.createParty(me, hostText)).bounds(x, y + 36, w, 20).build());
                codeBox = box(x, y + 84, w, codeText, 120, s -> codeText = s);
                addRenderableWidget(Button.builder(Component.literal("Join party with code"), b ->
                        pm.joinParty(codeText, me)).bounds(x, y + 108, w, 20).build());
            }
            case LEADER -> {
                addRenderableWidget(Button.builder(Component.literal("Copy party code"), b ->
                        minecraft.keyboardHandler.setClipboard(pm.code)).bounds(x, y, w, 20).build());
                serverBox = box(x, y + 50, w - 84, serverText, 100, s -> serverText = s);
                addRenderableWidget(Button.builder(Component.literal("Set server"), b ->
                        pm.setDestinationServer(serverText)).bounds(x + w - 80, y + 50, 80, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Next saved server"), b -> {
                    ServerList list = new ServerList(minecraft);
                    list.load();
                    if (list.size() == 0) return;
                    savedIdx = (savedIdx + 1) % list.size();
                    ServerData d = list.get(savedIdx);
                    serverText = d.ip;
                    serverBox.setValue(d.ip);
                }).bounds(x, y + 74, w / 2 - 2, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Host a world..."), b -> {
                    pm.hostOnLoad = true;
                    minecraft.gui.setScreen(new SelectWorldScreen(this));
                }).bounds(x + w / 2 + 2, y + 74, w / 2 - 2, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Clear destination"), b ->
                        pm.clearDestination()).bounds(x, y + 98, w / 2 - 2, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Disband party"), b ->
                        pm.leaveParty()).bounds(x + w / 2 + 2, y + 98, w / 2 - 2, 20).build());
            }
            case MEMBER -> {
                Button join = addRenderableWidget(Button.builder(Component.literal("Join the leader now"), b ->
                        PartyManager.connect(minecraft, this)).bounds(x, y, w, 20).build());
                join.active = !pm.destType.equals("NONE") && minecraft.level == null;
                addRenderableWidget(Button.builder(Component.literal("Auto-join: " + (pm.autoJoin ? "ON" : "OFF")), b -> {
                    pm.autoJoin = !pm.autoJoin;
                    b.setMessage(Component.literal("Auto-join: " + (pm.autoJoin ? "ON" : "OFF")));
                }).bounds(x, y + 24, w, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Leave party"), b ->
                        pm.leaveParty()).bounds(x, y + 48, w, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 60, height - 28, 120, 20).build());
    }

    @Override
    public void tick() {
        if (pm.version != seen) rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private void line(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        int cx = width / 2, w = 240, x = cx - w / 2;
        String title = "PARTY";
        g.text(font, title, cx - font.width(title) / 2, 14, 0xFFFFFFFF, true);

        switch (pm.role) {
            case NONE -> {
                line(g, "Your address (LAN / VPN / public IP) so friends can reach you:", x, 44, 0xFFAAAAAA);
                line(g, "Or paste a friend's party code:", x, 44 + 72, 0xFFAAAAAA);
            }
            case LEADER -> {
                line(g, "Code: " + pm.code, x, 32, 0xFF55FF55);
                line(g, "Pick where the party plays (server address):", x, 44 + 38, 0xFFAAAAAA);
                drawRoster(g, x, 44 + 128);
                drawDestination(g, x, 44 + 128 + 14 * (pm.roster.size() + 2));
            }
            case MEMBER -> {
                line(g, "Leader: " + pm.leaderName, x, 32, 0xFF55FF55);
                drawRoster(g, x, 44 + 80);
                drawDestination(g, x, 44 + 80 + 14 * (pm.roster.size() + 2));
                if (minecraft.level != null) line(g, "Return to the main menu to follow the leader.", x, height - 44, 0xFFFFAA00);
            }
        }
        if (!pm.status.isEmpty()) {
            line(g, pm.status, cx - font.width(pm.status) / 2, height - 44, 0xFFFFFF55);
        }
    }

    private void drawRoster(GuiGraphicsExtractor g, int x, int y) {
        line(g, "Members (" + pm.roster.size() + "):", x, y, 0xFFFFFFFF);
        int i = 1;
        for (String n : pm.roster) {
            line(g, " - " + n, x, y + 14 * i++, 0xFFCCCCCC);
        }
    }

    private void drawDestination(GuiGraphicsExtractor g, int x, int y) {
        String d = switch (pm.destType) {
            case "SERVER" -> "Server: " + pm.destValue;
            case "WORLD" -> "Leader's world (LAN port " + pm.destValue + ")";
            default -> "No destination chosen yet";
        };
        line(g, "Destination: " + d, x, y, 0xFF55FFFF);
    }
}
