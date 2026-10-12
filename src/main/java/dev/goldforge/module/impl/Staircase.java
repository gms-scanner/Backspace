package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Places a supported lower tread and upper riser, then steps onto it. */
public final class Staircase extends Module {
    public int targetY=250;
    /** Direction the scaffold advances; the player keeps looking toward the old heading. */
    private EnumFacing direction=EnumFacing.NORTH;
    private int stage;
    private int waitTicks;
    private int jumpCooldown;
    private int jumpAttempts;
    /** The solid block currently supporting the player's feet. */
    private BlockPos stepBase;
    private BlockPos lastUpper;

    public Staircase() {
        super("Staircase", "Builds a supported rising staircase to the selected Y level.", Category.MOVEMENT, 0);
        addIntSetting("Target height", 2, 320, 1, () -> targetY, value -> targetY=value);
    }
    @Override protected void onEnable() { stage=0; waitTicks=0; jumpCooldown=0; jumpAttempts=0; stepBase=null; lastUpper=null; if(mc.player!=null) direction=mc.player.getHorizontalFacing().getOpposite(); }

    @Override public void tick() {
        if(mc.player==null||mc.world==null) return;
        if(mc.player.posY>=targetY) { stop(); setEnabled(false); return; }
        // Keep vanilla edge protection active for placement and the step-up move.
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);

        if(stage==2) {
            mc.player.rotationYaw=direction.getOpposite().getHorizontalAngle();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            if(jumpCooldown>0) jumpCooldown--;
            boolean onStep=lastUpper!=null && mc.player.onGround
                    && mc.player.posY>=lastUpper.getY()+.90D
                    // Sneak can leave the player on the edge; accept any grounded landing
                    // whose horizontal position still overlaps the target block.
                    && mc.player.getDistanceSq(lastUpper.getX()+.5,mc.player.posY,lastUpper.getZ()+.5)<1.44D;
            if(onStep) { stepBase=lastUpper.toImmutable(); stage=0; waitTicks=0; jumpAttempts=0; jumpCooldown=0; }
            else {
                waitTicks++;
                if(mc.player.onGround && jumpCooldown==0 && jumpAttempts<2) {
                double dx=lastUpper.getX()+.5D-mc.player.posX;
                double dz=lastUpper.getZ()+.5D-mc.player.posZ;
                    // Walk into the riser while sneaking, then jump on collision/close approach.
                    if(mc.player.collidedHorizontally || dx*dx+dz*dz<=.72D) {
                    mc.player.jump();
                    jumpAttempts++;
                    jumpCooldown=14;
                    }
                }
                if(waitTicks>100) { stop(); setEnabled(false); return; }
            }
            return;
        }

        if(stage==0) {
            direction=mc.player.getHorizontalFacing().getOpposite();
            if(stepBase==null) {
                if(!mc.player.onGround) return;
                stepBase=findSupportUnderPlayer();
                if(stepBase==null) return;
            }
        }
        mc.player.rotationYaw=direction.getOpposite().getHorizontalAngle();
        BlockPos lower=stepBase.offset(direction);
        BlockPos upper=lower.up();
        BlockPos target=stage==0?lower:upper;
        if(!mc.world.isAirBlock(target)) { stage=0; waitTicks=0; return; }
        if(stage==1 && mc.world.isAirBlock(lower)) { if(++waitTicks>15){stage=0;waitTicks=0;} return; }

        int slot=InventoryBlocks.selectFullBlock(mc,mc.player.inventory.currentItem);
        if(slot<0) { stop(); return; }
        if(placeAgainstSolid(target,slot)) {
            waitTicks=0;
            if(stage==0) stage=1;
            else { lastUpper=upper; stage=2; jumpAttempts=0; jumpCooldown=0; waitTicks=0; }
        } else if(++waitTicks>15) { stage=0; waitTicks=0; }
    }

    private boolean placeAgainstSolid(BlockPos target,int slot) {
        for(EnumFacing towardSupport:EnumFacing.values()) {
            BlockPos support=target.offset(towardSupport);
            if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isFullBlock()) continue;
            mc.player.inventory.currentItem=slot;
            EnumFacing clickFace=towardSupport.getOpposite();
            net.minecraft.util.EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,clickFace,InventoryBlocks.hitVec(support,clickFace),EnumHand.MAIN_HAND);
            if(result==net.minecraft.util.EnumActionResult.SUCCESS) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    /** Finds the real floor block beneath any part of the player's collision footprint. */
    private BlockPos findSupportUnderPlayer() {
        AxisAlignedBB box=mc.player.getEntityBoundingBox();
        BlockPos min=new BlockPos(box.minX,box.minY-.05D,box.minZ);
        BlockPos max=new BlockPos(box.maxX,box.minY-.05D,box.maxZ);
        BlockPos best=null;
        double bestDistance=Double.MAX_VALUE;
        for(BlockPos candidate:BlockPos.getAllInBoxMutable(min,max)) {
            if(!mc.world.getBlockState(candidate).isSideSolid(mc.world,candidate,EnumFacing.UP)) continue;
            double dx=candidate.getX()+.5D-mc.player.posX;
            double dz=candidate.getZ()+.5D-mc.player.posZ;
            double distance=dx*dx+dz*dz;
            if(distance<bestDistance) { best=candidate.toImmutable(); bestDistance=distance; }
        }
        return best;
    }

    private void stop() {
        if(mc.gameSettings!=null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        }
    }
    @Override protected void onDisable() { stop(); }
}
