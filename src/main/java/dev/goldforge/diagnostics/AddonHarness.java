package dev.goldforge.diagnostics;

import dev.goldforge.addon.BackspaceAddon;
import dev.goldforge.module.Category;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ServiceLoader;

/** Checks the packaged service entry and dynamic category registry. */
public final class AddonHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected addon JAR path");
        URL jar = Paths.get(args[0]).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar}, AddonHarness.class.getClassLoader())) {
            int count = 0;
            for (BackspaceAddon addon : ServiceLoader.load(BackspaceAddon.class, loader)) {
                if (!"hello".equals(addon.id())) throw new AssertionError("Unexpected addon id");
                count++;
            }
            if (count != 1) throw new AssertionError("Expected one addon service, got " + count);
        }
        int before = Category.values().length;
        Category category = Category.register("HELLO_TOOLS");
        if (Category.values().length != before + 1 || Category.valueOf("HELLO_TOOLS") != category
                || category.ordinal() != before) throw new AssertionError("Category registration failed");
        Category.unregisterLast(category);
        if (Category.values().length != before) throw new AssertionError("Category rollback failed");
        System.out.println("Addon harness: PASS");
    }
}
