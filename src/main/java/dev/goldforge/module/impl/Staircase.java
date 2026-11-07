package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Forge 1.12.2 staircase builder; movement waits for observed block placement. */
public final class Staircase extends Module {
    public int targetY = 250, placementDelay = 1;
    public boolean followLook = true, startPaused;
    public boolean descendWhenLookingDown = true;
    public int downwardLimit = 1, downwardPitch = 40;
    public boolean useTimer;
    public int timerTickRate = 30;
    private net.minecraft.util.Timer ownedTimer;
    private float previousTickLength;
    private java.lang.reflect.Field tickLengthField;
    private boolean timerFailed;
    private boolean paused, useHeld, walking, sneaking, jumpRequested, jumped;
    private double moveX, moveZ;
    private EnumFacing direction;
    private BlockPos lower, upper, base;
    private BlockPos pending;
    private net.minecraft.block.Block pendingBlock;
    private int pendingTicks;
    private int stage, delay, attempts, jumpRetries, originalSlot = -1;
    private String stopReason="";

    public Staircase() {
        super("Staircase", "Builds a rising staircase from confirmed block placements.", Category.MOVEMENT, 0);
        addIntSetting("Target Y", 2, 255, 1, () -> targetY, v -> targetY = v);
        addIntSetting("Place delay", 1, 10, 1, () -> placementDelay, v -> placementDelay = v);
        addIntSetting("Follow look", 0, 1, 1, () -> followLook ? 1 : 0, v -> followLook = v != 0);
        addIntSetting("Look down to descend", 0, 1, 1, () -> descendWhenLookingDown?1:0, v -> descendWhenLookingDown=v!=0);
        addIntSetting("Downward pitch", 20, 80, 1, () -> downwardPitch, v -> downwardPitch=v);
        addIntSetting("Minimum Y", 1, 254, 1, () -> downwardLimit, v -> downwardLimit=v);
        addIntSetting("Start paused", 0, 1, 1, () -> startPaused ? 1 : 0, v -> startPaused = v != 0);
        addIntSetting("Use timer", 0, 1, 1, () -> useTimer ? 1 : 0, v -> {
            useTimer=v!=0;
            if (!useTimer) restoreTimer();
        });
        addIntSetting("Timer tick rate", 10, 100, 1, () -> timerTickRate, v -> timerTickRate=v);
    }

    @Override protected void onEnable() {
        reset();
        paused = startPaused;
        if (mc.player != null) {
            direction = mc.player.getHorizontalFacing();
            originalSlot = mc.player.inventory.currentItem;
        }
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null) { restoreTimer(); return; }
        if (!useTimer) restoreTimer();
        boolean use = mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown();
        if (use && !useHeld) { paused = !paused; reset(); }
        useHeld = use;
        if (paused || isAutomationBlockedByScreen()) { releaseMovement(); return; }
        if (direction == null) direction = mc.player.getHorizontalFacing();
        if (delay > 0) delay--;
        if (pending != null) {
            if (mc.world.getBlockState(pending).getBlock()==pendingBlock && solid(pending)) {
                pending=null; pendingBlock=null;
            } else {
                releaseMovement();
                if (++pendingTicks>40) pauseWithReason("Server did not confirm placement");
                return;
            }
        }

        if (stage == 0) {
            releaseMovement();
            if (!mc.player.onGround) return;
            if (followLook) direction = mc.player.getHorizontalFacing();
            base = supportUnderPlayer();
            if (base == null) return;
            stopReason="";
            if (descendWhenLookingDown && mc.player.rotationPitch>=downwardPitch) {
                upper=base.offset(direction).down();
                if (upper.getY()<downwardLimit || !mc.world.getWorldBorder().contains(upper)) {
                    pauseWithReason("Downward limit"); return;
                }
                if ((!solid(upper)&&!replaceable(upper)) || !replaceable(upper.up()) || !replaceable(upper.up(2))) {
                    pauseWithReason("Descent obstructed"); return;
                }
                stage=10; attempts=0;
            } else {
            lower = base.offset(direction);
            upper = lower.up();
            if (upper.getY() >= targetY) { setEnabled(false); return; }
            if ((!solid(lower) && !replaceable(lower)) || (!solid(upper) && !replaceable(upper))
                    || !replaceable(upper.up()) || !replaceable(upper.up(2))) return;
            if (!mc.world.getWorldBorder().contains(lower) || !mc.world.getWorldBorder().contains(upper)) return;
            AxisAlignedBB box = mc.player.getEntityBoundingBox();
            if (box.intersects(new AxisAlignedBB(lower)) || box.intersects(new AxisAlignedBB(upper))) return;
            stage = 1;
            attempts = 0;
            }
        }
        if (stage==10) { descend(); return; }
        if (stage == 1) {
            if (solid(lower)) { releaseMovement(); stage = 2; attempts = 0; }
            else {
            // Lean over the supported edge until the adjacent side face is visible.
            moveToward(base.getX()+.5+direction.getFrontOffsetX()*.7,
                    base.getZ()+.5+direction.getFrontOffsetZ()*.7, true);
            if (delay == 0 && place(lower)) { releaseMovement(); delay = placementDelay; }
            if (++attempts > 100) { paused = true; releaseMovement(); }
            return;
            }
        }
        if (stage == 2) {
            if (solid(upper)) { stage = 3; attempts = 0; jumpRetries = 0; jumped = false; }
            else {
            if (!solid(lower)) { reset(); return; }
            if (mc.player.getEntityBoundingBox().intersects(new AxisAlignedBB(upper))) {
                moveToward(base.getX()+.5, base.getZ()+.5, false);
                return;
            }
            releaseMovement();
            if (delay == 0 && place(upper)) { delay = placementDelay; attempts++; }
            if (++attempts > 100) { paused = true; releaseMovement(); }
            return;
            }
        }
        if (!solid(lower) || !solid(upper)) { reset(); return; }
        if (mc.player.onGround && mc.player.posY >= landingY(upper) - .04D
                && mc.player.getDistanceSq(upper.getX() + .5D, mc.player.posY, upper.getZ() + .5D) < 1.5D) {
            // Finish at the tread centre before choosing the next support block.
            if (mc.player.getDistanceSq(upper.getX()+.5D,mc.player.posY,upper.getZ()+.5D) > .015D)
                moveToward(upper.getX()+.5D,upper.getZ()+.5D,false);
            else reset();
            return;
        }
        moveToward(upper.getX()+.5, upper.getZ()+.5, false);
        // Minecraft's jump cooldown can reject a one-frame input. Keep asking
        // until takeoff is observed, then release immediately for the landing.
        if (!mc.player.onGround) jumped = true;
        if (jumped && mc.player.onGround && mc.player.posY < landingY(upper)-.04D) {
            if (++jumpRetries > 2) { paused=true; releaseMovement(); return; }
            jumped=false;
        }
        if (++attempts > 100) { paused = true; releaseMovement(); }
    }

    private void moveToward(double x, double z, boolean sneak) {
        moveX=x; moveZ=z; walking=true; sneaking=sneak;
    }
    private void pauseWithReason(String reason) {
        stopReason=reason; paused=true; releaseMovement();
        if(mc.player!=null)mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                "Staircase: "+reason+". Right-click to resume."));
    }
    private void descend() {
        if (!solid(upper)) {
            BlockPos support=base.down();
            if (!solid(support)) {
                releaseMovement();
                if (!replaceable(support)) { pauseWithReason("Downward support obstructed"); return; }
                if (delay==0 && place(support,true)) delay=placementDelay;
                if(++attempts>60)pauseWithReason("Cannot place downward support");
                return;
            }
            // Never leave the current tread until a lower landing exists.
            // Existing walls/terrain may provide a visible side face; open air does not.
            moveToward(base.getX()+.5+direction.getFrontOffsetX()*.7,
                    base.getZ()+.5+direction.getFrontOffsetZ()*.7,true);
            if(delay==0 && place(upper,true)) { releaseMovement(); delay=placementDelay; }
            if(++attempts>60)pauseWithReason("No reachable solid face for descending");
            return;
        }
        double distance=mc.player.getDistanceSq(upper.getX()+.5,mc.player.posY,upper.getZ()+.5);
        if(mc.player.onGround && Math.abs(mc.player.posY-landingY(upper))<.06 && distance<.015) {
            reset(); return;
        }
        moveToward(upper.getX()+.5,upper.getZ()+.5,false);
        if(++attempts>120)pauseWithReason("Descent blocked");
    }
    @Override public void onInputUpdate(net.minecraftforge.client.event.InputUpdateEvent event) {
        if (!walking || paused || isAutomationBlockedByScreen() || mc.player == null) return;
        applyTimer();
        double dx=moveX-mc.player.posX, dz=moveZ-mc.player.posZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        double yaw=Math.toRadians(mc.player.rotationYaw);
        // Steer relative to the user's view. Never feed an automatically changed
        // camera heading back into Follow look.
        double speed=distance<=.10D?0D:Math.min(1D,distance*3D);
        if(sneaking) speed*=.3D;
        double ux=distance>0?dx/distance:0, uz=distance>0?dz/distance:0;
        event.getMovementInput().moveForward=(float)((-ux*Math.sin(yaw)+uz*Math.cos(yaw))*speed);
        event.getMovementInput().moveStrafe=(float)((ux*Math.cos(yaw)+uz*Math.sin(yaw))*speed);
        event.getMovementInput().sneak=sneaking;
        event.getMovementInput().jump=stage==3 && !jumped && mc.player.onGround
                && dx*dx+dz*dz<1.44D;
    }

    private double landingY(BlockPos pos) {
        AxisAlignedBB collision=mc.world.getBlockState(pos).getCollisionBoundingBox(mc.world,pos);
        return pos.getY()+(collision==null?1D:collision.maxY);
    }

    private boolean replaceable(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        return state.getMaterial().isReplaceable() && !state.getMaterial().isLiquid();
    }
    private boolean solid(BlockPos pos) {
        return mc.world.getBlockState(pos).isSideSolid(mc.world, pos, EnumFacing.UP);
    }
    private boolean place(BlockPos target) {
        return place(target,false);
    }
    private boolean place(BlockPos target,boolean supportedDescent) {
        if (!replaceable(target) || mc.player.getEntityBoundingBox().intersects(new AxisAlignedBB(target))) return false;
        int slot = InventoryBlocks.selectFullBlock(mc, mc.player.inventory.currentItem);
        if (slot < 0) { setEnabled(false); return false; }
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos support = target.offset(side);
            EnumFacing face = side.getOpposite();
            if (!mc.world.getBlockState(support).isSideSolid(mc.world,support,face)) continue;
            net.minecraft.util.math.Vec3d hit=InventoryBlocks.hitVec(support,face);
            net.minecraft.util.math.Vec3d eyes=mc.player.getPositionEyes(1F);
            if (eyes.squareDistanceTo(hit)>mc.playerController.getBlockReachDistance()*mc.playerController.getBlockReachDistance()) continue;
            net.minecraft.util.math.RayTraceResult ray=mc.world.rayTraceBlocks(eyes,hit.subtract(new net.minecraft.util.math.Vec3d(face.getDirectionVec()).scale(.001)),false,true,false);
            // Downward placement still requires an adjacent solid face and reach.
            // Servers may reject an occluded face; never predict acceptance locally.
            if (!supportedDescent && (ray==null || !support.equals(ray.getBlockPos()) || ray.sideHit!=face)) continue;
            mc.player.inventory.currentItem = slot;
            mc.playerController.updateController();
            net.minecraft.util.math.Vec3d aim=hit.subtract(eyes);
            float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z)));
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround));
            // Send the supported face interaction without controller-side block
            // prediction. Only incoming world updates can complete this placement.
            pending=target.toImmutable();
            pendingBlock=((net.minecraft.item.ItemBlock)mc.player.inventory.getStackInSlot(slot).getItem()).getBlock();
            pendingTicks=0;
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(
                    support,face,EnumHand.MAIN_HAND,(float)(hit.x-support.getX()),
                    (float)(hit.y-support.getY()),(float)(hit.z-support.getZ())));
            mc.player.swingArm(EnumHand.MAIN_HAND);
            return true;
        }
        return false;
    }
    private BlockPos supportUnderPlayer() {
        AxisAlignedBB box = mc.player.getEntityBoundingBox();
        int y = (int) Math.floor(box.minY - .05D);
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++)
            for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!solid(pos)) continue;
                double dx = x + .5D - mc.player.posX, dz = z + .5D - mc.player.posZ;
                double d = dx * dx + dz * dz;
                if (d < distance) { best = pos; distance = d; }
            }
        return best;
    }
    private void releaseMovement() {
        walking = false; sneaking=false; jumpRequested=false;
        restoreTimer();
    }
    private void applyTimer() {
        if (!useTimer || timerFailed || pending!=null) { restoreTimer(); return; }
        try {
            if (ownedTimer==null) {
                java.lang.reflect.Field timerField=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                        net.minecraft.client.Minecraft.class,"timer","field_71428_T");
                net.minecraft.util.Timer timer=(net.minecraft.util.Timer)timerField.get(mc);
                tickLengthField=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                        net.minecraft.util.Timer.class,"tickLength","field_194149_e");
                previousTickLength=tickLengthField.getFloat(timer);
                ownedTimer=timer;
            }
            tickLengthField.setFloat(ownedTimer,1000F/Math.max(10,Math.min(100,timerTickRate)));
        } catch (ReflectiveOperationException failure) {
            timerFailed=true;
            restoreTimer();
            org.apache.logging.log4j.LogManager.getLogger("Backspace").error("Staircase timer unavailable",failure);
        }
    }
    private void restoreTimer() {
        if (ownedTimer==null) return;
        try { tickLengthField.setFloat(ownedTimer,previousTickLength); }
        catch (IllegalAccessException failure) {
            org.apache.logging.log4j.LogManager.getLogger("Backspace").error("Could not restore Staircase timer",failure);
        }
        finally { ownedTimer=null; }
    }
    @Override public String hudTag() {
        return paused ? (stopReason.isEmpty()?"Paused":stopReason) : pending!=null ? "Awaiting server" : stage==10 ? "Descending "+direction : stage==3 ? "Climbing "+direction : "Building";
    }
    private void reset() { releaseMovement(); stage = 0; delay = 0; attempts = 0; lower = upper = null; pending=null; pendingBlock=null; pendingTicks=0; }
    @Override protected void onDisable() {
        releaseMovement();
        if (mc.player != null && originalSlot >= 0) {
            mc.player.inventory.currentItem = originalSlot;
            mc.playerController.updateController();
        }
        reset();
    }
}
