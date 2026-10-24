package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;

public final class WhisperSpam extends Module {
    public String target="", message="";
    public int intervalSeconds=10;
    private long lastSent;
    public WhisperSpam(){
        super("WhisperSpam","Repeats a configured private message.",Category.MISC,0);
        addIntSetting("Interval (seconds)",3,120,1,()->intervalSeconds,v->intervalSeconds=v);
    }
    @Override protected void onEnable(){lastSent=System.currentTimeMillis();}
    @Override public void tick(){
        if(mc.player==null||mc.getConnection()==null||target==null||!target.matches("[A-Za-z0-9_]{1,16}")||message==null)return;
        String body=message.trim().replace('\n',' ').replace('\r',' ');
        if(body.isEmpty())return;
        long now=System.currentTimeMillis();
        if(now-lastSent<Math.max(3,intervalSeconds)*1000L)return;
        lastSent=now;
        String command="/msg "+target+" "+body;
        mc.player.sendChatMessage(command.substring(0,Math.min(256,command.length())));
    }
}
