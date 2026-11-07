package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.avatar.ysm.YsmManifestReader;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceSource;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryAnimationMapper;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryGeometryMapper;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmTextureDecoder;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;
import top.aihmc.omnimorph.model.ysm.YsmGeometryParser;

/** Shared bind geometry, resources and vertex ownership; playback state belongs to each instance. */
final class PreviewMesh {
    record DrawRange(int firstIndex, int indexCount, int textureIndex) {}

    final float[] vertices;
    final int[] indices;
    final BufferedImage texture;
    final java.util.List<BufferedImage> textures;
    final java.util.List<DrawRange> draws;
    final Vector3f center;
    final float radius;
    final float minimumY;
    final String label;
    final boolean fixture;
    final PreviewSkeleton skeleton;
    final int[] owners;
    final java.util.List<String> animationSources;
    final PreviewYsmResources ysmResources;
    PreviewFiguraParts figuraParts;
    PreviewVanillaModel vanilla;

    private PreviewMesh(float[] vertices, int[] indices, BufferedImage texture, String label, boolean fixture) {
        this(vertices, indices, texture == null ? java.util.List.of() : java.util.List.of(texture),
            java.util.List.of(new DrawRange(0, indices.length, texture == null ? -1 : 0)), label, fixture);
    }

    PreviewMesh(float[] vertices, int[] indices, java.util.List<BufferedImage> textures,
        java.util.List<DrawRange> draws, String label, boolean fixture) {
        this(vertices, indices, textures, draws, label, fixture, null, new int[vertices.length / 5],
            java.util.List.of(), PreviewYsmResources.EMPTY);
    }

    PreviewMesh(float[] vertices, int[] indices, java.util.List<BufferedImage> textures,
        java.util.List<DrawRange> draws, String label, boolean fixture, PreviewSkeleton skeleton, int[] owners,
        java.util.List<String> animationSources, PreviewYsmResources ysmResources) {
        this.vertices = vertices;
        this.indices = indices;
        this.texture = textures.isEmpty() ? null : textures.get(0);
        this.textures = java.util.List.copyOf(textures);
        this.draws = java.util.List.copyOf(draws);
        this.label = label;
        this.fixture = fixture;
        this.skeleton = skeleton;
        this.owners = owners;
        this.animationSources = java.util.List.copyOf(animationSources);
        this.ysmResources = ysmResources;
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        boolean anyVisible = skeleton == null;
        if (skeleton != null) {
            for (int owner : owners) {
                if (skeleton.restVisible[owner]) {
                    anyVisible = true;
                    break;
                }
            }
        }
        for (int offset = 0; offset < vertices.length; offset += 5) {
            Vector3f point = new Vector3f(vertices[offset], vertices[offset + 1], vertices[offset + 2]);
            if (!point.isFinite()) {
                throw new IllegalArgumentException("Non-finite preview vertex");
            }
            if (skeleton != null && anyVisible && !skeleton.restVisible[owners[offset / 5]]) {
                continue;
            }
            min.min(point);
            max.max(point);
        }
        center = min.add(max, new Vector3f()).mul(0.5f);
        radius = Math.max(0.01f, min.distance(max) * 0.5f);
        minimumY = min.y;
    }

    record ParserInfo(String implementation, int threads, double nativeParseMs, double loadMs) {}
    ParserInfo parserInfo = new ParserInfo("java", 1, -1, 0);

    static PreviewMesh load(PreviewConfig config) throws IOException {
        long started = System.nanoTime();
        PreviewMesh mesh = loadModel(config);
        PreviewMesh result =
            !mesh.fixture && config.vanilla.enabled && (mesh.figuraParts != null || config.scripts.enabled)
            ? PreviewVanillaModel.attach(mesh, config)
            : mesh;
        result.parserInfo = new ParserInfo(mesh.parserInfo.implementation, mesh.parserInfo.threads,
            mesh.parserInfo.nativeParseMs, (System.nanoTime() - started) / 1e6);
        return result;
    }

    private static PreviewMesh loadModel(PreviewConfig config) throws IOException {
        String model = config.scene.models.get(0);
        if (model.equals("builtin:cube")) {
            return cube();
        }
        if (config.benchmark.enabled && !config.animation.enabled) {
            throw new UnsupportedOperationException("Asset render benchmark requires animation.enabled=true");
        }
        Path source = config.configFile.getParent().resolve(model).normalize();
        if (config.execution.parser.equals("native"))
            return PreviewNativeAsset.load(config, source);
        if (YsmEncryptedSource.hasMagic(source)) {
            try (var encrypted = YsmEncryptedSource.read(source, () -> false)) {
                if (encrypted.legacyResources() != null) {
                    var mesh = loadResources(encrypted.legacyResources(), source.toString(), config);
                    return mesh;
                }
                var raw = encrypted.binary();
                YsmGeometry geometry = YsmBinaryGeometryMapper.map(raw.mainEntity.mainModel, encrypted.budget());
                BufferedImage texture = null;
                if (!raw.mainEntity.textures.isEmpty()) {
                    var selected = raw.mainEntity.textures.values()
                                       .stream()
                                       .filter(value
                                           -> value.name.equals(raw.properties.defaultTexture)
                                               || value.name.equals(raw.properties.defaultTexture + ".png"))
                                       .findFirst()
                                       .orElse(raw.mainEntity.textures.values().iterator().next());
                    byte[] png = YsmTextureDecoder.toPng(
                        selected.data, selected.imageFormat, selected.width, selected.height, encrypted.budget());
                    texture = ImageIO.read(new ByteArrayInputStream(png));
                    java.util.Arrays.fill(png, (byte) 0);
                }
                var animations = new ArrayList<String>();
                for (var file : raw.mainEntity.animationFiles.values()) {
                    if (file.animType != 11) {
                        animations.add(YsmBinaryAnimationMapper.animations(file, encrypted.budget()).toString());
                    }
                }
                for (var file : raw.mainEntity.animationControllerFiles) {
                    animations.add(YsmBinaryAnimationMapper.controllers(file, encrypted.budget()).toString());
                }
                var mesh = bake(geometry, texture, source.toString(), animations, PreviewYsmResources.binary(raw));
                return mesh;
            }
        }
        try (YsmPackage resources = YsmPackage.open(source)) {
            if (!resources.exists("ysm.json") && !resources.exists("main.json")
                && resources.resourcePaths().stream().anyMatch(
                    path -> path.toLowerCase(java.util.Locale.ROOT).endsWith(".bbmodel"))) {
                return FiguraLoader.load(source);
            }
            return loadResources(resources, source.toString(), config);
        }
    }

    private static PreviewMesh loadResources(YsmResourceSource resources, String label, PreviewConfig config)
        throws IOException {
        var manifest = YsmManifestReader.read(resources);
        if (manifest.mainModelPath().isBlank()) {
            throw new IOException("YSM manifest has no readable main model: " + label);
        }
        YsmGeometry geometry = YsmGeometryParser.parse(resources.readString(manifest.mainModelPath()));
        BufferedImage texture = null;
        if (!manifest.textures().isEmpty()) {
            var option = manifest.textures()
                             .stream()
                             .filter(value -> value.id().equals(manifest.defaultTexture()))
                             .findFirst()
                             .orElse(manifest.textures().get(0));
            byte[] bytes = resources.readBytes(option.path());
            texture = ImageIO.read(new ByteArrayInputStream(bytes));
            if (texture == null) {
                throw new IOException("Unsupported texture encoding: " + option.path());
            }
        }
        var animations = new ArrayList<String>();
        var firstPersonPaths = new java.util.HashSet<String>();
        for (String path : YsmManifestReader.firstPersonAnimationPaths(resources)) {
            firstPersonPaths.add(path.toLowerCase(java.util.Locale.ROOT));
        }
        for (String path : manifest.animationPaths()) {
            if (!firstPersonPaths.contains(path.toLowerCase(java.util.Locale.ROOT))) {
                animations.add(resources.readString(path));
            }
        }
        for (String path : manifest.animationControllerPaths()) {
            animations.add(resources.readString(path));
        }
        return bake(geometry, texture, label, animations, PreviewYsmResources.read(resources, manifest));
    }

    static PreviewMesh bake(YsmGeometry geometry, BufferedImage texture, String label) throws IOException {
        return bake(geometry, texture, label, java.util.List.of(), PreviewYsmResources.EMPTY);
    }

    static PreviewMesh bake(YsmGeometry geometry, BufferedImage texture, String label,
        java.util.List<String> animationSources, PreviewYsmResources ysmResources) throws IOException {
        var vertices = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();
        var owners = new ArrayList<Integer>();
        var boneIndices = new HashMap<String, Integer>();
        for (String name : geometry.bones.keySet()) {
            boneIndices.put(name, boneIndices.size());
        }
        var world = new HashMap<String, Matrix4f>();
        for (var bone : geometry.bones.values()) {
            Matrix4f matrix = world(bone, geometry, world, new HashSet<>());
            if (!bone.visible) {
                continue;
            }
            for (var cube : bone.cubes) {
                for (var quad : cube.quads) {
                    if (!quad.visible()) {
                        continue;
                    }
                    int first = vertices.size() / 5;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int position = vertex * 3;
                        Vector3f point = matrix.transformPosition(new Vector3f(quad.positions()[position],
                            quad.positions()[position + 1], quad.positions()[position + 2]));
                        vertices.add(point.x);
                        vertices.add(point.y);
                        vertices.add(point.z);
                        vertices.add(quad.uvs()[vertex * 2]);
                        vertices.add(quad.uvs()[vertex * 2 + 1]);
                        owners.add(boneIndices.get(bone.name));
                    }
                    for (int vertex : new int[] {0, 1, 2, 0, 2, 3}) {
                        indices.add(first + vertex);
                    }
                }
            }
        }
        if (indices.isEmpty()) {
            throw new IOException("Model contains no drawable quads: " + label);
        }
        float[] packed = new float[vertices.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = vertices.get(index);
        }
        int[] triangles = indices.stream().mapToInt(Integer::intValue).toArray();
        return new PreviewMesh(packed, triangles, texture == null ? java.util.List.of() : java.util.List.of(texture),
            java.util.List.of(new DrawRange(0, triangles.length, texture == null ? -1 : 0)), label, false,
            new PreviewSkeleton(geometry, world, YsmVisibilityDefaults.collect(animationSources)),
            owners.stream().mapToInt(Integer::intValue).toArray(), animationSources, ysmResources);
    }

    private static Matrix4f world(YsmGeometry.Bone bone, YsmGeometry geometry, Map<String, Matrix4f> cache,
        Set<String> visiting) throws IOException {
        if (cache.containsKey(bone.name)) {
            return cache.get(bone.name);
        }
        if (!visiting.add(bone.name)) {
            throw new IOException("Cyclic bone hierarchy: " + bone.name);
        }
        Matrix4f matrix = new Matrix4f();
        if (bone.parentName != null && !bone.parentName.isBlank()) {
            var parent = geometry.bones.get(bone.parentName);
            if (parent == null) {
                throw new IOException("Missing parent bone: " + bone.parentName);
            }
            matrix.set(world(parent, geometry, cache, visiting));
        }
        float x = bone.pivot[0] / 16f, y = bone.pivot[1] / 16f, z = bone.pivot[2] / 16f;
        matrix.translate(x, y, z)
            .rotateZ((float) Math.toRadians(bone.rotation[2]))
            .rotateY((float) Math.toRadians(bone.rotation[1]))
            .rotateX((float) Math.toRadians(bone.rotation[0]))
            .translate(-x, -y, -z);
        visiting.remove(bone.name);
        cache.put(bone.name, matrix);
        return matrix;
    }

    private static PreviewMesh cube() {
        float[] positions = {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1};
        float[] vertices = new float[40];
        for (int vertex = 0; vertex < 8; vertex++) {
            System.arraycopy(positions, vertex * 3, vertices, vertex * 5, 3);
        }
        return new PreviewMesh(vertices,
            new int[] {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2,
                6, 1, 6, 5},
            null, "BUILT-IN FIXTURE", true);
    }
}
