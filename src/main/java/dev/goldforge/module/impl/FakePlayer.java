package dev.goldforge.module.impl;

import com.mojang.authlib.GameProfile;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.MoverType;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

/** Local combat dummy for aim/crystal practice. It is never sent to a multiplayer server. */
public final class FakePlayer extends Module {
    public int totems=10, armor=0, speed=5, jumpFrequency=5;
    private EntityOtherPlayerMP dummy;
    private int entityId=-910001, waypointTicks, jumpDelay=24, spawnX, spawnZ;
    private double goalX,goalZ;

    public FakePlayer(){
        super("FakePlayer","Spawns a local training dummy with configurable totems and armor.",Category.PLAYER,0);
        addIntSetting("Totems",0,36,1,()->totems,v->totems=v);
        addIntSetting("Armor (0 Diamond, 1 Iron, 2 Chain, 3 Gold, 4 Leather)",0,4,1,()->armor,v->armor=v);
        addIntSetting("Movement speed",1,10,1,()->speed,v->speed=v);
        addIntSetting("Jump frequency",0,10,1,()->jumpFrequency,v->jumpFrequency=v);
    }

    @Override protected void onEnable(){spawnDummy();}

    private void spawnDummy(){
        if(mc.world==null||mc.player==null)return;
        removeDummy();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"BackspaceDummy");
        dummy=new EntityOtherPlayerMP(mc.world,profile);
        dummy.copyLocationAndAnglesFrom(mc.player);
        dummy.setPosition(mc.player.posX+2D,mc.player.posY,mc.player.posZ+2D);
        dummy.rotationYaw=mc.player.rotationYaw;dummy.rotationYawHead=dummy.rotationYaw;dummy.renderYawOffset=dummy.rotationYaw;
        dummy.setHealth(20F);
        for(int i=0;i<Math.min(36,totems);i++)dummy.inventory.mainInventory.set(i,new ItemStack(Items.TOTEM_OF_UNDYING));
        equipArmor();
        mc.world.addEntityToWorld(entityId,dummy);
        spawnX=MathHelper.floor(dummy.posX);spawnZ=MathHelper.floor(dummy.posZ);
        chooseWaypoint();
    }

    private void equipArmor(){
        Item[] pieces;
        switch(armor){
            case 1: pieces=new Item[]{Items.IRON_BOOTS,Items.IRON_LEGGINGS,Items.IRON_CHESTPLATE,Items.IRON_HELMET};break;
            case 2: pieces=new Item[]{Items.CHAINMAIL_BOOTS,Items.CHAINMAIL_LEGGINGS,Items.CHAINMAIL_CHESTPLATE,Items.CHAINMAIL_HELMET};break;
            case 3: pieces=new Item[]{Items.GOLDEN_BOOTS,Items.GOLDEN_LEGGINGS,Items.GOLDEN_CHESTPLATE,Items.GOLDEN_HELMET};break;
            case 4: pieces=new Item[]{Items.LEATHER_BOOTS,Items.LEATHER_LEGGINGS,Items.LEATHER_CHESTPLATE,Items.LEATHER_HELMET};break;
            default: pieces=new Item[]{Items.DIAMOND_BOOTS,Items.DIAMOND_LEGGINGS,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_HELMET};break;
        }
        for(int i=0;i<4;i++)dummy.inventory.armorInventory.set(i,new ItemStack(pieces[i]));
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(dummy==null||dummy.world!=mc.world){spawnDummy();return;}
        if(dummy.isDead){removeDummy();spawnDummy();return;}
        if(--waypointTicks<=0||dummy.getDistance(goalX,dummy.posY,goalZ)<1.1D)chooseWaypoint();
        double dx=goalX-dummy.posX,dz=goalZ-dummy.posZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        if(distance>.2D){
            double moveSpeed=.035D+speed*.014D;
            dummy.motionX=dx/distance*moveSpeed;
            dummy.motionZ=dz/distance*moveSpeed;
            float desiredYaw=(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F;
            dummy.rotationYaw+=MathHelper.clamp(MathHelper.wrapDegrees(desiredYaw-dummy.rotationYaw),-22F,22F);
            dummy.rotationYawHead=dummy.rotationYaw;dummy.renderYawOffset=dummy.rotationYaw;
        }else{dummy.motionX*=.7D;dummy.motionZ*=.7D;}
        if(jumpFrequency>0&&--jumpDelay<=0){
            if(dummy.onGround)dummy.jump();
            jumpDelay=Math.max(8,100-jumpFrequency*8)+dummy.getRNG().nextInt(15);
        }
        if(!dummy.onGround)dummy.motionY=Math.max(dummy.motionY-.08D,-.45D);
        dummy.move(MoverType.SELF,dummy.motionX,dummy.motionY,dummy.motionZ);
        if(dummy.collidedHorizontally&&dummy.onGround&&jumpFrequency>0)dummy.jump();
        if(dummy.onGround)dummy.motionY=0D;
        // Keep a visible, living health bar unless it has actually been hit during local practice.
        if(dummy.getHealth()<=0F){removeDummy();spawnDummy();}
    }

    private void chooseWaypoint(){
        waypointTicks=50+dummy.getRNG().nextInt(70);
        double angle=dummy.getRNG().nextDouble()*Math.PI*2D;
        double radius=2.0D+dummy.getRNG().nextDouble()*5D;
        goalX=spawnX+.5D+Math.cos(angle)*radius;
        goalZ=spawnZ+.5D+Math.sin(angle)*radius;
        // Prefer a nearby traversable floor point so the dummy does not aim into a wall or void.
        BlockPos origin=new BlockPos(dummy.posX,dummy.posY,dummy.posZ);
        BlockPos floor=findFloor(new BlockPos(MathHelper.floor(goalX),origin.getY(),MathHelper.floor(goalZ)));
        if(floor!=null){goalX=floor.getX()+.5D;goalZ=floor.getZ()+.5D;}
        // Bias some routes toward safe one-block holes, without teleporting the dummy.
        BlockPos nearest=null;double nearestDistance=Double.MAX_VALUE;
        for(BlockPos pos:BlockPos.getAllInBox(origin.add(-4,-1,-4),origin.add(4,1,4))){
            if(!isHole(pos))continue;
            double d=dummy.getDistanceSqToCenter(pos);
            if(d<nearestDistance){nearestDistance=d;nearest=pos;}
        }
        if(nearest!=null&&dummy.getRNG().nextInt(3)==0){goalX=nearest.getX()+.5D;goalZ=nearest.getZ()+.5D;jumpDelay=Math.min(jumpDelay,4);}
    }

    private BlockPos findFloor(BlockPos candidate){
        for(int dy=2;dy>=-3;dy--){
            BlockPos feet=candidate.add(0,dy,0);
            if(mc.world.isAirBlock(feet)&&mc.world.isAirBlock(feet.up())&&!mc.world.isAirBlock(feet.down())&&mc.world.getBlockState(feet.down()).isFullBlock())return feet;
        }
        return null;
    }

    private boolean isHole(BlockPos pos){
        if(!mc.world.isAirBlock(pos)||!mc.world.isAirBlock(pos.up())||mc.world.isAirBlock(pos.down())||!mc.world.getBlockState(pos.down()).isFullBlock())return false;
        for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.HORIZONTALS){BlockPos wall=pos.offset(side);if(mc.world.isAirBlock(wall)||!mc.world.getBlockState(wall).isFullBlock())return false;}
        return true;
    }

    @Override protected void onDisable(){removeDummy();waypointTicks=0;jumpDelay=24;}

    private void removeDummy(){
        if(mc.world!=null&&dummy!=null)mc.world.removeEntityFromWorld(entityId);
        dummy=null;goalX=goalZ=0D;
    }
}

