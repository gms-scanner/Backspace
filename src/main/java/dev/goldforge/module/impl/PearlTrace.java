package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Predicts other players' active pearl landings from observed projectile motion. */
public final class PearlTrace extends Module {
    public int range=96, maximumTicks=100, path=1, labels=1;
    private final Map<Integer,Prediction> predictions=new HashMap<Integer,Prediction>();
    private final Set<Integer> rejected=new HashSet<Integer>();
    public PearlTrace(){
        super("PearlTrace","Predicts where opponents' thrown pearls will land.",Category.RENDER,0);
        addIntSetting("Range",16,160,8,()->range,v->range=v);
        addIntSetting("Prediction ticks",20,160,10,()->maximumTicks,v->maximumTicks=v);
        addIntSetting("Trajectory (0/1)",0,1,1,()->path,v->path=v);
        addIntSetting("Landing label (0/1)",0,1,1,()->labels,v->labels=v);
    }
    @Override protected void onEnable(){predictions.clear();rejected.clear();}
    @Override protected void onDisable(){predictions.clear();rejected.clear();}
    @Override protected void onWorldUnload(){predictions.clear();rejected.clear();}
    @Override public void tick(){
        if(mc.player==null||mc.world==null){predictions.clear();return;}
        Set<Integer> active=new HashSet<Integer>();
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityEnderPearl))continue;
            EntityEnderPearl pearl=(EntityEnderPearl)entity;
            int id=pearl.getEntityId();active.add(id);
            if(rejected.contains(id)||mc.player.getDistanceSq(pearl)>range*(double)range)continue;
            EntityPlayer owner=owner(pearl);
            if(owner==mc.player){rejected.add(id);continue;}
            if(owner==null){
                // The 1.12 client does not always receive a projectile owner.
                // Only associate a newly observed pearl with a nearby thrower.
                if(pearl.ticksExisted>5){rejected.add(id);continue;}
                owner=inferThrower(pearl);
                if(owner==null)continue;
                if(owner==mc.player){rejected.add(id);continue;}
            }
            Prediction result=simulate(pearl,owner);
            if(result!=null)predictions.put(id,result);
        }
        predictions.keySet().retainAll(active);
        rejected.retainAll(active);
    }
    private EntityPlayer owner(EntityEnderPearl pearl){
        Entity thrower=pearl.getThrower();
        return thrower instanceof EntityPlayer?(EntityPlayer)thrower:null;
    }
    private EntityPlayer inferThrower(EntityEnderPearl pearl){
        EntityPlayer best=null;double bestScore=Double.MAX_VALUE;
        Vec3d motion=new Vec3d(pearl.motionX,pearl.motionY,pearl.motionZ).normalize();
        for(EntityPlayer player:mc.world.playerEntities){
            if(player.isDead||player.isSpectator())continue;
            Vec3d eyes=player.getPositionEyes(1F);
            Vec3d offset=new Vec3d(pearl.posX,pearl.posY,pearl.posZ).subtract(eyes);
            double distance=offset.lengthVector();
            if(distance>3.8D)continue;
            Vec3d look=player.getLookVec();
            double alignment=look.dotProduct(motion);
            if(alignment<.35D)continue;
            double score=distance+(1D-alignment)*2D;
            if(score<bestScore){best=player;bestScore=score;}
        }
        return best;
    }
    private Prediction simulate(EntityEnderPearl pearl,EntityPlayer owner){
        Vec3d position=new Vec3d(pearl.posX,pearl.posY,pearl.posZ);
        Vec3d velocity=new Vec3d(pearl.motionX,pearl.motionY,pearl.motionZ);
        if(velocity.lengthSquared()<.0001D)return null;
        List<Vec3d> points=new ArrayList<Vec3d>();points.add(position);
        int steps=0;boolean impact=false;
        for(;steps<maximumTicks;steps++){
            Vec3d next=position.add(velocity);
            BlockPos chunkProbe=new BlockPos(next);
            if(!mc.world.isBlockLoaded(chunkProbe))break;
            RayTraceResult block=mc.world.rayTraceBlocks(position,next,false,true,false);
            RayTraceResult entity=traceEntities(pearl,owner,position,next,steps);
            RayTraceResult hit=nearest(position,block,entity);
            if(hit!=null){position=hit.hitVec;points.add(position);impact=true;break;}
            position=next;points.add(position);
            float drag=mc.world.getBlockState(chunkProbe).getMaterial()==Material.WATER?.8F:.99F;
            velocity=new Vec3d(velocity.x*drag,velocity.y*drag-.03D,velocity.z*drag);
            if(position.y<0D)break;
        }
        return new Prediction(owner.getName(),points,position,steps+1,impact);
    }
    private RayTraceResult traceEntities(EntityEnderPearl pearl,EntityPlayer owner,Vec3d start,Vec3d end,int step){
        AxisAlignedBB swept=new AxisAlignedBB(start.x-.125D,start.y-.125D,start.z-.125D,
                start.x+.125D,start.y+.125D,start.z+.125D)
                .expand(end.x-start.x,end.y-start.y,end.z-start.z).grow(1D);
        RayTraceResult nearest=null;double distance=Double.MAX_VALUE;
        for(Entity candidate:mc.world.getEntitiesWithinAABBExcludingEntity(pearl,swept)){
            if(!candidate.canBeCollidedWith()||(candidate==owner&&step+pearl.ticksExisted<5))continue;
            RayTraceResult hit=candidate.getEntityBoundingBox().grow(.3D).calculateIntercept(start,end);
            if(hit==null)continue;
            double next=start.squareDistanceTo(hit.hitVec);
            if(next<distance){nearest=hit;distance=next;}
        }
        return nearest;
    }
    private RayTraceResult nearest(Vec3d start,RayTraceResult block,RayTraceResult entity){
        if(block==null)return entity;if(entity==null)return block;
        return start.squareDistanceTo(block.hitVec)<=start.squareDistanceTo(entity.hitVec)?block:entity;
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.getRenderManager()==null)return;
        CombatVisuals.begin();
        try{
            for(Prediction prediction:predictions.values()){
                if(path!=0)for(int i=1;i<prediction.points.size();i++){
                    Vec3d a=prediction.points.get(i-1),b=prediction.points.get(i);
                    CombatVisuals.line(a.x,a.y,a.z,b.x,b.y,b.z,1F,.79F,.13F,.65F);
                }
                Vec3d p=prediction.landing;
                AxisAlignedBB marker=new AxisAlignedBB(p.x-.32,p.y-.06,p.z-.32,p.x+.32,p.y+.06,p.z+.32);
                CombatVisuals.fill(marker,1F,.68F,.08F,.18F);
                CombatVisuals.outline(marker,1F,.86F,.2F,.95F);
                if(labels!=0)CombatVisuals.label(prediction.owner+" · "+(prediction.impact?"~":"≥")+prediction.ticks+"t",p.x,p.y+.48,p.z,0xFFFFD64A);
            }
        }finally{CombatVisuals.end();}
    }
    private static final class Prediction{
        final String owner;final List<Vec3d> points;final Vec3d landing;final int ticks;final boolean impact;
        Prediction(String owner,List<Vec3d> points,Vec3d landing,int ticks,boolean impact){this.owner=owner;this.points=points;this.landing=landing;this.ticks=ticks;this.impact=impact;}
    }
}
