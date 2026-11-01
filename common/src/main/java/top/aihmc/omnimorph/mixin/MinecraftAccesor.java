package top.aihmc.omnimorph.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccesor {
    @Accessor("metricsRecorder")
    MetricsRecorder figura$invokeGetMetricsRecorder();
}
