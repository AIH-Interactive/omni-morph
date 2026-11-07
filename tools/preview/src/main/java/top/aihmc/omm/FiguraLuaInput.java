package top.aihmc.omm;

/** Starts the original Avatar's queued scripts using its detached-preview entry point. */
final class FiguraLuaInput {
    private FiguraLuaInput() {
    }

    static Object initialize(Object avatar) throws Exception {
        avatar.getClass().getMethod("initializeLibraryPreview", Class.forName("net.minecraft.world.entity.Entity"))
            .invoke(avatar, new Object[]{null});
        Object runtime = avatar.getClass().getField("luaRuntime").get(avatar);
        if (avatar.getClass().getField("scriptError").getBoolean(avatar) || runtime == null) {
            throw new IllegalStateException("Original Figura model scripts failed to initialize; see Lua diagnostics");
        }
        System.out.println("FIGURA ORIGINAL MODEL LUA INITIALIZED");
        return runtime;
    }
}
