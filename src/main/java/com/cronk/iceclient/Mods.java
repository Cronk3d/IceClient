package com.cronk.iceclient;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class Mods {
    public static final List<ModDef> ALL = new ArrayList<ModDef>();
    public static ModDef APPEARANCE;   // settings for the Appearance page, not in the mods list
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void init() {
        ALL.clear();

        APPEARANCE = new ModDef("appearance", "Appearance", "Utility", "", () -> true, v -> { });
        APPEARANCE.settings.add(new ModDef.Slider("GUI size", "Size of this settings window (applies when you let go)",
                50, 150, "%", () -> ClickerConfig.guiScale, v -> ClickerConfig.guiScale = v));
        APPEARANCE.settings.add(new ModDef.Action("HUD sizes", "Put every HUD element and the chat tabs back to normal size",
                "Reset sizes", () -> {
                    for (Hud.Element e : Hud.ELEMENTS) { e.scale = 100; e.save(); }
                    ClickerConfig.chatScale = 100;
                    ClickerConfig.save();
                }));

        ModDef ac = new ModDef("autoclicker", "Auto Clicker", "Utility",
                "Left / right clicking while held. Pauses in menus, while mining and on potions, food and pearls.",
                () -> ClickerConfig.autoOn, v -> { ClickerConfig.autoOn = v; ClickerConfig.save(); });
        ac.settings.add(new ModDef.Toggle("Left click", "Keep clicking while you hold left click",
                () -> ClickerConfig.leftOn, v -> ClickerConfig.leftOn = v));
        ac.settings.add(cps("Left min", "Lowest clicks per second", () -> ClickerConfig.leftMin, v -> ClickerConfig.leftMin = v));
        ac.settings.add(cps("Left max", "Highest clicks per second", () -> ClickerConfig.leftMax, v -> ClickerConfig.leftMax = v));
        ac.settings.add(new ModDef.Toggle("Right click", "Keep clicking while you hold right click",
                () -> ClickerConfig.rightOn, v -> ClickerConfig.rightOn = v));
        ac.settings.add(cps("Right min", "Lowest clicks per second", () -> ClickerConfig.rightMin, v -> ClickerConfig.rightMin = v));
        ac.settings.add(cps("Right max", "Highest clicks per second", () -> ClickerConfig.rightMax, v -> ClickerConfig.rightMax = v));
        ac.settings.add(cps("Paper min", "Lowest clicks per second while holding paper", () -> ClickerConfig.paperMin, v -> ClickerConfig.paperMin = v));
        ac.settings.add(cps("Paper max", "Highest clicks per second while holding paper", () -> ClickerConfig.paperMax, v -> ClickerConfig.paperMax = v));
        ac.settings.add(new ModDef.Slider("Right start delay", "Wait this long after you press right click before it starts",
                0, 1000, "ms", () -> ClickerConfig.rightDelay, v -> ClickerConfig.rightDelay = v));
        ac.settings.add(new ModDef.Text("Allowed on", "Servers it may run on (part of the address, comma separated)",
                () -> ClickerConfig.allowedOn, v -> ClickerConfig.allowedOn = v));
        ac.reset = ClickerConfig::resetAuto;
        ALL.add(ac);

        ModDef upd = new ModDef("updater", "Auto Updater", "Utility",
                "Checks a version file you host and installs new versions when you say so (checksum verified).",
                () -> ClickerConfig.updOn, v -> { ClickerConfig.updOn = v; ClickerConfig.save(); });
        upd.settings.add(new ModDef.Info("Status", "Current update state", () -> Updater.status));
        upd.settings.add(new ModDef.Text("Update URL", "HTTPS link to your version.json",
                () -> ClickerConfig.gets("updater", "url", ""), v -> ClickerConfig.sets("updater", "url", v)));
        upd.settings.add(new ModDef.Action("Check", "Look for a new version now", "Check now", Updater::check));
        upd.settings.add(new ModDef.Action("Install", "Download it. It is applied when you close the game",
                "Install update", Updater::install));
        upd.settings.add(new ModDef.Action("Cancel", "Throw away a downloaded update", "Cancel", Updater::cancel));
        upd.reset = () -> { ClickerConfig.updOn = true; ClickerConfig.save(); };
        ALL.add(upd);

        ModDef chat = new ModDef("chattabs", "Chat Tabs", "Chat",
                "Tabs for All, Mentions, Private, Faction, Truce and Ally chat, with unread counts and a channel tag.",
                () -> ClickerConfig.chatOn,
                v -> { ClickerConfig.chatOn = v; ClickerConfig.save(); if (!v) ChatTabs.reset(); });
        chat.settings.add(text("Mention words", "Extra words that count as a mention (your name always does)",
                "mentionWords", ""));
        chat.settings.add(text("Ignore words", "Messages containing these are never mentions",
                "ignoreWords", ""));
        chat.settings.add(text("Private matches", "Text near the start of a private message",
                "privatePat", ChatTabs.D_PRIV));
        chat.settings.add(text("Faction matches", "Text near the start of faction chat", "factionPat", ChatTabs.D_FAC));
        chat.settings.add(text("Truce matches", "Text near the start of truce chat", "trucePat", ChatTabs.D_TRU));
        chat.settings.add(text("Ally matches", "Text near the start of ally chat", "allyPat", ChatTabs.D_ALY));
        chat.settings.add(text("Faction command", "Command that switches you to faction chat (tag shows)",
                "chanFaction", ChatTabs.D_CMD_FAC));
        chat.settings.add(text("Truce command", "Command that switches you to truce chat",
                "chanTruce", ChatTabs.D_CMD_TRU));
        chat.settings.add(text("Ally command", "Command that switches you to ally chat",
                "chanAlly", ChatTabs.D_CMD_ALY));
        chat.settings.add(text("All chat command", "Command that switches you to all chat",
                "chanAll", ChatTabs.D_CMD_ALL));
        chat.settings.add(new ModDef.Slider("Tab size", "Size of the tab bar above the chat box",
                50, 200, "%", () -> ClickerConfig.chatScale, v -> ClickerConfig.chatScale = v));
        chat.reset = () -> {
            String[][] d = {{"mentionWords", ""}, {"ignoreWords", ""}, {"privatePat", ChatTabs.D_PRIV},
                    {"factionPat", ChatTabs.D_FAC}, {"trucePat", ChatTabs.D_TRU}, {"allyPat", ChatTabs.D_ALY},
                    {"chanFaction", ChatTabs.D_CMD_FAC}, {"chanTruce", ChatTabs.D_CMD_TRU},
                    {"chanAlly", ChatTabs.D_CMD_ALY}, {"chanAll", ChatTabs.D_CMD_ALL}};
            for (String[] kv : d) ClickerConfig.sets("chat", kv[0], kv[1]);
            ClickerConfig.chatScale = 100;
            ClickerConfig.save();
        };
        ALL.add(chat);

        ModDef wm = new ModDef("worldmap", "World Map", "Utility",
                "Full-screen terrain map of what you have explored. Drag to pan, scroll to zoom.",
                () -> ClickerConfig.worldMapOn, v -> { ClickerConfig.worldMapOn = v; ClickerConfig.save(); },
                Keyboard.KEY_M);
        wm.keyAction = () -> {
            if (ClickerConfig.enabled && mc.theWorld != null) mc.displayGuiScreen(new WorldMapGui());
        };
        wm.settings.add(new ModDef.Action("Open", "Open the map now", "Open map", () -> {
            if (mc.theWorld != null) mc.displayGuiScreen(new WorldMapGui());
        }));
        wm.settings.add(new ModDef.Toggle("Save map to disk",
                "Remember explored terrain between sessions (saved in the background)",
                () -> ClickerConfig.mapSave, v -> ClickerConfig.mapSave = v));
        wm.settings.add(new ModDef.Action("Saved map", "Delete the saved map for this server and start fresh",
                "Clear saved map", MapManager::clearSaved));
        ALL.add(wm);

        for (final Hud.Element el : Hud.ELEMENTS) {
            ModDef m = new ModDef("hud_" + el.id, el.name, "HUD", hudDesc(el.name),
                    () -> el.enabled, v -> { el.enabled = v; el.save(); });
            ModDef.Slider size = new ModDef.Slider("Size", "How big it is on screen (you can also scroll over it in Edit HUD)",
                    30, 400, "%", () -> el.scale, v -> el.scale = v);
            size.onRelease = el::save;
            m.settings.add(size);
            if (el.movable) {
                m.settings.add(new ModDef.Action("Position", "Drag it where you want it",
                        "Edit HUD", () -> mc.displayGuiScreen(new HudEditGui())));
            }
            if (el.name.equals("Minimap")) {
                m.settings.add(new ModDef.Toggle("Circle", "Round minimap instead of a square one",
                        () -> ClickerConfig.mapCircle, v -> ClickerConfig.mapCircle = v));
                m.settings.add(new ModDef.Slider("Zoom", "How close the map is",
                        1, 8, "x", () -> ClickerConfig.mapZoom, v -> ClickerConfig.mapZoom = v));
                m.settings.add(new ModDef.KeySetting("Zoom in key", "Press to zoom the minimap in",
                        "minimap_zoom_in", Keyboard.KEY_EQUALS, () -> {
                            if (ClickerConfig.mapZoom < 8) { ClickerConfig.mapZoom++; ClickerConfig.save(); }
                        }));
                m.settings.add(new ModDef.KeySetting("Zoom out key", "Press to zoom the minimap out",
                        "minimap_zoom_out", Keyboard.KEY_MINUS, () -> {
                            if (ClickerConfig.mapZoom > 1) { ClickerConfig.mapZoom--; ClickerConfig.save(); }
                        }));
                m.settings.add(new ModDef.Toggle("Rotate", "Keep the map facing the way you look",
                        () -> ClickerConfig.mapRotate, v -> ClickerConfig.mapRotate = v));
                m.settings.add(new ModDef.Toggle("Coordinates", "Show your coordinates under the map",
                        () -> ClickerConfig.mapCoords, v -> ClickerConfig.mapCoords = v));
            }
            if (el.name.equals("Mention Bar")) {
                m.settings.add(new ModDef.Slider("Show for", "Seconds the box stays on screen",
                        1, 20, "s", () -> ClickerConfig.mentionSecs, v -> ClickerConfig.mentionSecs = v));
                m.settings.add(new ModDef.Toggle("Sound", "Play a sound when you are mentioned",
                        () -> ClickerConfig.mentionSound, v -> ClickerConfig.mentionSound = v));
            }
            if (el.name.equals("Gapple Ring")) {
                m.settings.add(new ModDef.Slider("Ring time", "Seconds the ring counts down for",
                        1, 120, "s", () -> ClickerConfig.gappleSeconds, v -> ClickerConfig.gappleSeconds = v));
            }
            m.reset = () -> {
                el.fx = el.dfx;
                el.fy = el.dfy;
                el.enabled = true;
                el.scale = 100;
                if (el.name.equals("Gapple Ring")) ClickerConfig.gappleSeconds = 30;
                if (el.name.equals("Minimap")) {
                    ClickerConfig.mapSize = 100; ClickerConfig.mapZoom = 4;
                    ClickerConfig.mapRotate = true; ClickerConfig.mapCoords = true;
                    ClickerConfig.mapCircle = false;
                }
                if (el.name.equals("Mention Bar")) { ClickerConfig.mentionSecs = 6; ClickerConfig.mentionSound = true; }
                el.save();
                ClickerConfig.save();
            };
            ALL.add(m);
        }
    }

    private static ModDef.Slider cps(String l, String d, java.util.function.IntSupplier g, java.util.function.IntConsumer s) {
        return new ModDef.Slider(l, d, 1, ClickerConfig.MAX_CPS, "cps", g, s);
    }

    private static ModDef.Text text(String label, String desc, String key, String def) {
        return new ModDef.Text(label, desc,
                () -> ClickerConfig.gets("chat", key, def), v -> ClickerConfig.sets("chat", key, v));
    }

    private static String hudDesc(String n) {
        switch (n) {
            case "Minimap": return "Top-down terrain map. Terrain only, no player or entity markers";
            case "Mention Bar": return "Popup box when your name is said in chat";
            case "Looking At": return "Shows the block or player you are aiming at";
            case "Compass": return "Direction strip along the top of the screen";
            case "Heart Counter": return "Your hearts, plus absorption hearts";
            default: return "Countdown ring around the crosshair after an enchanted gapple";
        }
    }

    /** Per-mod keybinds set in the settings GUI. */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (mc.currentScreen != null || !Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey();
        if (k == 0) return;
        for (ModDef m : ALL) {
            if (m.key == k && m.keyAction != null) {
                if (m.get.get()) m.keyAction.run();
            } else if (m.key == k) {
                m.set.accept(!m.get.get());
                if (mc.ingameGUI != null) {
                    mc.ingameGUI.setRecordPlaying(m.name + " " + (m.get.get() ? "ON" : "OFF"), false);
                }
            }
            // extra keybinds on the mod's page (e.g. minimap zoom) only work while the mod is on
            if (ClickerConfig.enabled && m.get.get()) {
                for (ModDef.Setting s : m.settings) {
                    if (s instanceof ModDef.KeySetting && ((ModDef.KeySetting) s).key == k) {
                        ((ModDef.KeySetting) s).action.run();
                    }
                }
            }
        }
    }
}
