package top.aihmc.omnimorph.model.outfit;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.permissions.Permissions;
import top.aihmc.omnimorph.config.InstructionCeilingConfig;
import top.aihmc.omnimorph.lua.AdaptiveInstructionWindow;

/** Generic VM execution, with no converted data or source-engine dependencies. */
public final class AdaptiveRenderBudgetSmokeTest {
    public static void verify() throws Exception {
        boolean enabled = Configs.ADAPTIVE_RENDER_BUDGET.value;
        int ceiling = Configs.RENDER_INSTRUCTION_CEILING.value;
        int base = Configs.RENDER_INSTRUCTION_BASE.value;
        try {
            require(Configs.RENDER_INSTRUCTION_CEILING.defaultValue == 32768, "Default hard ceiling");
            require(Configs.RENDER_INSTRUCTION_BASE.defaultValue == 8192, "Default base limit");
            require(Configs.RENDER_INSTRUCTION_BASE.parent == Configs.PERFORMANCE
                            && Configs.RENDER_INSTRUCTION_CEILING.parent == Configs.PERFORMANCE
                            && Configs.ADAPTIVE_RENDER_BUDGET.parent == Configs.PERFORMANCE,
                    "All budget controls belong in Performance settings");
            Configs.RENDER_INSTRUCTION_BASE.value = 8192;
            Configs.ADAPTIVE_RENDER_BUDGET.value = true;
            Configs.RENDER_INSTRUCTION_CEILING.value = 32768;
            try (var fixture = new Owner()) {
                var result = fixture.runtime.run(fixture.runtime.load("adaptive",
                        "hits=0; for i=1,6000 do hits=hits+1 end; return hits"), fixture.avatar.render);
                require(result != null && result.arg1().checkint() == 6000,
                        "One VM execution must grow beyond 8192 without replaying side effects");
                require(fixture.runtime.getInstructions() > 8192
                                && fixture.runtime.getInstructions() < 32768,
                        "Adaptive execution keeps its actual monotonically increasing instruction count");
                require(fixture.avatar.render.max == 32768, "Client ceiling reaches the real render counter");
            }
            try (var fixture = new Owner()) {
                var result = fixture.runtime.run(fixture.runtime.load("hard-stop", "while true do end"),
                        fixture.avatar.render);
                require(result == null && fixture.avatar.scriptError, "Hard ceiling stops an actual runaway VM");
                require(fixture.runtime.getInstructions() <= 32768, "Hard stop must not exceed configured ceiling");
            }
            Configs.ADAPTIVE_RENDER_BUDGET.value = false;
            try (var fixture = new Owner()) {
                var result = fixture.runtime.run(fixture.runtime.load("legacy", "while true do end"),
                        fixture.avatar.render);
                require(result == null && fixture.avatar.scriptError && fixture.avatar.render.max == 8192,
                        "Turning adaptation off preserves the existing permissions budget");
            }
            var window = new AdaptiveInstructionWindow(Integer.MAX_VALUE);
            int previous = 0;
            while (window.grow(window.threshold())) {
                require(window.threshold() > previous, "Threshold growth cannot overflow");
                previous = window.threshold();
            }
            require(window.threshold() == Integer.MAX_VALUE, "Largest signed-int ceiling remains exact");
            for (InstructionCeilingConfig input : new InstructionCeilingConfig[]{
                    Configs.RENDER_INSTRUCTION_BASE, Configs.RENDER_INSTRUCTION_CEILING}) {
                for (int value : new int[]{8192, 32768, 65536, Integer.MAX_VALUE}) {
                    require(input.parseValue(Integer.toString(value)) == value,
                            "Integer input accepts exact configured limits");
                    require(input.inputType.validator.test(Integer.toString(value)),
                            "Settings field accepts in-range values");
                }
                for (String invalid : new String[]{"8", "8191", "0", "-1", "2147483648", "1.5"}) {
                    require(!input.inputType.validator.test(invalid),
                            "Settings field must reject invalid intermediate text before updating config");
                    try {
                        input.parseValue(invalid);
                        throw new AssertionError("Input accepted an out-of-range limit: " + invalid);
                    } catch (IllegalArgumentException expected) {
                        // Exact bounds are enforced for UI, file loading and programmatic updates.
                    }
                }
            }
            require(new AdaptiveInstructionWindow(16384, 32768).threshold() == 16384,
                    "Configured base controls the initial instruction window");
            require(new AdaptiveInstructionWindow(65536, 32768).threshold() == 32768,
                    "Hard ceiling takes precedence when the base is larger");
            require(AdaptiveInstructionWindow.ceiling(0, true, 32768) == 0
                            && AdaptiveInstructionWindow.ceiling(4096, true, 32768) == 4096,
                    "Adaptation cannot grant script execution to blocked or low-trust models");
        } finally {
            Configs.ADAPTIVE_RENDER_BUDGET.value = enabled;
            Configs.RENDER_INSTRUCTION_CEILING.value = ceiling;
            Configs.RENDER_INSTRUCTION_BASE.value = base;
        }
        System.out.println("OMM_NATIVE_ADAPTIVE_BUDGET_OK base=8192 hard=32768 range=8192..2147483647");
    }

    private static final class Owner implements AutoCloseable {
        final Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "Generic adaptive budget");
        final FiguraLuaRuntime runtime;

        Owner() {
            avatar.load(new CompoundTag());
            runtime = new FiguraLuaRuntime(avatar, Map.of());
            require(runtime.init(new ListTag()), "Initialize actual Figura VM");
            avatar.render.reset(avatar.permissions.get(Permissions.RENDER_INST));
        }

        @Override
        public void close() {
            avatar.clean();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
