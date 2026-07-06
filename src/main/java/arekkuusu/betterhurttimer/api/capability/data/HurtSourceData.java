package arekkuusu.betterhurttimer.api.capability.data;

public class HurtSourceData {

    public final HurtSourceInfo info;
    public double lastHurtAmount;
    private long lastHurtTick = Long.MIN_VALUE;

    public HurtSourceData(HurtSourceInfo info) {
        this.info = info;
    }

    public boolean hasTriggered() {
        return this.lastHurtTick != Long.MIN_VALUE;
    }

    public boolean canApply(long worldTime) {
        return !this.hasTriggered() || worldTime - this.lastHurtTick > this.info.waitTime;
    }

    public void trigger(long worldTime) {
        this.lastHurtTick = worldTime;
        this.lastHurtAmount = 0;
    }
}
