package top.aihmc.omnimorph.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.screens.WardrobeScreen;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.permissions.Permissions;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.PreviewHierarchy;
import top.aihmc.omnimorph.model.outfit.LocalOutfitPreview;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

/** Detached JSON built on demand from already loaded state, never by invoking model code. */
public final class PerformanceSnapshot {
    private static final String PROCESS_ID = UUID.randomUUID().toString();

    private PerformanceSnapshot() {
    }

    public static JsonObject capture() {
        var client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            throw new IllegalStateException("PERFORMANCE_CAPTURE_REQUIRES_CLIENT_THREAD");
        }
        var user = AvatarManager.getLoadedUser(FiguraMod.getLocalPlayerUUID());
        var worn = user == null ? null : user.getOutfit();
        Avatar world = user == null ? null : worn == null ? user.getMainAvatar() : worn.base();
        OutfitComposition worldComposition = worn == null ? null : worn.composition();
        var draft = LocalOutfitPreview.performanceSession();
        if (draft != null) {
            world = draft.outfitBase();
            worldComposition = draft.outfitComposition();
        }
        PerformanceSelection selection = null;
        if (client.gui.screen() instanceof WardrobeScreen wardrobe) {
            selection = wardrobe.performanceSelection();
        } else if (client.gui.screen() instanceof ModelLibraryScreen library) {
            selection = library.performanceSelection();
        }
        EntityPreview widget = preview(client.gui.screen());
        LibraryPreviewSession session = widget == null ? null : widget.performanceSession();
        Avatar observed = session == null ? world : session.outfitBase();
        OutfitComposition composition = session == null ? worldComposition : session.outfitComposition();
        String context = session == null ? "world" : "preview";
        var snapshot = captureObserved(selection, observed, composition, context);
        snapshot.add("worldModel", model(world));
        snapshot.add("worldInstances", instances(worldComposition));
        snapshot.addProperty("selectionState", session == null ? "not_previewing" : session.status().stage().name());
        snapshot.addProperty("paused", client.isPaused());
        snapshot.getAsJsonObject("metrics").add("client.fps",
                metric(client.getFps(), "frames/s", "client", null, "ok", "Minecraft FPS counter; engine window"));
        var fps = snapshot.getAsJsonObject("metrics").getAsJsonObject("client.fps").deepCopy();
        fps.remove("value");
        fps.remove("frame");
        fps.addProperty("name", "client.fps");
        snapshot.getAsJsonObject("references").getAsJsonArray("availableMetrics").add(fps);
        return snapshot;
    }

    /** Shared collector seam for native regression fixtures; it does not schedule or execute scripts. */
    public static JsonObject captureObserved(PerformanceSelection selected, Avatar avatar,
                                             OutfitComposition composition, String context) {
        var result = new JsonObject();
        result.addProperty("processId", PROCESS_ID);
        result.addProperty("capturedFrame", YsmRenderFrameClock.currentFrame());
        result.addProperty("capturedTick", FiguraMod.ticks);
        result.addProperty("renderContext", context);
        result.add("currentSelection", selection(selected));
        result.add("activeModel", model(avatar));
        result.add("instances", instances(composition));
        var metrics = avatarMetrics(avatar);
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        metrics.add("jvm.heap.used", metric(heap.getUsed(), "bytes", "process", null, "ok", "JVM heap"));
        metrics.add("jvm.heap.committed", metric(heap.getCommitted(), "bytes", "process", null, "ok", "JVM heap"));
        metrics.add("jvm.heap.max", metric(heap.getMax() < 0 ? null : heap.getMax(), "bytes", "process", null,
                heap.getMax() < 0 ? "unsupported" : "ok", "JVM heap; not physical or GPU memory"));
        var collectors = new JsonArray();
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            var item = new JsonObject();
            item.addProperty("name", collector.getName());
            item.add("count", metric(collector.getCollectionCount() < 0 ? null : collector.getCollectionCount(),
                    "count", "process_cumulative", null,
                    collector.getCollectionCount() < 0 ? "unsupported" : "ok", "Since process start"));
            item.add("time", metric(collector.getCollectionTime() < 0 ? null : collector.getCollectionTime(),
                    "ms", "process_cumulative", null,
                    collector.getCollectionTime() < 0 ? "unsupported" : "ok", "Since process start"));
            collectors.add(item);
        }
        result.add("garbageCollectors", collectors);
        result.add("metrics", metrics);
        var references = new JsonObject();
        references.addProperty("modVersion", FiguraMod.VERSION.toString());
        references.addProperty("debug", FiguraMod.debugModeEnabled());
        references.addProperty("endpoint", "http://127.0.0.1:" + Configs.PERFORMANCE_HTTP_PORT.value
                + PerformanceHttpServer.PATH);
        references.addProperty("sampling", "one request, one client-thread capture; no script execution");
        references.addProperty("missingValues", "null with status; no GPU inference or zero substitution");
        var render = new JsonObject();
        render.addProperty("adaptive", Configs.ADAPTIVE_RENDER_BUDGET.value);
        render.addProperty("base", Configs.RENDER_INSTRUCTION_BASE.value);
        render.addProperty("hardCeiling", Configs.RENDER_INSTRUCTION_CEILING);
        references.add("renderBudget", render);
        var init = new JsonObject();
        init.addProperty("base", Configs.INIT_INSTRUCTION_BASE.value);
        init.addProperty("hardCeiling", Configs.INIT_INSTRUCTION_CEILING);
        references.add("initBudget", init);
        var catalog = new JsonArray();
        for (var entry : metrics.entrySet()) {
            var definition = entry.getValue().getAsJsonObject().deepCopy();
            definition.remove("value");
            definition.remove("frame");
            definition.addProperty("name", entry.getKey());
            catalog.add(definition);
        }
        references.add("availableMetrics", catalog);
        result.add("references", references);
        return result;
    }

    private static EntityPreview preview(GuiEventListener listener) {
        if (listener instanceof EntityPreview widget) {
            return widget;
        }
        if (listener instanceof ContainerEventHandler container) {
            for (var child : container.children()) {
                var found = preview(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonElement selection(PerformanceSelection selected) {
        if (selected == null) {
            return JsonNull.INSTANCE;
        }
        var result = new JsonObject();
        result.addProperty("modelId", selected.modelId());
        result.addProperty("name", selected.name());
        result.addProperty("type", selected.type());
        result.addProperty("format", selected.format());
        result.addProperty("generation", selected.generation());
        return result;
    }

    private static JsonElement model(Avatar avatar) {
        if (avatar == null) {
            return JsonNull.INSTANCE;
        }
        var result = new JsonObject();
        var source = avatar.outfitSource();
        result.addProperty("modelId", source == null ? avatar.owner.toString() : source.identity());
        result.addProperty("name", avatar.name == null ? avatar.entityName : avatar.name);
        result.addProperty("type", avatar.cosmeticContext == null ? "avatar" : "cosmetic");
        result.addProperty("format", avatar.isYsmNative() ? "ysm" : "figura");
        result.addProperty("generation", avatar.performanceGeneration());
        result.addProperty("loaded", avatar.loaded);
        result.addProperty("scriptError", avatar.scriptError);
        result.addProperty("errorCode", avatar.scriptError ? "SCRIPT_ERROR" : null);
        result.addProperty("context", avatar.renderMode.name());
        var denied = new JsonArray();
        avatar.noPermissions.forEach(permission -> denied.add(permission.name));
        result.add("deniedPermissions", denied);
        return result;
    }

    private static JsonArray instances(OutfitComposition composition) {
        var result = new JsonArray();
        if (composition == null) {
            return result;
        }
        Map<UUID, Avatar> live = composition.performanceInstances();
        for (var declaration : composition.configuration().instances()) {
            Avatar avatar = live.get(declaration.instanceId());
            var item = new JsonObject();
            item.addProperty("instanceId", declaration.instanceId().toString());
            item.addProperty("resourceId", declaration.resource().identity());
            item.addProperty("enabled", declaration.enabled());
            item.addProperty("anchorType", declaration.anchor().type().name());
            UUID parent = declaration.anchor().bone() == null ? null : declaration.anchor().bone().instanceId();
            item.addProperty("declaredParentInstanceId", parent == null ? null : parent.toString());
            item.add("model", model(avatar));
            item.add("metrics", avatarMetrics(avatar));
            if (avatar != null && avatar.cosmeticContext.instructionBudget() != null) {
                item.add("sharedBudget", avatar.cosmeticContext.instructionBudget().performanceSnapshot());
            }
            result.add(item);
        }
        return result;
    }

    private static JsonObject avatarMetrics(Avatar avatar) {
        var result = new JsonObject();
        counter(result, "lua.init", avatar, avatar == null ? null : avatar.init, Permissions.INIT_INST);
        counter(result, "lua.render", avatar, avatar == null ? null : avatar.render, Permissions.RENDER_INST);
        counter(result, "lua.world_render", avatar, avatar == null ? null : avatar.worldRender, Permissions.WORLD_RENDER_INST);
        counter(result, "lua.tick", avatar, avatar == null ? null : avatar.tick, Permissions.TICK_INST);
        counter(result, "lua.world_tick", avatar, avatar == null ? null : avatar.worldTick, Permissions.WORLD_TICK_INST);
        counter(result, "animation", avatar, avatar == null ? null : avatar.animation, Permissions.ANIMATION_INST);
        counter(result, "geometry.complexity", avatar, avatar == null ? null : avatar.complexity, Permissions.COMPLEXITY);
        boolean ready = avatar != null && avatar.loaded && avatar.nbt != null;
        result.add("model.nodes", metric(ready ? PreviewHierarchy.fromAvatar(avatar).nodes().size() : null,
                "count", "loaded_model", null, ready ? "ok" : avatar == null ? "no_model" : "not_ready",
                "Hierarchy nodes, not submitted meshes or bones alone"));
        boolean textures = ready && avatar.renderer != null;
        result.add("texture.loaded", metric(textures ? avatar.renderer.textures.size() : null, "count", "loaded_model",
                null, textures ? "ok" : avatar == null ? "no_model" : "unsupported", "Figura model texture entries"));
        result.add("texture.custom", metric(textures ? avatar.renderer.customTextures.size() : null, "count", "instance",
                null, textures ? "ok" : avatar == null ? "no_model" : "unsupported", "Current instance custom textures"));
        long pixels = 0;
        if (textures) {
            for (var texture : avatar.renderer.textures.values()) {
                pixels += (long) texture.getWidth() * texture.getHeight();
            }
        }
        result.add("texture.rgba_estimate", metric(textures ? pixels * 4 : null, "bytes", "loaded_model", null,
                textures ? "ok" : avatar == null ? "no_model" : "unsupported", "Width*height*4; excludes mipmaps; not measured VRAM"));
        var unavailable = Map.of(
                "gpu.time", "ms", "lua.render.time", "ms", "render.draw_calls", "count",
                "particle.emitters", "count", "trail.samples", "count", "shader.compiles", "count");
        for (var entry : unavailable.entrySet()) {
            result.add(entry.getKey(), metric(null, entry.getValue(), "activeModel", null,
                    "not_collected", "No verified observation source in this release"));
        }
        if (avatar != null && avatar.previewParticles != null) {
            result.add("particle.live", metric(avatar.previewParticles.liveCount(), "count", "preview_scope", null,
                    "ok", "Shared isolated preview; may include sibling instances"));
        } else {
            result.add("particle.live", metric(avatar == null ? null
                    : top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime.liveCount(
                            avatar.cosmeticContext == null ? avatar.owner : avatar.cosmeticContext.wearerId()),
                    "count", "wearer_world", null, avatar == null ? "no_model" : "ok",
                    "All live custom world particles for this wearer; not emitter or per-instance count"));
        }
        return result;
    }

    private static void counter(JsonObject metrics, String name, Avatar avatar,
                                Avatar.Instructions counter, Permissions permission) {
        boolean sampled = counter != null && counter.performanceNanos != 0;
        boolean failed = avatar != null && avatar.noPermissions.contains(permission);
        String unit = permission == Permissions.COMPLEXITY ? "faces" : "instructions";
        String status = avatar == null ? "no_model" : failed ? "unavailable_after_limit"
                : sampled ? "ok" : "not_collected";
        var value = metric(sampled && !failed ? (long) counter.pre + counter.post : null, unit,
                "last_retained_counter", sampled ? counter.performanceFrame : null, status,
                "Existing pre+post counter; latest use, not necessarily the captured frame; animation includes native budget units");
        value.addProperty("tick", sampled ? counter.performanceTick : null);
        value.addProperty("ageMs", sampled ? (System.nanoTime() - counter.performanceNanos) / 1_000_000d : null);
        metrics.add(name + (permission == Permissions.COMPLEXITY ? ".used" : ".instructions"), value);
        metrics.add(name + ".limit", metric(avatar == null ? null : FiguraLuaRuntime.instructionCeiling(avatar, permission),
                unit, "effective_permission", null, avatar == null ? "no_model" : "ok",
                "Client setting constrained by actual permission; not a fresh shared allowance"));
        metrics.add(name + ".remaining", metric(sampled && !failed ? counter.remaining : null,
                unit, "last_retained_counter", sampled ? counter.performanceFrame : null, status,
                "Existing counter field; shared ledger reported separately"));
    }

    private static JsonObject metric(Number value, String unit, String scope, Long frame,
                                      String status, String source) {
        var result = new JsonObject();
        result.addProperty("value", value);
        result.addProperty("unit", unit);
        result.addProperty("scope", scope);
        result.addProperty("frame", frame);
        result.addProperty("status", status);
        result.addProperty("source", source);
        return result;
    }
}
