package dev.goldforge.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Central gateway for packets deliberately sent by Backspace modules. */
public final class PacketManager {
    private static final Logger LOGGER=LogManager.getLogger("Backspace/Packets");
    private static final Deque<String> RECENT=new ArrayDeque<String>();
    private static boolean logging;
    private static long moduleSends;
    private PacketManager(){}

    public static synchronized boolean send(Packet<?> packet,String source){
        Minecraft mc=Minecraft.getMinecraft();
        if(packet==null||mc.getConnection()==null)return false;
        moduleSends++;
        if(logging){String entry=System.currentTimeMillis()+" "+(source==null?"unknown":source)+" -> "+packet.getClass().getSimpleName();RECENT.addLast(entry);while(RECENT.size()>128)RECENT.removeFirst();LOGGER.info(entry);}
        mc.getConnection().sendPacket(packet);
        return true;
    }
    public static synchronized void setLogging(boolean value){logging=value;if(!value)RECENT.clear();}
    public static synchronized boolean isLogging(){return logging;}
    public static synchronized List<String> recent(){return Collections.unmodifiableList(new ArrayList<String>(RECENT));}
    public static synchronized long getModuleSendCount(){return moduleSends;}
}
