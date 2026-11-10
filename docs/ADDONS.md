# Backspace addons (Java 8, Forge 1.12.2)

Backspace loads addon JARs from `minecraft/Backspace/addons/` during startup. Restart Minecraft after adding or removing an addon. An addon can register modules, its own category windows, Forge event listeners, and client commands. Modules use Backspace's normal keybind, settings, ClickGUI, tick/render, and configuration paths.

An addon JAR contains a class implementing `dev.goldforge.addon.BackspaceAddon` and a service file named `META-INF/services/dev.goldforge.addon.BackspaceAddon`. The service file contains the implementing class name, one per line.

```java
package example;

import dev.goldforge.addon.AddonContext;
import dev.goldforge.addon.BackspaceAddon;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.text.TextComponentString;

public final class ExampleAddon implements BackspaceAddon {
    @Override public String id() { return "example"; }
    @Override public String name() { return "Example addon"; }
    @Override public void onLoad(AddonContext context) {
        Category category = context.registerCategory("TOOLS"); // EXAMPLE_TOOLS
        context.registerModule(new PulseModule(category));
        // context.registerForgeListener(new YourForgeEventListener());
        // context.registerClientCommand(new YourClientCommand());
    }

    private static final class PulseModule extends Module {
        public int interval = 200;
        private int ticks;
        PulseModule(Category category) {
            super("ExamplePulse", "Sends a message periodically.", category, 0);
            addIntSetting("Interval", 20, 600, 20, () -> interval, value -> interval = value);
        }
        @Override public void tick() {
            if (mc.player != null && ++ticks >= interval) {
                ticks = 0;
                mc.player.sendMessage(new TextComponentString("Example addon is running"));
            }
        }
    }
}
```

The service file for this example contains:

```text
example.ExampleAddon
```

Compile against the Backspace JAR and Forge 14.23.5.2859 with Java 8. Package your classes and the service file in one JAR; do not bundle Backspace or Forge inside it. Put that JAR in `Backspace/addons`. Addon IDs use lowercase letters, digits, and underscores (up to 16 characters). Category names use uppercase letters, digits, and underscores; Backspace prefixes them with the addon ID to avoid clashes.

A buildable example is in [examples/hello-addon](../examples/hello-addon). Build Backspace first, then run `gradlew.bat -p examples/hello-addon build` from the repository root. The example JAR appears in `examples/hello-addon/build/libs/`.

Addon modules can subclass `Module` and use `addIntSetting` and `addDoubleSetting`. Public primitive and String fields are saved by Backspace's configuration system. Use `context.modules()` to look up built-in modules. Forge event listeners can extend rendering and GUI behavior, including adding their own screens; Backspace does not replace those screens automatically.

Each addon JAR runs with normal mod permissions. Install addons only from authors you trust. Loading errors are written to `logs/latest.log` under `Backspace/Addons`; a failed addon does not stop the other JARs from loading.
