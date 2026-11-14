package dev.livemenu;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

public class LiveMenuMod implements ClientModInitializer {
    public static final String MOD_ID = "livemenu";

    /** Opens/closes the panel with Singleplayer / Multiplayer / Options / Quit. Default: P. Rebindable in Controls. */
    public static KeyMapping MENU_KEY;

    /** Opens the Party screen from anywhere (title screen or in-game). Default: O. */
    public static KeyMapping PARTY_KEY;

    @Override
    public void onInitializeClient() {
        LiveMenuConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.livemenu.menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_P,
                category));

        PARTY_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.livemenu.party",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_O,
                category));

        // Swap the vanilla title screen for ours whenever it shows up.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.gui.screen() instanceof TitleScreen) {
                mc.gui.setScreen(new LiveTitleScreen());
            }
            while (PARTY_KEY.consumeClick()) {
                if (mc.gui.screen() == null) mc.gui.setScreen(new PartyScreen(null));
            }
            PartyManager.I.onTick(mc);
        });
    }
}
