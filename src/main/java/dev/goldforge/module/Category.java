package dev.goldforge.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Startup registry for built-in and addon ClickGUI categories. */
public final class Category {
    private static final Map<String, Category> REGISTRY = new LinkedHashMap<String, Category>();
    private static boolean frozen;
    public static final Category COMBAT = builtin("COMBAT");
    public static final Category MOVEMENT = builtin("MOVEMENT");
    public static final Category PLAYER = builtin("PLAYER");
    public static final Category RENDER = builtin("RENDER");
    public static final Category WORLD = builtin("WORLD");
    public static final Category EXPLOIT = builtin("EXPLOIT");
    public static final Category MISC = builtin("MISC");
    public static final Category CLIENT = builtin("CLIENT");
    public static final Category HUD = builtin("HUD");

    private final String name;
    private final int ordinal;
    private Category(String name, int ordinal) { this.name = name; this.ordinal = ordinal; }
    private static Category builtin(String name) { return register(name); }

    public static synchronized Category register(String requestedName) {
        if (frozen) throw new IllegalStateException("Categories can only be registered during addon loading");
        if (requestedName == null) throw new IllegalArgumentException("Category name is required");
        String name = requestedName.trim().toUpperCase(Locale.ROOT);
        if (!name.matches("[A-Z][A-Z0-9_]{0,31}"))
            throw new IllegalArgumentException("Invalid category name: " + requestedName);
        if (REGISTRY.containsKey(name)) throw new IllegalArgumentException("Duplicate category: " + name);
        Category category = new Category(name, REGISTRY.size());
        REGISTRY.put(name, category);
        return category;
    }
    public static synchronized Category valueOf(String name) {
        Category category = REGISTRY.get(name);
        if (category == null) throw new IllegalArgumentException("Unknown category: " + name);
        return category;
    }
    public static synchronized Category[] values() {
        return new ArrayList<Category>(REGISTRY.values()).toArray(new Category[0]);
    }
    public static synchronized void freeze() { frozen = true; }
    public static synchronized void unregisterLast(Category category) {
        if(frozen || category == null || category.ordinal != REGISTRY.size() - 1)
            throw new IllegalStateException("Can only roll back the last addon category during startup");
        REGISTRY.remove(category.name);
    }
    public String name() { return name; }
    public int ordinal() { return ordinal; }
    @Override public String toString() { return name; }
}
