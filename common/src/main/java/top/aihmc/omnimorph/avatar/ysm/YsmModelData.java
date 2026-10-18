package top.aihmc.omnimorph.avatar.ysm;

import top.aihmc.omnimorph.model.ysm.YsmGeometry;
import top.aihmc.omnimorph.model.ysm.YsmGeometryParser;
import top.aihmc.omnimorph.model.ysm.YsmModelValidator;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryResourceMapper;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Owned, in-memory runtime input. It is separate from a serializable source payload. */
public final class YsmModelData implements AutoCloseable {
    private final YsmSourceIdentity identity;
    private final YsmManifest manifest;
    private final YsmMemoryResourceSource resources;
    private final YsmGeometry mainGeometry;
    private final YsmGeometry armGeometry;
    private final List<String> diagnostics;
    private final Map<String, YsmGeometry> additionalGeometries;
    private final byte[] ciphertext;
    private boolean closed;

    public YsmModelData(YsmSourceIdentity identity, YsmManifest manifest, YsmMemoryResourceSource resources,
                        YsmGeometry mainGeometry, YsmGeometry armGeometry) throws IOException {
        this(identity, manifest, resources, mainGeometry, armGeometry, Map.of(), null);
    }

    private YsmModelData(YsmSourceIdentity identity, YsmManifest manifest, YsmMemoryResourceSource resources,
                          YsmGeometry mainGeometry, YsmGeometry armGeometry,
                          Map<String, YsmGeometry> geometries, byte[] ciphertext) throws IOException {
        this.identity = identity;
        this.manifest = manifest;
        this.resources = resources;
        List<String> diagnostics = new ArrayList<>(manifest.resourceIndex().warnings());
        boolean privateSource = identity.containerKind() == YsmSourceIdentity.ContainerKind.YSGP;
        validateGeometry(manifest.mainModelPath(), mainGeometry, diagnostics, privateSource);
        if (armGeometry != null) {
            validateGeometry(manifest.armModelPath(), armGeometry, diagnostics, privateSource);
        }
        this.mainGeometry = copyGeometry(mainGeometry);
        this.armGeometry = armGeometry == null ? null : copyGeometry(armGeometry);
        Map<String, YsmGeometry> additional = new LinkedHashMap<>();
        for (var entry : geometries.entrySet()) {
            if (entry.getKey().equals(manifest.mainModelPath()) || entry.getKey().equals(manifest.armModelPath())) {
                continue;
            }
            validateGeometry(entry.getKey(), entry.getValue(), diagnostics, privateSource);
            additional.put(entry.getKey(), copyGeometry(entry.getValue()));
        }
        this.additionalGeometries = Map.copyOf(additional);
        this.ciphertext = ciphertext == null ? null : ciphertext.clone();
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static YsmModelData read(Path path, BooleanSupplier cancelled) throws IOException {
        if (YsmEncryptedSource.hasMagic(path)) {
            try (YsmEncryptedSource source = YsmEncryptedSource.read(path, cancelled)) {
                return readEncrypted(source, cancelled);
            }
        }
        try (YsmPackage source = YsmPackage.open(path)) {
            return read(source, Files.isDirectory(path) ? YsmSourceIdentity.ContainerKind.DIRECTORY
                    : YsmSourceIdentity.ContainerKind.ZIP, cancelled);
        }
    }

    public static YsmModelData readEncrypted(byte[] ciphertext, String displayName,
                                             BooleanSupplier cancelled) throws IOException {
        try (YsmEncryptedSource source = YsmEncryptedSource.read(ciphertext, displayName, cancelled)) {
            return readEncrypted(source, cancelled);
        }
    }

    private static YsmModelData readEncrypted(YsmEncryptedSource source,
                                              BooleanSupplier cancelled) throws IOException {
        try {
            if (source.legacyResources() != null) {
                try (YsmModelData legacy = read(source.legacyResources(), YsmSourceIdentity.ContainerKind.YSGP, cancelled)) {
                    return encryptedCopy(source, legacy.manifest, legacy.resources,
                            legacy.mainGeometry, legacy.armGeometry, Map.of(), cancelled);
                }
            }
            try (var mapped = YsmBinaryResourceMapper.map(source.binary(), source.displayName(), source.budget())) {
                return encryptedCopy(source, mapped.manifest(), mapped.resources(),
                        mapped.geometries().get(mapped.manifest().mainModelPath()),
                        mapped.geometries().get(mapped.manifest().armModelPath()), mapped.geometries(), cancelled);
            }
        } catch (java.io.InterruptedIOException | YsmDecodeException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "data/encrypted-assembly", 0);
        }
    }

    private static YsmModelData encryptedCopy(YsmEncryptedSource source, YsmManifest manifest,
                                               YsmResourceSource resources, YsmGeometry main, YsmGeometry arm,
                                               Map<String, YsmGeometry> geometries,
                                               BooleanSupplier cancelled) throws IOException {
        YsmMemoryResourceSource snapshot = YsmMemoryResourceSource.snapshot(resources, source.budget().limits(), cancelled);
        byte[] ciphertext = source.ciphertextCopy();
        YsmModelData data = null;
        try {
            data = new YsmModelData(source.identity(), manifest, snapshot, main, arm, geometries, ciphertext);
            source.budget().check("data/encrypted-ready", 0);
            return data;
        } catch (IOException | RuntimeException e) {
            if (data != null) {
                data.close();
            } else {
                snapshot.close();
            }
            throw e;
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    public static YsmModelData read(YsmResourceSource source, YsmSourceIdentity.ContainerKind kind,
                                    BooleanSupplier cancelled) throws IOException {
        YsmMemoryResourceSource snapshot = YsmMemoryResourceSource.snapshot(source, YsmResourceLimits.DEFAULT, cancelled);
        try {
            YsmManifest manifest = YsmManifestReader.read(snapshot);
            YsmSourceIdentity identity = YsmSourceIdentity.ofResources(snapshot, kind);
            YsmResourceLimits.checkCancelled(cancelled);
            YsmGeometry main = YsmGeometryParser.parse(snapshot.readString(manifest.mainModelPath()));
            YsmGeometry arm = manifest.hasArmModel()
                    ? YsmGeometryParser.parse(snapshot.readString(manifest.armModelPath())) : null;
            YsmResourceLimits.checkCancelled(cancelled);
            return new YsmModelData(identity, manifest, snapshot, main, arm);
        } catch (IOException | RuntimeException e) {
            snapshot.close();
            throw e;
        }
    }

    private static void validateGeometry(String path, YsmGeometry geometry, List<String> diagnostics,
                                         boolean privateSource) throws IOException {
        if (geometry == null) {
            throw new IOException("MISSING_RESOURCE: YSM main geometry is required");
        }
        var result = YsmModelValidator.validate(path, geometry, !privateSource);
        if (privateSource) {
            for (int index = 0; index < result.warnings().size(); index++) {
                diagnostics.add("ENCRYPTED_GEOMETRY_WARNING");
            }
        } else {
            diagnostics.addAll(result.warnings());
        }
        if (!result.valid()) {
            if (privateSource) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "data/geometry-validation", 0);
            }
            throw new IOException("INVALID_GEOMETRY: " + String.join("; ", result.errors()));
        }
        if (result.stats().boneCount() > 4096 || result.stats().quadCount() > 262144) {
            throw new IOException("RESOURCE_LIMIT: YSM geometry exceeds the M0 budget");
        }
    }

    /** Copies baked geometry for independent runtime ownership, including binary-only inputs. */
    public static YsmGeometry copyGeometry(YsmGeometry source) {
        YsmGeometry copy = new YsmGeometry();
        copy.textureWidth = source.textureWidth;
        copy.textureHeight = source.textureHeight;
        for (YsmGeometry.Bone bone : source.bones.values()) {
            YsmGeometry.Bone target = new YsmGeometry.Bone(
                    bone.name, bone.parentName, bone.pivot.clone(), bone.rotation.clone());
            target.visible = bone.visible;
            for (YsmGeometry.Cube group : bone.cubes) {
                List<YsmGeometry.Quad> faces = new ArrayList<>();
                for (YsmGeometry.Quad face : group.quads) {
                    faces.add(new YsmGeometry.Quad(face.positions().clone(), face.uvs().clone(), face.normal().clone()));
                }
                target.cubes.add(new YsmGeometry.Cube(faces));
            }
            copy.bones.put(target.name, target);
        }
        for (YsmGeometry.Bone bone : copy.bones.values()) {
            YsmGeometry.Bone parent = copy.bones.get(bone.parentName);
            if (parent == null) {
                copy.roots.add(bone);
            } else {
                parent.children.add(bone);
            }
        }
        return copy;
    }

    public YsmSourceIdentity identity() {
        return identity;
    }

    public YsmManifest manifest() {
        return manifest;
    }

    public YsmResourceSource resources() {
        return resources;
    }

    public YsmGeometry mainGeometry() {
        return copyGeometry(mainGeometry);
    }

    public YsmGeometry armGeometry() {
        return armGeometry == null ? null : copyGeometry(armGeometry);
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public boolean encrypted() {
        return ciphertext != null;
    }

    public byte[] ciphertextCopy() {
        if (closed || ciphertext == null) {
            throw new IllegalStateException("YSM data has no encrypted source");
        }
        return ciphertext.clone();
    }

    public YsmGeometry geometry(String path) {
        if (manifest.mainModelPath().equals(path)) {
            return mainGeometry();
        }
        if (manifest.armModelPath().equals(path)) {
            return armGeometry();
        }
        YsmGeometry geometry = additionalGeometries.get(path);
        return geometry == null ? null : copyGeometry(geometry);
    }

    public boolean hasGeometry(String path) {
        return manifest.mainModelPath().equals(path) || armGeometry != null && manifest.armModelPath().equals(path)
                || additionalGeometries.containsKey(path);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        resources.close();
        if (ciphertext != null) {
            Arrays.fill(ciphertext, (byte) 0);
        }
        closed = true;
    }
}
