package dev.goldforge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class FriendCommand extends CommandBase {
    @Override public String getName(){return "friend";}
    @Override public String getUsage(ICommandSender sender){return "/friend + <player> | - <player> | rename <old> <new> | group <player> <group> | whitelist|blacklist +|- <player>";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){
        if(args.length==1&&"list".equalsIgnoreCase(args[0])){java.util.Map<String,String> friends=FriendManager.grouped();tell(friends.isEmpty()?"No friends saved.":"Friends: "+friends.size());for(java.util.Map.Entry<String,String> entry:friends.entrySet())tell(entry.getKey()+" ["+entry.getValue()+"]");return;}
        if(args.length==2&&"whitelist".equalsIgnoreCase(args[0])&&"list".equalsIgnoreCase(args[1])){tell("Whitelist: "+String.join(", ",FriendManager.whitelist()));return;}
        if(args.length==2&&"blacklist".equalsIgnoreCase(args[0])&&"list".equalsIgnoreCase(args[1])){tell("Blacklist: "+String.join(", ",FriendManager.blacklist()));return;}
        if(args.length==3&&"rename".equalsIgnoreCase(args[0])){tell(FriendManager.rename(args[1],args[2])?"Renamed friend "+args[1]+" to "+args[2]+".":"Could not rename; check names and whether the destination already exists.");return;}
        if(args.length==3&&"group".equalsIgnoreCase(args[0])){tell(FriendManager.setGroup(args[1],args[2])?"Set "+args[1]+" group to "+args[2]+".":"Could not set group; player must be a friend and group must use letters, numbers, _ or -.");return;}
        if(args.length==3&&("whitelist".equalsIgnoreCase(args[0])||"blacklist".equalsIgnoreCase(args[0]))&&(args[1].equals("+")||args[1].equals("-"))){
            boolean white="whitelist".equalsIgnoreCase(args[0]),add=args[1].equals("+");
            boolean changed=white?(add?FriendManager.addWhitelist(args[2]):FriendManager.removeWhitelist(args[2])):(add?FriendManager.addBlacklist(args[2]):FriendManager.removeBlacklist(args[2]));
            tell(changed?(add?"Added ":"Removed ")+args[2]+(white?" "+(add?"to":"from")+" whitelist.":" "+(add?"to":"from")+" blacklist."):"No change; verify the player name and current list.");return;
        }
        if(args.length!=2||(!args[0].equals("+")&&!args[0].equals("-"))||!validPlayer(args[1])){tell(getUsage(sender));return;}
        boolean add=args[0].equals("+");boolean changed=add?FriendManager.add(args[1]):FriendManager.remove(args[1]);
        tell(changed?(add?"Added ":"Removed ")+args[1]+(add?" as a friend.":" from local lists."):args[1]+(add?" is already a friend.":" is not present in local lists."));
    }
    private static boolean validPlayer(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    private void tell(String text){if(Minecraft.getMinecraft().player!=null)Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§e[Backspace] §f"+text));}
}
