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

/** Actual VM execution distinguishes initial windows, unlimited execution and restricted permissions. */
public final class AdaptiveRenderBudgetSmokeTest {
    public static void verify() throws Exception {
        boolean enabled = Configs.ADAPTIVE_RENDER_BUDGET.value;
        int renderBase = Configs.RENDER_INSTRUCTION_BASE.value;
        int initBase = Configs.INIT_INSTRUCTION_BASE.value;
        try {
            require(Configs.RENDER_INSTRUCTION_CEILING == -1 && Configs.INIT_INSTRUCTION_CEILING == -1,
                    "Client initialization/render ceilings are unlimited constants");
            require(Configs.PERFORMANCE.children.stream().noneMatch(config ->
                            config.id.equals("render_instruction_ceiling") || config.id.equals("init_instruction_ceiling")),
                    "Hard ceilings are absent from editable settings");
            require(Configs.RENDER_INSTRUCTION_BASE.defaultValue == 8192
                            && Configs.INIT_INSTRUCTION_BASE.defaultValue == 65536,
                    "Existing base defaults remain configurable");
            Configs.ADAPTIVE_RENDER_BUDGET.value = true;
            Configs.RENDER_INSTRUCTION_BASE.value = 8192;
            for (int attempt = 0; attempt < 2; attempt++) {
                try (var fixture = new Owner()) {
                    var result = fixture.runtime.run(fixture.runtime.load("unlimited-render",
                            "hits=0; for i=1,40000 do hits=hits+1 end; "
                                    + "assert(avatar:getMaxRenderCount()==-1); return hits"), fixture.avatar.render);
                    require(result != null && result.arg1().checkint() == 40000,
                            "Rendering crosses its initial window without replaying side effects");
                    require(fixture.runtime.getInstructions() > 65536 && fixture.avatar.render.max == -1
                                    && fixture.avatar.render.remaining == -1,
                            "Unlimited rendering still records work without exhausting remaining allowance");
                    require(fixture.runtime.particleCallbackAllowance(true) == -1,
                            "Unlimited rendering remains available to native particle callbacks");
                    int previous = fixture.avatar.render.getTotal();
                    require(fixture.runtime.run(fixture.runtime.load("second-render",
                                    "local n=0; for i=1,1000 do n=n+1 end; return n"), fixture.avatar.render) != null
                                    && fixture.avatar.render.getTotal() > previous,
                            "Unlimited remaining values do not reset cumulative same-frame instruction counts");
                }
            }
            Configs.INIT_INSTRUCTION_BASE.value = 8192;
            for (int attempt = 0; attempt < 2; attempt++) {
                try (var fixture = new Owner()) {
                    var runtime = new FiguraLuaRuntime(fixture.avatar, Map.of("main",
                            "hits=0; start=avatar:getCurrentInstructions(); for i=1,40000 do hits=hits+1 end; "
                                    + "assert(hits==40000 and start<8192 and avatar:getCurrentInstructions()>65536); "
                                    + "assert(avatar:getMaxInitCount()==-1)"));
                    require(runtime.init(null) && fixture.avatar.init.max == -1 && fixture.avatar.init.remaining == -1,
                            "Each initialization starts from its configured base and has no hard stop");
                }
            }
            Configs.ADAPTIVE_RENDER_BUDGET.value = false;
            try (var fixture = new Owner()) {
                var result = fixture.runtime.run(fixture.runtime.load("legacy", "while true do end"), fixture.avatar.render);
                require(result == null && fixture.avatar.scriptError && fixture.avatar.render.max == 8192,
                        "Disabling adaptation preserves the existing permission budget");
            }
            Configs.ADAPTIVE_RENDER_BUDGET.value = true;
            for (int permission : new int[]{0, 4096}) {
                try (var fixture = new Owner()) {
                    fixture.avatar.permissions.insert(Permissions.RENDER_INST, permission,
                            org.figuramc.figura.FiguraMod.MOD_ID);
                    fixture.avatar.render.reset(permission);
                    require(FiguraLuaRuntime.instructionCeiling(fixture.avatar, Permissions.RENDER_INST) == permission,
                            "Explicitly restricted rendering cannot borrow unlimited execution");
                    require(fixture.runtime.run(fixture.runtime.load("restricted-render", "while true do end"),
                                    fixture.avatar.render) == null && fixture.avatar.scriptError,
                            "Restricted rendering still stops the actual Lua VM");
                }
                try (var fixture = new Owner()) {
                    fixture.avatar.permissions.insert(Permissions.INIT_INST, permission,
                            org.figuramc.figura.FiguraMod.MOD_ID);
                    require(FiguraLuaRuntime.instructionCeiling(fixture.avatar, Permissions.INIT_INST) == permission,
                            "Explicitly restricted initialization stays finite");
                    var runtime = new FiguraLuaRuntime(fixture.avatar, Map.of("main", "while true do end"));
                    require(!runtime.init(null) && fixture.avatar.scriptError,
                            "Restricted initialization still stops the actual Lua VM");
                }
            }
            var unlimited = new AdaptiveInstructionWindow(16384, -1);
            require(unlimited.threshold() == 16384, "Configured base controls the initial window");
            while (unlimited.threshold() < Integer.MAX_VALUE) {
                require(unlimited.grow(unlimited.threshold()), "Unlimited windows grow without a hard stop");
            }
            require(unlimited.unlimited() && unlimited.grow(Integer.MAX_VALUE),
                    "LuaJ's largest hook count does not become an execution ceiling");
            var finite = new AdaptiveInstructionWindow(8192, 16384);
            require(finite.grow(8192) && !finite.grow(16384), "Finite permission windows retain their hard stop");
            verifyInputs();
        } finally {
            Configs.ADAPTIVE_RENDER_BUDGET.value = enabled;
            Configs.RENDER_INSTRUCTION_BASE.value = renderBase;
            Configs.INIT_INSTRUCTION_BASE.value = initBase;
        }
        System.out.println("OMM_NATIVE_UNLIMITED_BUDGET_OK render_base=8192 init_base=65536 "
                + "ceiling=-1 editable_bases=true restricted_permissions=retained");
    }

    private static void verifyInputs() {
        for (InstructionCeilingConfig input : new InstructionCeilingConfig[]{
                Configs.RENDER_INSTRUCTION_BASE, Configs.INIT_INSTRUCTION_BASE}) {
            require(input.parent == Configs.PERFORMANCE, "Base inputs remain in Performance");
            for (int value : new int[]{8192, 32768, 65536, Integer.MAX_VALUE}) {
                require(input.parseValue(Integer.toString(value)) == value
                                && input.inputType.validator.test(Integer.toString(value)),
                        "Base inputs preserve their existing valid range");
            }
            for (String invalid : new String[]{"8", "8191", "0", "-1", "2147483648", "1.5"}) {
                require(!input.inputType.validator.test(invalid), "Invalid base input is rejected before applying");
                try {
                    input.parseValue(invalid);
                    throw new AssertionError("Base input accepted: " + invalid);
                } catch (IllegalArgumentException expected) {
                    // File loading and programmatic updates enforce the same range as the UI.
                }
            }
        }
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
