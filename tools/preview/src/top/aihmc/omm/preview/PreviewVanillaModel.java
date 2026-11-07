package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.lua.LuaWhitelist;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

/** A real skinned vanilla player, sharing the avatar's draw/palette path and Lua visibility state. */
public final class PreviewVanillaModel {
    private static final String[] NAMES = {"HEAD", "BODY", "RIGHT_ARM", "LEFT_ARM", "RIGHT_LEG", "LEFT_LEG", "HAT",
        "JACKET", "RIGHT_SLEEVE", "LEFT_SLEEVE", "RIGHT_PANTS", "LEFT_PANTS"};
    private final PreviewFiguraParts definition = new PreviewFiguraParts();
    private final List<Float> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final List<Integer> owners = new ArrayList<>();
    private int offset;
    private int firstIndex;
    private String skinHash;
    private String skinSource;
    private boolean slim;

    private PreviewVanillaModel(boolean slim) {
        int arm = slim ? 3 : 4;
        box(0, -4, 24, -4, 8, 8, 8, 0, 0, 0);
        box(1, -4, 12, -2, 8, 12, 4, 16, 16, 0);
        box(2, -4 - arm, 12, -2, arm, 12, 4, 40, 16, 0);
        box(3, 4, 12, -2, arm, 12, 4, 32, 48, 0);
        box(4, -4, 0, -2, 4, 12, 4, 0, 16, 0);
        box(5, 0, 0, -2, 4, 12, 4, 16, 48, 0);
        box(6, -4, 24, -4, 8, 8, 8, 32, 0, 0.5f);
        box(7, -4, 12, -2, 8, 12, 4, 16, 32, 0.25f);
        box(8, -4 - arm, 12, -2, arm, 12, 4, 40, 32, 0.25f);
        box(9, 4, 12, -2, arm, 12, 4, 48, 48, 0.25f);
        box(10, -4, 0, -2, 4, 12, 4, 0, 32, 0.25f);
        box(11, 0, 0, -2, 4, 12, 4, 0, 48, 0.25f);
    }

    static PreviewMesh attach(PreviewMesh base, PreviewConfig config) throws IOException {
        Skin skin = skin(config);
        boolean slim =
            config.vanilla.model.equals("alex") || config.vanilla.model.equals("auto") && isSlim(skin.image());
        var vanilla = new PreviewVanillaModel(slim);
        vanilla.slim = slim;
        vanilla.skinHash = skin.hash();
        vanilla.skinSource = skin.source();
        vanilla.firstIndex = base.indices.length;
        vanilla.offset = base.skeleton.names.length;
        int vertexOffset = base.vertices.length / 5;
        float[] vertices = Arrays.copyOf(base.vertices, base.vertices.length + vanilla.vertices.size());
        for (int index = 0; index < vanilla.vertices.size(); index++)
            vertices[base.vertices.length + index] = vanilla.vertices.get(index);
        int[] indices = Arrays.copyOf(base.indices, base.indices.length + vanilla.indices.size());
        for (int index = 0; index < vanilla.indices.size(); index++)
            indices[base.indices.length + index] = vertexOffset + vanilla.indices.get(index);
        int[] owners = Arrays.copyOf(base.owners, base.owners.length + vanilla.owners.size());
        for (int index = 0; index < vanilla.owners.size(); index++)
            owners[base.owners.length + index] = vanilla.offset + vanilla.owners.get(index);
        var textures = new ArrayList<>(base.textures);
        var draws = new ArrayList<>(base.draws);
        draws.add(new PreviewMesh.DrawRange(base.indices.length, vanilla.indices.size(), textures.size()));
        textures.add(skin.image());
        var mesh = new PreviewMesh(vertices, indices, textures, draws, base.label, base.fixture,
            new PreviewSkeleton(base.skeleton, new PreviewSkeleton(vanilla.definition)), owners, base.animationSources,
            base.ysmResources);
        mesh.figuraParts = base.figuraParts;
        mesh.vanilla = vanilla;
        return mesh;
    }

    private record Skin(BufferedImage image, String hash, String source) {}

    String description() {
        return "vanilla_model=" + (slim ? "alex" : "steve") + "\nvanilla_skin=" + skinSource
            + "\nvanilla_skin_sha256=" + skinHash;
    }

    boolean owns(PreviewMesh.DrawRange draw) {
        return draw.firstIndex() == firstIndex;
    }

    int modelBoneCount() {
        return offset;
    }

    private static Skin skin(PreviewConfig config) throws IOException {
        var candidate = PreviewSkinCache.load(config);
        byte[] bytes;
        if (candidate == null) {
            String name = "/assets/minecraft/textures/entity/player/"
                + (config.vanilla.model.equals("alex") ? "slim/alex.png" : "wide/steve.png");
            try (var input = PreviewVanillaModel.class.getResourceAsStream(name)) {
                if (input == null)
                    throw new IOException("Default player skin missing from JAR: " + name);
                bytes = input.readAllBytes();
            }
        } else {
            bytes = candidate.bytes();
        }
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < 24 || !Arrays.equals(Arrays.copyOf(bytes, 8), signature)
            || java.nio.ByteBuffer.wrap(bytes, 16, 8).getInt() != 64
            || java.nio.ByteBuffer.wrap(bytes, 20, 4).getInt() != 64) {
            throw new IOException("Player skin must be a 64x64 PNG");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null)
            throw new IOException("Skin PNG could not be decoded");
        if (candidate != null)
            PreviewSkinCache.remember(candidate);
        return new Skin(image, PreviewTextures.sha256(bytes),
            candidate == null ? "bundled default"
                              : candidate.url() + (candidate.cached() ? " (cached)" : " (downloaded)"));
    }

    static boolean isSlim(BufferedImage skin) {
        for (int[] strip : new int[][] {{54, 20}, {54, 36}, {46, 52}, {62, 52}}) {
            for (int y = strip[1]; y < strip[1] + 12; y++) {
                for (int x = strip[0]; x < strip[0] + 2; x++) {
                    if ((skin.getRGB(x, y) >>> 24) != 0)
                        return false;
                }
            }
        }
        return true;
    }

    private void box(
        int owner, float x, float y, float z, int width, int height, int depth, int u, int v, float inflate) {
        Vector3f pivot = new Vector3f(x + width / 2f, owner % 6 == 0 ? y : y + height, z + depth / 2f).div(16);
        definition.nodes.add(new PreviewFiguraParts.Node(NAMES[owner], "__vanilla." + NAMES[owner], -1, pivot,
            new Vector3f(), false, true, new Matrix4f(), new Matrix4f()));
        float x0 = x - inflate, x1 = x + width + inflate, y0 = y - inflate, y1 = y + height + inflate, z0 = z - inflate,
              z1 = z + depth + inflate;
        face(owner, new float[] {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, u + depth, v + depth, width, height);
        face(owner, new float[] {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, u + depth + width + depth, v + depth,
            width, height);
        face(owner, new float[] {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, u + depth + width, v + depth, depth,
            height);
        face(owner, new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, u, v + depth, depth, height);
        face(owner, new float[] {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, u + depth, v, width, depth);
        face(owner, new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, u + depth + width, v, width, depth);
    }

    private void face(int owner, float[] positions, int u, int v, int width, int height) {
        int first = vertices.size() / 5;
        int[][] corners = {{1, 1}, {0, 1}, {0, 0}, {1, 0}};
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) vertices.add(positions[vertex * 3 + axis] / 16);
            vertices.add((u + corners[vertex][0] * width) / 64f);
            vertices.add((v + corners[vertex][1] * height) / 64f);
            owners.add(owner);
        }
        for (int index : new int[] {0, 1, 2, 0, 2, 3}) indices.add(first + index);
    }

    State newState() {
        return new State();
    }

    final class State {
        final PreviewFiguraParts.State parts = definition.newState();
        private final Matrix4f[] world = new Matrix4f[NAMES.length];
        private final Matrix4f[] palette = new Matrix4f[NAMES.length];
        State() {
            for (int index = 0; index < NAMES.length; index++) {
                world[index] = new Matrix4f();
                palette[index] = new Matrix4f();
            }
        }

        void install(LuaTypeManager types, Globals globals) {
            types.generateMetatableFor(Group.class);
            types.generateMetatableFor(UnequippedArmor.class);
            types.generateMetatableFor(PreviewFiguraParts.Part.class);
            LuaTable api = new LuaTable();
            for (int index = 0; index < NAMES.length; index++)
                api.set(NAMES[index], types.javaToLua(parts.parts[index]).arg1());
            api.set("INNER_LAYER", types.javaToLua(new Group(Arrays.asList(parts.parts).subList(0, 6))).arg1());
            api.set("OUTER_LAYER", types.javaToLua(new Group(Arrays.asList(parts.parts).subList(6, 12))).arg1());
            var all = types.javaToLua(new Group(Arrays.asList(parts.parts))).arg1();
            api.set("PLAYER", all);
            api.set("ALL", all);
            api.set("ARMOR", types.javaToLua(new UnequippedArmor()).arg1());
            globals.set("vanilla_model", api);
        }

        void update(Matrix4f[] target, PreviewNativeVertices nativeVertices) {
            parts.update(world, palette, nativeVertices, offset);
            for (int index = 0; index < palette.length; index++) target[offset + index].set(palette[index]);
        }

        boolean visible() {
            return Arrays.stream(parts.parts).anyMatch(PreviewFiguraParts.Part::getVisible);
        }

        Map<String, Boolean> visibility() {
            var result = new LinkedHashMap<String, Boolean>();
            for (int index = 0; index < NAMES.length; index++)
                result.put(NAMES[index], parts.parts[index].getVisible());
            return Map.copyOf(result);
        }
    }

    /** The preview has no equipped armor geometry; retain OMP's nullable visibility override independently. */
    @LuaWhitelist
    public static final class UnequippedArmor {
        private Boolean visible;

        @LuaWhitelist
        public UnequippedArmor setVisible(Boolean visible) {
            this.visible = visible;
            return this;
        }

        @LuaWhitelist
        public Boolean getVisible() {
            return visible;
        }
    }

    @LuaWhitelist
    public static final class Group {
        private final List<PreviewFiguraParts.Part> parts;
        Group(List<PreviewFiguraParts.Part> parts) {
            this.parts = List.copyOf(parts);
        }
        @LuaWhitelist
        public Group setVisible(boolean visible) {
            parts.forEach(part -> part.setVisible(visible));
            return this;
        }
        @LuaWhitelist
        public boolean getVisible() {
            return parts.stream().allMatch(PreviewFiguraParts.Part::getVisible);
        }
    }
}
