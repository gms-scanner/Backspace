package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.util.EnumHand;

/** Experimental local vehicle detach with passenger-packet observation and explicit recovery. */
public final class EntityDesync extends Module {
    public int detach=1,recoverOnDisable=1,maximumDistance=32;
    private Entity tracked;
    private boolean serverPassengerKnown,serverPassenger;
    private String state="Idle";
    public EntityDesync(){
        super("EntityDesync","Locally detaches from a vehicle and tracks server passenger updates.",Category.MOVEMENT,0);
        addIntSetting("Detach locally (0/1)",0,1,1,()->detach,v->detach=v);
        addIntSetting("Recover on disable (0/1)",0,1,1,()->recoverOnDisable,v->recoverOnDisable=v);
        addIntSetting("Maximum vehicle distance",4,64,1,()->maximumDistance,v->maximumDistance=v);
    }
    @Override protected void onEnable(){
        if(mc.player==null||mc.world==null){clear();return;}
        tracked=mc.player.getRidingEntity();
        serverPassengerKnown=false;
        if(tracked==null){state="Mount a vehicle first";return;}
        for(String name:new String[]{"BoatFly","EntityFly"}){
            dev.goldforge.module.Module controller=dev.goldforge.GoldForge.MODULES.get(name);
            if(controller!=null&&controller.enabled)controller.setEnabled(false);
        }
        state="Server passenger unknown";
        if(detach!=0){mc.player.dismountRidingEntity();state="Locally detached; awaiting server packet";}
    }
    @Override public void tick(){
        if(tracked==null||mc.player==null||mc.world==null||tracked.world!=mc.world||tracked.isDead){
            if(tracked!=null)clear();return;
        }
        if(mc.player.getDistanceSq(tracked)>maximumDistance*maximumDistance){
            state="Vehicle out of range";return;
        }
        if(serverPassengerKnown)state=serverPassenger?"Server passenger observed":"Server dismount observed";
    }
    public void onPassengerPacket(SPacketSetPassengers packet){
        if(!enabled||tracked==null||mc.player==null||packet.getEntityId()!=tracked.getEntityId())return;
        serverPassengerKnown=true;serverPassenger=false;
        for(int id:packet.getPassengerIds())if(id==mc.player.getEntityId()){serverPassenger=true;break;}
    }
    @Override protected void onDisable(){
        if(recoverOnDisable!=0&&tracked!=null&&mc.player!=null&&mc.world==tracked.world
                &&!tracked.isDead&&mc.player.getRidingEntity()==null
                &&mc.player.getDistanceSq(tracked)<=maximumDistance*maximumDistance
                &&mc.playerController!=null)
            mc.playerController.interactWithEntity(mc.player,tracked,EnumHand.MAIN_HAND);
        clear();
    }
    @Override protected void onWorldUnload(){clear();}
    private void clear(){tracked=null;serverPassengerKnown=serverPassenger=false;state="Idle";}
    @Override public String hudTag(){return "["+state+"]";}
}
