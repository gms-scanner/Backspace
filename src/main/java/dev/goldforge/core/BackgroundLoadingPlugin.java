package dev.goldforge.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("BackspaceBackgroundTicks")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"dev.goldforge.core."})
public final class BackgroundLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass(){return new String[]{"dev.goldforge.core.BackgroundTransformer"};}
    public String getModContainerClass(){return null;}
    public String getSetupClass(){return null;}
    public void injectData(Map<String,Object> data){}
    public String getAccessTransformerClass(){return null;}
}
