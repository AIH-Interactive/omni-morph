package org.figuramc.figura.avatar.ysm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;
import org.figuramc.figura.model.ysm.YsmGeometry;
import org.figuramc.figura.model.ysm.YsmModelValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Captures the actual logging backend while validating private and ordinary geometry. */
public final class YsmPrivateDiagnosticsSmokeTest {
    private static final String MARKER = "PRIVATE_DECRYPTED_BONE_MARKER";

    private YsmPrivateDiagnosticsSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("Figura/YSM");
        List<String> messages = new ArrayList<>();
        var appender = new AbstractAppender("ysm-private-diagnostics", null, null, true, null) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            YsmGeometry invalid = new YsmGeometry();
            var bone = new YsmGeometry.Bone(MARKER, MARKER, new float[3], new float[3]);
            invalid.bones.put(bone.name, bone);
            var identity = new YsmSourceIdentity(YsmSourceIdentity.ContainerKind.YSGP, 3, 26, 1, "fixture");
            var manifest = new YsmManifest(YsmAvatarKind.NEW, "fixture", "", new String[0], "main", "",
                    List.of(), List.of(), "");
            try (var resources = new YsmMemoryResourceSource("fixture", Map.of())) {
                try {
                    new YsmModelData(identity, manifest, resources, invalid, null).close();
                    throw new AssertionError("Private invalid geometry accepted");
                } catch (YsmDecodeException expected) {
                    require(expected.code() == YsmDecodeException.Code.INVALID_DATA, "private validation failure category");
                    require(!expected.getMessage().contains(MARKER) && expected.getCause() == null, "private failure sanitized");
                }
            }
            require(messages.isEmpty(), "private validation writes no detailed logs");
            YsmModelValidator.validate("ordinary", invalid);
            require(messages.stream().anyMatch(message -> message.contains(MARKER)), "ordinary diagnostics remain and capture works");
            messages.clear();
            var warning = new YsmGeometry.Bone(MARKER, "missing-" + MARKER, new float[3], new float[3]);
            YsmGeometry geometry = new YsmGeometry();
            geometry.bones.put(warning.name, warning);
            geometry.roots.add(warning);
            try (var resources = new YsmMemoryResourceSource("fixture", Map.of());
                 var data = new YsmModelData(identity, manifest, resources, geometry, null)) {
                require(data.diagnostics().contains("ENCRYPTED_GEOMETRY_WARNING"), "private warnings remain observable");
                require(data.diagnostics().stream().noneMatch(message -> message.contains(MARKER)), "private warning details sanitized");
            }
            require(messages.isEmpty(), "private warnings write no detailed logs");
            System.out.println("Private YSM geometry diagnostics and actual log capture checks passed.");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
