package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;

public final class PreviewMolangTest {
    public static void main(String[] args) throws Exception {
        JsonObject running = new JsonObject();
        running.addProperty("running", true);
        JsonObject ysm = new JsonObject();
        ysm.addProperty("food_level", 20);
        running.add("ysm", ysm);
        var active = new PreviewMolang(running, 42);
        JsonObject stopped = new JsonObject();
        stopped.addProperty("running", false);
        var idle = new PreviewMolang(stopped, 42);
        active.clock(0.5f, 1 / 60f);
        idle.clock(0.5f, 1 / 60f);
        var channel = active.compile("return query.is_running ? math.sin(query.anim_time * 180) * 30 : 0;");
        close(active.evaluate(channel), 30, "Real trig/time/running evaluation");
        close(active.evaluate(active.compile("return ysm.food_level < 6 ? 5 : 10;")), 10,
            "Configured real-model host scalar");
        close(idle.evaluate(channel), 0, "Simulated running=false");
        var counter = active.compile("variable.counter = variable.counter + 1; return variable.counter;");
        close(active.evaluate(counter), 1, "First variable update");
        close(active.evaluate(counter), 2, "Persistent instance state");
        close(idle.evaluate(counter), 1, "Separate instance state");
        var temporary = active.compile("temp.counter = temp.counter + 1; return temp.counter;");
        close(active.evaluate(temporary), 1, "Temporary variable");
        close(active.evaluate(temporary), 1, "Temporary scope reset");
        var random = active.compile("return math.random(0, 1);");
        for (int index = 0; index < 20; index++) {
            close(active.evaluate(random), idle.evaluate(random), "Fixed seed sequence");
        }
        var movingState = com.google.gson.JsonParser.parseString("""
            {"query":{"ground_speed":0.75,"vertical_speed":-0.5,"yaw_speed":2},
             "ctrl":{"run":true,"idle":false}}
            """).getAsJsonObject();
        var moving = new PreviewMolang(movingState, 42);
        close(moving.evaluate(moving.compile("q.ground_speed")), 0.75f, "Configured movement speed");
        close(moving.evaluate(moving.compile("q.vertical_speed")), -0.5f, "Configured vertical speed");
        close(moving.evaluate(moving.compile("q.yaw_speed")), 2, "Explicit query override");
        close(moving.evaluate(moving.compile("ctrl.run")), 1, "Explicit simulated controller argument");
        close(moving.evaluate(moving.compile("ctrl.idle")), 0, "No automatic movement-state derivation");
        close(idle.evaluate(idle.compile("ctrl.run")), 0, "Controller arguments remain instance isolated");
        try {
            active.evaluate(active.compile("return query.unprovided_test_query;"));
            throw new AssertionError("Missing host query silently accepted");
        } catch (UnsupportedOperationException expected) {
            if (!expected.getMessage().contains("states.query.unprovided_test_query")) {
                throw expected;
            }
        }
        try {
            active.evaluate(active.compile("loop(1024, { loop(1024, { v.counter = v.counter + 1; }); });"));
            throw new AssertionError("Molang execution budget did not stop nested work");
        } catch (MolangExecutionBudget.Exceeded expected) {
            // The original runtime must enforce its real execution budget.
        }
        System.out.println(
            "Molang PASS: real evaluation, simulated state, shared AST/isolated variables, seed and budget");
    }

    private static void close(float actual, float expected, String label) {
        if (Math.abs(actual - expected) > 1e-5f) {
            throw new AssertionError(label + ": " + actual + " != " + expected);
        }
    }
}
