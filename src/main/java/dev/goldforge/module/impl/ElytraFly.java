package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;

/** Adjustable control and position-packet profiles for Elytra gliding. */
public final class ElytraFly extends Module {
    /** 0=Control, 1=Packet, 2=Control+Packet. */
    public int mode = 0;
    /** Target horizontal speed in blocks/tick, scaled by 100. */
    public int horizontalSpeed = 150;
    /** Horizontal steering acceleration, scaled by 1000. */
    public int steering = 30;
    /** Vertical input speed, scaled by 100. */
    public int verticalSpeed = 30;
    /** Packet-mode extra movement multiplier, scaled by 100. */
    public int packetMultiplier = 150;
    /** Vertical packet pulse, scaled by 10000. */
    public int packetPulse = 625;
    /** Integer-backed toggle for the movement packet onGround field. */
    public int spoofGround = 0;
    public boolean autoTakeoff=true, brakeOnRelease=true;
    private boolean launchArmed, jumpHeld;
    private int launchTicks, launchRequests, retryDelay, launchLiftTicks;
    private boolean wasGliding, leftGround;
    private String flightStatus="Ready";

    public ElytraFly() {
        super("ElytraFly", "Configurable Elytra steering and packet movement profiles.", Category.MOVEMENT, 0);
        addIntSetting("Auto takeoff",0,1,1,()->autoTakeoff?1:0,v->autoTakeoff=v!=0);
        addIntSetting("Brake on release",0,1,1,()->brakeOnRelease?1:0,v->brakeOnRelease=v!=0);
        addIntSetting("Experimental packet assist",0,1,1,()->mode==0?0:1,v->mode=v==0?0:2);
        addDoubleSetting("Flight speed (blocks/sec)", 4, 100, 1,
                () -> horizontalSpeed / 5.0, v -> horizontalSpeed = (int) Math.round(v * 5));
        addDoubleSetting("Turn acceleration", 0.001, 0.2, 0.001,
                () -> steering / 1000.0, v -> steering = (int) Math.round(v * 1000));
        addDoubleSetting("Up/down (blocks/sec)", 1, 40, 1,
                () -> verticalSpeed / 5.0, v -> verticalSpeed = (int) Math.round(v * 5));
        addDoubleSetting("Packet multiplier", 1, 4, 0.05,
                () -> packetMultiplier / 100.0, v -> packetMultiplier = (int) Math.round(v * 100));
        addDoubleSetting("Packet pulse", 0, 0.2, 0.0025,
                () -> packetPulse / 10000.0, v -> packetPulse = (int) Math.round(v * 10000));
        addIntSetting("Spoof onGround (0/1)", 0, 1, 1, () -> spoofGround, v -> spoofGround = v);
    }

    @Override
    public String hudTag() {
        return flightStatus;
    }
    @Override protected void onEnable(){launchArmed=true;jumpHeld=false;launchTicks=0;launchRequests=0;retryDelay=0;wasGliding=false;leftGround=false;launchLiftTicks=0;}
    @Override protected void onDisable(){launchArmed=false;launchTicks=0;launchRequests=0;flightStatus="Ready";}

    @Override
    public void tick() {
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || mc.getConnection() == null) return;
        if(isAutomationBlockedByScreen()){flightStatus="Inventory open";return;}
        net.minecraft.item.ItemStack chest=player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST);
        if(chest.getItem()!=net.minecraft.init.Items.ELYTRA || !net.minecraft.item.ItemElytra.isUsable(chest)){
            flightStatus="Equip usable Elytra";return;
        }
        boolean jump=mc.gameSettings.keyBindJump.isKeyDown();
        if(jump&&!jumpHeld&&!player.isElytraFlying()&&!launchArmed){launchArmed=true;launchRequests=0;launchTicks=0;retryDelay=0;leftGround=false;}
        jumpHeld=jump;
        if(!player.isElytraFlying()){
            wasGliding=false;launchLiftTicks=0;
            if(!autoTakeoff){flightStatus="Jump, then press Jump again";return;}
            if(player.isInWater()||player.isInLava()||player.isRiding()||player.capabilities.isFlying){
                flightStatus="Cannot launch here";return;
            }
            if(!launchArmed){flightStatus="Press Jump to launch";return;}
            if(++launchTicks>60){launchArmed=false;flightStatus="Launch not accepted";return;}
            if(player.onGround){
                if(leftGround){launchArmed=false;flightStatus="Launch rejected: release/repress Jump";return;}
                if(launchTicks==1){
                    if(!mc.world.getCollisionBoxes(player,player.getEntityBoundingBox().offset(0,1,0)).isEmpty()){
                        launchArmed=false;flightStatus="Need headroom";return;
                    }
                    player.jump();
                }
                flightStatus="Taking off";return;
            }
            leftGround=true;
            if(retryDelay>0)retryDelay--;
            if(player.motionY<0 && retryDelay==0 && launchRequests<8){
                PacketManager.send(new net.minecraft.network.play.client.CPacketEntityAction(player,
                        net.minecraft.network.play.client.CPacketEntityAction.Action.START_FALL_FLYING),name);
                launchRequests++;retryDelay=2;
            }
            flightStatus="Opening Elytra";return;
        }
        if(!wasGliding && launchArmed)launchLiftTicks=8;
        wasGliding=true;
        launchArmed=false;launchTicks=0;launchRequests=0;
        flightStatus=mode==0?"Control":"Control + packet";

        int selectedMode = clamp(mode, 0, 2);
        applyControl(player);
        if(launchLiftTicks>0){
            launchLiftTicks--;
            if(!mc.gameSettings.keyBindSneak.isKeyDown())player.motionY=Math.max(player.motionY,.15D);
        }
        if (selectedMode == 1 || selectedMode == 2) applyPacketProfile(player);
    }

    @Override public void onInputUpdate(net.minecraftforge.client.event.InputUpdateEvent event){
        // Consume held Space during launch so vanilla cannot repeatedly jump on
        // landing. The launch state machine reads the physical key separately.
        if(autoTakeoff && mc.player!=null && !mc.player.isElytraFlying() && !isAutomationBlockedByScreen()){
            net.minecraft.item.ItemStack chest=mc.player.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST);
            if(chest.getItem()==net.minecraft.init.Items.ELYTRA && net.minecraft.item.ItemElytra.isUsable(chest))
                event.getMovementInput().jump=false;
        }
    }

    private void applyControl(EntityPlayerSP player) {
        if (player.movementInput == null) return;
        double forward = player.movementInput.moveForward;
        double strafe = player.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);

        if (length > 1.0D) {
            forward /= length;
            strafe /= length;
        }
        if(length>1.0E-4D || brakeOnRelease) {
            double yaw = Math.toRadians(player.rotationYaw);
            double wishX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
            double wishZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
            double targetSpeed = clamp(horizontalSpeed, 20, 500) / 100.0D;
            double acceleration = clamp(steering, 1, 200) / 1000.0D;
            double targetX = wishX * targetSpeed;
            double targetZ = wishZ * targetSpeed;
            double deltaX = targetX - player.motionX;
            double deltaZ = targetZ - player.motionZ;
            double deltaLength = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (deltaLength > acceleration && deltaLength > 1.0E-6D) {
                double scale = acceleration / deltaLength;
                deltaX *= scale;
                deltaZ *= scale;
            }
            player.motionX += deltaX;
            player.motionZ += deltaZ;
        }

        double vertical = clamp(verticalSpeed, 5, 200) / 100.0D;
        player.motionY = player.movementInput.jump ? vertical : player.movementInput.sneak ? -vertical : 0D;
    }

    private void applyPacketProfile(EntityPlayerSP player) {
        double multiplier = clamp(packetMultiplier, 100, 400) / 100.0D;
        double extra = multiplier - 1.0D;
        if (extra > 0.0D) {
            player.setPosition(player.posX + player.motionX * extra,
                    player.posY + player.motionY * extra,
                    player.posZ + player.motionZ * extra);
        }

        boolean onGround = spoofGround != 0;
        double pulse = clamp(packetPulse, 0, 2000) / 10000.0D;
        if (pulse > 0.0D) {
            PacketManager.send(new CPacketPlayer.Position(player.posX, player.posY + pulse, player.posZ, onGround), name);
        }
        PacketManager.send(new CPacketPlayer.Position(player.posX, player.posY, player.posZ, onGround), name);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

