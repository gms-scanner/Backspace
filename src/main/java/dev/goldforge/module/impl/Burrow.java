package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/** One-shot packet burrow attempt with separate block and position observations. */
public final class Burrow extends Module {
    public int heightHundredths=101,confirmTimeout=20;
    private BlockPos target;
    private int ticks,attemptTick,corrections;
    private boolean attempted,blockObserved;
    private String state="Ready";
    public Burrow(){
        super("Burrow","Attempts to place obsidian at the player's former feet and observes server replies.",Category.COMBAT,0);
        addIntSetting("Step height (hundredths)",101,150,1,()->heightHundredths,v->heightHundredths=v);
        addIntSetting("Confirmation timeout",5,60,1,()->confirmTimeout,v->confirmTimeout=v);
    }
    @Override protected void onEnable(){clear();}
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.getConnection()==null)return;
        ticks++;
        if(attempted){
            if(blockObserved)state=corrections>0?"Block observed; position corrected":"Block observed; position unverified";
            else if(ticks-attemptTick>=confirmTimeout)state="Placement unconfirmed";
            return;
        }
        if(isAutomationBlockedByScreen()||!mc.player.onGround||mc.player.isRiding()||mc.player.isHandActive())return;
        dev.goldforge.module.Module blink=dev.goldforge.GoldForge.MODULES.get("Blink");
        if(blink!=null&&blink.enabled){state="Disable Blink first";return;}
        target=new BlockPos(mc.player.posX,mc.player.getEntityBoundingBox().minY,mc.player.posZ);
        if(!mc.world.isBlockLoaded(target)||!mc.world.getBlockState(target).getMaterial().isReplaceable()
                ||!mc.world.getBlockState(target.down()).isFullBlock()
                ||!mc.world.getBlockState(target.up()).getMaterial().isReplaceable()
                ||!mc.world.getBlockState(target.up(2)).getMaterial().isReplaceable()){
            state="No valid space/support";return;
        }
        double reach=mc.playerController.getBlockReachDistance();
        if(mc.player.getDistanceSq(target.getX()+.5D,target.getY()-.5D,target.getZ()+.5D)>reach*reach){
            state="Out of reach";return;
        }
        int original=mc.player.inventory.currentItem,slot=findHotbarObsidian();
        int source=-1;
        if(slot<0&&mc.player.openContainer==mc.player.inventoryContainer){
            source=findInventoryObsidian();
            if(source>=0){slot=original;mc.playerController.windowClick(mc.player.inventoryContainer.windowId,
                    source,slot,ClickType.SWAP,mc.player);}
        }
        if(slot<0){state="Obsidian required";return;}
        try{
            mc.player.inventory.currentItem=slot;
            mc.playerController.updateController();
            double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
            double lift=Math.max(1.01D,Math.min(1.5D,heightHundredths/100D));
            if(!PacketManager.send(new CPacketPlayer.Position(x,y+lift,z,false),name))return;
            PacketManager.send(new CPacketPlayerTryUseItemOnBlock(
                    target.down(),EnumFacing.UP,EnumHand.MAIN_HAND,.5F,1F,.5F),name);
            PacketManager.send(new CPacketPlayer.Position(x,y+.01D,z,false),name);
            attempted=true;attemptTick=ticks;state="Placement requested";
        }finally{
            if(source>=9&&mc.player.openContainer==mc.player.inventoryContainer){
                ItemStack stack=mc.player.inventory.getStackInSlot(slot);
                if(stack.isEmpty()||isObsidian(stack))
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId,source,slot,ClickType.SWAP,mc.player);
            }
            mc.player.inventory.currentItem=original;
            mc.playerController.updateController();
        }
    }
    public void onServerBlockChange(BlockPos pos,IBlockState block){
        if(attempted&&target!=null&&target.equals(pos)&&block.getBlock()==Blocks.OBSIDIAN)blockObserved=true;
    }
    public void onServerCorrection(SPacketPlayerPosLook packet){
        if(attempted)corrections++;
    }
    private int findHotbarObsidian(){
        for(int i=0;i<9;i++)if(isObsidian(mc.player.inventory.getStackInSlot(i)))return i;
        return -1;
    }
    private int findInventoryObsidian(){
        for(int i=9;i<36;i++)if(isObsidian(mc.player.inventory.getStackInSlot(i)))return i;
        return -1;
    }
    private boolean isObsidian(ItemStack stack){
        return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock
                &&((ItemBlock)stack.getItem()).getBlock()==Blocks.OBSIDIAN;
    }
    private void clear(){target=null;ticks=attemptTick=corrections=0;attempted=blockObserved=false;state="Ready";}
    @Override protected void onDisable(){clear();}
    @Override protected void onWorldUnload(){clear();}
    @Override public String hudTag(){return "["+state+"]";}
}
