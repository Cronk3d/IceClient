package com.cronk.iceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

public class AutoClicker {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random rnd = new Random();
    private long nextLeft = 0;
    private long nextRight = 0;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (IceClient.toggleMod.isPressed()) {
            ClickerConfig.enabled = !ClickerConfig.enabled;
            ClickerConfig.save();
            status("Ice Client " + (ClickerConfig.enabled ? "ON" : "OFF"));
        }
        if (IceClient.openGui.isPressed()) {
            mc.displayGuiScreen(new SettingsGui());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        // Pauses whenever any GUI (inventory, chest, chat, menus) is open.
        if (!ClickerConfig.enabled || !ClickerConfig.autoOn || mc.thePlayer == null
                || mc.theWorld == null || mc.currentScreen != null || !allowedHere()) {
            nextLeft = 0;
            nextRight = 0;
            return;
        }

        long now = System.currentTimeMillis();

        // Left: clicks while the attack button is held. Pauses while mining.
        if (ClickerConfig.leftOn && mc.gameSettings.keyBindAttack.isKeyDown() && !lookingAtBlock()) {
            if (nextLeft == 0) {
                nextLeft = now + interval(ClickerConfig.leftMin, ClickerConfig.leftMax);
            } else if (now >= nextLeft) {
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                nextLeft = now + interval(ClickerConfig.leftMin, ClickerConfig.leftMax);
            }
        } else {
            nextLeft = 0;
        }

        // Right: clicks while the use button is held. Paper has its own range;
        // potions, food, pearls and milk are never clicked.
        int[] r = (ClickerConfig.rightOn && mc.gameSettings.keyBindUseItem.isKeyDown()) ? rightRange() : null;
        if (r != null) {
            if (nextRight == 0) {
                nextRight = now + Math.max(ClickerConfig.rightDelay, interval(r[0], r[1]));
            } else if (now >= nextRight) {
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                nextRight = now + interval(r[0], r[1]);
            }
        } else {
            nextRight = 0;
        }
    }

    /** Random delay between clicks, from a CPS somewhere between min and max. */
    private long interval(int min, int max) {
        if (min > max) { int t = min; min = max; max = t; }
        double cps = min + rnd.nextDouble() * (max - min);
        return Math.max(1L, (long) (1000.0 / Math.max(1.0, cps)));
    }

    private boolean allowedHere() {
        String a = ClickerConfig.allowedOn == null ? "" : ClickerConfig.allowedOn.trim();
        if (a.isEmpty()) return true;
        ServerData sd = mc.getCurrentServerData();
        if (sd == null || sd.serverIP == null) return false;
        String ip = sd.serverIP.toLowerCase();
        for (String part : a.split(",")) {
            part = part.trim().toLowerCase();
            if (!part.isEmpty() && ip.contains(part)) return true;
        }
        return false;
    }

    private boolean lookingAtBlock() {
        MovingObjectPosition mop = mc.objectMouseOver;
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private int[] rightRange() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) return null;
        Item item = stack.getItem();
        if (item == Items.paper) return new int[]{ClickerConfig.paperMin, ClickerConfig.paperMax};
        if (item instanceof ItemPotion || item instanceof ItemFood
                || item instanceof ItemEnderPearl || item == Items.milk_bucket) return null;
        if (mc.thePlayer.isUsingItem()) return null;
        return new int[]{ClickerConfig.rightMin, ClickerConfig.rightMax};
    }

    private void status(String msg) {
        mc.ingameGUI.setRecordPlaying(msg, false);
    }
}
