package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Displays server-reported block damage rather than guessing mining progress. */
public final class BreakESP extends Module {
    public int range=48, expiryTicks=50, showMiner=1, showSelf=0;
    private final Map<Integer, BreakRecord> breaks=new LinkedHashMap<Integer, BreakRecord>();
    private int tick;
    public BreakESP() {
        super("BreakESP","Shows server-reported block break progress and its miner.",Category.RENDER,0);
        addIntSetting("Range",8,128,8,()->range,v->range=v);
        addIntSetting("Stale timeout (ticks)",10,100,5,()->expiryTicks,v->expiryTicks=v);
        addIntSetting("Miner label (0/1)",0,1,1,()->showMiner,v->showMiner=v);
        addIntSetting("Show own mining (0/1)",0,1,1,()->showSelf,v->showSelf=v);
    }
    @Override protected void onEnable(){breaks.clear();tick=0;}
    @Override protected void onDisable(){breaks.clear();}
    @Override protected void onWorldUnload(){breaks.clear();}
    public void onBreakPacket(int breakerId,BlockPos pos,int progress) {
        if(!enabled||mc.world==null||pos==null)return;
        if(progress<0||progress>9){breaks.remove(breakerId);return;}
        breaks.put(breakerId,new BreakRecord(pos.toImmutable(),progress,tick));
        if(breaks.size()>64)breaks.remove(breaks.keySet().iterator().next());
    }
    @Override public void tick(){
        tick++;
        Iterator<Map.Entry<Integer,BreakRecord>> it=breaks.entrySet().iterator();
        while(it.hasNext()){
            BreakRecord record=it.next().getValue();
            if(mc.world==null||tick-record.tick>expiryTicks||!mc.world.isBlockLoaded(record.pos)||mc.world.isAirBlock(record.pos))it.remove();
        }
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null||mc.getRenderManager()==null)return;
        CombatVisuals.begin();
        try {
            for(Map.Entry<Integer,BreakRecord> entry:breaks.entrySet()){
                if(showSelf==0&&entry.getKey()==mc.player.getEntityId())continue;
                BreakRecord record=entry.getValue();
                if(mc.player.getDistanceSqToCenter(record.pos)>range*(double)range)continue;
                double inset=.025D, height=(record.progress+1)/10D;
                AxisAlignedBB box=new AxisAlignedBB(record.pos).grow(-inset);
                CombatVisuals.fill(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.minY+height*(box.maxY-box.minY),box.maxZ),1F,.73F,.04F,.12F);
                CombatVisuals.outline(box,1F,.78F,.08F,.9F);
                Entity miner=mc.world.getEntityByID(entry.getKey());
                String label=(showMiner!=0&&miner!=null?miner.getName()+" · ":"")+((record.progress+1)*10)+"%";
                CombatVisuals.label(label,record.pos.getX()+.5,record.pos.getY()+1.18,record.pos.getZ()+.5,0xFFFFD64A);
            }
        } finally {CombatVisuals.end();}
    }
    private static final class BreakRecord {
        final BlockPos pos;final int progress,tick;
        BreakRecord(BlockPos pos,int progress,int tick){this.pos=pos;this.progress=progress;this.tick=tick;}
    }
}
