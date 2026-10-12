package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.play.client.CPacketPlayer;

/** Places a block beneath the moving player, using a real neighboring block as support. */
public final class Scaffold extends Module {
    public int actionDelay=0;
    public int ncpProfile=1;
    public int targetY=-1;
    private int anchorY;
    private int cooldown;
    private BlockPos pending;
    private int pendingTicks;

    public Scaffold() {
        super("Scaffold", "Places full blocks beneath you using adjacent solid support; never airplaces.", Category.MOVEMENT, 0);
        addIntSetting("Action delay (ticks)",0,10,1,()->actionDelay,value->actionDelay=value);
        addIntSetting("NCP timing (0/1)",0,1,1,()->ncpProfile,value->ncpProfile=value);
        addIntSetting("Target Y (-1=current)",-1,320,1,()->targetY,value->targetY=value);
    }

    @Override protected void onEnable() {
        if(mc.player!=null) anchorY=targetY<0?new BlockPos(mc.player.posX,mc.player.posY-.05D,mc.player.posZ).getY():targetY;
        pending=null; pendingTicks=0; cooldown=0;
    }

    @Override public void tick() {
        if(mc.player==null||mc.world==null)return;
        if(cooldown>0)cooldown--;
        if(pending!=null) {
            if(!mc.world.isAirBlock(pending)) { pending=null; pendingTicks=0; }
            else if(++pendingTicks>6) { pending=null; pendingTicks=0; }
        }
        if(cooldown>0||pending!=null)return;
        if(Math.abs(mc.player.motionX)+Math.abs(mc.player.motionZ)<.025D)return;
        int placeY=targetY<0?anchorY:targetY;
        BlockPos target=new BlockPos(mc.player.posX,placeY,mc.player.posZ);
        if(!mc.world.isAirBlock(target))return;
        int slot=InventoryBlocks.selectFullBlock(mc,mc.player.inventory.currentItem);
        if(slot<0)return;
        for(EnumFacing side:EnumFacing.values()) {
            BlockPos support=target.offset(side);
            if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isFullBlock())continue;
            EnumFacing face=side.getOpposite();
            Vec3d hit=InventoryBlocks.hitVec(support,face);
            double reach=mc.playerController.getBlockReachDistance();
            if(mc.player.getPositionEyes(1.0F).squareDistanceTo(hit)>reach*reach)continue;
            mc.player.inventory.currentItem=slot;
            // Send a one-action server rotation toward the real support face while
            // leaving the local camera untouched.
            Vec3d eye=mc.player.getPositionEyes(1.0F);
            double dx=hit.x-eye.x, dy=hit.y-eye.y, dz=hit.z-eye.z;
            float yaw=(float)(MathHelper.atan2(dz,dx)*180.0D/Math.PI)-90.0F;
            float pitch=(float)(-MathHelper.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180.0D/Math.PI);
            if(mc.getConnection()!=null) dev.goldforge.network.PacketManager.send(new CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround),name);
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,face,hit,EnumHand.MAIN_HAND);
            if(mc.getConnection()!=null) dev.goldforge.network.PacketManager.send(new CPacketPlayer.Rotation(mc.player.rotationYaw,mc.player.rotationPitch,mc.player.onGround),name);
            if(result==EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
                pending=target.toImmutable(); pendingTicks=0;
                cooldown=Math.max(actionDelay,ncpProfile!=0?1:0);
                return;
            }
        }
    }

    @Override protected void onDisable() { pending=null; pendingTicks=0; cooldown=0; }
}
