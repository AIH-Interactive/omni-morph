package org.figuramc.figura.model.rendering.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec2;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.mixin.render.TextureManagerAccessor;
import org.figuramc.figura.utils.ColorUtils;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.LuaUtils;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

@LuaWhitelist
@LuaTypeDoc(
        name = "Texture",
        value = "texture"
)
public class FiguraTexture extends SimpleTexture {

    /**
     * The ID of the texture, used to register to Minecraft.
     */
    private boolean registered = false;
    private boolean dirty = true;
    private boolean modified = false;
    private final String name;
    private final Avatar owner;

    /**
     * Native image holding the texture data for this texture.
     */
    private final NativeImage nativeImageTexture;
    private NativeImage backup;
    private volatile boolean isClosed = false;
    private boolean closeRequested;
    private int retainers;
    private boolean removalQueued;
    private boolean clamp;
    // Named apart from AbstractTexture.blur, which only setFilter() maintains.
    private boolean uploadBlur;

    public FiguraTexture(Avatar owner, String name, byte[] data) {
        super(FiguraIdentifier.of("avatar_tex/" + owner.owner + "/" + UUID.randomUUID()));

        // Read image from wrapper
        NativeImage image;
        try {
            ByteBuffer wrapper = BufferUtils.createByteBuffer(data.length);
            wrapper.put(data);
            wrapper.rewind();
            image = NativeImage.read(wrapper);
        } catch (IOException e) {
            FiguraMod.LOGGER.error("", e);
            image = new NativeImage(1, 1, true);
        }

        this.nativeImageTexture = image;
        this.name = name;
        this.owner = owner;
    }

    public FiguraTexture(Avatar owner, String name, int width, int height) {
        super(FiguraIdentifier.of("avatar_tex/" + owner.owner + "/" + UUID.randomUUID()));
        this.nativeImageTexture = new NativeImage(width, height, true);
        this.name = name;
        this.owner = owner;
    }

    public FiguraTexture(Avatar owner, String name, NativeImage image) {
        super(FiguraIdentifier.of("avatar_tex/" + owner.owner + "/custom/" + UUID.randomUUID()));
        this.nativeImageTexture = image;
        this.name = name;
        this.owner = owner;
    }

    /**
     * Texture reloads re-upload the retained image instead of reading a resource pack file.
     * A texture that was already closed only needs to leave the texture manager.
     */
    @Override
    public synchronized void load(ResourceManager resourceManager) {
        if (isClosed) {
            queueRemoval();
            return;
        }
        dirty = true;
        if (RenderSystem.isOnRenderThreadOrInit()) {
            uploadIfDirty(clamp, uploadBlur);
        } else {
            RenderSystem.recordRenderCall(() -> uploadIfDirty(clamp, uploadBlur));
        }
    }

    public void closeFromRenderThread() {
        Minecraft.getInstance().execute(this::close);
    }

    public boolean belongsTo(Avatar avatar) {
        return owner == avatar;
    }

    /** New consumers cannot acquire a retired texture; existing particles may retain extracted frames. */
    public synchronized Lease retain() {
        if (closeRequested || isClosed) {
            throw new IllegalStateException("TEXTURE_RETIRED");
        }
        retainers++;
        return new Lease();
    }

    public final class Lease implements AutoCloseable {
        private boolean released;

        private Lease() {
        }

        public Lease retain() {
            synchronized (FiguraTexture.this) {
                if (released || isClosed) {
                    throw new IllegalStateException("TEXTURE_LEASE_RELEASED");
                }
                retainers++;
                return new Lease();
            }
        }

        @Override
        public void close() {
            synchronized (FiguraTexture.this) {
                if (released) {
                    return;
                }
                released = true;
                retainers--;
                if (retainers == 0 && closeRequested) {
                    FiguraTexture.this.close();
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        closeRequested = true;
        if (retainers != 0) {
            return;
        }
        // Make sure it doesn't close twice (minecraft tries to close the texture when reloading textures
        if (isClosed) return;

        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(this::close);
            return;
        }

        isClosed = true;

        // Close native images
        if (nativeImageTexture != null)
            nativeImageTexture.close();
        if (backup != null)
            backup.close();

        this.releaseId();
        registered = false;
        queueRemoval();
    }

    private void queueRemoval() {
        if (removalQueued) {
            return;
        }
        removalQueued = true;
        // execute() runs immediately on the render thread; schedule() also permits manager iteration.
        Minecraft.getInstance().schedule(() -> {
            ((TextureManagerAccessor) Minecraft.getInstance().getTextureManager()).getByPath()
                    .remove(getLocation(), this);
            removalQueued = false;
        });
    }

    private void ensureUploaded() {
        RenderSystem.assertOnRenderThread();
        if (!dirty) {
            return;
        }
        int width = nativeImageTexture.getWidth();
        int height = nativeImageTexture.getHeight();
        TextureUtil.prepareImage(super.getId(), width, height);
        nativeImageTexture.upload(0, 0, 0, 0, 0, width, height, uploadBlur, clamp, false, false);
        // Clear the flag only after a successful upload so failures are retried.
        dirty = false;
    }

    public void uploadIfDirty() {
        uploadIfDirty(clamp, uploadBlur);
    }

    public void uploadIfDirty(boolean clamp, boolean blur) {
        if (isClosed || nativeImageTexture == null) {
            return;
        }
        if (this.clamp != clamp || this.uploadBlur != blur) {
            dirty = true;
        }
        this.clamp = clamp;
        this.uploadBlur = blur;
        ensureUploaded();

        // Registration may immediately expose the texture to rendering/PBR consumers.
        // Publish only after a successful upload, and retain dirty state on failure for retry.
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(this.getLocation(), this);
            registered = true;
        }
    }

    private AbstractTexture retiredBinding() {
        queueRemoval();
        return Minecraft.getInstance().getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation());
    }

    /** Retired textures resolve to the missing texture; live ones upload pending pixels first. */
    @Override
    public int getId() {
        if (isClosed) {
            return retiredBinding().getId();
        }
        if (RenderSystem.isOnRenderThread()) {
            ensureUploaded();
        }
        return super.getId();
    }

    @Override
    public void bind() {
        if (isClosed) {
            retiredBinding().bind();
            return;
        }
        ensureUploaded();
        super.bind();
    }

    public void writeTexture(Path dest) throws IOException {
        nativeImageTexture.writeToFile(dest);
    }

    private void backupImage() {
        this.modified = true;
        if (this.backup == null)
            backup = copy();
    }

    public NativeImage copy() {
        NativeImage image = new NativeImage(nativeImageTexture.format(), nativeImageTexture.getWidth(), nativeImageTexture.getHeight(), true);
        image.copyFrom(nativeImageTexture);
        return image;
    }

    public int getWidth() {
        return nativeImageTexture.getWidth();
    }

    public int getHeight() {
        return nativeImageTexture.getHeight();
    }

    public ResourceLocation getLocation() {
        return this.location;
    }


    // -- lua stuff -- // 


    private FiguraVec4 parseColor(String method, Object r, Double g, Double b, Double a) {
        return LuaUtils.parseVec4(method, r, g, b, a, 0, 0, 0, 1);
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.get_name")
    public String getName() {
        return name;
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.get_path")
    public String getPath() {
        return getLocation().toString();
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.get_dimensions")
    public FiguraVec2 getDimensions() {
        return FiguraVec2.of(getWidth(), getHeight());
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {Integer.class, Integer.class},
                    argumentNames = {"x", "y"}
            ),
            value = "texture.get_pixel")
    public FiguraVec4 getPixel(int x, int y) {
        try {
            return ColorUtils.abgrToRGBA(nativeImageTexture.getPixelRGBA(x, y));
        } catch (Exception e) {
            throw new LuaError(e.getMessage());
        }
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, FiguraVec3.class},
                            argumentNames = {"x", "y", "rgb"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, FiguraVec4.class},
                            argumentNames = {"x", "y", "rgba"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "r", "g", "b", "a"}
                    )
            },
            aliases = "pixel",
            value = "texture.set_pixel")
    public FiguraTexture setPixel(int x, int y, Object r, Double g, Double b, Double a) {
        try {
            backupImage();
            nativeImageTexture.setPixelRGBA(x, y, ColorUtils.rgbaToIntABGR(parseColor("setPixel", r, g, b, a)));
            return this;
        } catch (Exception e) {
            throw new LuaError(e.getMessage());
        }
    }

    @LuaWhitelist
    public FiguraTexture pixel(int x, int y, Object r, Double g, Double b, Double a) {
        return setPixel(x, y, r, g, b, a);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, Integer.class, Integer.class, FiguraVec3.class},
                            argumentNames = {"x", "y", "width", "height", "rgb"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, Integer.class, Integer.class, FiguraVec4.class},
                            argumentNames = {"x", "y", "width", "height", "rgba"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Integer.class, Integer.class, Integer.class, Integer.class, Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "width", "height", "r", "g", "b", "a"}
                    )
            },
            value = "texture.fill")
    public FiguraTexture fill(int x, int y, int width, int height, Object r, Double g, Double b, Double a) {
        try {
            backupImage();
            nativeImageTexture.fillRect(x, y, width, height, ColorUtils.rgbaToIntABGR(parseColor("fill", r, g, b, a)));
            return this;
        } catch (Exception e) {
            throw new LuaError(e.getMessage());
        }
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.update")
    public FiguraTexture update() {
        this.dirty = true;
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.restore")
    public FiguraTexture restore() {
        if (modified) {
            this.nativeImageTexture.copyFrom(backup);
            this.modified = false;
        }
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("texture.save")
    public String save() {
        try {
            return Base64.getEncoder().encodeToString(nativeImageTexture.asByteArray());
        } catch (Exception e) {
            throw new LuaError(e.getMessage());
        }
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {Integer.class, Integer.class, Integer.class, Integer.class, LuaFunction.class},
                    argumentNames = {"x", "y", "width", "height", "func"}
            ),
            value = "texture.apply_func"
    )
    public FiguraTexture applyFunc(int x, int y, int width, int height, @LuaNotNil LuaFunction function) {
        for (int i = y; i < y + height; i++) {
            for (int j = x; j < x + width; j++) {
                FiguraVec4 color = getPixel(j, i);
                LuaValue result = function.call(owner.luaRuntime.typeManager.javaToLua(color).arg1(), LuaValue.valueOf(j), LuaValue.valueOf(i));
                if (!result.isnil() && result.isuserdata(FiguraVec4.class))
                    setPixel(j, i, result.checkuserdata(FiguraVec4.class), null, null, null);
            }
        }
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {Integer.class, Integer.class, Integer.class, Integer.class, FiguraMat4.class, Boolean.class},
                    argumentNames = {"x", "y", "width", "height", "matrix", "clip"}
            ),
            value = "texture.apply_matrix"
    )
    public FiguraTexture applyMatrix(int x, int y, int width, int height, @LuaNotNil FiguraMat4 matrix, boolean clip) {
        for (int i = y; i < y + height; i++) {
            for (int j = x; j < x + width; j++) {
                FiguraVec4 color = getPixel(j, i);
                color.transform(matrix);

                if (clip) {
                    color.x = Math.max(0, Math.min(color.x, 1));
                    color.y = Math.max(0, Math.min(color.y, 1));
                    color.z = Math.max(0, Math.min(color.z, 1));
                    color.w = Math.max(0, Math.min(color.w, 1));
                }

                setPixel(j, i, color, null, null, null);
            }
        }
        return this;
    }

    @LuaWhitelist
    public Object __index(String arg) {
        return "name".equals(arg) ? name : null;
    }

    @Override
    public String toString() {
        return name + " (" + getWidth() + "x" + getHeight() + ") (Texture)";
    }
}
