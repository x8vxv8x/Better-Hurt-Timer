package arekkuusu.betterhurttimer;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SortingIndex(9999)
public class MixinCore implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.betterhurttimer.json");
    }

    @Override
    public String[] getASMTransformerClass() { return null; }
    @Override
    public String getModContainerClass() { return null; }
    @Nullable
    @Override
    public String getSetupClass() { return null; }
    @Override
    public void injectData(Map<String, Object> data) {}
    @Override
    public String getAccessTransformerClass() { return null; }
}
