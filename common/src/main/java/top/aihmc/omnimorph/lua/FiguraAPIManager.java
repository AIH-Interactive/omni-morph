package top.aihmc.omnimorph.lua;

import top.aihmc.omnimorph.animation.Animation;
import top.aihmc.omnimorph.entries.FiguraAPI;
import top.aihmc.omnimorph.lua.api.*;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.lua.api.data.*;
import top.aihmc.omnimorph.lua.api.json.*;
import top.aihmc.omnimorph.lua.api.entity.*;
import top.aihmc.omnimorph.lua.api.net.HttpRequestsAPI;
import top.aihmc.omnimorph.lua.api.net.NetworkingAPI;
import top.aihmc.omnimorph.model.rendertasks.*;
import top.aihmc.omnimorph.lua.api.action_wheel.Action;
import top.aihmc.omnimorph.lua.api.action_wheel.ActionWheelAPI;
import top.aihmc.omnimorph.lua.api.action_wheel.Page;
import top.aihmc.omnimorph.lua.api.avatar_controls.AvatarControlsAPI;
import top.aihmc.omnimorph.lua.api.event.EventsAPI;
import top.aihmc.omnimorph.lua.api.event.LuaEvent;
import top.aihmc.omnimorph.lua.api.keybind.FiguraKeybind;
import top.aihmc.omnimorph.lua.api.keybind.KeybindAPI;
import top.aihmc.omnimorph.lua.api.math.MatricesAPI;
import top.aihmc.omnimorph.lua.api.math.VectorsAPI;
import top.aihmc.omnimorph.lua.api.nameplate.EntityNameplateCustomization;
import top.aihmc.omnimorph.lua.api.nameplate.NameplateAPI;
import top.aihmc.omnimorph.lua.api.nameplate.NameplateCustomization;
import top.aihmc.omnimorph.lua.api.nameplate.NameplateCustomizationGroup;
import top.aihmc.omnimorph.lua.api.particle.LuaParticle;
import top.aihmc.omnimorph.lua.api.particle.ParticleAPI;
import top.aihmc.omnimorph.lua.api.ping.PingAPI;
import top.aihmc.omnimorph.lua.api.ping.PingFunction;
import top.aihmc.omnimorph.lua.api.sound.LuaSound;
import top.aihmc.omnimorph.lua.api.sound.SoundAPI;
import top.aihmc.omnimorph.lua.api.vanilla_model.VanillaGroupPart;
import top.aihmc.omnimorph.lua.api.vanilla_model.VanillaModelAPI;
import top.aihmc.omnimorph.lua.api.vanilla_model.VanillaModelPart;
import top.aihmc.omnimorph.lua.api.vanilla_model.VanillaPart;
import top.aihmc.omnimorph.lua.api.ysm_model.YsmModelAPI;
import top.aihmc.omnimorph.lua.api.ysm_actions.YsmActionsAPI;
import top.aihmc.omnimorph.lua.api.world.BiomeAPI;
import top.aihmc.omnimorph.lua.api.world.BlockStateAPI;
import top.aihmc.omnimorph.lua.api.world.ItemStackAPI;
import top.aihmc.omnimorph.lua.api.world.WorldAPI;
import top.aihmc.omnimorph.math.matrix.FiguraMat2;
import top.aihmc.omnimorph.math.matrix.FiguraMat3;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.math.vector.FiguraVec2;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.math.vector.FiguraVec4;
import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.rendering.Vertex;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;
import top.aihmc.omnimorph.model.ysm.action.YsmActionDefinition;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A set of Globals of which there is only one in the MC instance.
 * This set of Globals is used to compile and run other scripts.
 */
public class FiguraAPIManager {

    /**
     * Addon mods simply need to add their classes to the WHITELISTED_CLASSES set,
     * and whichever global vars they want to set into the API_GETTERS map.
     */
    public static final Set<Class<?>> WHITELISTED_CLASSES = new HashSet<>() {{
        add(FiguraVec2.class);
        add(FiguraVec3.class);
        add(FiguraVec4.class);

        add(FiguraMat2.class);
        add(FiguraMat3.class);
        add(FiguraMat4.class);

        add(NullEntity.class);
        add(EntityAPI.class);
        add(LivingEntityAPI.class);
        add(PlayerAPI.class);
        add(ViewerAPI.class);

        add(EventsAPI.class);
        add(LuaEvent.class);

        add(Vertex.class);
        add(FiguraModelPart.class);
        add(RenderTask.class);
        add(ItemTask.class);
        add(BlockTask.class);
        add(EntityTask.class);
        add(TextTask.class);
        add(SpriteTask.class);

        add(SoundAPI.class);
        add(LuaSound.class);

        add(ParticleAPI.class);
        add(LuaParticle.class);

        add(VanillaModelAPI.class);
        add(VanillaPart.class);
        add(VanillaGroupPart.class);
        add(VanillaModelPart.class);
        add(YsmModelAPI.class);
        add(YsmModelPart.class);
        add(YsmActionsAPI.class);
        add(YsmActionDefinition.class);

        add(KeybindAPI.class);
        add(FiguraKeybind.class);

        add(NameplateAPI.class);
        add(NameplateCustomization.class);
        add(EntityNameplateCustomization.class);
        add(NameplateCustomizationGroup.class);

        add(ActionWheelAPI.class);
        add(Page.class);
        add(Action.class);
        add(AvatarControlsAPI.class);
        add(AvatarControlDefinition.class);
        add(AvatarControlType.class);

        add(VectorsAPI.class);
        add(MatricesAPI.class);

        add(WorldAPI.class);
        add(BiomeAPI.class);
        add(BlockStateAPI.class);
        add(ItemStackAPI.class);

        add(PingAPI.class);
        add(PingFunction.class);

        add(TextureAPI.class);
        add(FiguraTexture.class);

        add(AnimationAPI.class);
        add(Animation.class);

        add(HostAPI.class);

        add(RendererAPI.class);

        add(ClientAPI.class);

        add(AvatarAPI.class);

        add(ConfigAPI.class);

        add(TextureAtlasAPI.class);

        add(FiguraInputStream.class);
        add(FiguraOutputStream.class);

        add(DataAPI.class);

        add(FiguraBuffer.class);

        add(FileAPI.class);

        add(JsonAPI.class);
        add(FiguraJsonBuilder.class);
        add(FiguraJsonSerializer.class);
        add(FiguraJsonArray.class);
        add(FiguraJsonObject.class);

        add(ResourcesAPI.class);

        add(NetworkingAPI.class);

        add(HttpRequestsAPI.class);
        add(HttpRequestsAPI.HttpRequestBuilder.class);
        add(HttpRequestsAPI.HttpResponse.class);

        add(FiguraFuture.class);

        add(RaycastAPI.class);
    }};

    public static final Map<String, Function<FiguraLuaRuntime, Object>> API_GETTERS = new LinkedHashMap<>() {{
        put("events", r -> r.events = new EventsAPI());
        put("sounds", r -> new SoundAPI(r.owner));
        put("vanilla_model", r -> r.vanilla_model = new VanillaModelAPI(r.owner));
        put("ysm_model", r -> r.ysm_model = new YsmModelAPI(r.owner));
        put("ysm_actions", r -> new YsmActionsAPI(r.owner));
        put("keybinds", r -> r.keybinds = new KeybindAPI(r.owner));
        put("host", r -> r.host = new HostAPI(r.owner));
        put("nameplate", r -> r.nameplate = new NameplateAPI());
        put("renderer", r -> r.renderer = new RendererAPI(r.owner));
        put("action_wheel", r -> r.action_wheel = new ActionWheelAPI(r.owner));
        put("avatar_controls", r -> r.avatar_controls = new AvatarControlsAPI(r.owner));
        put("animations", r -> new AnimationAPI(r.owner));
        put("client", r -> ClientAPI.INSTANCE);
        put("particles", r -> new ParticleAPI(r.owner));
        put("avatar", r -> r.avatar_meta = new AvatarAPI(r.owner));
        put("vectors", r -> VectorsAPI.INSTANCE);
        put("matrices", r -> MatricesAPI.INSTANCE);
        put("world", r -> WorldAPI.INSTANCE);
        put("pings", r -> r.ping = new PingAPI(r.owner));
        put("textures", r -> r.texture = new TextureAPI(r.owner));
        put("config", r -> new ConfigAPI(r.owner));
        put("data", r -> new DataAPI(r.owner));
        put("file", r -> new FileAPI(r.owner));
        put("json", r -> JsonAPI.INSTANCE);
        put("resources", r -> new ResourcesAPI(r.owner));
        put("net", r -> new NetworkingAPI(r.owner));
        put("raycast", r -> new RaycastAPI(r.owner));
    }};

    private static final Set<FiguraAPI> ENTRYPOINTS = new HashSet<>();

    private static final Set<String> COSMETIC_APIS = Set.of(
            "events", "ysm_model", "animations", "vectors", "matrices", "json", "particles");
    private static final Set<String> COSMETIC_INTERNAL_APIS = Set.of(
            "vanilla_model", "keybinds", "host", "nameplate", "renderer", "action_wheel",
            "avatar_controls", "avatar", "pings", "textures");

    public static void initEntryPoints(Set<FiguraAPI> set) {
        for (FiguraAPI api : set) {
            ENTRYPOINTS.add(api);
            WHITELISTED_CLASSES.addAll(api.getWhitelistedClasses());
        }
    }

    public static void setupTypesAndAPIs(FiguraLuaRuntime runtime) {
        for (Class<?> clazz : WHITELISTED_CLASSES)
            runtime.registerClass(clazz);
        boolean cosmetic = runtime.owner.cosmeticContext != null;
        for (Map.Entry<String, Function<FiguraLuaRuntime, Object>> entry : API_GETTERS.entrySet()) {
            if (cosmetic && !COSMETIC_APIS.contains(entry.getKey())
                    && !COSMETIC_INTERNAL_APIS.contains(entry.getKey())) {
                continue;
            }
            // Native rendering still needs its private API objects; Lua only receives instance capabilities.
            Object api = entry.getValue().apply(runtime);
            if (!cosmetic || COSMETIC_APIS.contains(entry.getKey())) {
                runtime.setGlobal(entry.getKey(), api);
            }
        }
        if (cosmetic) {
            runtime.registerClass(CosmeticPlayerAPI.class);
            runtime.registerClass(CosmeticAnimationsAPI.class);
            runtime.setGlobal("cosmetic_controls", runtime.avatar_controls);
            runtime.setGlobal("cosmetic_animations", new CosmeticAnimationsAPI(runtime.owner));
            return;
        }
        for (FiguraAPI api : ENTRYPOINTS) {
            String name = api.getName();
            if (name != null)
                runtime.setGlobal(name, api.build(runtime.owner));
        }
    }
}
