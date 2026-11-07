package top.aihmc.omm;

import java.nio.file.Path;
import java.util.Map;

/** Runs real Figura Lua against original vanilla-model APIs without a game client. */
public final class FiguraLuaProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        FabricRuntimeInputs.configure(output.resolve("figura-config"), Path.of(System.getProperty("omm.preview.modJar")));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        boolean playerWindow = args.length > 1 && args[1].equals("player-window");
        String mode = args.length > 2 ? args[2] : "gpu";
        if (!playerWindow || !mode.equals("gpu")) {
            Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethod("initRenderThread").invoke(null);
        }
        Path model = Path.of(args[0]);
        Object data = FiguraWorker.compileModel(model);
        if (playerWindow) {
            var tag = (net.minecraft.nbt.CompoundTag) data;
            var scripts = new net.minecraft.nbt.CompoundTag();
            scripts.putByteArray("script", "models:setVisible(false)".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            tag.put("scripts", scripts);
            tag.getCompoundOrEmpty("metadata").remove("autoScripts");
        }
        Object owner = FiguraWorker.createRuntime(data, model);
        try (var resources = new PreviewAvatarResources(owner)) {
            if (playerWindow) {
                Object lua = FiguraLuaInput.initialize(owner);
                Object renderer = owner.getClass().getField("renderer").get(owner);
                try (var batches = new FiguraBatches(renderer, lua.getClass().getField("vanilla_model").get(lua))) {
                    var initial = batches.drawBatches();
                    long visible = 0;
                    for (Object batch : initial) {
                        Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
                        if ((boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                            visible++;
                        }
                    }
                    if (visible != 12) {
                        throw new AssertionError("Expected only the 12 original player parts, got " + visible);
                    }
                    Object api = lua.getClass().getField("vanilla_model").get(lua);
                    Object player = api.getClass().getField("PLAYER").get(api);
                    var visibility = player.getClass().getMethod("setVisible", Boolean.class);
                    visibility.invoke(player, false);
                    batches.refresh();
                    var hidden = batches.drawBatches();
                    if (hidden.size() != initial.size()) {
                        throw new AssertionError("Player visibility changed fixed batch topology");
                    }
                    for (int index = 0; index < hidden.size(); index++) {
                        Object batch = hidden.get(index);
                        Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
                        if ((boolean) semantics.getClass().getMethod("visible").invoke(semantics)
                                || batch.getClass().getMethod("mesh").invoke(batch)
                                    != initial.get(index).getClass().getMethod("mesh").invoke(initial.get(index))) {
                            throw new AssertionError("Player hide left visible geometry or changed mesh identity");
                        }
                    }
                    visibility.invoke(player, new Object[]{null});
                    batches.refresh();
                    if (difference(OmniMorphBatches.transform(initial, false), batches.transform(false), initial.size()) > 1e-6) {
                        throw new AssertionError("Player hide/restore changed geometry");
                    }
                    System.out.println("VANILLA PLAYER FIXED SLOTS PASS slots=" + initial.size());
                    if (mode.equals("gpu")) {
                        OmniMorphGpuWindow.run(initial, batches.transform(false), batches.textureBytes(), null);
                    } else {
                        Object backend = OmniMorphBatches.backend(mode.equals("simd"));
                        CpuPreviewWindow.run("Original vanilla player " + mode,
                            OmniMorphBatches.transform(initial, backend), batches.triangleIndices(), batches.textureBytes(),
                            new OmniMorphCpuFrames(frame -> {
                                batches.refresh();
                                return batches.drawBatches();
                            }, backend));
                    }
                    System.out.println("FIGURA VANILLA ONLY WINDOW PASS visibleParts=" + visible);
                }
                return;
            }
            if (args.length > 1 && args[1].equals("model")) {
                Object lua = FiguraLuaInput.initialize(owner);
                Object api = lua.getClass().getField("vanilla_model").get(lua);
                System.out.println("FIGURA MODEL LUA PLAYER vertices=" + new VanillaPlayerGeometry(false).vertices(api).length / 9);
                Object renderer = owner.getClass().getField("renderer").get(owner);
                try (var batches = new FiguraBatches(renderer, api)) {
                    var baseline = batches.transform(false);
                    Object head = api.getClass().getField("HEAD").get(api);
                    var rotate = head.getClass().getMethod("setRot", Object.class, Double.class, Double.class);
                    Object previous = head.getClass().getMethod("getRot").invoke(head);
                    rotate.invoke(head, 0d, 45d, 0d);
                    batches.refresh();
                    var rotated = batches.transform(false);
                    double change = difference(baseline, rotated, baseline.size() - 12);
                    if (change < 1e-6) {
                        throw new AssertionError("Original HEAD rotation did not reach bound Figura geometry");
                    }
                    batches.refresh();
                    if (difference(rotated, batches.transform(false), rotated.size()) > 1e-6) {
                        throw new AssertionError("Repeated refresh accumulated vanilla binding transforms");
                    }
                    rotate.invoke(head, previous, null, null);
                    batches.refresh();
                    if (difference(baseline, batches.transform(false), baseline.size()) > 1e-6) {
                        throw new AssertionError("Original binding state did not restore");
                    }
                    System.out.println("FIGURA VANILLA BINDING PASS customGeometryDelta=" + change + " restored=true stableRefresh=true");
                }
                return;
            }
            Class<?> runtimeType = Class.forName("org.figuramc.figura.lua.FiguraLuaRuntime");
            Object lua = runtimeType.getConstructor(owner.getClass(), Map.class).newInstance(owner,
                Map.of("script", "vanilla_model.PLAYER:setVisible(false)"));
            runtimeType.getMethod("setInstructionLimit", int.class).invoke(lua, 1000000);
            boolean initialized = (boolean) runtimeType.getMethod("init", Class.forName("net.minecraft.nbt.ListTag"))
                .invoke(lua, new Object[]{null});
            if (!initialized || owner.getClass().getField("scriptError").getBoolean(owner)) {
                throw new AssertionError("Original Lua initialization failed");
            }
            Object api = runtimeType.getField("vanilla_model").get(lua);
            if (new VanillaPlayerGeometry(false).vertices(api).length != 0) {
                throw new AssertionError("Executed Lua did not hide the original player mesh");
            }
            System.out.println("FIGURA ORIGINAL LUA PASS scriptHiddenPlayer=true");
        }
    }

    private static double difference(java.util.List<double[]> first, java.util.List<double[]> second, int limit) {
        if (first.size() != second.size()) {
            throw new AssertionError("Binding changed batch topology");
        }
        double maximum = 0;
        for (int batch = 0; batch < limit; batch++) {
            if (first.get(batch).length != second.get(batch).length) {
                throw new AssertionError("Binding changed vertex topology");
            }
            for (int index = 0; index < first.get(batch).length; index++) {
                if (index % 9 < 3) {
                    maximum = Math.max(maximum, Math.abs(first.get(batch)[index] - second.get(batch)[index]));
                }
            }
        }
        return maximum;
    }
}
