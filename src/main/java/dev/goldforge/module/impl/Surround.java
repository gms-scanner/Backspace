package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Maintains a supported, blast-resistant ring around every cell touched by the player's feet. */
public final class Surround extends Module {
    public int blocksPerTick=4,refillInventory=1,includeFloor=1;
    private int ticks;
    private final Map<BlockPos,Integer> pending=new LinkedHashMap<BlockPos,Integer>();

    public Surround(){
        super("Surround","Places obsidian or ender chests around the complete player footprint.",Category.COMBAT,0);
        addIntSetting("Blocks per tick",1,5,1,()->blocksPerTick,v->blocksPerTick=v);
        addIntSetting("Refill from inventory (0/1)",0,1,1,()->refillInventory,v->refillInventory=v);
        addIntSetting("Repair floor (0/1)",0,1,1,()->includeFloor,v->includeFloor=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||isAutomationBlockedByScreen()
                ||mc.player.isDead||mc.player.isHandActive()){pending.clear();return;}
        ticks++;
        Iterator<Map.Entry<BlockPos,Integer>> iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Integer> entry=iterator.next();
            if(!replaceable(entry.getKey())||ticks-entry.getValue()>3)iterator.remove();
        }
        Set<BlockPos> targets=targets();
        boolean missing=false;
        for(BlockPos pos:targets)if(replaceable(pos)&&!pending.containsKey(pos)&&!intersectsPlayer(pos)){
            missing=true;break;
        }
        if(!missing)return;

        int original=mc.player.inventory.currentItem;
        Selection selection=findBlocks(original);
        if(selection==null)return;
        int placed=0;
        try{
            for(BlockPos target:targets){
                if(placed>=blocksPerTick)break;
                if(!replaceable(target)||pending.containsKey(target)||intersectsPlayer(target))continue;
                if(place(target,selection.slot)){
                    pending.put(target.toImmutable(),ticks);
                    placed++;
                }else if(placed<blocksPerTick&&target.getY()==footY()&&replaceable(target.down())
                        &&!pending.containsKey(target.down())&&!intersectsPlayer(target.down())
                        &&place(target.down(),selection.slot)){
                    pending.put(target.down().toImmutable(),ticks);
                    placed++;
                    if(placed<blocksPerTick&&place(target,selection.slot)){
                        pending.put(target.toImmutable(),ticks);
                        placed++;
                    }
                }
            }
        }finally{
            if(selection.movedFrom>=9&&mc.player.openContainer==mc.player.inventoryContainer){
                ItemStack stack=mc.player.inventory.getStackInSlot(selection.slot);
                if(stack.isEmpty()||isSurroundBlock(stack))
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId,
                            selection.movedFrom,selection.slot,ClickType.SWAP,mc.player);
            }
            if(mc.player.inventory.currentItem==selection.slot&&original!=selection.slot){
                mc.player.inventory.currentItem=original;
                mc.playerController.updateController();
            }
        }
    }

    private Set<BlockPos> targets(){
        AxisAlignedBB box=mc.player.getEntityBoundingBox();
        int minX=MathHelper.floor(box.minX+.001D),maxX=MathHelper.floor(box.maxX-.001D);
        int minZ=MathHelper.floor(box.minZ+.001D),maxZ=MathHelper.floor(box.maxZ-.001D);
        int y=footY();
        Set<BlockPos> result=new LinkedHashSet<BlockPos>();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(includeFloor!=0)result.add(new BlockPos(x,y-1,z));
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            BlockPos feet=new BlockPos(x,y,z);
            result.add(feet.north());result.add(feet.south());
            result.add(feet.east());result.add(feet.west());
        }
        return result;
    }

    private int footY(){return MathHelper.floor(mc.player.getEntityBoundingBox().minY+.01D);}
    private boolean replaceable(BlockPos pos){
        return mc.world.isBlockLoaded(pos)&&mc.world.getWorldBorder().contains(pos)
                &&mc.world.getBlockState(pos).getMaterial().isReplaceable();
    }
    private boolean intersectsPlayer(BlockPos pos){
        return mc.player.getEntityBoundingBox().intersects(new AxisAlignedBB(pos));
    }
    private boolean place(BlockPos target,int slot){
        if(!replaceable(target)||intersectsPlayer(target))return false;
        EnumFacing[] order={EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,
                EnumFacing.EAST,EnumFacing.WEST,EnumFacing.UP};
        double reach=mc.playerController.getBlockReachDistance();
        for(EnumFacing side:order){
            BlockPos support=target.offset(side);
            if(!mc.world.isBlockLoaded(support)||!mc.world.getBlockState(support).isFullBlock())continue;
            EnumFacing face=side.getOpposite();
            Vec3d hit=InventoryBlocks.hitVec(support,face);
            if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>reach*reach)continue;
            if(mc.player.inventory.currentItem!=slot){
                mc.player.inventory.currentItem=slot;
                mc.playerController.updateController();
            }
            EnumActionResult result=mc.playerController.processRightClickBlock(
                    mc.player,mc.world,support,face,hit,EnumHand.MAIN_HAND);
            if(result==EnumActionResult.SUCCESS){mc.player.swingArm(EnumHand.MAIN_HAND);return true;}
        }
        return false;
    }
    private Selection findBlocks(int original){
        int slot=hotbarSlot(Blocks.OBSIDIAN);
        if(slot<0)slot=hotbarSlot(Blocks.ENDER_CHEST);
        if(slot>=0)return new Selection(slot,-1);
        if(refillInventory==0||mc.player.openContainer!=mc.player.inventoryContainer)return null;
        int source=inventorySlot(Blocks.OBSIDIAN);
        if(source<0)source=inventorySlot(Blocks.ENDER_CHEST);
        if(source<0)return null;
        int destination=original;
        for(int i=0;i<9;i++)if(mc.player.inventory.getStackInSlot(i).isEmpty()){destination=i;break;}
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId,source,destination,ClickType.SWAP,mc.player);
        return new Selection(destination,source);
    }
    private int hotbarSlot(Block block){
        for(int i=0;i<9;i++)if(isBlock(mc.player.inventory.getStackInSlot(i),block))return i;
        return -1;
    }
    private int inventorySlot(Block block){
        for(int i=9;i<36;i++)if(isBlock(mc.player.inventory.getStackInSlot(i),block))return i;
        return -1;
    }
    private boolean isBlock(ItemStack stack,Block block){
        return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock
                &&((ItemBlock)stack.getItem()).getBlock()==block;
    }
    private boolean isSurroundBlock(ItemStack stack){
        return isBlock(stack,Blocks.OBSIDIAN)||isBlock(stack,Blocks.ENDER_CHEST);
    }
    @Override protected void onDisable(){pending.clear();}
    @Override protected void onWorldUnload(){pending.clear();}
    private static final class Selection{
        final int slot,movedFrom;
        Selection(int slot,int movedFrom){this.slot=slot;this.movedFrom=movedFrom;}
    }
}
