package org.figuramc.figura.model.ysm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class YsmBaselineSmokeTest {
    private YsmBaselineSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ysm-baseline-");
        try {
            Path model = root.resolve("valid");
            Files.createDirectories(model);
            Files.writeString(model.resolve("main.json"), """
                    {"minecraft:geometry":[{"description":{"identifier":"fixture"},
                      "bones":[{"name":"root","pivot":[0,0,0]}]}]}
                    """);
            YsmModelScanner.ScanSummary valid = YsmModelScanner.scan(root);
            require(valid.reports().size() == 1 && valid.validCount() == 1, "valid minimal model");
            YsmModelScanner.validateGate(root, valid, null);

            Path functions = model.resolve("functions");
            Files.createDirectories(functions);
            Files.writeString(functions.resolve("known.molang"),
                    "v.counter=q.anim_time; t.local=c.weight; return v.counter+t.local;");
            Files.writeString(model.resolve("main.animation_controllers.json"), """
                    {"animation_controllers":{"fixture":{"states":{"default":{
                      "on_entry":["v.counter=q.anim_time; t.local=c.weight;"],
                      "transitions":[{"default":"q.anim_time>0"}]}}}}}
                    """);
            require(YsmModelScanner.scanOne(model).valid(), "scanner uses formal query/v/c/t registration");
            Files.writeString(functions.resolve("unknown.molang"), "return q.not_registered;");
            require(YsmModelScanner.scanOne(model).errors().stream().anyMatch(error ->
                            error.contains("unknown.molang") && error.contains("not_registered")),
                    "function body is compiled and unknown binding identifies its resource");
            Files.delete(functions.resolve("unknown.molang"));
            Files.writeString(model.resolve("main.animation_controllers.json"), """
                    {"animation_controllers":{"fixture":{"states":{"default":{
                      "on_entry":["q.not_registered;"]}}}}}
                    """);
            require(YsmModelScanner.scanOne(model).errors().stream().anyMatch(error ->
                            error.contains("main.animation_controllers.json") && error.contains("fixture")
                                    && error.contains("default") && error.contains("not_registered")),
                    "controller compile failure identifies resource/controller/state/binding");
            Files.delete(model.resolve("main.animation_controllers.json"));

            Path baseline = root.resolve("baseline.json");
            Files.writeString(baseline, "{\"total\":1,\"issues\":{}}");
            YsmModelScanner.validateGate(root, valid, baseline);
            Files.writeString(baseline, "{\"total\":2,\"issues\":{}}");
            expectFailure(() -> YsmModelScanner.validateGate(root, valid, baseline), "candidate count gate");
            Files.writeString(baseline, """
                    {"total":1,"issues":{"valid":{"errors":[],"unsupported":["ysm.second_order"]}}}
                    """);
            expectFailure(() -> YsmModelScanner.validateGate(root, valid, baseline), "binding baseline changes fail");

            Files.write(root.resolve("old.ysm"), new byte[]{'Y', 'S', 'G', 'P', 0, 0, 0, 2});
            Files.write(root.resolve("new.ysm"), new byte[]{(byte) 239, (byte) 187, (byte) 191, 'Y', 'S', 'G', 'P'});
            Files.write(root.resolve("broken.ysm"), new byte[]{1, 2, 3});
            YsmModelScanner.ScanSummary candidates = YsmModelScanner.scan(root);
            require(candidates.reports().size() == 4 && candidates.invalidCount() == 3,
                    "encrypted and unrecognized candidates are counted");
            for (String name : new String[]{"old.ysm", "new.ysm"}) {
                require(YsmModelScanner.scanOne(root.resolve(name)).errors().getFirst()
                        .startsWith("INVALID_DATA: encrypted scan diagnostic sha256="), "encrypted source diagnostic " + name);
            }
            require(YsmModelScanner.scan(root.resolve("old.ysm")).reports().size() == 1,
                    "single encrypted input is counted");
            expectFailure(() -> YsmModelScanner.validateGate(root, candidates, null), "invalid candidate gate");

            Files.writeString(model.resolve("main.animation.json"), """
                    {"animations":{"animation.fixture":{"animation_length":1,
                      "timeline":{"0":"ysm.bone_param('root'); ysm.unknown_core();"}}}}
                    """);
            YsmModelScanner.ScanReport bindings = YsmModelScanner.scanOne(model);
            require(bindings.unsupportedBindings().contains("ysm.bone_param"), "unresolved reference contract is unsupported");
            require(bindings.unsupportedBindings().contains("ysm.unknown_core"), "unknown binding is unsupported");
            Files.writeString(model.resolve("main.animation.json"), """
                    {"animations":{"animation.fixture":{"animation_length":1,
                      "timeline":{"0":"ctrl.tac_gun_type; ysm.unknown_core();"}}}}
                    """);
            YsmModelScanner.ScanReport optional = YsmModelScanner.scanOne(model);
            require(optional.unavailableOptionalBindings().keySet().equals(java.util.Set.of("ctrl.tac_gun_type")),
                    "optional unavailable capabilities are classified separately from unknown core bindings");
            require(optional.unavailableOptionalBindings().get("ctrl.tac_gun_type").startsWith("CAPABILITY_UNAVAILABLE:"),
                    "optional capability report provides its unavailable reason");
            expectFailure(() -> YsmModelScanner.validateGate(model, YsmModelScanner.scan(model), null),
                    "optional unavailable is not silently accepted as full bridge support");
            Files.writeString(model.resolve("main.animation.json"), """
                    {"animations":{"animation.fixture":{"animation_length":1,
                      "timeline":{"0":"ysm.defer('layer',1); ysm.first_order('a',1); ysm.second_order('b',2); ysm.particle('minecraft:flame'); ysm.abs_particle('minecraft:flame');"}}}}
                    """);
            require(!YsmModelScanner.scanOne(model).unsupportedBindings().contains("ysm.defer"),
                    "scanner recognizes the formally implemented defer binding");
            require(YsmModelScanner.scanOne(model).unsupportedBindings().isEmpty(),
                    "scanner recognizes verified filter and particle bindings");
            Files.writeString(model.resolve("main.animation.json"), """
                    {"animations":{"animation.fixture":{"animation_length":1,
                      "timeline":{"0":"ysm.bone_param('root'); ysm.unknown_core();"}}}}
                    """);
            expectFailure(() -> YsmModelScanner.validateGate(model,
                    YsmModelScanner.scan(model), null), "unsupported binding gate");

            Files.writeString(model.resolve("main.animation.json"), "{\"animations\":{");
            require(!YsmModelScanner.scanOne(model).valid(), "malformed animation cannot pass");
            Files.writeString(model.resolve("main.animation.json"), "{\"animations\":{\"bad\":7}}");
            require(!YsmModelScanner.scanOne(model).valid(), "skipped clip cannot pass");
            Files.writeString(model.resolve("main.animation.json"), "{\"animations\":{}}");
            Files.writeString(model.resolve("main.animation_controllers.json"), """
                    {"animation_controllers":{"fixture":{"states":{"default":{
                      "animations":["missing_clip"],"transitions":[{"missing_state":"1"}]}}}}}
                    """);
            YsmModelScanner.ScanReport references = YsmModelScanner.scanOne(model);
            require(references.errors().stream().anyMatch(error -> error.contains("missing animation 'missing_clip'")),
                    "missing animation negative fixture: " + references.errors());
            require(references.errors().stream().anyMatch(error -> error.contains("missing transition target 'missing_state'")),
                    "missing state negative fixture");
            Files.writeString(model.resolve("main.animation_controllers.json"), "{");
            require(!YsmModelScanner.scanOne(model).valid(), "malformed controller cannot pass");
            Path empty = root.resolve("empty");
            Files.createDirectories(empty);
            expectFailure(() -> YsmModelScanner.validateGate(empty,
                    YsmModelScanner.scan(empty), null), "empty scan cannot pass");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
