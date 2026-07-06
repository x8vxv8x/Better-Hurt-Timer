package arekkuusu.betterhurttimer.api.capability.data;

public class HurtSourceData {

    public final int waitTime;
    private long lastHurtTick = Long.MIN_VALUE;

    public HurtSourceData(int waitTime) {
        this.waitTime = waitTime;
    }

    public boolean hasTriggered() {
        return this.lastHurtTick != Long.MIN_VALUE;
    }

    public boolean canApply(long worldTime) {
        return !this.hasTriggered() || worldTime - this.lastHurtTick > this.waitTime;
    }

    public void trigger(long worldTime) {
        this.lastHurtTick = worldTime;
    }
}
