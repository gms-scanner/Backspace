package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** Builds the four-block T and then places its three skulls over successive ticks. */
public final class AutoWither extends Module {
    private boolean armed;
    private boolean building;
    private boolean wasUseDown;
    private BlockPos base;
    private int stage;
    private int waitTicks;

    public AutoWither() { super("AutoWither", "Waits for use input, then assembles the wither structure at the aimed block.", Category.WORLD, 0); }

    @Override protected void onEnable() {
        armed=true; building=false; stage=0; waitTicks=0; wasUseDown=false;
        message("Hold soul sand, aim at a support block, and right-click to place the base.");
    }

    @Override public void tick() {
        if (mc.player==null || mc.world==null) return;
        boolean useDown=mc.gameSettings.keyBindUseItem.isKeyDown();
        boolean usePressed=useDown&&!wasUseDown;
        wasUseDown=useDown;
        if (!building) {
            if (!armed || !usePressed || mc.objectMouseOver==null || mc.objectMouseOver.getBlockPos()==null) return;
            if(!beginBuild()) return;
        }

        BlockPos target=targetForStage(stage);
        if (!mc.world.isAirBlock(target)) { finish("Placement space became blocked."); return; }
        Block required=stage<4 ? Blocks.SOUL_SAND : Blocks.SKULL;
        int slot=stage<4 ? InventoryBlocks.selectBlock(mc,required,0) : InventoryBlocks.selectWitherSkull(mc,1);
        if (slot<0) { finish("Required block stack is no longer available."); return; }
        if (placeAgainstSolid(target, slot)) {
            stage++; waitTicks=0;
            if (stage>=7) {
                int sandSlot=InventoryBlocks.selectBlock(mc,Blocks.SOUL_SAND,mc.player.inventory.currentItem);
                if(sandSlot>=0)mc.player.inventory.currentItem=sandSlot;
                finish("Wither structure placed. Soul sand selected for the next one.");
            }
        } else if (++waitTicks>12) {
            finish("Could not place the next block. Check reach and support.");
        }
    }

    /** Uses the soul sand from the user's right-click as the bottom-center block. */
    private boolean beginBuild() {
        BlockPos clicked=mc.objectMouseOver.getBlockPos();
        if(mc.world.getBlockState(clicked).getBlock()==Blocks.SOUL_SAND) base=clicked.toImmutable();
        else base=clicked.offset(mc.objectMouseOver.sideHit).toImmutable();
        if(mc.world.getBlockState(base).getBlock()!=Blocks.SOUL_SAND) {
            message("Place one soul sand block with that right-click to anchor the structure.");
            base=null; return false;
        }
        BlockPos[] remaining={base.up(),base.up().north(),base.up().south(),base.up(2),base.up(2).north(),base.up(2).south()};
        for(BlockPos position:remaining) if(!mc.world.isAirBlock(position)) {
            message("The six remaining wither positions must be clear."); base=null; return false;
        }
        int soulSand=count(Blocks.SOUL_SAND), skulls=countWitherSkulls();
        if(soulSand<3||skulls<3) {
            message("Inventory scan: soul sand "+soulSand+"/3 more, wither skeleton skulls "+skulls+"/3.");
            base=null; return false;
        }
        building=true; armed=false; stage=1; waitTicks=0;
        message("Base found. Building the remaining six blocks...");
        return true;
    }

    private BlockPos targetForStage(int n) {
        switch(n) {
            case 0: return base;
            case 1: return base.up();
            case 2: return base.up().north();
            case 3: return base.up().south();
            case 4: return base.up(2);
            case 5: return base.up(2).north();
            default: return base.up(2).south();
        }
    }

    private boolean placeAgainstSolid(BlockPos target, int slot) {
        for (EnumFacing towardSupport:EnumFacing.values()) {
            BlockPos support=target.offset(towardSupport);
            if (mc.world.isAirBlock(support) || !mc.world.getBlockState(support).isFullBlock()) continue;
            mc.player.inventory.currentItem=slot;
            EnumFacing clickFace=towardSupport.getOpposite();
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,clickFace,InventoryBlocks.hitVec(support,clickFace),EnumHand.MAIN_HAND);
            if(result==EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private int count(Block block) {
        int amount=0;
        for(int i=0;i<36;i++) if(!mc.player.inventory.getStackInSlot(i).isEmpty()&&mc.player.inventory.getStackInSlot(i).getItem() instanceof net.minecraft.item.ItemBlock&&((net.minecraft.item.ItemBlock)mc.player.inventory.getStackInSlot(i).getItem()).getBlock()==block) amount+=mc.player.inventory.getStackInSlot(i).getCount();
        return amount;
    }
    private int countWitherSkulls() {
        int amount=0;
        for(int i=0;i<36;i++) {
            net.minecraft.item.ItemStack stack=mc.player.inventory.getStackInSlot(i);
            if(InventoryBlocks.isWitherSkull(stack)) amount+=stack.getCount();
        }
        return amount;
    }
    private void message(String value) { if(mc.player!=null) mc.player.sendMessage(new TextComponentString("§e[Backspace] §f"+value)); }
    private void finish(String status) {
        building=false; armed=true; base=null; stage=0; waitTicks=0;
        message(status);
    }
}

