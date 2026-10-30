package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.network.play.server.SPacketMoveVehicle;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

/** Shared, bounded vehicle movement controller. Packet modes remain server dependent. */
abstract class VehicleFlight extends Module {
    /** 0 Vanilla, 1 Motion, 2 Packet, 3 Glide, 4 Stepped packet. */
    public int mode=1,horizontalSpeed=80,verticalSpeed=40,acceleration=8;
    public int yLock=0,autoMount=0,rotate=1,packetInterval=1,antiKickInterval=0,recoverDismount=1;
    private Entity tracked;
    private double lockedY;
    private Vec3d requested,corrected;
    private int tick,corrections,packetSends,backoff,mountCooldown,requestTick;
    private String state="Idle";

    VehicleFlight(String name,String description){
        super(name,description,Category.MOVEMENT,0);
        addIntSetting("Mode (0 Vanilla, 1 Motion, 2 Packet, 3 Glide, 4 Step)",0,4,1,()->mode,v->mode=v);
        addDoubleSetting("Horizontal speed",.1,3,.05,()->horizontalSpeed/100D,v->horizontalSpeed=(int)Math.round(v*100));
        addDoubleSetting("Vertical speed",.05,2,.05,()->verticalSpeed/100D,v->verticalSpeed=(int)Math.round(v*100));
        addDoubleSetting("Acceleration",.01,.5,.01,()->acceleration/100D,v->acceleration=(int)Math.round(v*100));
        addIntSetting("Lock Y (0/1)",0,1,1,()->yLock,v->yLock=v);
        addIntSetting("Auto mount (0/1)",0,1,1,()->autoMount,v->autoMount=v);
        addIntSetting("Sync rotation (0/1)",0,1,1,()->rotate,v->rotate=v);
        addIntSetting("Packet interval (ticks)",1,10,1,()->packetInterval,v->packetInterval=v);
        addIntSetting("Anti-kick interval (0 off)",0,80,1,()->antiKickInterval,v->antiKickInterval=v);
        addIntSetting("Recover dismount (0/1)",0,1,1,()->recoverDismount,v->recoverDismount=v);
    }

    protected abstract boolean accepts(Entity entity);

    @Override public final void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.getConnection()==null){
            reset();return;
        }
        tick++;
        if(backoff>0)backoff--;
        if(mountCooldown>0)mountCooldown--;
        Entity riding=mc.player.getRidingEntity();
        if(riding==null||!accepts(riding)||riding.isDead){
            if(tracked!=null&&riding!=tracked)state="Dismounted";
            if(autoMount!=0||recoverDismount!=0&&tracked!=null)tryMount();
            if(riding!=tracked)tracked=null;
            return;
        }
        if(tracked!=riding){
            tracked=riding;lockedY=riding.posY;requested=corrected=null;
            state="Mounted";
        }
        if(mc.currentScreen!=null||mc.player.movementInput==null)return;
        double forward=mc.player.movementInput.moveForward;
        double strafe=mc.player.movementInput.moveStrafe;
        double magnitude=Math.sqrt(forward*forward+strafe*strafe);
        if(magnitude>1D){forward/=magnitude;strafe/=magnitude;}
        double yaw=Math.toRadians(mc.player.rotationYaw);
        double speed=Math.max(.1D,Math.min(3D,horizontalSpeed/100D));
        double targetX=(strafe*Math.cos(yaw)-forward*Math.sin(yaw))*speed;
        double targetZ=(forward*Math.cos(yaw)+strafe*Math.sin(yaw))*speed;
        double step=Math.max(.01D,Math.min(.5D,acceleration/100D));
        riding.motionX=approach(riding.motionX,targetX,step);
        riding.motionZ=approach(riding.motionZ,targetZ,step);
        double vertical=Math.max(.05D,Math.min(2D,verticalSpeed/100D));
        if(mc.player.movementInput.jump)riding.motionY=approach(riding.motionY,vertical,step);
        else if(mc.player.movementInput.sneak)riding.motionY=approach(riding.motionY,-vertical,step);
        else if(yLock!=0)riding.motionY=approach(riding.motionY,
                Math.max(-vertical,Math.min(vertical,(lockedY-riding.posY)*.5D)),step);
        else if(mode==3)riding.motionY=Math.max(riding.motionY,-.08D);
        else if(mode!=0)riding.motionY=approach(riding.motionY,0D,step);
        if(rotate!=0)riding.rotationYaw=mc.player.rotationYaw;
        if(mode==0||mode==1||mode==3){state="Motion (unconfirmed)";return;}
        if(backoff>0){state="Correction cooldown";return;}
        if(tick%Math.max(1,packetInterval)!=0)return;
        double x=riding.posX+riding.motionX,y=riding.posY+riding.motionY,z=riding.posZ+riding.motionZ;
        if(antiKickInterval>0&&tick%antiKickInterval==0)y-=.04D;
        if(mode==4){
            riding.setPosition(riding.posX+riding.motionX*.5D,riding.posY+riding.motionY*.5D,
                    riding.posZ+riding.motionZ*.5D);
            if(PacketManager.send(new CPacketVehicleMove(riding),name))packetSends++;
        }
        riding.setPosition(x,y,z);
        requested=new Vec3d(x,y,z);requestTick=tick;
        if(PacketManager.send(new CPacketVehicleMove(riding),name)){packetSends++;state="Requested (unconfirmed)";}
    }

    private void tryMount(){
        if(mountCooldown>0||mc.currentScreen!=null)return;
        Entity candidate=tracked;
        if(candidate==null||candidate.isDead||!candidate.getPassengers().isEmpty()||candidate.world!=mc.world
                ||mc.player.getDistanceSq(candidate)>9D){
            candidate=null;double best=9D;
            for(Entity entity:mc.world.loadedEntityList){
                if(!accepts(entity)||entity.isDead||!entity.getPassengers().isEmpty())continue;
                double distance=mc.player.getDistanceSq(entity);
                if(distance<best){best=distance;candidate=entity;}
            }
        }
        if(candidate!=null){
            mc.playerController.interactWithEntity(mc.player,candidate,EnumHand.MAIN_HAND);
            mountCooldown=20;state="Mount requested";
        }
    }

    /** Called on the client thread before vanilla applies the server vehicle correction. */
    public final void onServerCorrection(SPacketMoveVehicle packet){
        if(!enabled||mc.player==null||tracked==null||mc.player.getRidingEntity()!=tracked)return;
        corrected=new Vec3d(packet.getX(),packet.getY(),packet.getZ());
        if(requested!=null&&tick-requestTick<=10&&requested.squareDistanceTo(corrected)>.09D){
            corrections++;backoff=20;lockedY=corrected.y;state="Server corrected";
            tracked.motionX=tracked.motionY=tracked.motionZ=0D;
        }
    }

    @Override public String hudTag(){return "["+state+" C:"+corrections+" P:"+packetSends+"]";}
    private static double approach(double current,double target,double limit){
        return current+Math.max(-limit,Math.min(limit,target-current));
    }
    private void reset(){tracked=null;requested=corrected=null;backoff=mountCooldown=0;state="Idle";}
    @Override protected void onDisable(){reset();}
    @Override protected void onWorldUnload(){reset();}
}
