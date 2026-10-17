package top.aihmc.omnimorph.entries;

import dev.architectury.injectables.annotations.ExpectPlatform;
import top.aihmc.omnimorph.gui.widgets.PanelSelectorWidget;
import top.aihmc.omnimorph.lua.FiguraAPIManager;
import top.aihmc.omnimorph.lua.api.event.EventsAPI;
import top.aihmc.omnimorph.lua.api.vanilla_model.VanillaModelAPI;
import top.aihmc.omnimorph.lua.docs.FiguraDocsManager;
import top.aihmc.omnimorph.permissions.PermissionManager;

import java.util.Set;

public class EntryPointManager {

    public static void init() {
        // APIs
        Set<FiguraAPI> apis = load("figura_api", FiguraAPI.class);
        FiguraAPIManager.initEntryPoints(apis);
        FiguraDocsManager.initEntryPoints(apis);

        // other
        PermissionManager.initEntryPoints(load("figura_permissions", FiguraPermissions.class));
        PanelSelectorWidget.initEntryPoints(load("figura_screen", FiguraScreen.class));
        VanillaModelAPI.initEntryPoints(load("figura_vanilla_part", FiguraVanillaPart.class));
        EventsAPI.initEntryPoints(load("figura_event", FiguraEvent.class));
    }

    public static void reinit() {
        PermissionManager.initEntryPoints(load("figura_permissions", FiguraPermissions.class));
    }

    @ExpectPlatform
    @SuppressWarnings("unchecked")
    private static <T> Set<T> load(String name, Class<T> clazz) {
        try {
            return (Set<T>) Class.forName("top.aihmc.omnimorph.entries.fabric.EntryPointManagerImpl")
                .getMethod("load", String.class, Class.class).invoke(null, name, clazz);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
