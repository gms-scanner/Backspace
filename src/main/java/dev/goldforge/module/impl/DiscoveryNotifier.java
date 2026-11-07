package dev.goldforge.module.impl;

import dev.goldforge.GoldForge;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextComponentString;

/** Optional local sound and chat presentation for new observations. */
public final class DiscoveryNotifier extends Module {
    public int chat=1,sound=1,minConfidence=50;
    public DiscoveryNotifier(){super("DiscoveryNotifier","Alerts on new high-confidence discoveries.",Category.CLIENT,0);
        addIntSetting("Chat alerts",0,1,1,()->chat,v->chat=v);
        addIntSetting("Notification sound",0,1,1,()->sound,v->sound=v);
        addIntSetting("Minimum confidence",0,100,5,()->minConfidence,v->minConfidence=v);}
    public static void alert(String type,int confidence,int x,int z,String reason){
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        if(mc.player==null)return;
        Module module=GoldForge.MODULES.get("DiscoveryNotifier");
        if(module instanceof DiscoveryNotifier&&module.enabled){
            DiscoveryNotifier notifier=(DiscoveryNotifier)module;
            if(confidence>=notifier.minConfidence){
                if(notifier.chat!=0)mc.player.sendMessage(new TextComponentString("[Backspace] "+type+" "+confidence+"% at "+x+", "+z+": "+reason));
                if(notifier.sound!=0)mc.player.playSound(SoundEvents.BLOCK_NOTE_PLING,.55F,1.4F);
            }
        }else mc.player.sendMessage(new TextComponentString("[Backspace] "+type+" "+confidence+"% at "+x+", "+z+": "+reason));
        AutoScreenshot.capture(confidence);
    }
}
