# Build a Backspace addon

This tutorial builds a Java 8 addon for Backspace dev-4.3.0-rc1 on Minecraft Forge 1.12.2. It adds a **HelloPulse** module in a new **HELLO_TOOLS** ClickGUI category. The complete project is in [`examples/hello-addon`](../examples/hello-addon).

## 1. Set up the project

Install a Java 8 JDK. Clone Backspace and build its JAR from the repository root:

```powershell
.\gradlew.bat clean build
```

For a separate addon project, copy `examples/hello-addon` outside this repository. In its `build.gradle`, point `compileOnly files(...)` at the built Backspace JAR. Keep Forge `1.12.2-14.23.5.2859` and Java source/target compatibility `1.8`. Backspace and Forge are supplied at runtime; do not package either inside the addon JAR.

## 2. Implement the addon entry point

Create `src/main/java/example/HelloAddon.java`:

```java
package example;

import dev.goldforge.addon.AddonContext;
import dev.goldforge.addon.BackspaceAddon;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.text.TextComponentString;

public final class HelloAddon implements BackspaceAddon {
    @Override public String id() { return "hello"; }
    @Override public String name() { return "Hello addon"; }

    @Override public void onLoad(AddonContext context) {
        Category tools = context.registerCategory("TOOLS");
        context.registerModule(new HelloModule(tools));
    }

    private static final class HelloModule extends Module {
        public int interval = 200;
        private int ticks;

        HelloModule(Category category) {
            super("HelloPulse", "Shows how an addon module works.", category, 0);
            addIntSetting("Interval", 20, 600, 20,
                    () -> interval, value -> interval = value);
        }

        @Override public void tick() {
            if (mc.player != null && ++ticks >= interval) {
                ticks = 0;
                mc.player.sendMessage(new TextComponentString("[Hello addon] Module ticked"));
            }
        }
    }
}
```

`id()` must be unique and contain 1–16 lowercase letters, digits, or underscores, starting with a letter. Backspace prefixes the category name with this ID, so `TOOLS` appears internally as `HELLO_TOOLS`. Register categories and modules inside `onLoad`; registration is closed after startup. A module can also use a built-in `Category` constant such as `Category.MISC`.

`Module.tick()` runs while the module is enabled. Other hooks include `render(float)`, `onInputUpdate(...)`, and overlay hooks. Use `onEnable()` and `onDisable()` to acquire and release resources. The `interval` field is public so Backspace can save it; the numeric setting gives it a ClickGUI slider. Use `addDoubleSetting` for decimal values. Keep module names unique across Backspace and installed addons.

## 3. Declare the service

Create this file with **no extension**:

```text
src/main/resources/META-INF/services/dev.goldforge.addon.BackspaceAddon
```

Its contents are the fully qualified entry-point class name:

```text
example.HelloAddon
```

This service file is required. Without it, Backspace will see the JAR but will not discover the addon.

## 4. Build and install

From the Backspace repository root, build the supplied example:

```powershell
.\gradlew.bat -p examples/hello-addon clean build
```

Copy `examples/hello-addon/build/libs/backspace-hello-addon-1.0.0.jar` into the Minecraft instance's `Backspace/addons/` folder, **not** `mods/`. For Prism Launcher, that is inside the selected instance's `minecraft/Backspace/addons/`. Start or restart Minecraft, open ClickGUI, and enable **HelloPulse** under **HELLO_TOOLS**. With a world open, it writes a chat message every 200 ticks by default. Adjust **Interval** to test that settings save and reload.

## 5. Extend Backspace

`AddonContext` also provides:

- `modules()` to inspect registered modules, such as `context.modules().get("TreeBurner")`.
- `registerForgeListener(listener)` for Forge events. Keep world access on the Minecraft client thread.
- `registerClientCommand(command)` for a client command.
- `minecraft()` for the current Minecraft client instance.

Forge listeners can implement custom rendering and screens. The addon API does not automatically replace Backspace's built-in GUI; your own UI code must draw and handle input for new screens. Addon JARs run with normal mod permissions, so use code from authors you trust.

## Troubleshooting

- **Category or module missing:** Check the service path and entry-point name inside the JAR, then restart Minecraft.
- **Compilation fails:** Use a Java 8 JDK, the matching Forge version, and the dev-4.3.0-rc1 Backspace JAR as a compile-only dependency.
- **Addon fails during startup:** Read `logs/latest.log` for `Backspace/Addons`. Duplicate addon IDs, categories, or module names are rejected. One failed JAR does not prevent other JARs from loading.
- **Setting does not persist:** Make the setting's backing field public and use the setting getter and setter to read and update that same field.

For the API contract and another code sample, see the [addon reference](ADDONS.md).
