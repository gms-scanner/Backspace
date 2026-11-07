package dev.goldforge.module;

import dev.goldforge.module.impl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityPig;
import java.util.List;

/** Gives one active movement controller ownership; read-only diagnostics remain enabled. */
public final class MovementConflictManager {
    private MovementConflictManager(){}
    public static void reconcile(List<Module> modules){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.world==null)return;
        Entity riding=mc.player.getRidingEntity();
        String[] priority;
        if(riding instanceof EntityBoat)
            priority=new String[]{"BoatFly","Flight","PacketFly","ElytraFly","Phase","Speed","Step"};
        else if(riding instanceof AbstractHorse||riding instanceof EntityPig)
            priority=new String[]{"EntityFly","Flight","PacketFly","ElytraFly","Phase","Speed","Step"};
        else if(mc.player.isElytraFlying())
            priority=new String[]{"Flight","ElytraFly","PacketFly","Phase","Speed","Step"};
        else priority=new String[]{"Flight","PacketFly","ElytraFly","Phase","Speed","Step"};
        String winner=null;
        for(String name:priority){
            Module module=find(modules,name);
            if(module==null||!module.enabled||module.isSuspended())continue;
            if(module instanceof Phase&&((Phase)module).mode==3)continue; // Trace-only mode.
            if(winner==null)winner=name;
            else module.setEnabled(false);
        }
        if("BoatFly".equals(winner)){
            Module boatPhase=find(modules,"BoatPhase");
            if(boatPhase!=null&&boatPhase.enabled)boatPhase.setEnabled(false);
        }
    }
    private static Module find(List<Module> modules,String name){
        for(Module module:modules)if(module.name.equals(name))return module;
        return null;
    }
}
