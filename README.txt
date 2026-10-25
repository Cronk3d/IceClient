Ice Client (Forge 1.8.9) - one mod, one jar

Build:  gradlew setupDecompWorkspace  then  gradlew build
Jar:    build/libs/IceClient-0.1.0.jar  (drop into your mods folder)

Keys:   ` (backtick) = toggle whole mod, Right Shift = settings. Per-mod keybinds are set in the GUI.
Source: src/main/java/com/cronk/iceclient/

Updates
-------
Mods > Auto Updater checks an HTTPS link to a version.json file that you host:
  { "version": "0.2.0", "url": "https://.../IceClient-0.2.0.jar", "sha256": "...", "notes": "..." }
Make it with:  python tools/make_manifest.py build/libs/IceClient-0.2.0.jar 0.2.0 <jar link>
Paste the link to version.json into "Update URL". Updates only download when you press
"Install update", must match the sha256, and are applied when you close the game.
Remember to raise IceClient.VERSION in IceClient.java for each release.
