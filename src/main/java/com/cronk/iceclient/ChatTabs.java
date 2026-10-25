package com.cronk.iceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat tabs (All / Mentions / Private / Faction / Truce / Ally) with unread counts,
 * mention detection and a channel tag next to the chat box.
 *
 * Which messages land in which tab is decided by text patterns that you can edit in
 * the "Chat Tabs" mod page, because every server formats its chat differently.
 */
public class ChatTabs {
    public static ChatTabs I;

    public static final String[] TABS = {"All", "Mentions", "Private", "Faction", "Truce", "Ally"};
    private static final int ALL = 0, MENT = 1, PRIV = 2, FAC = 3, TRU = 4, ALY = 5;

    public static final String D_PRIV = "-> me],whispers to you,[from ,(from ";
    public static final String D_FAC = "faction";
    public static final String D_TRU = "truce";
    public static final String D_ALY = "[ally]";
    // /f c f = faction, /f c t = truce, /f c a = ally, /f c = all chat
    public static final String D_CMD_FAC = "/f c f";
    public static final String D_CMD_TRU = "/f c t";
    public static final String D_CMD_ALY = "/f c a";
    public static final String D_CMD_ALL = "/f c";

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static class Msg {
        IChatComponent comp;
        String text;
        boolean mention, priv;
        int chan;   // 0 none, FAC, TRU or ALY
    }

    private final List<Msg> history = new ArrayList<Msg>();
    private final int[][] rects = new int[TABS.length][];
    private final int[] unread = new int[TABS.length];
    private int tab = ALL;

    /** Chat channel you last switched to with a command (FACTION / TRUCE / ALLY / ALL), or null. */
    public static String channel = null;

    private Field inputField;
    private boolean fieldTried;

    public ChatTabs() { I = this; }

    private static boolean active() {
        return ClickerConfig.enabled && ClickerConfig.chatOn;
    }

    // ---------------------------------------------------------------- classify

    private static String[] split(String csv) {
        List<String> out = new ArrayList<String>();
        for (String p : csv.split(",")) {
            p = p.trim().toLowerCase();
            if (!p.isEmpty()) out.add(p);
        }
        return out.toArray(new String[0]);
    }

    private static boolean hasAny(String text, String csv) {
        for (String p : split(csv)) if (text.contains(p)) return true;
        return false;
    }

    private Msg classify(IChatComponent comp) {
        Msg m = new Msg();
        m.comp = comp;
        String raw = EnumChatFormatting.getTextWithoutFormattingCodes(comp.getUnformattedText());
        m.text = raw == null ? "" : raw;
        String lower = m.text.toLowerCase();
        String head = lower.length() > 48 ? lower.substring(0, 48) : lower;

        m.priv = hasAny(head, ClickerConfig.gets("chat", "privatePat", D_PRIV));
        if (hasAny(head, ClickerConfig.gets("chat", "factionPat", D_FAC))) m.chan = FAC;
        else if (hasAny(head, ClickerConfig.gets("chat", "trucePat", D_TRU))) m.chan = TRU;
        else if (hasAny(head, ClickerConfig.gets("chat", "allyPat", D_ALY))) m.chan = ALY;
        m.mention = isMention(lower);
        return m;
    }

    /** A mention is your name (or a mention word) in the message body, not in the sender part. */
    private boolean isMention(String lower) {
        for (String ign : split(ClickerConfig.gets("chat", "ignoreWords", ""))) {
            if (lower.contains(ign)) return false;
        }
        List<String> words = new ArrayList<String>();
        words.add(mc.getSession().getUsername().toLowerCase());
        for (String w : split(ClickerConfig.gets("chat", "mentionWords", ""))) words.add(w);

        int colon = lower.indexOf(':');
        for (String w : words) {
            int idx = lower.indexOf(w);
            while (idx >= 0) {
                boolean inSender = colon >= 0 && idx < colon;
                if (!inSender) return true;
                idx = lower.indexOf(w, idx + 1);
            }
        }
        return false;
    }

    private boolean matches(int t, Msg m) {
        switch (t) {
            case MENT: return m.mention;
            case PRIV: return m.priv;
            case FAC: return m.chan == FAC;
            case TRU: return m.chan == TRU;
            case ALY: return m.chan == ALY;
            default: return true;
        }
    }

    // ---------------------------------------------------------------- chat flow

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!active() || e.message == null || e.type == 2) return;   // 2 = action bar
        Msg m = classify(e.message);
        history.add(m);
        if (history.size() > 500) history.remove(0);

        for (int t = 1; t < TABS.length; t++) {
            if (t != tab && matches(t, m)) unread[t]++;
        }
        if (m.mention) Hud.pushMention(m.text);
        if (!matches(tab, m)) e.setCanceled(true);
    }

    private void switchTab(int t) {
        tab = t;
        unread[t] = 0;
        redisplay();
    }

    private void redisplay() {
        if (mc.ingameGUI == null) return;
        GuiNewChat gc = mc.ingameGUI.getChatGUI();
        List<String> sent = new ArrayList<String>(gc.getSentMessages());
        gc.clearChatMessages();
        for (String s : sent) gc.addToSentMessages(s);
        for (Msg m : history) {
            if (matches(tab, m)) gc.printChatMessage(m.comp);
        }
    }

    /** Back to the All tab (used when Chat Tabs is switched off). */
    public static void reset() {
        if (I != null && I.tab != ALL) {
            I.tab = ALL;
            I.redisplay();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        history.clear();
        tab = ALL;
        channel = null;
        for (int i = 0; i < unread.length; i++) unread[i] = 0;
    }

    // ---------------------------------------------------------------- tab bar

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiChat) || !active()) return;
        int acc = Theme.current().accent();
        float sc = ClickerConfig.chatScale / 100f;
        int baseX = 4, baseY = e.gui.height - 16;      // bottom-left corner of the bar
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) baseX, (float) baseY, 0f);
        GlStateManager.scale(sc, sc, 1f);
        int y = -13;
        int x = 0;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i];
            String badge = (i != ALL && unread[i] > 0) ? (unread[i] > 99 ? "99+" : String.valueOf(unread[i])) : null;
            int w = Ui.width(label) + 12 + (badge == null ? 0 : Ui.width(badge) + 10);
            boolean sel = i == tab;
            Ui.roundBorder(x, y, w, 13, sel ? Ui.alpha(acc, 0x50) : 0xCC0B0812,
                    sel ? acc : 0xFF332E45);
            Ui.text(label, x + 6, y + 3, 0xFFFFFFFF);
            if (badge != null) {
                int bx = x + 6 + Ui.width(label) + 4;
                Ui.round(bx, y + 2, Ui.width(badge) + 6, 9, acc);
                mc.fontRendererObj.drawString(badge, bx + 3, y + 3, 0xFF000000);
            }
            rects[i] = new int[]{Math.round(baseX + x * sc), Math.round(baseY + y * sc),
                    Math.round(w * sc), Math.round(13 * sc)};
            x += w + 3;
        }
        if (channel != null) {
            int col = channel.equals("FACTION") ? 0xFF55FF77 : channel.equals("TRUCE") ? 0xFFFFD24A
                    : channel.equals("ALLY") ? 0xFFFF8AD8 : 0xFFC8C2D8;
            int w = Ui.width(channel) + 10;
            Ui.roundBorder(x + 4, y, w, 13, Ui.alpha(col, 0x30), col);
            Ui.text(channel, x + 9, y + 3, col);
        }
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!(e.gui instanceof GuiChat) || !active()) return;
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        int mx = Mouse.getEventX() * e.gui.width / mc.displayWidth;
        int my = e.gui.height - Mouse.getEventY() * e.gui.height / mc.displayHeight - 1;
        for (int i = 0; i < TABS.length; i++) {
            int[] r = rects[i];
            if (r != null && Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                switchTab(i);
                e.setCanceled(true);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- channel tag

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre e) {
        if (!(e.gui instanceof GuiChat) || !active() || !Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey();
        if (k != Keyboard.KEY_RETURN && k != Keyboard.KEY_NUMPADENTER) return;
        String text = readInput((GuiChat) e.gui);
        if (text == null) return;
        detectChannel(text.trim().toLowerCase());
    }

    private String readInput(GuiChat g) {
        try {
            if (inputField == null && !fieldTried) {
                fieldTried = true;
                Field f = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
                f.setAccessible(true);
                inputField = f;
            }
            if (inputField == null) return null;
            return ((GuiTextField) inputField.get(g)).getText();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isCmd(String text, String csv, boolean exact) {
        for (String c : split(csv)) {
            if (text.equals(c) || (!exact && text.startsWith(c + " "))) return true;
        }
        return false;
    }

    private void detectChannel(String text) {
        if (!text.startsWith("/")) return;
        // The specific ones are checked first because "/f c f" also starts with "/f c".
        if (isCmd(text, ClickerConfig.gets("chat", "chanFaction", D_CMD_FAC))) channel = "FACTION";
        else if (isCmd(text, ClickerConfig.gets("chat", "chanTruce", D_CMD_TRU))) channel = "TRUCE";
        else if (isCmd(text, ClickerConfig.gets("chat", "chanAlly", D_CMD_ALY))) channel = "ALLY";
        else if (isCmd(text, ClickerConfig.gets("chat", "chanAll", D_CMD_ALL))) channel = "ALL";
    }
}
