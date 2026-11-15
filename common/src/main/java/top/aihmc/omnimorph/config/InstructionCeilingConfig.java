package top.aihmc.omnimorph.config;

import org.figuramc.figura.config.ConfigType;
import org.figuramc.figura.config.InputType;

/** Validated integer input for client render instruction limits. */
public final class InstructionCeilingConfig extends ConfigType.InputConfig<Integer> {
    public InstructionCeilingConfig(String name, ConfigType.Category category, int defaultValue) {
        super(name, category, defaultValue, InputType.RENDER_INSTRUCTIONS);
    }

    @Override
    public Integer parseValue(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 8192) {
            throw new IllegalArgumentException("Instruction limit must be between 8192 and 2147483647");
        }
        return parsed;
    }

}
