package com.cronk.iceclient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** One mod in the "Mods" list: a switch, a keybind and a list of settings. */
public class ModDef {
    public final String id, name, category, desc;
    public final Supplier<Boolean> get;
    public final Consumer<Boolean> set;
    public final List<Setting> settings = new ArrayList<Setting>();
    public Runnable reset = () -> { };
    public int key;   // LWJGL key code, 0 = none

    public Runnable keyAction;   // if set, the keybind runs this instead of toggling the mod

    public ModDef(String id, String name, String category, String desc,
                  Supplier<Boolean> get, Consumer<Boolean> set) {
        this(id, name, category, desc, get, set, 0);
    }

    public ModDef(String id, String name, String category, String desc,
                  Supplier<Boolean> get, Consumer<Boolean> set, int defKey) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.desc = desc;
        this.get = get;
        this.set = set;
        this.key = ClickerConfig.geti("keys", id, defKey);
    }

    public void setKey(int k) {
        key = k;
        ClickerConfig.seti("keys", id, k);
    }

    public abstract static class Setting {
        public final String label, desc;

        Setting(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }
    }

    public static class Toggle extends Setting {
        public final Supplier<Boolean> get;
        public final Consumer<Boolean> set;

        public Toggle(String l, String d, Supplier<Boolean> g, Consumer<Boolean> s) {
            super(l, d); get = g; set = s;
        }
    }

    public static class Slider extends Setting {
        public final int min, max;
        public final String unit;
        public final IntSupplier get;
        public final IntConsumer set;
        public Runnable onRelease;   // run when the slider is let go (e.g. to save)

        public Slider(String l, String d, int min, int max, String unit, IntSupplier g, IntConsumer s) {
            super(l, d); this.min = min; this.max = max; this.unit = unit; get = g; set = s;
        }
    }

    public static class Text extends Setting {
        public final Supplier<String> get;
        public final Consumer<String> set;

        public Text(String l, String d, Supplier<String> g, Consumer<String> s) {
            super(l, d); get = g; set = s;
        }
    }

    public static class Action extends Setting {
        public final String button;
        public final Runnable run;

        public Action(String l, String d, String button, Runnable run) {
            super(l, d); this.button = button; this.run = run;
        }
    }

    /** An extra keybind on a mod's page that runs an action (e.g. minimap zoom). */
    public static class KeySetting extends Setting {
        public final String id;
        public final Runnable action;
        public int key;

        public KeySetting(String l, String d, String id, int defKey, Runnable action) {
            super(l, d);
            this.id = id;
            this.action = action;
            this.key = ClickerConfig.geti("keys", id, defKey);
        }

        public void setKey(int k) {
            key = k;
            ClickerConfig.seti("keys", id, k);
        }
    }

    /** Read-only line of text on a mod's page (e.g. the updater status). */
    public static class Info extends Setting {
        public final Supplier<String> value;

        public Info(String l, String d, Supplier<String> value) {
            super(l, d);
            this.value = value;
        }
    }

    public static class KeyRow extends Setting {
        public KeyRow() { super("Keybind", "Key that turns this mod on and off"); }
    }
}
