package org.figuramc.figura.avatar.ysm;

import org.figuramc.figura.model.ysm.YsmGeometry;
import org.figuramc.figura.model.ysm.YsmGeometryParser;
import org.figuramc.figura.model.ysm.YsmModelValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Owned, in-memory runtime input. It is separate from a serializable source payload. */
public final class YsmModelData implements AutoCloseable {
    private final YsmSourceIdentity identity;
    private final YsmManifest manifest;
    private final YsmMemoryResourceSource resources;
    private final YsmGeometry mainGeometry;
    private final YsmGeometry armGeometry;
    private final List<String> diagnostics;

    public YsmModelData(YsmSourceIdentity identity, YsmManifest manifest, YsmMemoryResourceSource resources,
                        YsmGeometry mainGeometry, YsmGeometry armGeometry) throws IOException {
        this.identity = identity;
        this.manifest = manifest;
        this.resources = resources;
        List<String> diagnostics = new ArrayList<>(manifest.resourceIndex().warnings());
        validateGeometry(manifest.mainModelPath(), mainGeometry, diagnostics);
        if (armGeometry != null) {
            validateGeometry(manifest.armModelPath(), armGeometry, diagnostics);
        }
        this.mainGeometry = copyGeometry(mainGeometry);
        this.armGeometry = armGeometry == null ? null : copyGeometry(armGeometry);
        this.diagnostics = List.copyOf(diagnostics);
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

    private static void validateGeometry(String path, YsmGeometry geometry, List<String> diagnostics) throws IOException {
        if (geometry == null) {
            throw new IOException("MISSING_RESOURCE: YSM main geometry is required");
        }
        var result = YsmModelValidator.validate(path, geometry);
        diagnostics.addAll(result.warnings());
        if (!result.valid()) {
            throw new IOException("INVALID_GEOMETRY: " + String.join("; ", result.errors()));
        }
        if (result.stats().boneCount() > 4096 || result.stats().quadCount() > 262144) {
            throw new IOException("RESOURCE_LIMIT: YSM geometry exceeds the M0 budget");
        }
    }

    private static YsmGeometry copyGeometry(YsmGeometry source) {
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

    @Override
    public void close() {
        resources.close();
    }
}
