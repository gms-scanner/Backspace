package dev.goldforge;

import dev.goldforge.module.impl.ExpeditionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import java.util.ArrayList;
import java.util.List;

public final class ExpeditionCommand extends CommandBase {
    @Override public String getName(){return "expedition";}
    @Override public String getUsage(ICommandSender sender){return "/expedition route <waypoint names...> | start | pause | resume | restore | status";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){Minecraft.getMinecraft().addScheduledTask(()->run(sender,args));}
    private void run(ICommandSender sender,String[] args){
        ExpeditionMode mode=(ExpeditionMode)GoldForge.MODULES.get("ExpeditionMode");
        if(args.length==0){say(sender,getUsage(sender));return;}
        if("route".equalsIgnoreCase(args[0])&&args.length>1){List<String> names=new ArrayList<String>();for(int i=1;i<args.length;i++)names.add(args[i]);mode.route(names);say(sender,"Route set: "+String.join(" → ",names));return;}
        if("start".equalsIgnoreCase(args[0])){say(sender,mode.start()?"Expedition started.":"Set a route and join a world first.");return;}
        if("pause".equalsIgnoreCase(args[0])){mode.stop();say(sender,"Expedition stopped; checkpoint saved.");return;}
        if("resume".equalsIgnoreCase(args[0])){say(sender,mode.resume()?"Expedition resumed.":"No route available.");return;}
        if("restore".equalsIgnoreCase(args[0])){say(sender,mode.restore()?"Checkpoint loaded. Run /expedition resume to continue.":"No checkpoint for this server.");return;}
        if("status".equalsIgnoreCase(args[0])){say(sender,mode.status());return;}
        say(sender,getUsage(sender));
    }
    private static void say(ICommandSender sender,String text){sender.sendMessage(new TextComponentString("§e[Backspace] §f"+text));}
}
