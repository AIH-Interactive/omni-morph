package top.aihmc.omm.preview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.figuramc.figura.parsers.BlockbenchParser2;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omm.preview.data.PreviewTags.*;

/** Reuses OMP's v4/v5 bbmodel parser, then uploads its real tagged geometry/texture output. */
final class FiguraLoader {
    private static final Map<String, float[][]> CUBE_FACES =
        Map.of("n", new float[][] {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}, "s",
            new float[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, "e",
            new float[][] {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}, "w",
            new float[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, "u",
            new float[][] {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}}, "d",
            new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}});
    private static final int[][] UV_CORNERS = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

    private final List<BufferedImage> textures = new ArrayList<>();
    private record Face(float[] vertices, int owner) {}
    private final Map<Integer, List<Face>> faces = new LinkedHashMap<>();
    private final PreviewFiguraParts parts = new PreviewFiguraParts();
    private int currentOwner;

    static PreviewMesh load(Path source) throws IOException {
        if (Files.isDirectory(source)) {
            return new FiguraLoader().read(source.toRealPath(), source.toString());
        }
        try (FileSystem zip = FileSystems.newFileSystem(source, (ClassLoader) null)) {
            Path root = zip.getPath("/");
            List<Path> manifests;
            try (var paths = Files.walk(root)) {
                manifests = paths.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().equalsIgnoreCase("avatar.json"))
                                .sorted(Comparator.comparingInt(Path::getNameCount))
                                .toList();
            }
            if (!manifests.isEmpty()) {
                int shallowest = manifests.get(0).getNameCount();
                if (manifests.stream().filter(path -> path.getNameCount() == shallowest).count() != 1) {
                    throw new IOException("Ambiguous Figura ZIP: multiple avatar roots");
                }
                root = manifests.get(0).getParent();
            }
            return new FiguraLoader().read(root, source.toString());
        }
    }

    private PreviewMesh read(Path root, String label) throws IOException {
        List<Path> models;
        try (var paths = Files.walk(root)) {
            models = paths.filter(Files::isRegularFile)
                         .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                         .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                         .toList();
        }
        if (models.isEmpty()) {
            throw new IOException("Figura avatar has no bbmodel: " + label);
        }
        var parser = new BlockbenchParser2();
        for (Path path : models) {
            String name = path.getFileName().toString();
            String within = root.relativize(path.getParent()).toString().replace('\\', '.').replace('/', '.');
            if (!within.isBlank()) {
                within += ".";
            }
            try {
                var parsed = parser.parseModel(
                    root, path, Files.readString(path), name.substring(0, name.length() - ".bbmodel".length()), within);
                readTextures(parsed.textures());
                parts.animations.addAll(parsed.animationList());
                parts.modelRoots.add(parsed.modelNbt());
                visit(parsed.modelNbt(), new Matrix4f(), -1, "");
            } catch (Exception error) {
                throw new IOException("Cannot load Figura bbmodel " + path + ": " + error.getMessage(), error);
            }
        }
        var vertices = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();
        var draws = new ArrayList<PreviewMesh.DrawRange>();
        var owners = new ArrayList<Integer>();
        for (var material : faces.entrySet()) {
            int firstIndex = indices.size();
            for (Face owned : material.getValue()) {
                float[] face = owned.vertices();
                int firstVertex = vertices.size() / 5;
                for (float value : face) {
                    vertices.add(value);
                }
                int count = face.length / 5;
                for (int vertex = 0; vertex < count; vertex++) {
                    owners.add(owned.owner());
                }
                for (int vertex = 1; vertex < count - 1; vertex++) {
                    indices.add(firstVertex);
                    indices.add(firstVertex + vertex);
                    indices.add(firstVertex + vertex + 1);
                }
            }
            draws.add(new PreviewMesh.DrawRange(firstIndex, indices.size() - firstIndex, material.getKey()));
        }
        if (indices.isEmpty()) {
            throw new IOException("Figura avatar contains no visible drawable faces: " + label);
        }
        float[] packed = new float[vertices.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = vertices.get(index);
        }
        var result = new PreviewMesh(packed, indices.stream().mapToInt(Integer::intValue).toArray(), textures, draws,
            label, false, new PreviewSkeleton(parts), owners.stream().mapToInt(Integer::intValue).toArray(), List.of(),
            PreviewYsmResources.EMPTY);
        result.figuraParts = parts;
        return result;
    }

    private void readTextures(CompoundTag root) throws IOException {
        CompoundTag sources = root.compound("src");
        for (Tag value : root.list("data")) {
            CompoundTag data = (CompoundTag) value;
            String path = data.string("d", "");
            if (path.isEmpty()) {
                throw new IOException("Figura texture set has no diffuse texture");
            }
            byte[] encoded = ((ByteArrayTag) sources.get(path)).value();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
            if (image == null) {
                throw new IOException("Unsupported Figura texture: " + path);
            }
            textures.add(image);
        }
    }

    private void visit(CompoundTag part, Matrix4f parent, int parentIndex, String parentPath) throws IOException {
        boolean visible = part.number("vsb", 1) != 0;
        Vector3f pivot = vector(part, "piv");
        Vector3f degrees = vector(part, "rot");
        Vector3f rotation = new Vector3f(degrees).mul((float) (Math.PI / 180));
        Matrix4f local = new Matrix4f().translate(pivot);
        if (part.contains("mesh_data")) {
            local.rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z);
        } else {
            local.rotateZ(rotation.z).rotateY(rotation.y).rotateX(rotation.x);
        }
        local.translate(-pivot.x, -pivot.y, -pivot.z);
        Matrix4f world = new Matrix4f(parent).mul(local);
        int index = parts.nodes.size();
        String name = part.string("name", "");
        String path = parentPath.isEmpty() ? name : parentPath + "." + name;
        Matrix4f bind = new Matrix4f(world);
        bind.m30(bind.m30() / 16).m31(bind.m31() / 16).m32(bind.m32() / 16);
        parts.nodes.add(new PreviewFiguraParts.Node(name, path, parentIndex, new Vector3f(pivot).div(16), degrees,
            part.contains("mesh_data"), visible, bind, new Matrix4f(bind).invert()));
        currentOwner = index;
        if (part.contains("anim")) {
            var channels = new ArrayList<CompoundTag>();
            for (Tag animation : part.list("anim")) {
                channels.add((CompoundTag) animation);
            }
            parts.channels.put(index, List.copyOf(channels));
        }
        if (part.contains("cube_data")) {
            cube(part, world);
        }
        if (part.contains("mesh_data")) {
            mesh(part.compound("mesh_data"), world);
        }
        if (part.contains("chld")) {
            for (Tag child : part.list("chld")) {
                visit((CompoundTag) child, world, index, path);
            }
        }
    }

    private void cube(CompoundTag part, Matrix4f world) throws IOException {
        float inflate = part.number("inf", 0);
        Vector3f from = vector(part, "f").sub(inflate, inflate, inflate);
        Vector3f difference = vector(part, "t").add(inflate, inflate, inflate).sub(from);
        for (var entry : part.compound("cube_data").entries().entrySet()) {
            CompoundTag face = (CompoundTag) entry.getValue();
            int texture = (int) face.number("tex", 0);
            float[][] corners = CUBE_FACES.get(entry.getKey());
            float[] uv = values(face, "uv", 4);
            int rotation = Math.floorMod((int) (face.number("rot", 0) / 90), 4);
            float[] packed = new float[20];
            for (int vertex = 0; vertex < 4; vertex++) {
                float[] corner = corners[vertex];
                Vector3f point = new Vector3f(corner[0], corner[1], corner[2]).mul(difference).add(from);
                int[] uvCorner = UV_CORNERS[(vertex + rotation) % 4];
                putVertex(packed, vertex, point, world, texture, uvCorner[0] == 0 ? uv[0] : uv[2],
                    uvCorner[1] == 0 ? uv[1] : uv[3]);
            }
            faces.computeIfAbsent(texture, unused -> new ArrayList<>()).add(new Face(packed, currentOwner));
        }
    }

    private void mesh(CompoundTag mesh, Matrix4f world) throws IOException {
        if (mesh.contains("vtx_weights")) {
            throw new UnsupportedOperationException("Figura weighted armature skinning has not been connected yet");
        }
        ListTag positions = mesh.list("vtx"), uv = mesh.list("uvs"), indices = mesh.list("fac");
        int indexOffset = 0, uvOffset = 0;
        for (Tag value : mesh.list("tex")) {
            int material = ((NumericTag) value).number().intValue() & 0xffff;
            int texture = material >>> 4, count = material & 15;
            float[] packed = new float[count * 5];
            for (int vertex = 0; vertex < count; vertex++) {
                Tag indexTag = indices.get(indexOffset++);
                int index = switch (indexTag) {
                    case ByteTag number -> Byte.toUnsignedInt(number.value());
                    case ShortTag number -> Short.toUnsignedInt(number.value());
                    case IntTag number -> number.value();
                    default -> throw new IOException("Unexpected Figura mesh index type");
                };
                Vector3f point = new Vector3f(
                    positions.number(index * 3), positions.number(index * 3 + 1), positions.number(index * 3 + 2));
                putVertex(packed, vertex, point, world, texture, uv.number(uvOffset++), uv.number(uvOffset++));
            }
            faces.computeIfAbsent(texture, unused -> new ArrayList<>()).add(new Face(packed, currentOwner));
        }
    }

    private void putVertex(float[] packed, int vertex, Vector3f point, Matrix4f world, int texture, float u, float v)
        throws IOException {
        if (texture < 0 || texture >= textures.size()) {
            throw new IOException("Figura face references missing texture " + texture);
        }
        world.transformPosition(point).div(16);
        packed[vertex * 5] = point.x;
        packed[vertex * 5 + 1] = point.y;
        packed[vertex * 5 + 2] = point.z;
        packed[vertex * 5 + 3] = u / textures.get(texture).getWidth();
        packed[vertex * 5 + 4] = v / textures.get(texture).getHeight();
    }

    private static Vector3f vector(CompoundTag part, String key) {
        float[] values = values(part, key, 3);
        return new Vector3f(values[0], values[1], values[2]);
    }

    private static float[] values(CompoundTag part, String key, int count) {
        float[] result = new float[count];
        if (part.contains(key)) {
            ListTag list = part.list(key);
            for (int index = 0; index < count; index++) {
                result[index] = list.number(index);
            }
        }
        return result;
    }
}
