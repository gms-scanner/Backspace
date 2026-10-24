package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

/** Delays keepalive replies; the server still determines the displayed latency. */
public final class PingSpoof extends Module {
    public int delayMs=500;
    public PingSpoof(){
        super("PingSpoof","Delays outgoing keepalive replies.",Category.EXPLOIT,0);
        addIntSetting("Delay (ms)",0,5000,100,()->delayMs,v->delayMs=v);
    }
}
