package dev.goldforge;

import dev.goldforge.module.impl.WhisperSpam;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class WhisperSpamCommand extends CommandBase {
    @Override public String getName(){return "whisperspam";}
    @Override public String getUsage(ICommandSender sender){return "/whisperspam <player> <message...> | off";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){
        dev.goldforge.module.Module module=GoldForge.MODULES.get("WhisperSpam");
        if(!(module instanceof WhisperSpam))return;
        WhisperSpam spam=(WhisperSpam)module;
        if(args.length==1&&"off".equalsIgnoreCase(args[0])){spam.setEnabled(false);tell(sender,"WhisperSpam stopped.");return;}
        if(args.length<2||!args[0].matches("[A-Za-z0-9_]{1,16}")){tell(sender,getUsage(sender));return;}
        StringBuilder text=new StringBuilder();for(int i=1;i<args.length;i++){if(i>1)text.append(' ');text.append(args[i]);}
        if(text.length()==0||text.length()>220){tell(sender,"Message must be 1 to 220 characters.");return;}
        spam.target=args[0];spam.message=text.toString();ConfigManager.save();spam.setEnabled(true);
        tell(sender,"WhisperSpam set for "+spam.target+" every "+spam.intervalSeconds+" seconds.");
    }
    private void tell(ICommandSender sender,String text){sender.sendMessage(new TextComponentString("§e[Backspace] §f"+text));}
}
