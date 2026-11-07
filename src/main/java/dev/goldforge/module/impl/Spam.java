package dev.goldforge.module.impl;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
public final class Spam extends Module {
    public String message="";
    public int intervalSeconds=10;
    private long lastSent;
    public Spam(){super("Spam","Repeats configured chat text. Edit with /moduleconfig Spam.",Category.MISC,0);
        addIntSetting("Interval (seconds)",3,120,1,()->intervalSeconds,v->intervalSeconds=v);}
    @Override protected void onEnable(){lastSent=System.currentTimeMillis();}
    @Override public void tick(){
        if(mc.player==null||mc.getConnection()==null||message==null)return;
        String body=message.trim().replace('\n',' ').replace('\r',' ');
        if(body.isEmpty()||body.startsWith("/"))return;
        long now=System.currentTimeMillis();
        if(now-lastSent<Math.max(3,intervalSeconds)*1000L)return;
        lastSent=now;mc.player.sendChatMessage(body.substring(0,Math.min(256,body.length())));
    }
}
