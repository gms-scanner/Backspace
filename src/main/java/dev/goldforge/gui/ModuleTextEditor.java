package dev.goldforge.gui;
import dev.goldforge.ConfigManager;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
public final class ModuleTextEditor extends GuiScreen {
    private final Module module;
    private final GuiScreen parent;
    private final List<Field> fields=new ArrayList<Field>();
    private final List<GuiTextField> inputs=new ArrayList<GuiTextField>();
    public ModuleTextEditor(Module module){this(null,module);}
    public ModuleTextEditor(GuiScreen parent,Module module){this.parent=parent;this.module=module;}
    public static boolean supports(Module module){
        for(Field field:module.getClass().getFields())
            if(field.getType()==String.class&&!Modifier.isStatic(field.getModifiers())&&!Modifier.isFinal(field.getModifiers())
                    &&(!field.getName().equals("targetNames")||module.supportsEntitySelection()))return true;
        return false;
    }
    @Override public void initGui(){
        fields.clear();inputs.clear();buttonList.clear();
        for(Field field:module.getClass().getFields()){
            if(field.getType()!=String.class||Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers()))continue;
            if(field.getName().equals("targetNames")&&!module.supportsEntitySelection())continue;
            try{
                GuiTextField input=new GuiTextField(inputs.size(),fontRenderer,width/2-150,55+inputs.size()*42,300,20);
                input.setMaxStringLength(512);input.setText((String)field.get(module));
                fields.add(field);inputs.add(input);
            }catch(IllegalAccessException ignored){}
        }
        buttonList.add(new GuiButton(0,width/2-100,height-35,"Save"));
    }
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();drawCenteredString(fontRenderer,module.name+" text settings",width/2,20,0xFFFFD700);
        for(int i=0;i<inputs.size();i++){fontRenderer.drawString(fields.get(i).getName(),width/2-150,43+i*42,0xFFFFD700);inputs.get(i).drawTextBox();}
        super.drawScreen(x,y,partial);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        super.mouseClicked(x,y,button);for(GuiTextField input:inputs)input.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(key==1){mc.displayGuiScreen(parent);return;}
        for(GuiTextField input:inputs)if(input.isFocused()){input.textboxKeyTyped(c,key);return;}
        super.keyTyped(c,key);
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id!=0)return;
        for(int i=0;i<fields.size();i++)try{fields.get(i).set(module,inputs.get(i).getText());}catch(IllegalAccessException ignored){}
        ConfigManager.save();mc.displayGuiScreen(parent);
    }
    @Override public void updateScreen(){for(GuiTextField input:inputs)input.updateCursorCounter();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
