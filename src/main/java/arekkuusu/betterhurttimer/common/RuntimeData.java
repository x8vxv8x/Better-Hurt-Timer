package arekkuusu.betterhurttimer.common;

import arekkuusu.betterhurttimer.api.capability.Capabilities;
import arekkuusu.betterhurttimer.api.capability.data.HurtSourceData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RuntimeData {

    public static final Map<String, Integer> DAMAGE_SOURCE_TIMES = new LinkedHashMap<>();
    public static final Map<ResourceLocation, Double> ATTACK_THRESHOLDS = new LinkedHashMap<>();
    public static final Map<ResourceLocation, Double> ATTACK_ITEM_THRESHOLDS = new LinkedHashMap<>();

    public static final Field TICKS_SINCE_LAST_SWING;
    private static final ServerClock SERVER_CLOCK = new ServerClock();
    private static boolean serverClockRegistered;
    private static long serverTick;

    static {
        TICKS_SINCE_LAST_SWING = ObfuscationReflectionHelper.findField(EntityLivingBase.class, "field_184617_aD");
        TICKS_SINCE_LAST_SWING.setAccessible(true);
    }

    private RuntimeData() {
    }

    public static void initServerClock() {
        if (!serverClockRegistered) {
            MinecraftForge.EVENT_BUS.register(SERVER_CLOCK);
            serverClockRegistered = true;
        }
    }

    public static void resetServerTick() {
        serverTick = 0L;
    }

    public static long serverTick() {
        return serverTick;
    }

    public static boolean hasCustomAttackThreshold(@Nullable Entity entity) {
        ResourceLocation location = getEntityLocation(entity);
        return location != null && ATTACK_THRESHOLDS.containsKey(location);
    }

    @Nullable
    public static ResourceLocation getEntityLocation(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        EntityEntry entry = EntityRegistry.getEntry(entity.getClass());
        return entry != null ? entry.getRegistryName() : null;
    }

    public static Optional<HurtSourceData> getConfiguredSource(EntityLivingBase entity, DamageSource source) {
        String damageType = source.getDamageType();
        Integer waitTime = DAMAGE_SOURCE_TIMES.get(damageType);
        if (waitTime == null) {
            return Optional.empty();
        }
        return getSource(entity, damageType, waitTime);
    }

    public static Optional<HurtSourceData> getFixedSource(EntityLivingBase entity, DamageSource source, int waitTime) {
        String damageType = source.getDamageType();
        return getSource(entity, damageType, waitTime);
    }

    private static Optional<HurtSourceData> getSource(EntityLivingBase entity, String damageType, int waitTime) {
        return Capabilities.hurt(entity).map(capability -> capability.sourceData(damageType, waitTime));
    }

    private static class ServerClock {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                serverTick++;
            }
        }
    }
}


