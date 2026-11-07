package top.aihmc.omm.preview;

import java.util.List;
import java.util.Set;

public final class YsmVisibilityDefaultsTest {
    public static void main(String[] args) {
        String source = """
            {"animations":{
              "pre_parallel0":{"bones":{
                "Scalar":{"scale":0},"vector":{"scale":[0,0,0]},
                "metadata":{"scale":{"0":{"post":[0,0,0],"bezier_linked":false}}},
                "dynamic":{"scale":"q.is_running ? 0 : 1"},
                "pre_nonzero":{"scale":{"0":{"pre":[1,1,1],"post":[0,0,0]}}},
                "keys":{"scale":{"0":[0,0,0],"1":{"pre":[0,0,0],"post":[0,0,0],"lerp_mode":"linear"}}}
              }},
              "animation.model.parallel_7":{"bones":{"other":{"scale":0}}},
              "pre_parallel8":{"bones":{"outside_slot":{"scale":0}}},
              "idle":{"bones":{"action_only":{"scale":0}}}
            }}
            """;
        Set<String> hidden = YsmVisibilityDefaults.collect(List.of(source));
        if (!hidden.equals(Set.of("scalar", "vector", "keys", "other", "metadata"))) {
            throw new AssertionError("OMP base hidden semantics differ: " + hidden);
        }
        Set<String> overridden = YsmVisibilityDefaults.collect(
            List.of(source, "{\"animations\":{\"pre_parallel0\":{\"bones\":{\"Scalar\":{\"scale\":1}}}}}"));
        if (!overridden.equals(Set.of("other"))) {
            throw new AssertionError("A later animation resource did not own the duplicate clip name: " + overridden);
        }
        String controlled = """
            {"animations":{
              "animation.startup":{"bones":{"controller_hidden":{"scale":0}}},
              "gated":{"bones":{"conditional_hidden":{"scale":0}}},
              "later":{"bones":{"later_only":{"scale":0}}}},
             "animation_controllers":{"controller.player.main":{
               "initial_state":"initial","states":{
                 "initial":{"animations":["STARTUP",{"gated":"q.is_running"}],
                   "transitions":[{"next":"unsupported.runtime_call()"}]},
                 "next":{"animations":[{"later":"unsupported.runtime_condition()"}]}}}}}
            """;
        if (!YsmVisibilityDefaults.collect(List.of(controlled)).equals(Set.of("controller_hidden"))) {
            throw new AssertionError("Initial unconditional controller reference was not applied");
        }
        var running = new com.google.gson.JsonObject();
        running.addProperty("running", true);
        if (!YsmVisibilityDefaults.collect(List.of(controlled), new PreviewMolang(running, 1))
                .equals(Set.of("controller_hidden", "conditional_hidden"))) {
            throw new AssertionError("Initial controller condition did not use actual simulated state");
        }
        running.addProperty("running", false);
        if (!YsmVisibilityDefaults.collect(List.of(controlled), new PreviewMolang(running, 1))
                .equals(Set.of("controller_hidden"))) {
            throw new AssertionError("Inactive controller condition hid an attachment");
        }
        String replacement = """
            {"animation_controllers":{"controller.player.main":{
              "initial_state":"other","states":{"other":{"animations":["later"]}}}}}
            """;
        if (!YsmVisibilityDefaults.collect(List.of(controlled, replacement)).equals(Set.of("later_only"))) {
            throw new AssertionError("Later controller resource did not override its initial state");
        }
        String arrayController = """
            {"animation_controllers":[{"name":"controller.array","initial_state":"absent","states":[
              {"id":"first","animations":{"gated":""}},
              {"id":"second","animations":["later"]}]}]}
            """;
        if (!YsmVisibilityDefaults.collect(List.of(controlled, replacement, arrayController))
                .equals(Set.of("later_only", "conditional_hidden"))) {
            throw new AssertionError("Array states, initial fallback or empty unconditional weight differs from OMP");
        }
        System.out.println(
            "YSM visibility PASS: base slots, scalar/vector/keyframe zero, pre/post and dynamic exclusion");
    }
}
