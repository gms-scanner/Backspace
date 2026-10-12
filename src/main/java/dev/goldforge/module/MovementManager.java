package dev.goldforge.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Resolves client-side horizontal movement intents once per client tick.
 * Modules submit desired velocity instead of overwriting one another's motion.
 */
public final class MovementManager {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static Request pending;
    private static long committedIntents;
    private static long tickWindowStarted;
    private static int ticksInWindow;
    private static double ticksPerSecond;
    private static long suspectedPositionJumps;
    private static double acceleration;
    private static float yawDelta;
    private static MovementSnapshot previousSnapshot;
    private static double desiredX,desiredZ;
    private MovementManager(){}

    public static void beginClientTick(){
        pending=null;
        long now=System.nanoTime();
        if(tickWindowStarted==0L)tickWindowStarted=now;
        ticksInWindow++;
        long elapsed=now-tickWindowStarted;
        if(elapsed>=1_000_000_000L){ticksPerSecond=ticksInWindow*1_000_000_000D/elapsed;ticksInWindow=0;tickWindowStarted=now;}
    }

    public static void requestHorizontalVelocity(Module owner,double x,double z,int priority){
        if(owner==null||!owner.enabled||Double.isNaN(x)||Double.isNaN(z)||Double.isInfinite(x)||Double.isInfinite(z))return;
        if(pending==null||priority>pending.priority)pending=new Request(owner,x,z,priority);
    }

    /** Applies at most one winning intent, after all module tick hooks have run. */
    public static void commitClientTick(){
        Request request=pending;pending=null;
        if(request!=null&&request.owner.enabled&&MC.player!=null&&MC.world!=null){
            EntityPlayerSP player=MC.player;
            player.motionX=request.x;player.motionZ=request.z;
            desiredX=request.x;desiredZ=request.z;committedIntents++;
        }
        MovementSnapshot current=MovementSnapshot.capture();
        if(current!=null&&previousSnapshot!=null){
            acceleration=current.accelerationFrom(previousSnapshot);yawDelta=current.yawDeltaFrom(previousSnapshot);
            double dx=current.x-previousSnapshot.x,dy=current.y-previousSnapshot.y,dz=current.z-previousSnapshot.z;
            if(dx*dx+dy*dy+dz*dz>9D)suspectedPositionJumps++;
        }
        previousSnapshot=current;
    }

    public static void clear(){pending=null;desiredX=0D;desiredZ=0D;previousSnapshot=null;}
    public static double getDesiredX(){return desiredX;}
    public static double getDesiredZ(){return desiredZ;}
    public static long getCommittedIntentCount(){return committedIntents;}
    public static double getAcceleration(){return acceleration;}
    public static float getYawDelta(){return yawDelta;}
    public static double getTicksPerSecond(){return ticksPerSecond;}
    public static long getSuspectedPositionJumps(){return suspectedPositionJumps;}
    public static MovementSnapshot snapshot(){return MovementSnapshot.capture();}

    private static final class Request{
        final Module owner;final double x,z;final int priority;
        Request(Module owner,double x,double z,int priority){this.owner=owner;this.x=x;this.z=z;this.priority=priority;}
    }
}
