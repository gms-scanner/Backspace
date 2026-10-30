package dev.goldforge.module.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityPig;

public final class EntityFly extends VehicleFlight {
    public EntityFly(){super("EntityFly","Experimental horse, donkey, mule, llama, and pig flight.");}
    @Override protected boolean accepts(Entity entity){
        return entity instanceof AbstractHorse||entity instanceof EntityPig;
    }
}
