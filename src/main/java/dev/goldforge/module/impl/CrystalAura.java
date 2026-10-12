package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.combat.CrystalActionTracker;
import dev.goldforge.combat.CrystalDamage;
import dev.goldforge.combat.CrystalMath;
import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.network.PacketManager;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Fresh world planning and independent, acknowledged place/break execution on the client thread. */
public final class CrystalAura extends Module {
    private static final Logger LOGGER = LogManager.getLogger("Backspace/CrystalAura");
    // Retain existing public field names so saved profiles continue to load.
    public int profile=1, actionDelay=0, breakRange=4, aimOnly=0, targetRange=12, minDamage=4, maxSelfDamage=8, attacksPerTick=1;
    public int faceplace=1, faceplaceHealth=8, faceplaceArmorPercent=22, faceplaceMinDamage=2, friendFilter=1;
    public int place=1, breaking=1, placeRange=4, placeDelay=0, breakDelay=0, placeWallsRange=0, breakWallsRange=0;
    public int rotate=1, swapMode=2, antiWeakness=1, pauseUsing=1, pauseMining=1;
    public int predictionTicks=2, selfPredictionTicks=1, healthReserve=2, damageRatio=0, protectFriends=1;
    public int retryTicks=2, retryLimit=3, acknowledgementTicks=4, maxPending=2, minCrystalAge=0;
    public int multiTask=1, maxTargets=4, renderDamage=1, debug=0;
    private final CrystalActionTracker<Integer> breakAttempts = new CrystalActionTracker<Integer>();
    private final CrystalActionTracker<BlockPos> placeAttempts = new CrystalActionTracker<BlockPos>();
    private final Map<Vec3d, Evaluation> damageCache = new HashMap<Vec3d, Evaluation>();
    private final Map<EntityPlayer, Double> committedDamage = new HashMap<EntityPlayer, Double>();
    private final List<CrystalDamage.Snapshot> targets = new ArrayList<CrystalDamage.Snapshot>();
    private final List<CrystalDamage.Snapshot> friends = new ArrayList<CrystalDamage.Snapshot>();
    private final List<EntityEnderCrystal> crystals = new ArrayList<EntityEnderCrystal>();
    private CrystalDamage.Snapshot self;
    private World activeWorld;
    private long clientTicks, nextPlaceTick, nextBreakTick;
    private EntityLivingBase renderTarget;
    private List<CrystalLabel> crystalLabels=Collections.emptyList();
    private long placeSends, breakSends, placeConfirms;
    private int exposureCandidates;
    private String state="Idle";

    public CrystalAura() {
        super("CrystalAura","Predicts crystal damage and independently places and breaks with confirmed retries.",Category.COMBAT,0);
        addIntSetting("Profile (0 Strict, 1 HvH)",0,1,1,()->profile,v->profile=v);
        addIntSetting("Place crystals",0,1,1,()->place,v->place=v);
        addIntSetting("Break crystals",0,1,1,()->breaking,v->breaking=v);
        addIntSetting("Place range",2,6,1,()->placeRange,v->placeRange=v);
        addIntSetting("Break range",2,6,1,()->breakRange,v->breakRange=v);
        addIntSetting("Place walls range",0,6,1,()->placeWallsRange,v->placeWallsRange=v);
        addIntSetting("Break walls range",0,6,1,()->breakWallsRange,v->breakWallsRange=v);
        addIntSetting("Target range",4,16,1,()->targetRange,v->targetRange=v);
        addIntSetting("Targets to evaluate",1,8,1,()->maxTargets,v->maxTargets=v);
        addIntSetting("Place delay (ticks)",0,20,1,()->placeDelay,v->placeDelay=v);
        addIntSetting("Break delay (ticks)",0,20,1,()->breakDelay,v->breakDelay=v);
        addIntSetting("Extra action delay (ticks)",0,10,1,()->actionDelay,v->actionDelay=v);
        addIntSetting("Break and place together",0,1,1,()->multiTask,v->multiTask=v);
        addIntSetting("Breaks per tick (HvH)",1,4,1,()->attacksPerTick,v->attacksPerTick=v);
        addIntSetting("Break retry (ticks)",1,20,1,()->retryTicks,v->retryTicks=v);
        addIntSetting("Retry attempts before backoff",1,6,1,()->retryLimit,v->retryLimit=v);
        addIntSetting("Spawn timeout minimum (ticks)",2,40,1,()->acknowledgementTicks,v->acknowledgementTicks=v);
        addIntSetting("Pending placements (HvH)",1,4,1,()->maxPending,v->maxPending=v);
        addIntSetting("Minimum crystal age (ticks)",0,20,1,()->minCrystalAge,v->minCrystalAge=v);
        addIntSetting("Aim-only break",0,1,1,()->aimOnly,v->aimOnly=v);
        addIntSetting("Rotate (0 Off, 1 Silent, 2 Camera)",0,2,1,()->rotate,v->rotate=v);
        addIntSetting("Swap (0 Held, 1 Select, 2 Restore)",0,2,1,()->swapMode,v->swapMode=v);
        addIntSetting("Anti weakness",0,1,1,()->antiWeakness,v->antiWeakness=v);
        addIntSetting("Pause while using item",0,1,1,()->pauseUsing,v->pauseUsing=v);
        addIntSetting("Pause while mining",0,1,1,()->pauseMining,v->pauseMining=v);
        addIntSetting("Target prediction (ticks)",0,5,1,()->predictionTicks,v->predictionTicks=v);
        addIntSetting("Self prediction (ticks)",0,5,1,()->selfPredictionTicks,v->selfPredictionTicks=v);
        addIntSetting("Minimum damage",1,20,1,()->minDamage,v->minDamage=v);
        addIntSetting("Maximum self damage",1,20,1,()->maxSelfDamage,v->maxSelfDamage=v);
        addIntSetting("Health reserve",0,10,1,()->healthReserve,v->healthReserve=v);
        addIntSetting("Damage / self ratio (%)",0,300,25,()->damageRatio,v->damageRatio=v);
        addIntSetting("Faceplace",0,1,1,()->faceplace,v->faceplace=v);
        addIntSetting("Faceplace health",2,20,1,()->faceplaceHealth,v->faceplaceHealth=v);
        addIntSetting("Faceplace armor durability (%)",5,60,5,()->faceplaceArmorPercent,v->faceplaceArmorPercent=v);
        addIntSetting("Faceplace minimum damage",1,8,1,()->faceplaceMinDamage,v->faceplaceMinDamage=v);
        addIntSetting("Ignore friends",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Protect friends from explosions",0,1,1,()->protectFriends,v->protectFriends=v);
        addIntSetting("Render plan and damage",0,1,1,()->renderDamage,v->renderDamage=v);
        addIntSetting("Debug logging",0,1,1,()->debug,v->debug=v);
    }

    @Override public String hudTag(){return profile==0?"[Strict]":"[HvH]";}

    @Override public void tick() {
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.getConnection()==null){reset();return;}
        if(activeWorld!=mc.world){reset();activeWorld=mc.world;}
        clientTicks++;
        // Configuration can also be loaded reflectively from edited profile files.
        clampSettings();
        observeCrystals();
        damageCache.clear();committedDamage.clear();targets.clear();friends.clear();exposureCandidates=0;
        crystalLabels=Collections.emptyList();renderTarget=null;
        if(mc.player.isDead||mc.player.getHealth()<=0F){reset();return;}
        if(mc.player.isSpectator()
                ||mc.currentScreen!=null||(pauseUsing!=0&&mc.player.isHandActive())
                ||(pauseMining!=0&&mc.playerController.getIsHittingBlock())){
            state="Paused";return;
        }
        collectTargets();
        if(targets.isEmpty()){state="No target";return;}
        self=new CrystalDamage.Snapshot(mc.player,selfPredictionTicks);
        long start=System.nanoTime();
        List<AttackPlan> attacks=planBreaks();
        List<CrystalLabel> labels=new ArrayList<CrystalLabel>();
        boolean attacked=false;
        if(breaking!=0&&clientTicks>=nextBreakTick){
            int budget=profile==0?1:attacksPerTick;
            for(AttackPlan plan:attacks){
                if(budget--<=0)break;
                if(breakCrystal(plan)){
                    attacked=true;
                    renderTarget=plan.evaluation.target.player;
                    labels.add(new CrystalLabel(plan.crystal,null,plan.evaluation.damage,false));
                }
            }
            if(attacked)nextBreakTick=clientTicks+1+effectiveDelay(breakDelay);
        }
        // The controller retains crystals until the server removes them. Occupied bases remain blocked.
        PlacePlan placement=null;
        if(place!=0&&hasAvailableCrystals()){
            placement=planPlacement();
            if(placement!=null){
                if(renderTarget==null)renderTarget=placement.evaluation.target.player;
                labels.add(new CrystalLabel(null,placement.base,placement.evaluation.damage,true));
                if(clientTicks>=nextPlaceTick&&(!attacked||(profile==1&&multiTask!=0))
                        &&placeAttempts.pending(clientTicks)<(profile==0?1:maxPending)){
                    placeCrystal(placement);
                }
            }
        }
        if(!attacked&&placement==null)state="No safe action";
        if(renderDamage!=0)crystalLabels=labels;
        if(debug!=0&&clientTicks%40==0)LOGGER.info(
                "state={} placeSent={} spawnConfirmed={} breakSent={} pending={} candidates={} planMs={}",
                state,placeSends,placeConfirms,breakSends,placeAttempts.pending(clientTicks),
                exposureCandidates,String.format(Locale.ROOT,"%.2f",(System.nanoTime()-start)/1_000_000D));
    }

    private void clampSettings(){
        profile=clamp(profile,0,1);actionDelay=clamp(actionDelay,0,10);
        placeRange=clamp(placeRange,2,6);breakRange=clamp(breakRange,2,6);targetRange=clamp(targetRange,4,16);
        placeWallsRange=clamp(placeWallsRange,0,placeRange);breakWallsRange=clamp(breakWallsRange,0,breakRange);
        placeDelay=clamp(placeDelay,0,20);breakDelay=clamp(breakDelay,0,20);
        attacksPerTick=clamp(attacksPerTick,1,4);maxTargets=clamp(maxTargets,1,8);maxPending=clamp(maxPending,1,4);
        retryTicks=clamp(retryTicks,1,20);retryLimit=clamp(retryLimit,1,6);acknowledgementTicks=clamp(acknowledgementTicks,2,40);
        rotate=clamp(rotate,0,2);swapMode=clamp(swapMode,0,2);minCrystalAge=clamp(minCrystalAge,0,20);
        predictionTicks=clamp(predictionTicks,0,5);selfPredictionTicks=clamp(selfPredictionTicks,0,5);
        minDamage=clamp(minDamage,1,20);maxSelfDamage=clamp(maxSelfDamage,1,20);
        healthReserve=clamp(healthReserve,0,10);damageRatio=clamp(damageRatio,0,300);
        faceplaceHealth=clamp(faceplaceHealth,2,20);faceplaceArmorPercent=clamp(faceplaceArmorPercent,5,60);
        faceplaceMinDamage=clamp(faceplaceMinDamage,1,8);
    }

    private void observeCrystals(){
        crystals.clear();
        Set<Integer> live=new HashSet<Integer>();
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityEnderCrystal)||entity.isDead)continue;
            EntityEnderCrystal crystal=(EntityEnderCrystal)entity;
            crystals.add(crystal);live.add(crystal.getEntityId());
            BlockPos base=new BlockPos(crystal.posX,crystal.posY-1D,crystal.posZ);
            if(placeAttempts.confirm(base))placeConfirms++;
        }
        breakAttempts.retain(live);
        placeAttempts.prune(clientTicks);
    }

    private void collectTargets(){
        List<EntityPlayer> eligible=new ArrayList<EntityPlayer>();
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player||player.isDead||player.getHealth()<=0F||player.isSpectator()||player.isCreative())continue;
            boolean friend=FriendManager.isFriend(player.getName());
            if(friend&&protectFriends!=0&&mc.player.getDistanceSq(player)<=324D)
                friends.add(new CrystalDamage.Snapshot(player,selfPredictionTicks));
            if(friend&&friendFilter!=0)continue;
            if(mc.player.getDistanceSq(player)<=targetRange*targetRange)eligible.add(player);
        }
        eligible.sort(Comparator.comparingDouble((EntityPlayer p)->mc.player.getDistanceSq(p)).thenComparingInt(Entity::getEntityId));
        for(int i=0;i<Math.min(maxTargets,eligible.size());i++)targets.add(new CrystalDamage.Snapshot(eligible.get(i),predictionTicks));
    }

    private Evaluation evaluate(Vec3d explosion){
        if(damageCache.containsKey(explosion))return damageCache.get(explosion);
        exposureCandidates++;
        double own=Math.max(self.damage(mc.world,explosion,false),self.damage(mc.world,explosion,true));
        Evaluation best=null;
        if(CrystalMath.safe(own,self.health,maxSelfDamage,healthReserve)){
            boolean friendSafe=true;
            for(CrystalDamage.Snapshot friend:friends){
                double value=Math.max(friend.damage(mc.world,explosion,false),friend.damage(mc.world,explosion,true));
                if(!CrystalMath.safe(value,friend.health,maxSelfDamage,healthReserve)){friendSafe=false;break;}
            }
            if(friendSafe)for(CrystalDamage.Snapshot target:targets){
                double minimum=minimumDamage(target.player);
                if(!CrystalMath.qualifies(target.maximumPossible(explosion),own,target.health,minimum,damageRatio/100D))continue;
                double dealt=target.damage(mc.world,explosion,true);
                if(!CrystalMath.qualifies(dealt,own,target.health,minimum,damageRatio/100D))continue;
                double score=CrystalMath.score(dealt,own,target.health,self.position.distanceTo(explosion));
                if(best==null||score>best.score)best=new Evaluation(target,dealt,own,score);
            }
        }
        damageCache.put(explosion,best);
        return best;
    }

    private List<AttackPlan> planBreaks(){
        List<AttackPlan> result=new ArrayList<AttackPlan>();
        if(breaking==0)return result;
        for(EntityEnderCrystal crystal:crystals){
            if(crystal.ticksExisted<minCrystalAge||!breakAttempts.ready(crystal.getEntityId(),clientTicks))continue;
            if(aimOnly!=0&&(mc.objectMouseOver==null||mc.objectMouseOver.entityHit!=crystal))continue;
            Vec3d aim=breakAim(crystal);
            if(aim==null)continue;
            Evaluation evaluation=evaluate(crystal.getPositionVector());
            if(evaluation!=null)result.add(new AttackPlan(crystal,aim,evaluation));
        }
        result.sort(Comparator.comparingDouble((AttackPlan p)->-p.evaluation.score).thenComparingInt(p->p.crystal.getEntityId()));
        return result;
    }

    private PlacePlan planPlacement(){
        Vec3d eye=mc.player.getPositionEyes(1F);
        double reach=Math.min(placeRange,mc.playerController.getBlockReachDistance());
        BlockPos origin=new BlockPos(eye);
        int radius=(int)Math.ceil(reach);
        PlacePlan best=null;
        for(BlockPos mutable:BlockPos.getAllInBox(origin.add(-radius,-radius-1,-radius),origin.add(radius,radius,radius))){
            BlockPos base=mutable.toImmutable();
            Vec3d center=new Vec3d(base.getX()+.5D,base.getY()+1D,base.getZ()+.5D);
            // Reject out-of-reach cubes before damage density ray sampling.
            if(eye.squareDistanceTo(center)>(reach+.75D)*(reach+.75D))continue;
            if(!placeAttempts.ready(base,clientTicks)||!validBase(base))continue;
            PlacementHit hit=placementHit(base);
            if(hit==null)continue;
            Evaluation value=evaluate(center);
            if(value==null)continue;
            if(best==null||value.score>best.evaluation.score
                    ||(value.score==best.evaluation.score&&base.toLong()<best.base.toLong()))
                best=new PlacePlan(base,value);
        }
        return best;
    }

    private boolean validBase(BlockPos base){
        if(base.getY()<0||base.getY()>253||!mc.world.isBlockLoaded(base)
                ||!mc.world.getWorldBorder().contains(base))return false;
        Block block=mc.world.getBlockState(base).getBlock();
        if(block!=Blocks.OBSIDIAN&&block!=Blocks.BEDROCK)return false;
        if(!mc.world.isAirBlock(base.up())||!mc.world.isAirBlock(base.up(2)))return false;
        // ItemEndCrystal checks this entire 1 x 2 x 1 column in 1.12.2.
        AxisAlignedBB volume=new AxisAlignedBB(base.getX(),base.getY()+1,base.getZ(),base.getX()+1,base.getY()+3,base.getZ()+1);
        return mc.world.getEntitiesWithinAABBExcludingEntity(null,volume).isEmpty();
    }

    private PlacementHit placementHit(BlockPos base){
        Vec3d eye=mc.player.getPositionEyes(1F);
        double reach=Math.min(placeRange,mc.playerController.getBlockReachDistance());
        PlacementHit best=null;
        double bestDistance=Double.MAX_VALUE;
        for(EnumFacing face:EnumFacing.values()){
            Vec3d point=new Vec3d(base).addVector(.5D+face.getFrontOffsetX()*.5D,.5D+face.getFrontOffsetY()*.5D,.5D+face.getFrontOffsetZ()*.5D);
            double distance=eye.squareDistanceTo(point);
            if(distance>reach*reach)continue;
            // Trace slightly into the face to avoid floating-point edge misses.
            Vec3d inside=point.addVector(-face.getFrontOffsetX()*.001D,-face.getFrontOffsetY()*.001D,-face.getFrontOffsetZ()*.001D);
            RayTraceResult trace=mc.world.rayTraceBlocks(eye,inside,false,true,false);
            if(trace!=null&&trace.typeOfHit==RayTraceResult.Type.BLOCK&&base.equals(trace.getBlockPos())&&trace.sideHit==face&&distance<bestDistance){
                best=new PlacementHit(face,point);bestDistance=distance;
            }
        }
        if(best!=null)return best;
        Vec3d top=new Vec3d(base).addVector(.5D,1D,.5D);
        double walls=profile==0?0D:Math.min(placeWallsRange,reach);
        return walls>0D&&eye.squareDistanceTo(top)<=walls*walls?new PlacementHit(EnumFacing.UP,top):null;
    }

    private Vec3d breakAim(EntityEnderCrystal crystal){
        Vec3d eye=mc.player.getPositionEyes(1F);
        AxisAlignedBB b=crystal.getEntityBoundingBox();
        Vec3d closest=new Vec3d(MathHelper.clamp(eye.x,b.minX+.05D,b.maxX-.05D),
                MathHelper.clamp(eye.y,b.minY+.05D,b.maxY-.05D),MathHelper.clamp(eye.z,b.minZ+.05D,b.maxZ-.05D));
        // Use the hitbox for aim; enforce origin range too, matching the server's use-entity distance check.
        double serverRangeSq=mc.player.canEntityBeSeen(crystal)?36D:9D;
        if(mc.player.getDistanceSq(crystal)>=serverRangeSq||eye.squareDistanceTo(closest)>breakRange*breakRange)return null;
        if(mc.world.rayTraceBlocks(eye,closest,false,true,false)==null)return closest;
        Vec3d center=new Vec3d((b.minX+b.maxX)*.5D,(b.minY+b.maxY)*.5D,(b.minZ+b.maxZ)*.5D);
        if(eye.squareDistanceTo(center)<=breakRange*breakRange&&mc.world.rayTraceBlocks(eye,center,false,true,false)==null)return center;
        double walls=profile==0?0D:Math.min(3D,breakWallsRange);
        return walls>0D&&mc.player.getDistanceSq(crystal)<walls*walls&&eye.squareDistanceTo(closest)<=walls*walls?closest:null;
    }

    private boolean breakCrystal(AttackPlan plan){
        EntityEnderCrystal crystal=plan.crystal;
        if(crystal.isDead||mc.world.getEntityByID(crystal.getEntityId())!=crystal||breakAim(crystal)==null)return false;
        if(!validTarget(plan.evaluation.target.player))return false;
        if(!safeAfterCommitted(crystal.getPositionVector()))return false;
        int previous=mc.player.inventory.currentItem, selected=previous;
        if(!canBreakWith(mc.player.getHeldItemMainhand())){
            if(antiWeakness==0||swapMode==0)return false;
            selected=-1;
            for(int slot=0;slot<9;slot++)if(canBreakWith(mc.player.inventory.getStackInSlot(slot))){selected=slot;break;}
            if(selected<0){state="Weakness: weapon needed";return false;}
        }
        try{
            selectSlot(selected);
            if(!aim(plan.aim))return false;
            mc.playerController.attackEntity(mc.player,crystal);
            recordCommitted(crystal.getPositionVector());
            mc.player.swingArm(EnumHand.MAIN_HAND);
            breakAttempts.sent(crystal.getEntityId(),clientTicks,retryTicks,retryLimit,confirmationTimeout()+10);
            breakSends++;state="Breaking";return true;
        }finally{restoreAim();if(swapMode==2)selectSlot(previous);}
    }

    private void placeCrystal(PlacePlan plan){
        if(!validBase(plan.base)||!validTarget(plan.evaluation.target.player))return;
        PlacementHit hit=placementHit(plan.base);
        if(hit==null)return;
        Vec3d explosion=new Vec3d(plan.base).addVector(.5D,1D,.5D);
        if(!safeAfterCommitted(explosion))return;
        int previous=mc.player.inventory.currentItem;
        int selected=previous;
        EnumHand hand=crystalHand();
        if(hand==null){
            if(swapMode==0)return;
            int slot=InventoryManager.findItemInHotbar(mc,Items.END_CRYSTAL);
            if(slot<0)return;
            selected=slot;hand=EnumHand.MAIN_HAND;
        }
        try{
            selectSlot(selected);
            if(mc.player.getHeldItem(hand).getItem()!=Items.END_CRYSTAL||!aim(hit.point))return;
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,plan.base,hit.face,hit.point,hand);
            // Even a rejected interaction is paced. SUCCESS is only a sent attempt, never a server acknowledgement.
            nextPlaceTick=clientTicks+1+effectiveDelay(placeDelay);
            placeAttempts.sent(plan.base,clientTicks,result==EnumActionResult.SUCCESS?confirmationTimeout():retryTicks,
                    retryLimit,confirmationTimeout()+10);
            if(result==EnumActionResult.SUCCESS){recordCommitted(explosion);mc.player.swingArm(hand);placeSends++;state="Awaiting spawn";}
            else state="Placement rejected";
        }finally{restoreAim();if(swapMode==2)selectSlot(previous);}
    }

    private double worstDamage(CrystalDamage.Snapshot snapshot,Vec3d explosion){
        return Math.max(snapshot.damage(mc.world,explosion,false),snapshot.damage(mc.world,explosion,true));
    }
    private boolean safeAfterCommitted(Vec3d explosion){
        if(!CrystalMath.safe(worstDamage(self,explosion)+committedDamage.getOrDefault(self.player,0D),
                self.health,maxSelfDamage,healthReserve))return false;
        for(CrystalDamage.Snapshot friend:friends)
            if(!CrystalMath.safe(worstDamage(friend,explosion)+committedDamage.getOrDefault(friend.player,0D),
                    friend.health,maxSelfDamage,healthReserve))return false;
        return true;
    }
    private void recordCommitted(Vec3d explosion){
        committedDamage.merge(self.player,worstDamage(self,explosion),Double::sum);
        for(CrystalDamage.Snapshot friend:friends)
            committedDamage.merge(friend.player,worstDamage(friend,explosion),Double::sum);
    }

    private EnumHand crystalHand(){
        if(mc.player.getHeldItemOffhand().getItem()==Items.END_CRYSTAL)return EnumHand.OFF_HAND;
        return mc.player.getHeldItemMainhand().getItem()==Items.END_CRYSTAL?EnumHand.MAIN_HAND:null;
    }
    private boolean hasAvailableCrystals(){
        return crystalHand()!=null||(swapMode!=0&&InventoryManager.findItemInHotbar(mc,Items.END_CRYSTAL)>=0);
    }
    private void selectSlot(int slot){
        if(slot!=mc.player.inventory.currentItem){
            mc.player.inventory.currentItem=slot;
            // Keep the controller's cached server slot in sync, including when restoring.
            mc.playerController.updateController();
        }
    }
    private boolean canBreakWith(ItemStack stack){
        PotionEffect weakness=mc.player.getActivePotionEffect(MobEffects.WEAKNESS);
        if(weakness==null)return true;
        double value=1D-4D*(weakness.getAmplifier()+1);
        PotionEffect strength=mc.player.getActivePotionEffect(MobEffects.STRENGTH);
        if(strength!=null)value+=3D*(strength.getAmplifier()+1);
        for(AttributeModifier modifier:stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND).get(SharedMonsterAttributes.ATTACK_DAMAGE.getName()))
            if(modifier.getOperation()==0)value+=modifier.getAmount();
        return value>0D;
    }
    private boolean aim(Vec3d point){
        if(rotate==0)return true;
        Vec3d delta=point.subtract(mc.player.getPositionEyes(1F));
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90F;
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        if(rotate==2){mc.player.rotationYaw=yaw;mc.player.rotationPitch=pitch;}
        return PacketManager.send(new CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround),name);
    }
    private void restoreAim(){
        if(rotate==1)PacketManager.send(new CPacketPlayer.Rotation(mc.player.rotationYaw,mc.player.rotationPitch,mc.player.onGround),name);
    }
    private int confirmationTimeout(){
        net.minecraft.client.network.NetworkPlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
        return CrystalMath.confirmationTicks(acknowledgementTicks,info==null?0:info.getResponseTime());
    }
    private int effectiveDelay(int delay){return Math.max(profile==0?1:0,Math.max(delay,actionDelay));}
    private boolean validTarget(EntityPlayer target){
        return target!=null&&!target.isDead&&target.getHealth()>0F&&!target.isSpectator()&&!target.isCreative()
                &&mc.world.getEntityByID(target.getEntityId())==target&&mc.player.getDistanceSq(target)<=targetRange*targetRange
                &&(friendFilter==0||!FriendManager.isFriend(target.getName()));
    }
    private double minimumDamage(EntityPlayer target){
        return faceplace!=0&&shouldFaceplace(target)?Math.min(minDamage,faceplaceMinDamage):minDamage;
    }
    private boolean shouldFaceplace(EntityPlayer target){
        if(target.getHealth()+target.getAbsorptionAmount()<=faceplaceHealth)return true;
        for(ItemStack armor:target.getArmorInventoryList()){
            if(armor.isEmpty())return true;
            if(armor.isItemStackDamageable()&&armor.getMaxDamage()>0
                    &&100D*(armor.getMaxDamage()-armor.getItemDamage())/armor.getMaxDamage()<=faceplaceArmorPercent)return true;
        }
        return false;
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private void reset(){
        breakAttempts.clear();placeAttempts.clear();damageCache.clear();committedDamage.clear();targets.clear();friends.clear();crystals.clear();
        activeWorld=null;self=null;clientTicks=0;nextPlaceTick=0;nextBreakTick=0;
        renderTarget=null;crystalLabels=Collections.emptyList();placeSends=breakSends=placeConfirms=0;state="Idle";
    }
    @Override protected void onEnable(){reset();}
    @Override protected void onDisable(){reset();}

    private static final class Evaluation{
        final CrystalDamage.Snapshot target;final double damage,selfDamage,score;
        Evaluation(CrystalDamage.Snapshot target,double damage,double selfDamage,double score){
            this.target=target;this.damage=damage;this.selfDamage=selfDamage;this.score=score;
        }
    }
    private static final class AttackPlan{
        final EntityEnderCrystal crystal;final Vec3d aim;final Evaluation evaluation;
        AttackPlan(EntityEnderCrystal crystal,Vec3d aim,Evaluation evaluation){this.crystal=crystal;this.aim=aim;this.evaluation=evaluation;}
    }
    private static final class PlacementHit{
        final EnumFacing face;final Vec3d point;
        PlacementHit(EnumFacing face,Vec3d point){this.face=face;this.point=point;}
    }
    private static final class PlacePlan{
        final BlockPos base;final Evaluation evaluation;
        PlacePlan(BlockPos base,Evaluation evaluation){this.base=base;this.evaluation=evaluation;}
    }

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||renderDamage==0)return;
        RenderManager manager=mc.getRenderManager();
        EntityLivingBase target=renderTarget;
        if(target!=null&&target!=mc.player&&!target.isDead){
            double ix=target.lastTickPosX+(target.posX-target.lastTickPosX)*partialTicks;
            double iy=target.lastTickPosY+(target.posY-target.lastTickPosY)*partialTicks;
            double iz=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*partialTicks;
            net.minecraft.util.math.AxisAlignedBB box=target.getEntityBoundingBox().offset(ix-target.posX-manager.viewerPosX,iy-target.posY-manager.viewerPosY,iz-target.posZ-manager.viewerPosZ);
            GlStateManager.pushMatrix();GlStateManager.enableBlend();GlStateManager.disableTexture2D();GlStateManager.disableDepth();GlStateManager.depthMask(false);
            GL11.glLineWidth(2.0F);RenderGlobal.drawSelectionBoundingBox(box,1.0F,0.84F,0.0F,0.95F);
            GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
        }
        for(CrystalLabel label:crystalLabels){
            double x,y,z;AxisAlignedBB bounds;
            if(label.placement){
                BlockPos base=label.base;
                x=base.getX()+.5D;y=base.getY()+2.1D;z=base.getZ()+.5D;
                bounds=new AxisAlignedBB(base.getX()+.08D,base.getY()+1.02D,base.getZ()+.08D,base.getX()+.92D,base.getY()+2.92D,base.getZ()+.92D);
            }else{
                Entity crystal=label.crystal;
                if(crystal.isDead||mc.player.getDistance(crystal)>16.0F)continue;
                x=crystal.lastTickPosX+(crystal.posX-crystal.lastTickPosX)*partialTicks;
                y=crystal.lastTickPosY+(crystal.posY-crystal.lastTickPosY)*partialTicks+crystal.height+.25D;
                z=crystal.lastTickPosZ+(crystal.posZ-crystal.lastTickPosZ)*partialTicks;
                bounds=crystal.getEntityBoundingBox().grow(.08D,.08D,.08D);
            }
            drawCrystalGlow(bounds,manager);
            drawDamageLabel(manager,x,y,z,String.format(Locale.US,"%.1f",label.damage));
        }
    }

    private void drawCrystalGlow(AxisAlignedBB worldBounds,RenderManager manager){
        AxisAlignedBB box=worldBounds.offset(-manager.viewerPosX,-manager.viewerPosY,-manager.viewerPosZ);
        GlStateManager.pushMatrix();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.depthMask(false);
        RenderGlobal.renderFilledBox(box,1.0F,.84F,0.0F,.20F);
        GL11.glLineWidth(2.0F);RenderGlobal.drawSelectionBoundingBox(box,1.0F,.84F,0.0F,.95F);
        GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }

    private void drawDamageLabel(RenderManager manager,double x,double y,double z,String value){
        GlStateManager.pushMatrix();
        GlStateManager.translate(x-manager.viewerPosX,y-manager.viewerPosY,z-manager.viewerPosZ);
        GlStateManager.rotate(-manager.playerViewY,0.0F,1.0F,0.0F);
        GlStateManager.rotate(manager.options.thirdPersonView==2?-manager.playerViewX:manager.playerViewX,1.0F,0.0F,0.0F);
        GlStateManager.scale(-0.03F,-0.03F,0.03F);
        GlStateManager.disableLighting();GlStateManager.depthMask(false);GlStateManager.disableDepth();GlStateManager.enableBlend();
        int width=mc.fontRenderer.getStringWidth(value);
        Gui.drawRect(-width/2-5,-3,width/2+5,mc.fontRenderer.FONT_HEIGHT+2,0xEE050505);
        mc.fontRenderer.drawStringWithShadow(value,-width/2,0,0xFFFFFF00);
        GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        GlStateManager.popMatrix();
    }

    private static final class CrystalLabel { final Entity crystal;final BlockPos base;final double damage;final boolean placement;CrystalLabel(Entity e,BlockPos b,double d,boolean p){crystal=e;base=b;damage=d;placement=p;} }
}
