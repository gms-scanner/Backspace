package dev.goldforge.module.impl;

import dev.goldforge.module.*;
import dev.goldforge.module.Module;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Reuses a placement face, not mining progress from a destroyed block. */
public final class EChestFarm extends Module {
    public int timeout=60;
    public int finishPercent=70;
    private int miningSlot=-1, countedTicks;
    private long miningStarted;
    private BlockPos target,support;
    private EnumFacing face;
    private int stage,ticks,oldSlot=-1;
    private float progress;
    public EChestFarm(){
        super("EChestFarm","Repeated packet placement and mining at the selected block face.",Category.WORLD,0);
        addIntSetting("Server timeout",20,200,1,()->timeout,v->timeout=v);
        addIntSetting("Finish damage percent",70,100,1,()->finishPercent,v->finishPercent=v);
    }
    @Override protected void onEnable(){stage=0;target=null;ticks=0;if(mc.player!=null)oldSlot=mc.player.inventory.currentItem;}
    @Override public void tick(){
        if(mc.player==null||mc.world==null)return;
        if(mc.currentScreen!=null){abort();return;}
        if(target==null){
            RayTraceResult hit=mc.objectMouseOver;
            if(hit==null||hit.typeOfHit!=RayTraceResult.Type.BLOCK)return;
            support=hit.getBlockPos();face=hit.sideHit;target=support.offset(face);
        }
        double reach=mc.playerController.getBlockReachDistance();
        if(mc.player.getPositionEyes(1F).squareDistanceTo(new Vec3d(target).addVector(.5,.5,.5))>reach*reach){abort();target=null;return;}
        // Re-place on the first tick that observes the confirmed break.
        if(stage==3&&mc.world.getBlockState(target).getMaterial().isReplaceable()){stage=0;ticks=0;}
        if(stage==0){
            if(!mc.world.getBlockState(target).getMaterial().isReplaceable()){target=null;return;}
            if(!mc.world.getBlockState(support).isSideSolid(mc.world,support,face)){target=null;return;}
            if(mc.player.getEntityBoundingBox().intersects(new AxisAlignedBB(target)))return;
            int slot=-1;
            for(int i=0;i<9;i++){ItemStack s=mc.player.inventory.getStackInSlot(i);
                if(!s.isEmpty()&&s.getItem() instanceof ItemBlock&&((ItemBlock)s.getItem()).getBlock()==Blocks.ENDER_CHEST){slot=i;break;}}
            if(slot<0){setEnabled(false);return;}
            mc.player.inventory.currentItem=slot;mc.playerController.updateController();
            Vec3d hit=InventoryBlocks.hitVec(support,face), d=hit.subtract(mc.player.getPositionEyes(1F));
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation((float)Math.toDegrees(Math.atan2(-d.x,d.z)),
                (float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))),mc.player.onGround));
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(support,face,EnumHand.MAIN_HAND,
                (float)(hit.x-support.getX()),(float)(hit.y-support.getY()),(float)(hit.z-support.getZ())));
            stage=1;ticks=0;return;
        }
        if(stage==1){
            if(mc.world.getBlockState(target).getBlock()==Blocks.ENDER_CHEST){
                int best=-1;float speed=0;
                for(int i=0;i<9;i++){ItemStack s=mc.player.inventory.getStackInSlot(i);
                    if(!(s.getItem() instanceof ItemPickaxe)||EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH,s)>0)continue;
                    int saved=mc.player.inventory.currentItem;mc.player.inventory.currentItem=i;
                    float rate=mc.world.getBlockState(target).getPlayerRelativeBlockHardness(mc.player,mc.world,target);
                    mc.player.inventory.currentItem=saved;if(rate>speed){speed=rate;best=i;}}
                if(best<0){setEnabled(false);return;}
                mc.player.inventory.currentItem=best;mc.playerController.updateController();
                miningSlot=best;
                dig(CPacketPlayerDigging.Action.START_DESTROY_BLOCK);
                progress=speed;miningStarted=System.nanoTime();countedTicks=0;stage=2;ticks=0;return;
            }
        }else if(stage==2){
            if(mc.world.getBlockState(target).getBlock()!=Blocks.ENDER_CHEST){abort();return;}
            if(mc.player.inventory.currentItem!=miningSlot){abort();setEnabled(false);return;}
            // Client Timer must not make us count more mining time than elapsed.
            int elapsed=(int)((System.nanoTime()-miningStarted)/50000000L);
            if(elapsed>countedTicks){
                float rate=mc.world.getBlockState(target).getPlayerRelativeBlockHardness(mc.player,mc.world,target);
                progress+=rate;
                countedTicks=elapsed;
            }
            if(progress>=Math.max(.7F,Math.min(1F,finishPercent/100F))){
                dig(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK);stage=3;ticks=0;
            }
            return;
        }else if(stage==3&&mc.world.getBlockState(target).getMaterial().isReplaceable()){stage=0;ticks=0;return;}
        if(++ticks>timeout)setEnabled(false);
    }
    private void dig(CPacketPlayerDigging.Action action){if(target!=null&&mc.getConnection()!=null)mc.player.connection.sendPacket(new CPacketPlayerDigging(action,target,face.getOpposite()));}
    private void abort(){if(stage==2)dig(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK);stage=0;progress=0;ticks=0;}
    @Override protected void onDisable(){abort();target=null;if(mc.player!=null&&oldSlot>=0){mc.player.inventory.currentItem=oldSlot;mc.playerController.updateController();}}
    @Override public String hudTag(){return stage==2?"Mining":stage==1||stage==3?"Awaiting server":"Place";}
}
