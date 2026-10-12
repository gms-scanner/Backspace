package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.util.*;

/** Detects player entries into the loaded render range and animates stacked right-side notices. */
public final class Radar extends Module {
    public int durationSeconds=4,spacing=5,red=255,green=205,blue=45,sound=0;
    private final Set<UUID> inside=new HashSet<UUID>();
    private final List<Notice> notices=new ArrayList<Notice>();
    private int color;
    public Radar(){
        super("Radar","Notifies when a newly observed player enters render distance.",Category.RENDER,0);
        addIntSetting("Duration (seconds)",1,12,1,()->durationSeconds,v->durationSeconds=v);
        addIntSetting("Spacing (pixels)",0,16,1,()->spacing,v->spacing=v);
        addIntSetting("Red",0,255,5,()->red,v->red=v);addIntSetting("Green",0,255,5,()->green,v->green=v);addIntSetting("Blue",0,255,5,()->blue,v->blue=v);
        addIntSetting("Sound (0/1)",0,1,1,()->sound,v->sound=v);
    }
    @Override protected void onEnable(){inside.clear();notices.clear();color=rgb();snapshot();}
    @Override protected void onDisable(){inside.clear();notices.clear();}
    @Override protected void onWorldUnload(){inside.clear();notices.clear();}
    @Override protected void onWorldLoad(){inside.clear();notices.clear();snapshot();}
    @Override public void tick(){
        if(mc.world==null||mc.player==null){inside.clear();notices.clear();return;}
        color=rgb();Set<UUID> current=new HashSet<UUID>();double range=Math.max(16,mc.gameSettings.renderDistanceChunks*16D);
        for(EntityPlayer player:mc.world.playerEntities){
            if(player==mc.player||player.isDead||mc.player.getDistance(player)>range)continue;
            UUID id=player.getUniqueID();current.add(id);
            if(!inside.contains(id)){notices.add(new Notice(player.getName(),System.currentTimeMillis()));if(sound!=0)mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK,0.8F));}
        }
        inside.retainAll(current);inside.addAll(current);
        long now=System.currentTimeMillis();for(Iterator<Notice> it=notices.iterator();it.hasNext();)if(now-it.next().createdAt>durationSeconds*1000L)it.remove();
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.currentScreen!=null||notices.isEmpty())return;
        long now=System.currentTimeMillis();int y=mc.displayHeight/Math.max(1,mc.gameSettings.guiScale==0?1:mc.gameSettings.guiScale)/2-22;
        for(Notice notice:notices){
            long age=now-notice.createdAt;float fade=Math.min(1F,Math.min(age/160F,(durationSeconds*1000L-age)/350F));int alpha=(int)(220*Math.max(0F,fade));
            String text=notice.name+" entered render distance";int width=mc.fontRenderer.getStringWidth(text);int x=event.getResolution().getScaledWidth()-8-width-12;
            int bg=(alpha<<24)|0x0010161C;Gui.drawRect(x,y,x+width+12,y+20,bg);Gui.drawRect(x,y,x+2,y+20,(alpha<<24)|(color&0x00FFFFFF));
            mc.fontRenderer.drawStringWithShadow(text,x+7,y+6,(alpha<<24)|0x00F5F7F8);y+=20+spacing;
        }
    }
    private void snapshot(){if(mc.world==null||mc.player==null)return;double range=Math.max(16,mc.gameSettings.renderDistanceChunks*16D);for(EntityPlayer player:mc.world.playerEntities)if(player!=mc.player&&mc.player.getDistance(player)<=range)inside.add(player.getUniqueID());}
    private int rgb(){return (red&255)<<16|(green&255)<<8|(blue&255);}
    private static final class Notice{final String name;final long createdAt;Notice(String name,long createdAt){this.name=name;this.createdAt=createdAt;}}
}
