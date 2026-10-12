package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/** Low-allocation, configurable telemetry block for the HUD. */
public final class HudInfo extends Module {
    public int x=8,y=62,coordinates=1,fps=1,speed=1,direction=1,server=0,memory=0,ping=0,sessionTime=0;
    private String[] lines=new String[0];
    private int refreshTicks;
    private long enabledAtMillis;
    public HudInfo(){
        super("Info HUD","Shows selected local performance and player telemetry.",Category.HUD,0);
        addIntSetting("X",0,500,4,()->x,v->x=v);addIntSetting("Y",0,500,4,()->y,v->y=v);
        addIntSetting("Coordinates (0/1)",0,1,1,()->coordinates,v->coordinates=v);addIntSetting("FPS (0/1)",0,1,1,()->fps,v->fps=v);
        addIntSetting("Speed (0/1)",0,1,1,()->speed,v->speed=v);addIntSetting("Direction (0/1)",0,1,1,()->direction,v->direction=v);
        addIntSetting("Server (0/1)",0,1,1,()->server,v->server=v);addIntSetting("Memory (0/1)",0,1,1,()->memory,v->memory=v);
        addIntSetting("Ping (0/1)",0,1,1,()->ping,v->ping=v);addIntSetting("Session time (0/1)",0,1,1,()->sessionTime,v->sessionTime=v);
    }
    @Override public void tick(){if(++refreshTicks>=5){refreshTicks=0;refresh();}}
    @Override protected void onEnable(){enabledAtMillis=System.currentTimeMillis();refresh();}
    @Override protected void onDisable(){lines=new String[0];enabledAtMillis=0L;}
    private void refresh(){
        if(mc.player==null){lines=new String[0];return;}
        java.util.ArrayList<String> result=new java.util.ArrayList<String>(8);
        if(coordinates!=0)result.add(String.format(java.util.Locale.ROOT,"XYZ %.1f / %.1f / %.1f",mc.player.posX,mc.player.posY,mc.player.posZ));
        if(fps!=0)result.add("FPS "+mc.getDebugFPS());
        if(speed!=0){double horizontal=Math.sqrt(mc.player.motionX*mc.player.motionX+mc.player.motionZ*mc.player.motionZ);result.add(String.format(java.util.Locale.ROOT,"Speed %.2f m/s",horizontal*20.0D));}
        if(direction!=0)result.add("Facing "+mc.player.getHorizontalFacing().getName().toUpperCase(java.util.Locale.ROOT));
        if(server!=0){ServerData data=mc.getCurrentServerData();result.add("Server "+(data==null?"Singleplayer":data.serverIP));}
        if(memory!=0){Runtime runtime=Runtime.getRuntime();result.add("Memory "+((runtime.totalMemory()-runtime.freeMemory())/1048576L)+" MB");}
        if(ping!=0)result.add("Ping "+currentPing()+" ms");
        if(sessionTime!=0)result.add("Session "+formatDuration(Math.max(0L,System.currentTimeMillis()-enabledAtMillis)));
        lines=result.toArray(new String[result.size()]);
    }
    private int currentPing(){
        if(mc.player==null||mc.getConnection()==null)return -1;
        net.minecraft.client.network.NetworkPlayerInfo info=mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
        return info==null?-1:Math.max(0,info.getResponseTime());
    }
    private static String formatDuration(long millis){long seconds=millis/1000L;return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600L,(seconds/60L)%60L,seconds%60L);}
    public String[] editorPreviewLines(){
        java.util.ArrayList<String> preview=new java.util.ArrayList<String>(8);
        if(coordinates!=0)preview.add(mc.player==null?"XYZ - / - / -":String.format(java.util.Locale.ROOT,"XYZ %.1f / %.1f / %.1f",mc.player.posX,mc.player.posY,mc.player.posZ));
        if(fps!=0)preview.add("FPS "+mc.getDebugFPS());
        if(speed!=0)preview.add("Speed 0.00 m/s");
        if(direction!=0)preview.add("Facing NORTH");
        if(server!=0)preview.add("Server Singleplayer");
        if(memory!=0)preview.add("Memory 0 MB");
        if(ping!=0)preview.add("Ping "+currentPing()+" ms");
        if(sessionTime!=0)preview.add("Session 00:00:00");
        if(preview.isEmpty())preview.add("Info HUD (no elements selected)");
        return preview.toArray(new String[preview.size()]);
    }
    @Override public void onOverlay(RenderGameOverlayEvent.Post event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.TEXT||mc.currentScreen instanceof dev.goldforge.gui.HudEditor)return;
        int drawY=y;
        for(String line:lines){int width=mc.fontRenderer.getStringWidth(line);Gui.drawRect(x-4,drawY-2,x+width+5,drawY+mc.fontRenderer.FONT_HEIGHT+2,0xB80A0A0A);Gui.drawRect(x-4,drawY-2,x-2,drawY+mc.fontRenderer.FONT_HEIGHT+2,0xFFFFD700);mc.fontRenderer.drawStringWithShadow(line,x,drawY,0xFFF4F4F4);drawY+=mc.fontRenderer.FONT_HEIGHT+4;}
    }
}
