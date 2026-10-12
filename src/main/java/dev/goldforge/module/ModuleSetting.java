package dev.goldforge.module;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Small GUI-editable integer option owned by a module. */
public final class ModuleSetting {
    public final String name;
    private final int min, max, step;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public ModuleSetting(String name, int min, int max, int step, IntSupplier getter, IntConsumer setter) {
        this.name=name; this.min=min; this.max=max; this.step=step; this.getter=getter; this.setter=setter;
    }
    public int get() { return getter.getAsInt(); }
    public void adjust(int direction) { setter.accept(Math.max(min,Math.min(max,get()+step*direction))); dev.goldforge.ConfigManager.save(); }
}
