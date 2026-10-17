package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Audits the real shared scanner, including decoded encrypted resources, without executing model code. */
public final class YsmBindingReferenceAudit {
    private static final Pattern SYNC_ACCESS = Pattern.compile("(?i)\\bysm\\s*\\.\\s*sync\\b");
    private static final String NUMBER = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
    private static final Pattern NUMERIC_SYNC_ARGUMENTS = Pattern.compile(
            "\\s*\\(\\s*(?:" + NUMBER + "(?:\\s*,\\s*" + NUMBER + "){0,15})?\\s*\\)");
    private static final Set<String> REVIEWED_PENDING_BINDINGS = Set.of(
            "ctrl.im_delta", "ctrl.im_pitch", "ctrl.im_time",
            "ctrl.bcombat_attack_animation", "ctrl.carryon_is_princess", "ctrl.climb", "ctrl.climbing",
            "ctrl.parcool_state", "ctrl.tac_gun_type", "ctrl.tac_hold_gun", "ctrl.tac_is_aim",
            "ctrl.tac_is_fire", "ctrl.tac_is_melee", "ctrl.tac_is_reload",
            "ctrl.ladder_up", "ctrl.ladder_down", "ctrl.ladder_stillness",
            "ctrl.slashblade_animation", "ctrl.has_sophisticated_backpack",
            "ysm.first_order", "ysm.second_order", "ysm.particle", "ysm.abs_particle", "ysm.bone_param",
            "ysm.has_any_curios", "ysm.has_any_curios_with_any_tag", "ysm.has_any_curios_with_all_tags");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Provide the avatar corpus directory");
        }
        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Avatar corpus directory is missing");
        }
        YsmModelScanner.ScanSummary summary = YsmModelScanner.scan(root);
        long encryptedCandidates = 0;
        long encryptedValidated = 0;
        long unresolvedBoneParamModels = 0;
        long syncResources = 0;
        long numericSyncCalls = 0;
        long unresolvedSyncAccesses = 0;
        long auditedModels = 0;
        TreeMap<String, Integer> pendingBindings = new TreeMap<>();
        TreeMap<String, Integer> optionalUnavailableBindings = new TreeMap<>();
        int unreviewedBindings = 0;
        for (YsmModelScanner.ScanReport report : summary.reports()) {
            for (String binding : report.unsupportedBindings()) {
                if (REVIEWED_PENDING_BINDINGS.contains(binding)) {
                    if (report.unavailableOptionalBindings().containsKey(binding)) {
                        optionalUnavailableBindings.merge(binding, 1, Integer::sum);
                    } else {
                        pendingBindings.merge(binding, 1, Integer::sum);
                    }
                } else {
                    unreviewedBindings++;
                    if (!YsmEncryptedSource.hasMagic(report.path())) {
                        System.out.println("M4 unreviewed plaintext binding: " + binding);
                    }
                }
            }
            if (Files.isRegularFile(report.path())
                    && report.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ysm")) {
                encryptedCandidates++;
                if (report.valid()) {
                    encryptedValidated++;
                }
            }
            if (report.unsupportedBindings().stream().anyMatch(binding -> binding.contains("bone_param"))) {
                unresolvedBoneParamModels++;
            }
            if (report.valid()) {
                try (YsmModelData data = YsmModelData.read(report.path(), () -> false)) {
                    for (String path : data.resources().resourcePaths()) {
                        String extension = path.toLowerCase(Locale.ROOT);
                        if (extension.endsWith(".json") || extension.endsWith(".molang") || extension.endsWith(".lua")) {
                            String source = data.resources().readString(path);
                            var accesses = SYNC_ACCESS.matcher(source);
                            boolean containsSync = false;
                            while (accesses.find()) {
                                containsSync = true;
                                var arguments = NUMERIC_SYNC_ARGUMENTS.matcher(source).region(accesses.end(), source.length());
                                if (arguments.lookingAt()) {
                                    numericSyncCalls++;
                                } else {
                                    unresolvedSyncAccesses++;
                                }
                            }
                            if (containsSync) {
                                syncResources++;
                            }
                        }
                    }
                }
                auditedModels++;
            }
        }
        require(summary.reports().size() == 40 && summary.validCount() == 38 && summary.invalidCount() == 2,
                "reviewed M2 corpus candidate/valid/known-negative counts changed; audit coverage must be reviewed");
        require(encryptedCandidates == 5 && encryptedValidated == 5,
                "all five encrypted candidates must be decoded and validated for this reference audit");
        require(unresolvedBoneParamModels == 0,
                "corpus now depends on unresolved bone_param; obtain a contract before replacing its placeholder");
        require(auditedModels == 38, "all valid decoded models must participate in the sync migration audit");
        System.out.printf(Locale.ROOT,
                "M4 binding reference audit: candidates=%d valid=%d knownNegative=%d encryptedValidated=%d boneParamUsers=%d syncModelsAudited=%d syncResources=%d numericSyncCalls=%d unresolvedSyncAccesses=%d%n",
                summary.reports().size(), summary.validCount(), summary.invalidCount(),
                encryptedValidated, unresolvedBoneParamModels, auditedModels, syncResources,
                numericSyncCalls, unresolvedSyncAccesses);
        System.out.println("M4 reviewed pending binding/model counts: " + pendingBindings);
        System.out.println("M4 optional unavailable capability/model counts: " + optionalUnavailableBindings);
        require(unreviewedBindings == 0,
                "new unreviewed binding dependencies require a private contract audit before inventory acceptance");
        require(unresolvedSyncAccesses == 0,
                "corpus has non-literal, string-key or value ysm.sync access; review migration semantics");
        require(syncResources == 1 && numericSyncCalls == 2,
                "reviewed sync call inventory changed; inspect the new dependencies before accepting migration");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
