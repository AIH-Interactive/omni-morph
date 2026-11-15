package top.aihmc.omnimorph.config;

import org.figuramc.figura.config.ConfigType;

/** Exact persisted integer with a logarithmic settings slider. */
public final class InstructionCeilingConfig extends ConfigType.PositiveIntConfig {
    public InstructionCeilingConfig(String name, ConfigType.Category category) {
        super(name, category, 32768);
    }

    @Override
    public Integer parseValue(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException("Instruction ceiling must be positive");
        }
        return parsed;
    }

    public static double sliderPosition(int value) {
        return Math.log(Math.max(1, value)) / Math.log(2) / 31;
    }

    public static int sliderValue(double position) {
        double clamped = Math.clamp(position, 0, 1);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Math.pow(2, clamped * 31)));
    }
}
