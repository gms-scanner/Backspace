package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.inventory.InventoryManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Keeps a totem in the offhand and completes golden-apple use at low health. */
public final class AutoTotem extends Module {
    public int advanced=0, gapHealth=14, threatRadius=10,offhandCrystal=0,totemHealth=12;
    public int gapCooldownTicks=10,enchantedHealth=6;
    private boolean startedGap;
    private boolean forcedUse;
    private int scanDelay,gapRetry,gapReturnSlot=-1,gapHotbarSlot=-1,gapInventorySlot=-1;
    private int gapPrepareTicks,gapUseTicks,gapStartCount,gapCooldown;
    private Threat cachedThreat=new Threat(0D,false,false);

    public AutoTotem(){
        super("AutoTotem","Automatically equips an offhand totem; Advanced mode completes a gap at low health.",Category.PLAYER,0);
        addIntSetting("Advanced (0/1)",0,1,1,()->advanced,v->advanced=v);
        addIntSetting("Gap health",4,20,1,()->gapHealth,v->gapHealth=v);
        addIntSetting("Threat radius",4,16,1,()->threatRadius,v->threatRadius=v);
        addIntSetting("Offhand crystal (0/1)",0,1,1,()->offhandCrystal,v->offhandCrystal=v);
        addIntSetting("Totem health",4,20,1,()->totemHealth,v->totemHealth=v);
        addIntSetting("Gap cooldown (ticks)",0,80,1,()->gapCooldownTicks,v->gapCooldownTicks=v);
        addIntSetting("Enchanted gap health",2,20,1,()->enchantedHealth,v->enchantedHealth=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null){stopGap();return;}
        if(gapCooldown>0)gapCooldown--;
        if(gapRetry>0)gapRetry--;
        if(scanDelay--<=0){cachedThreat=assessThreat();scanDelay=2;}
        double health=mc.player.getHealth()+mc.player.getAbsorptionAmount();
        boolean danger=health<=totemHealth||cachedThreat.lethal||cachedThreat.imminent
                ||(cachedThreat.damage>0D&&cachedThreat.damage+2D>=health);
        ItemStack offhand=mc.player.getHeldItemOffhand();
        boolean needsGap=advanced!=0&&(mc.player.getHealth()<=gapHealth||startedGap||gapReturnSlot>=0);
        if(danger||offhandCrystal==0||needsGap){
            if(offhand.getItem()!=Items.TOTEM_OF_UNDYING){stopGap();if(findAndEquip(Items.TOTEM_OF_UNDYING))return;}
        }else if(offhand.getItem()!=Items.END_CRYSTAL){
            stopGap();
            if(findAndEquip(Items.END_CRYSTAL))return;
        }
        if(advanced==0){stopGap();return;}
        if(isAutomationBlockedByScreen()){stopGap();return;}
        if(startedGap||gapReturnSlot>=0){continueGap();return;}
        if(needsGap&&gapCooldown==0&&gapRetry==0)beginGap();
    }

    private boolean findAndEquip(net.minecraft.item.Item item){
        int slot=InventoryManager.findItem(mc,item);if(slot<0)return false;
        int hotbar=slot<9?slot:mc.player.inventory.currentItem;
        if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar))return false;
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId,45,hotbar,ClickType.SWAP,mc.player);return true;
    }

    private void beginGap(){
        if(mc.player.isHandActive())return;
        int slot=findApple();
        if(slot<0)return;
        gapReturnSlot=mc.player.inventory.currentItem;
        gapHotbarSlot=slot<9?slot:freeHotbarSlot();
        if(slot>=9){
            if(mc.player.openContainer!=mc.player.inventoryContainer){clearGapState();return;}
            gapInventorySlot=slot;
            mc.playerController.windowClick(mc.player.inventoryContainer.windowId,slot,gapHotbarSlot,ClickType.SWAP,mc.player);
            gapPrepareTicks=2; // Let the inventory transaction settle before using the apple.
            return;
        }
        startEating();
    }

    private void continueGap(){
        if(startedGap){
            boolean active=mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND
                    &&mc.player.getHeldItemMainhand().getItem()==Items.GOLDEN_APPLE;
            if(active){gapUseTicks++;holdUse();return;}
            int remaining=gapHotbarSlot<0?0:mc.player.inventory.getStackInSlot(gapHotbarSlot).getCount();
            boolean consumed=remaining<gapStartCount||gapUseTicks>=30;
            finishGap(consumed);
            return;
        }
        if(gapPrepareTicks>0){gapPrepareTicks--;return;}
        if(gapHotbarSlot>=0&&mc.player.inventory.getStackInSlot(gapHotbarSlot).getItem()==Items.GOLDEN_APPLE){
            startEating();return;
        }
        finishGap(false);gapRetry=5;
    }

    private void startEating(){
        if(mc.player.isHandActive()||gapHotbarSlot<0)return;
        mc.player.inventory.currentItem=gapHotbarSlot;
        mc.playerController.updateController();
        ItemStack apple=mc.player.getHeldItemMainhand();
        if(apple.getItem()!=Items.GOLDEN_APPLE){finishGap(false);gapRetry=3;return;}
        gapStartCount=apple.getCount();
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        startedGap=mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND
                &&mc.player.getHeldItemMainhand().getItem()==Items.GOLDEN_APPLE;
        if(startedGap){gapUseTicks=0;holdUse();}
        else {finishGap(false);gapRetry=3;}
    }

    private int findApple(){
        boolean enchanted=mc.player.getHealth()<=enchantedHealth;
        for(int pass=0;pass<2;pass++)for(int slot=0;slot<36;slot++){
            ItemStack stack=mc.player.inventory.getStackInSlot(slot);
            if(stack.isEmpty()||stack.getItem()!=Items.GOLDEN_APPLE)continue;
            if((stack.getMetadata()>0)==(pass==0?enchanted:!enchanted))return slot;
        }
        return -1;
    }

    private int freeHotbarSlot(){
        for(int slot=0;slot<9;slot++)if(mc.player.inventory.getStackInSlot(slot).isEmpty())return slot;
        return mc.player.inventory.currentItem;
    }

    private void holdUse(){
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),true);
        forcedUse=true;
    }

    private void releaseUse(){
        if(!forcedUse)return;
        int key=mc.gameSettings.keyBindUseItem.getKeyCode();
        boolean physical=key>=0?Keyboard.isKeyDown(key):key>=-100&&key<=-85&&Mouse.isButtonDown(key+100);
        KeyBinding.setKeyBindState(key,physical);
        forcedUse=false;
    }

    private void finishGap(boolean consumed){
        releaseUse();
        restoreGapInventory();
        clearGapState();
        if(consumed)gapCooldown=Math.max(0,gapCooldownTicks);
        else gapRetry=Math.max(gapRetry,3);
    }

    private void restoreGapInventory(){
        if(mc.player==null)return;
        if(gapInventorySlot>=9&&gapHotbarSlot>=0&&mc.playerController!=null
                &&mc.player.openContainer==mc.player.inventoryContainer){
            ItemStack hotbar=mc.player.inventory.getStackInSlot(gapHotbarSlot);
            if(hotbar.isEmpty()||hotbar.getItem()==Items.GOLDEN_APPLE)
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId,
                        gapInventorySlot,gapHotbarSlot,ClickType.SWAP,mc.player);
        }
        if(gapReturnSlot>=0&&gapHotbarSlot==mc.player.inventory.currentItem)
            mc.player.inventory.currentItem=gapReturnSlot;
    }

    private void clearGapState(){
        startedGap=false;gapReturnSlot=gapHotbarSlot=gapInventorySlot=-1;
        gapPrepareTicks=gapUseTicks=gapStartCount=0;
    }

    private void stopGap(){
        if(startedGap&&mc.player!=null&&mc.playerController!=null&&mc.player.isHandActive()
                &&mc.player.getActiveHand()==EnumHand.MAIN_HAND
                &&mc.player.getHeldItemMainhand().getItem()==Items.GOLDEN_APPLE)
            mc.playerController.onStoppedUsingItem(mc.player);
        releaseUse();restoreGapInventory();clearGapState();
    }

    /** Multiple warning signals: live crystal damage, likely opponent placements, and nearby held crystals. */
    private Threat assessThreat(){
        double worst=0;boolean imminent=false,hasCrystal=false,armedOpponent=false;
        EntityPlayer self=mc.player;
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityEnderCrystal)||self.getDistance(entity)>threatRadius)continue;
            hasCrystal=true;
            double damage=explosionDamage(entity.posX,entity.posY,entity.posZ,self);
            worst=Math.max(worst,damage);
            if(damage>=self.getHealth()+self.getAbsorptionAmount()-1.0D)imminent=true;
        }
        // Check exposed obsidian/bedrock bases near enemies holding crystals. This estimates a threat before a crystal entity exists.
        for(EntityPlayer enemy:mc.world.playerEntities){
            if(enemy==self||enemy.isDead||enemy.getDistance(self)>threatRadius+3||!holdsCrystal(enemy))continue;
            armedOpponent=true;
            BlockPos center=new BlockPos(enemy.posX,enemy.posY,enemy.posZ);
            for(BlockPos base:BlockPos.getAllInBox(center.add(-2,-1,-2),center.add(2,1,2))){
                if(!crystalBase(base))continue;
                Vec3d explosion=new Vec3d(base.getX()+.5D,base.getY()+1D,base.getZ()+.5D);
                if(enemy.getPositionEyes(1F).squareDistanceTo(explosion)>36D)continue;
                double damage=explosionDamage(explosion.x,explosion.y,explosion.z,self);
                worst=Math.max(worst,damage*.82D); // Slightly discount potential placement for the player's exact timing.
                if(damage>=self.getHealth()+self.getAbsorptionAmount()-1.0D)imminent=true;
            }
        }
        // Nearby crystal users are a separate signal when the base is outside our compact search area.
        for(EntityPlayer enemy:mc.world.playerEntities)if(enemy!=self&&!enemy.isDead&&enemy.getDistance(self)<6&&holdsCrystal(enemy)){armedOpponent=true;break;}
        boolean popRisk=(hasCrystal||armedOpponent)&&worst>=Math.max(3D,(self.getHealth()+self.getAbsorptionAmount())*.55D);
        return new Threat(worst,imminent,imminent||popRisk);
    }

    private boolean holdsCrystal(EntityPlayer player){
        return player.getHeldItemMainhand().getItem()==Items.END_CRYSTAL||player.getHeldItemOffhand().getItem()==Items.END_CRYSTAL;
    }

    private boolean crystalBase(BlockPos pos){
        if(mc.world.getBlockState(pos).getBlock()!=Blocks.OBSIDIAN&&mc.world.getBlockState(pos).getBlock()!=Blocks.BEDROCK)return false;
        if(!mc.world.isAirBlock(pos.up())||!mc.world.isAirBlock(pos.up(2)))return false;
        return mc.world.getEntitiesWithinAABBExcludingEntity(null,new net.minecraft.util.math.AxisAlignedBB(pos.up())).isEmpty();
    }

    private double explosionDamage(double x,double y,double z,EntityLivingBase target){
        double distance=target.getDistance(x,y,z)/12D;if(distance>1D)return 0D;
        double exposure=mc.world.getBlockDensity(new Vec3d(x,y,z),target.getEntityBoundingBox());
        double impact=(1D-distance)*exposure;
        float raw=(float)((impact*impact+impact)*24D+1D);
        Explosion explosion=new Explosion(mc.world,null,x,y,z,6F,false,true);
        raw=CombatRules.getDamageAfterAbsorb(raw,target.getTotalArmorValue(),(float)target.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        int protection=EnchantmentHelper.getEnchantmentModifierDamage(target.getArmorInventoryList(),DamageSource.causeExplosionDamage(explosion));
        raw*=1F-MathHelper.clamp(protection,0,20)/25F;
        if(target.isPotionActive(Potion.getPotionById(11)))raw*=.25F;
        return Math.max(0D,raw);
    }

    @Override protected void onDisable(){stopGap();scanDelay=gapRetry=gapCooldown=0;cachedThreat=new Threat(0D,false,false);}

    private static final class Threat{
        final double damage;final boolean lethal,imminent;
        Threat(double damage,boolean lethal,boolean imminent){this.damage=damage;this.lethal=lethal;this.imminent=imminent;}
    }
}

