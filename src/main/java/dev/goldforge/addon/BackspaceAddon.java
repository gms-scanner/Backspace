package dev.goldforge.addon;

/** Entry point listed in META-INF/services/dev.goldforge.addon.BackspaceAddon. */
public interface BackspaceAddon {
    String id();
    String name();
    void onLoad(AddonContext context);
    default void onUnload() { }
}
