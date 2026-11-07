package dev.goldforge;

import dev.goldforge.discovery.DiscoveryDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import java.util.List;
import java.util.Locale;

/** Browse, edit and exchange observations without changing legacy user waypoints. */
public final class DiscoveryCommand extends CommandBase {
    @Override public String getName(){return "discovery";}
    @Override public String getUsage(ICommandSender sender){return "/discovery list [type] [distance|date|size|significance] | search <text> | name <id> <name> | note <id> <text> | remove <id> | export <name> | import <name>";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){Minecraft.getMinecraft().addScheduledTask(()->run(sender,args));}
    private void run(ICommandSender sender,String[] args){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null){say(sender,"Join a world first.");return;}
        if(args.length==0){say(sender,getUsage(sender));return;}
        String action=args[0].toLowerCase(Locale.ROOT);
        if("list".equals(action)||"search".equals(action)){
            String type="list".equals(action)&&args.length>1?args[1]:null;
            String sort="list".equals(action)&&args.length>2?args[2]:"distance";
            String query="search".equals(action)&&args.length>1?join(args,1):"";
            List<DiscoveryDatabase.Entry> found=DiscoveryDatabase.search(query,type,sort,mc.player.posX,mc.player.posY,mc.player.posZ);
            say(sender,found.size()+" discoveries on "+DiscoveryDatabase.currentServer()+" in dimension "+mc.player.dimension+". Showing first 20.");
            for(int i=0;i<Math.min(20,found.size());i++){
                DiscoveryDatabase.Entry e=found.get(i);
                say(sender,e.id.substring(0,8)+" "+e.type+" "+e.name+" "+e.x+","+e.y+","+e.z+" "+e.confidence+"% "+e.reason);
            }return;
        }
        if(args.length==2&&"export".equals(action)){say(sender,DiscoveryDatabase.exportJson(args[1])?"Exported to Backspace/discovery-exports/"+args[1]+".json":"Export failed.");return;}
        if(args.length==2&&"import".equals(action)){int n=DiscoveryDatabase.importJson(args[1]);say(sender,n<0?"Import failed or invalid file.":"Imported "+n+" new discoveries.");return;}
        DiscoveryDatabase.Entry entry=find(args.length>1?args[1]:"");
        if(entry==null){say(sender,"Discovery ID not found or ambiguous. Use /discovery list.");return;}
        if("remove".equals(action)&&args.length==2){say(sender,DiscoveryDatabase.remove(entry.id)?"Removed.":"Could not remove.");return;}
        if("name".equals(action)&&args.length>2){say(sender,DiscoveryDatabase.edit(entry.id,join(args,2),entry.notes,entry.color,entry.waypoint)?"Renamed.":"Invalid name (max 64 chars).");return;}
        if("note".equals(action)&&args.length>2){say(sender,DiscoveryDatabase.edit(entry.id,entry.name,join(args,2),entry.color,entry.waypoint)?"Note saved.":"Invalid note (max 1024 chars).");return;}
        say(sender,getUsage(sender));
    }
    private static DiscoveryDatabase.Entry find(String prefix){DiscoveryDatabase.Entry match=null;for(DiscoveryDatabase.Entry entry:DiscoveryDatabase.all())if(entry.id.startsWith(prefix)){if(match!=null)return null;match=entry;}return match;}
    private static String join(String[] args,int from){StringBuilder out=new StringBuilder();for(int i=from;i<args.length;i++){if(i>from)out.append(' ');out.append(args[i]);}return out.toString();}
    private static void say(ICommandSender sender,String text){sender.sendMessage(new TextComponentString("§e[Backspace] §f"+text));}
}
