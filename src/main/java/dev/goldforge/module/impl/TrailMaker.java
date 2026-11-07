package dev.goldforge.module.impl;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
public final class TrailMaker extends Module {
    public String block="minecraft:cobblestone";
    public int intervalTicks=2;
    private BlockPos previous;
    private final java.util.Deque<BlockPos> trail=new java.util.ArrayDeque<BlockPos>();
    private int cooldown;
    public TrailMaker(){super("TrailMaker","Places the selected block in the foot cell you just left. /moduleconfig TrailMaker",Category.WORLD,0);
        addIntSetting("Placement interval (ticks)",1,20,1,()->intervalTicks,v->intervalTicks=v);}
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null){previous=null;trail.clear();return;}
        int feetY=(int)Math.ceil(mc.player.getEntityBoundingBox().minY-.001D);
        BlockPos current=new BlockPos(mc.player.posX,feetY,mc.player.posZ);
        if(previous==null){previous=current;return;}
        if(current.getX()!=previous.getX()||current.getZ()!=previous.getZ()){
            if(current.distanceSq(previous)<=4D){
                trail.addLast(new BlockPos(previous.getX(),feetY,previous.getZ()));
                while(trail.size()>16)trail.removeFirst();
            }else trail.clear();
        }
        previous=current;
        if(cooldown>0){cooldown--;return;}
        if(isAutomationBlockedByScreen()||mc.player.isHandActive())return;
        BlockPos target=null;
        java.util.Iterator<BlockPos> iterator=trail.iterator();
        while(iterator.hasNext()){
            BlockPos candidate=iterator.next();
            if(candidate.getY()!=feetY||current.distanceSq(candidate)>16D
                    ||!mc.world.isBlockLoaded(candidate)
                    ||!mc.world.getBlockState(candidate).getMaterial().isReplaceable()){
                iterator.remove();continue;
            }
            if(mc.player.getEntityBoundingBox().intersects(new net.minecraft.util.math.AxisAlignedBB(candidate)))continue;
            target=candidate;break;
        }
        if(target==null)return;
        Block chosen=Block.getBlockFromName(block);
        if(chosen==null||!BlockFilter.allows(chosen))return;
        int original=mc.player.inventory.currentItem,slot=-1;
        for(int i=0;i<9;i++){ItemStack stack=mc.player.inventory.getStackInSlot(i);
            if(!stack.isEmpty()&&stack.getItem() instanceof ItemBlock&&((ItemBlock)stack.getItem()).getBlock()==chosen){slot=i;break;}}
        if(slot<0)return;
        try{
            for(EnumFacing side:EnumFacing.values()){
                BlockPos support=target.offset(side);
                if(!mc.world.getBlockState(support).isFullBlock())continue;
                EnumFacing face=side.getOpposite();
                net.minecraft.util.math.Vec3d hit=InventoryBlocks.hitVec(support,face);
                double reach=mc.playerController.getBlockReachDistance();
                if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>reach*reach)continue;
                mc.player.inventory.currentItem=slot;mc.playerController.updateController();
                // Send the actual support face interaction; do not predict a local block.
                dev.goldforge.network.PacketManager.send(
                        new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(
                                support,face,EnumHand.MAIN_HAND,
                                .5F+face.getFrontOffsetX()*.5F,
                                .5F+face.getFrontOffsetY()*.5F,
                                .5F+face.getFrontOffsetZ()*.5F),name);
                cooldown=Math.max(1,intervalTicks);break;
            }
        }finally{mc.player.inventory.currentItem=original;mc.playerController.updateController();}
    }
    @Override protected void onDisable(){previous=null;trail.clear();cooldown=0;}
}
