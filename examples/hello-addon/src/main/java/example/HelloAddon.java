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
        Category category = context.registerCategory("TOOLS");
        context.registerModule(new HelloModule(category));
    }

    private static final class HelloModule extends Module {
        public int interval = 200;
        private int ticks;
        HelloModule(Category category) {
            super("HelloPulse", "Shows how an addon module works.", category, 0);
            addIntSetting("Interval", 20, 600, 20, () -> interval, value -> interval = value);
        }
        @Override public void tick() {
            if (mc.player != null && ++ticks >= interval) {
                ticks = 0;
                mc.player.sendMessage(new TextComponentString("[Hello addon] Module ticked"));
            }
        }
    }
}
