package arekkuusu.betterhurttimer.api;

import arekkuusu.betterhurttimer.api.capability.HurtCapability;
import arekkuusu.betterhurttimer.common.Events;
import arekkuusu.betterhurttimer.common.RuntimeData;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;

import javax.annotation.Nullable;

public final class BetterHurtTimerApi {

    public static final String DEFAULT_DIRECT_ATTACK_CHANNEL = "default";

    private static final ThreadLocal<DirectAttackContext> DIRECT_ATTACK_CONTEXT = new ThreadLocal<>();

    private BetterHurtTimerApi() {
    }

    public static boolean attackEntityFrom(Entity target, DamageSource source, float amount, String channel, int cooldownTicks) {
        Entity attacker = source != null ? source.getImmediateSource() : null;
        return attackEntityFrom(attacker, target, source, amount, channel, cooldownTicks);
    }

    public static boolean attackEntityFrom(Entity attacker, Entity target, DamageSource source, float amount, String channel, int cooldownTicks) {
        if (target == null || source == null) {
            return false;
        }

        DirectAttackContext context = beginDirectAttack(attacker, target, channel, cooldownTicks);
        try {
            return context.isAllowed() && target.attackEntityFrom(source, amount);
        } finally {
            context.close();
        }
    }

    public static DirectAttackContext beginDirectAttack(Entity attacker, @Nullable Entity target, String channel, int cooldownTicks) {
        String normalizedChannel = normalizeChannel(channel);
        DirectAttackContext parent = DIRECT_ATTACK_CONTEXT.get();
        boolean allowed = attacker != null;

        if (allowed && !attacker.world.isRemote && cooldownTicks > 0) {
            long serverTick = RuntimeData.serverTick();
            int attackMarker = Events.getAttackAttemptMarker(attacker);
            allowed = HurtCapability.get(attacker)
                    .map(capability -> capability.allowDirectAttackAttempt(normalizedChannel, serverTick, attackMarker, cooldownTicks))
                    .orElse(true);
        }

        DirectAttackContext context = new DirectAttackContext(parent, attacker, target, normalizedChannel, cooldownTicks, allowed);
        DIRECT_ATTACK_CONTEXT.set(context);
        return context;
    }

    public static DirectAttackContext beginDirectAttack(Entity attacker, String channel, int cooldownTicks) {
        return beginDirectAttack(attacker, null, channel, cooldownTicks);
    }

    @Nullable
    public static DirectAttackContext getActiveDirectAttack(Entity attacker, @Nullable Entity target) {
        DirectAttackContext context = DIRECT_ATTACK_CONTEXT.get();
        if (context != null && context.isFor(attacker, target)) {
            return context;
        }
        return null;
    }

    public static String getActiveDirectAttackChannel(Entity attacker, @Nullable Entity target) {
        DirectAttackContext context = getActiveDirectAttack(attacker, target);
        return context != null ? context.getChannel() : DEFAULT_DIRECT_ATTACK_CHANNEL;
    }

    public static String normalizeChannel(String channel) {
        if (channel == null) {
            return DEFAULT_DIRECT_ATTACK_CHANNEL;
        }

        String trimmed = channel.trim();
        return trimmed.isEmpty() ? DEFAULT_DIRECT_ATTACK_CHANNEL : trimmed;
    }

    public static final class DirectAttackContext implements AutoCloseable {

        private final DirectAttackContext parent;
        private final Entity attacker;
        private final Entity target;
        private final String channel;
        private final int cooldownTicks;
        private final boolean allowed;
        private boolean closed;

        private DirectAttackContext(DirectAttackContext parent, Entity attacker, Entity target, String channel, int cooldownTicks, boolean allowed) {
            this.parent = parent;
            this.attacker = attacker;
            this.target = target;
            this.channel = channel;
            this.cooldownTicks = cooldownTicks;
            this.allowed = allowed;
        }

        public boolean isAllowed() {
            return this.allowed;
        }

        public String getChannel() {
            return this.channel;
        }

        public int getCooldownTicks() {
            return this.cooldownTicks;
        }

        private boolean isFor(Entity attacker, @Nullable Entity target) {
            return this.attacker != null && this.attacker == attacker && (this.target == null || target == null || this.target == target);
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }

            this.closed = true;
            if (DIRECT_ATTACK_CONTEXT.get() == this) {
                if (this.parent != null) {
                    DIRECT_ATTACK_CONTEXT.set(this.parent);
                } else {
                    DIRECT_ATTACK_CONTEXT.remove();
                }
            }
        }
    }
}
