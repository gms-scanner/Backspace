package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;

public final class BoatPhase extends Module {
    private EntityBoat tracked;
    public BoatPhase(){super("BoatPhase","Disables local boat collision while riding.",Category.MOVEMENT,0);}
    @Override public void tick(){
        Entity riding=mc.player==null?null:mc.player.getRidingEntity();
        if(tracked!=riding){restore();if(riding instanceof EntityBoat)tracked=(EntityBoat)riding;}
        if(tracked!=null)tracked.noClip=true;
    }
    @Override protected void onDisable(){restore();}
    @Override protected void onWorldUnload(){restore();}
    private void restore(){if(tracked!=null){tracked.noClip=false;tracked=null;}}
}
