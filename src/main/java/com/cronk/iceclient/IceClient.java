package com.cronk.iceclient;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = IceClient.MODID, name = "Ice Client", version = IceClient.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class IceClient {
    public static final String MODID = "iceclient";
    public static final String VERSION = "0.1.0";

    public static final long START = System.currentTimeMillis();

    public static KeyBinding toggleMod;
    public static KeyBinding openGui;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ClickerConfig.load(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleMod = new KeyBinding("Toggle Ice Client", Keyboard.KEY_GRAVE, "Ice Client");
        openGui = new KeyBinding("Open settings", Keyboard.KEY_RSHIFT, "Ice Client");
        ClientRegistry.registerKeyBinding(toggleMod);
        ClientRegistry.registerKeyBinding(openGui);
        Hud.init();
        Mods.init();
        MinecraftForge.EVENT_BUS.register(new AutoClicker());
        MinecraftForge.EVENT_BUS.register(new Hud());
        MinecraftForge.EVENT_BUS.register(new Mods());
        MinecraftForge.EVENT_BUS.register(new ChatTabs());
        MinecraftForge.EVENT_BUS.register(new MapManager());
        MinecraftForge.EVENT_BUS.register(new Updater());
        Updater.init();
    }
}
