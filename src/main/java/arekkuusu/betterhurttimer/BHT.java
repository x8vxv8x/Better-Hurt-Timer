package arekkuusu.betterhurttimer;

import arekkuusu.betterhurttimer.api.capability.HurtCapability;
import arekkuusu.betterhurttimer.common.RuntimeData;
import arekkuusu.betterhurttimer.common.command.CommandExport;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(
        modid = BHT.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION
)
public class BHT {

    public static final String MOD_ID = "betterhurttimer";

    public static final Logger LOG = LogManager.getLogger(Tags.MOD_NAME);

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        HurtCapability.init();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        reloadRuntimeCaches();
    }

    public static void reloadRuntimeCaches() {
        initAttackFrames();
        initDamageFrames();
        arekkuusu.betterhurttimer.common.Events.refreshConfigCaches();
    }

    public static void initAttackFrames() {
        RuntimeData.ATTACK_THRESHOLDS.clear();
        RuntimeData.ATTACK_ITEM_THRESHOLDS.clear();
        String patternAttackFrames = "^(.*:.*):((\\d*\\.)?\\d+)$";
        Pattern r = Pattern.compile(patternAttackFrames);
        for (String s : BHTConfig.CONFIG.attackFrames.attackThreshold) {
            Matcher m = r.matcher(s);
            if (m.matches()) {
                RuntimeData.ATTACK_THRESHOLDS.put(new ResourceLocation(m.group(1)), Double.parseDouble(m.group(2)));
            } else {
                BHT.LOG.warn("[Attack Frames Config] - String " + s + " is not a valid format");
            }
        }
        for (String s : BHTConfig.CONFIG.attackFrames.itemSource) {
            Matcher m = r.matcher(s);
            if (m.matches()) {
                RuntimeData.ATTACK_ITEM_THRESHOLDS.put(new ResourceLocation(m.group(1)), Double.parseDouble(m.group(2)));
            } else {
                BHT.LOG.warn("[Attack Frames Config] - String " + s + " is not a valid format");
            }
        }
    }

    public static void initDamageFrames() {
        RuntimeData.DAMAGE_SOURCE_TIMES.clear();
        String patternAttackFrames = "^([^:]+):(\\d+)$";
        Pattern r = Pattern.compile(patternAttackFrames);
        for (String s : BHTConfig.CONFIG.damageFrames.damageSource) {
            Matcher m = r.matcher(s);
            if (m.matches()) {
                RuntimeData.DAMAGE_SOURCE_TIMES.put(m.group(1), Integer.parseInt(m.group(2)));
            } else {
                BHT.LOG.warn("[Damage Frames Config] - String " + s + " is not a valid format");
            }
        }
    }

    @EventHandler
    public void onServerLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandExport());
    }

    @EventHandler
    public void onFingerprintViolation(FMLFingerprintViolationEvent event) {
        LOG.warn("Invalid fingerprint detected!");
    }
}
