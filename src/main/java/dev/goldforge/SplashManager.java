package dev.goldforge;

import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads editable splash lines from Backspace/splashes.txt with safe formatting fallback. */
public final class SplashManager {
    private static final Random RANDOM=new Random();
    private static final Pattern COLOR=Pattern.compile("^\\(([A-Za-z_]+)\\)(.*)$");
    private static String previous;
    private SplashManager(){}

    public static synchronized String choose(String[] fallback){
        List<String> splashes=load(fallback);
        if(splashes.isEmpty())for(String value:fallback)splashes.add(value);
        String next=splashes.get(RANDOM.nextInt(splashes.size()));
        if(splashes.size()>1)while(next.equals(previous))next=splashes.get(RANDOM.nextInt(splashes.size()));
        previous=next;
        return next;
    }

    private static List<String> load(String[] fallback){
        Path file=Paths.get(net.minecraft.client.Minecraft.getMinecraft().mcDataDir.getAbsolutePath(),"Backspace","splashes.txt");
        List<String> lines=new ArrayList<String>();
        try{
            Files.createDirectories(file.getParent());
            if(!Files.exists(file)){
                List<String> defaults=new ArrayList<String>();
                for(String value:fallback)defaults.add(toEditable(value));
                Files.write(file,defaults,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            }
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
                String value=line.trim();
                if(value.isEmpty()||value.startsWith("#"))continue;
                lines.add(parse(value));
            }
        }catch(IOException ignored){}
        return lines;
    }

    private static String parse(String value){
        Matcher matcher=COLOR.matcher(value);
        if(!matcher.matches())return value;
        TextFormatting formatting=color(matcher.group(1));
        return formatting==null?value:formatting.toString()+matcher.group(2);
    }

    private static TextFormatting color(String name){
        try{
            TextFormatting formatting=TextFormatting.valueOf(name.toUpperCase(Locale.ROOT));
            return formatting.isColor()?formatting:null;
        }catch(IllegalArgumentException ignored){return null;}
    }

    private static String toEditable(String value){
        if(value.length()>=2&&value.charAt(0)==(char)167){
            TextFormatting formatting=TextFormatting.fromColorIndex(Integer.parseInt(value.substring(1,2),16));
            if(formatting!=null)return "("+formatting.getFriendlyName()+")"+value.substring(2);
        }
        return value;
    }
}
