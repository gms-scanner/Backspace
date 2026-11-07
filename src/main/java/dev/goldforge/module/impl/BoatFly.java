package dev.goldforge.module.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;

public final class BoatFly extends VehicleFlight {
    public BoatFly(){super("BoatFly","Experimental boat motion and packet flight with correction detection.");}
    @Override protected boolean accepts(Entity entity){return entity instanceof EntityBoat;}
}
