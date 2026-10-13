package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Frozen, translucent player silhouette on a confirmed totem activation. */
public final class PopChams extends Module {
    public int lifetime=35, range=96, glow=1;
    private final List<Ghost> ghosts=new ArrayList<Ghost>();
    private int ticks;
    public PopChams(){
        super("PopChams","Leaves a fading silhouette where a player popped a totem.",Category.RENDER,0);
        addIntSetting("Duration (ticks)",10,100,5,()->lifetime,v->lifetime=v);
        addIntSetting("Range",16,128,8,()->range,v->range=v);
        addIntSetting("Soft glow (0/1)",0,1,1,()->glow,v->glow=v);
    }
    @Override protected void onEnable(){ghosts.clear();ticks=0;}
    @Override protected void onDisable(){ghosts.clear();}
    @Override protected void onWorldUnload(){ghosts.clear();}
    public void onTotemPop(int entityId){
        if(!enabled||mc.world==null||mc.player==null)return;
        Entity entity=mc.world.getEntityByID(entityId);
        if(!(entity instanceof EntityPlayer)||entity==mc.player||mc.player.getDistanceSq(entity)>range*(double)range)return;
        ghosts.add(new Ghost(entity.posX,entity.posY,entity.posZ,((EntityPlayer)entity).getName(),ticks));
        while(ghosts.size()>32)ghosts.remove(0);
    }
    @Override public void tick(){
        ticks++;
        Iterator<Ghost> it=ghosts.iterator();
        while(it.hasNext())if(ticks-it.next().tick>=lifetime)it.remove();
    }
    @Override public void render(float partialTicks){
        if(mc.player==null||mc.getRenderManager()==null)return;
        CombatVisuals.begin();
        try{
            for(Ghost ghost:ghosts){
                double dx=ghost.x-mc.player.posX,dz=ghost.z-mc.player.posZ;
                if(dx*dx+dz*dz>range*(double)range)continue;
                float alpha=Math.max(0F,1F-(ticks+partialTicks-ghost.tick)/Math.max(1F,lifetime));
                // A complete six-part figure reads as a player without mutating or
                // re-rendering the live entity, which may already have teleported.
                part(ghost,-.25,.72,-.13,.25,1.45,.13,alpha); // torso
                part(ghost,-.25,1.45,-.25,.25,1.95,.25,alpha); // head
                part(ghost,-.42,.72,-.12,-.25,1.43,.12,alpha); // left arm
                part(ghost,.25,.72,-.12,.42,1.43,.12,alpha); // right arm
                part(ghost,-.23,0,-.12,-.02,.72,.12,alpha); // left leg
                part(ghost,.02,0,-.12,.23,.72,.12,alpha); // right leg
                CombatVisuals.label(ghost.name+" · POP",ghost.x,ghost.y+2.16,ghost.z,0xFFFFD64A);
            }
        }finally{CombatVisuals.end();}
    }
    private void part(Ghost g,double x1,double y1,double z1,double x2,double y2,double z2,float alpha){
        AxisAlignedBB box=new AxisAlignedBB(g.x+x1,g.y+y1,g.z+z1,g.x+x2,g.y+y2,g.z+z2);
        if(glow!=0)CombatVisuals.fill(box.grow(.06D),1F,.7F,.02F,.045F*alpha);
        CombatVisuals.fill(box,1F,.78F,.12F,.23F*alpha);
        CombatVisuals.outline(box,1F,.9F,.35F,.78F*alpha);
    }
    private static final class Ghost {
        final double x,y,z;final String name;final int tick;
        Ghost(double x,double y,double z,String name,int tick){this.x=x;this.y=y;this.z=z;this.name=name;this.tick=tick;}
    }
}
