package dev.goldforge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class WaypointCommand extends CommandBase {
    @Override public String getName(){return "waypoint";}
    @Override public String getUsage(ICommandSender sender){return "/waypoint add <name> | remove <name> | list | export <name> | import <name>";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){
        Minecraft.getMinecraft().addScheduledTask(()->run(sender,args));
    }
    private void run(ICommandSender sender,String[] args){
        Minecraft mc=Minecraft.getMinecraft();
        if(args.length==1&&"list".equalsIgnoreCase(args[0])){java.util.List<WaypointManager.Waypoint> points=WaypointManager.all();tell(sender,points.isEmpty()?"No waypoints saved.":"Waypoints: "+points.size());for(WaypointManager.Waypoint p:points)tell(sender,p.name+" "+p.x+", "+p.y+", "+p.z+" (dimension "+p.dimension+")");return;}
        if(args.length==2&&"add".equalsIgnoreCase(args[0])){if(mc.player==null){tell(sender,"Join a world before adding a waypoint.");return;}boolean added=WaypointManager.add(args[1],(int)Math.floor(mc.player.posX),(int)Math.floor(mc.player.posY),(int)Math.floor(mc.player.posZ),mc.player.dimension);tell(sender,added?"Saved waypoint "+args[1]+".":"Use a name with 1-32 letters, numbers, _ or -.");return;}
        if(args.length==2&&"remove".equalsIgnoreCase(args[0])){tell(sender,WaypointManager.remove(args[1])?"Removed waypoint "+args[1]+".":"Waypoint not found.");return;}
        if(args.length==2&&"export".equalsIgnoreCase(args[0])){tell(sender,WaypointManager.exportProfile(args[1])?"Exported waypoints to Backspace/waypoint-exports/"+args[1]+".properties.":"Could not export; use a name with 1-32 letters, numbers, _ or -.");return;}
        if(args.length==2&&"import".equalsIgnoreCase(args[0])){int count=WaypointManager.importProfile(args[1]);tell(sender,count<0?"Import failed; export not found or invalid. Existing waypoints were kept.":"Imported "+count+" waypoint(s). Matching names were replaced.");return;}
        tell(sender,getUsage(sender));
    }
    private void tell(ICommandSender sender,String message){sender.sendMessage(new TextComponentString("§e[Backspace] §f"+message));}
}
