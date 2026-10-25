package dev.goldforge.module;

import dev.goldforge.ConfigManager;
import java.math.BigDecimal;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Numeric module option with a bounded, step-aligned slider value. */
public final class ModuleSetting {
    public final String name;
    private final double min, max, step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final boolean integral;

    public ModuleSetting(String name, int min, int max, int step, final IntSupplier getter, final IntConsumer setter) {
        this(name, min, max, step, () -> getter.getAsInt(), value -> setter.accept((int) Math.round(value)), true);
    }
    public ModuleSetting(String name, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) {
        this(name, min, max, step, getter, setter, false);
    }
    private ModuleSetting(String name, double min, double max, double step,
                          DoubleSupplier getter, DoubleConsumer setter, boolean integral) {
        if (max < min || step <= 0 || !Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step))
            throw new IllegalArgumentException("Invalid numeric setting range: " + name);
        this.name = name;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.integral = integral;
    }
    public int get() { return (int) Math.round(getter.getAsDouble()); }
    public double getDouble() { return getter.getAsDouble(); }
    public double fraction() { return max == min ? 0 : Math.max(0, Math.min(1, (getDouble() - min) / (max - min))); }
    public String formattedValue() {
        double value = getDouble();
        if (integral) return Integer.toString((int) Math.round(value));
        return BigDecimal.valueOf(value).setScale(Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale()),
                java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public void setFraction(double fraction) {
        double bounded = Math.max(0, Math.min(1, fraction));
        long increments = Math.round(((max - min) * bounded) / step);
        double value = BigDecimal.valueOf(min).add(BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(increments))).doubleValue();
        value = bounded >= 1 ? max : Math.max(min, Math.min(max, value));
        if (Math.abs(value - getDouble()) > 0.0000001) setter.accept(value);
    }
    public void adjust(int direction) {
        double value = Math.max(min, Math.min(max, getDouble() + step * direction));
        if (Math.abs(value - getDouble()) > 0.0000001) { setter.accept(value); ConfigManager.save(); }
    }
}

