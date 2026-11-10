package dev.goldforge.addon;

import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraft.command.ICommand;

import java.util.ArrayList;
import java.util.List;

/** Registration surface available while an addon loads on the client thread. */
public final class AddonContext {
    private final String addonId;
    private final List<Object> eventListeners = new ArrayList<Object>();
    private final List<Module> modules = new ArrayList<Module>();
    private final List<Category> categories = new ArrayList<Category>();
    AddonContext(String addonId) { this.addonId = addonId; }
    public String addonId() { return addonId; }
    public Minecraft minecraft() { return Minecraft.getMinecraft(); }
    public ModuleManager modules() { return GoldForge.MODULES; }
    public Category registerCategory(String name) {
        Category category = Category.register(addonId.toUpperCase(java.util.Locale.ROOT) + "_" + name);
        categories.add(category);
        return category;
    }
    public void registerModule(Module module) {
        GoldForge.MODULES.register(module);
        modules.add(module);
    }
    public void registerClientCommand(ICommand command) {
        if (command == null) throw new IllegalArgumentException("command");
        ClientCommandHandler.instance.registerCommand(command);
    }
    /** Allows addons to extend UI, input, and gameplay through normal Forge events. */
    public void registerForgeListener(Object listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        MinecraftForge.EVENT_BUS.register(listener);
        eventListeners.add(listener);
    }
    void unload() {
        for (Object listener : eventListeners) MinecraftForge.EVENT_BUS.unregister(listener);
        eventListeners.clear();
    }
    void rollback() {
        unload();
        for (int i = modules.size() - 1; i >= 0; i--) GoldForge.MODULES.unregister(modules.get(i));
        for (int i = categories.size() - 1; i >= 0; i--) Category.unregisterLast(categories.get(i));
    }
}
