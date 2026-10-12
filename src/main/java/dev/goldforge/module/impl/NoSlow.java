package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumAction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;

/** Compensates for item-use input drag and applies a bounded cobweb movement target. */
public final class NoSlow extends Module {
    /** 0: configurable slowdown compensation, 1: full compensation. */
    public int mode=0,ncpStrength=4;
    /** 0: off, 1: NCP web allowance, 2: restore normal walking/sprinting pace. */
    public int cobwebMode=1;
    public int cobwebNcpPercent=90,cobwebFullPercent=100;
    private float savedCobwebForward,savedCobwebStrafe;
    private boolean savedCobwebInput;

    public NoSlow(){
        super("NoSlow","Compensates for the client-side item-use input slowdown.",Category.MOVEMENT,0);
        addIntSetting("Mode (0 NCP, 1 Full)",0,1,1,()->mode,v->mode=v);
        addIntSetting("NCP compensation (1 partial, 4 full)",1,4,1,()->ncpStrength,v->ncpStrength=v);
        addIntSetting("Cobweb mode (0 off, 1 NCP, 2 Full; may flag)",0,2,1,()->cobwebMode,v->cobwebMode=v);
        addIntSetting("Cobweb NCP allowance (%)",25,90,5,()->cobwebNcpPercent,v->cobwebNcpPercent=v);
        addIntSetting("Cobweb full pace (%)",25,100,5,()->cobwebFullPercent,v->cobwebFullPercent=v);
    }

    @Override public String hudTag(){return mode==0?"[NCP]":"[Full]";}

    @Override public void onInputUpdate(InputUpdateEvent event){
        EntityPlayer player=event.getEntityPlayer();
        if(player==null||player!=mc.player||player.isRiding()||player.isInWater()||player.isInLava())return;
        net.minecraft.util.MovementInput input=event.getMovementInput();
        if(cobwebMode==1&&intersectsCobweb(player)){
            // The NCP-profile velocity is applied separately after this tick.
            // Preserve the requested direction but remove vanilla's additive
            // input acceleration, which would otherwise stack on top of that
            // velocity and push the resulting web displacement over NCP's cap.
            savedCobwebForward=input.moveForward;
            savedCobwebStrafe=input.moveStrafe;
            savedCobwebInput=true;
            input.moveForward=0F;
            input.moveStrafe=0F;
            return;
        }
        savedCobwebInput=false;
        boolean usingItem=player.isHandActive()&&!player.getActiveItemStack().isEmpty()
                &&player.getActiveItemStack().getItemUseAction()!=EnumAction.NONE;
        if(!usingItem)return;
        // InputUpdateEvent runs after key input is collected; vanilla then
        // multiplies these two axes by 0.2 while an item is active. Do not
        // clamp before that vanilla reduction or the compensation is erased.
        float multiplier=mode==1?5.0F:1.0F+ncpStrength;
        input.moveForward*=multiplier;
        input.moveStrafe*=multiplier;
    }

    @Override public void tick(){
        EntityPlayerSP player=mc.player;
        if(player==null||mc.world==null||player.isRiding()||player.isElytraFlying()
                ||player.isInWater()||player.isInLava()||cobwebMode==0||!intersectsCobweb(player))return;
        float forward=cobwebMode==1&&savedCobwebInput?savedCobwebForward:player.movementInput.moveForward;
        float strafe=cobwebMode==1&&savedCobwebInput?savedCobwebStrafe:player.movementInput.moveStrafe;
        savedCobwebInput=false;
        double length=Math.sqrt(forward*forward+strafe*strafe);
        if(length<1.0E-4D)return;
        forward/=length;
        strafe/=length;
        double yaw=Math.toRadians(player.rotationYaw);
        double x=strafe*Math.cos(yaw)-forward*Math.sin(yaw);
        double z=forward*Math.cos(yaw)+strafe*Math.sin(yaw);

        // Vanilla's web collision multiplies horizontal motion by 0.25. Set
        // the requested velocity before that collision so the resulting speed
        // matches the selected post-web target. NCP's web allowance is roughly
        // 0.475 of the ordinary walking/sprinting envelope; use its full
        // configured allowance with a small margin for server/config variation.
        double normalPace=player.isSprinting()?0.2873D:0.221D;
        double postWebTarget;
        if(cobwebMode==1){
            double ncpAllowance=normalPace*0.475D;
            postWebTarget=ncpAllowance*clampNcpPercent(cobwebNcpPercent)/100.0D;
        }else{
            postWebTarget=normalPace*clampPercent(cobwebFullPercent)/100.0D;
        }
        double preDampedTarget=postWebTarget/0.25D;
        dev.goldforge.module.MovementManager.requestHorizontalVelocity(
                this,x*preDampedTarget,z*preDampedTarget,20);
    }

    private static int clampPercent(int value){return Math.max(25,Math.min(100,value));}
    private static int clampNcpPercent(int value){return Math.max(25,Math.min(90,value));}

    private boolean intersectsCobweb(EntityPlayer player){
        if(mc.world==null)return false;
        AxisAlignedBB box=player.getEntityBoundingBox();
        int minX=MathHelper.floor(box.minX),minY=MathHelper.floor(box.minY),minZ=MathHelper.floor(box.minZ);
        int maxX=MathHelper.floor(box.maxX),maxY=MathHelper.floor(box.maxY),maxZ=MathHelper.floor(box.maxZ);
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
            if(mc.world.getBlockState(new BlockPos(x,y,z)).getBlock()!=Blocks.WEB)continue;
            if(box.intersects(new AxisAlignedBB(x,y,z,x+1D,y+1D,z+1D)))return true;
        }
        return false;
    }
}
