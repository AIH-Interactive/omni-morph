package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.backend2.library.LibraryModels;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class LibraryUploadDraftSmokeTest {
    public static void main(String[] args) {
        UUID uploader = UUID.randomUUID();
        var credits = new LibraryMetadataReader.Metadata("FIGURA", "AYL's Dragon Wings & Tail",
                java.util.stream.IntStream.range(0, 9).mapToObj(index -> "Original author " + index).toList(),
                "", true);
        var creditsDraft = new LibraryUploadDraft(uploader);
        long creditsGeneration = creditsDraft.beginSource(Path.of("aylDWT 4.2.zip"));
        creditsDraft.category("general");
        creditsDraft.prepared(creditsGeneration, new LibraryPackagePreparation.Prepared(
                credits, new CompoundTag(), new byte[]{1}, "synthetic"));
        require(creditsDraft.snapshot(limits(64)).metadata().get("tags").equals(List.of()),
                "a source with nine original authors can publish without optional tags");
        var longAuthor = "中".repeat(10000);
        var extensiveCredits = new LibraryMetadataReader.Metadata("FIGURA", "Full credits",
                List.of(longAuthor), "", true);
        creditsDraft.prepared(creditsGeneration, new LibraryPackagePreparation.Prepared(
                extensiveCredits, new CompoundTag(), new byte[]{1}, "synthetic"));
        require(creditsDraft.snapshot(limits(64)).metadata().get("authors").equals(List.of(longAuthor)),
                "source author text has no length limit and is never truncated");
        var manyAuthors = java.util.stream.IntStream.range(0, 128).mapToObj(index -> "Author " + index).toList();
        creditsDraft.prepared(creditsGeneration, new LibraryPackagePreparation.Prepared(
                new LibraryMetadataReader.Metadata("FIGURA", "Many authors", manyAuthors, "", true),
                new CompoundTag(), new byte[]{1}, "synthetic"));
        require(creditsDraft.snapshot(limits(64)).metadata().get("authors").equals(manyAuthors),
                "source author lists have no count limit");
        creditsDraft.tags("1,2,3,4,5,6,7,8,9");
        try {
            creditsDraft.snapshot(limits(64));
            throw new AssertionError("tag count limit was bypassed");
        } catch (LibraryUploadDraft.InvalidCount invalid) {
            require(invalid.field().equals("tags") && invalid.maximumEntries() == 8,
                    "tag count errors identify the exact field and count");
        }
        var draft = new LibraryUploadDraft(uploader);
        var free = new LibraryMetadataReader.Metadata("FIGURA", "内部名称", List.of("原作者"), "说明", false);
        var restricted = new LibraryMetadataReader.Metadata("YSM_PLAIN", "YSM", List.of(), "", true);
        var categoryDraft = new LibraryUploadDraft(uploader);
        categoryDraft.categories(List.of("full_model", "decorative_parts", "custom"));
        categoryDraft.beginSource(Path.of("component.zip"));
        require(categoryDraft.category().equals("full_model"), "noncomponent sources default to the built-in full model category");
        categoryDraft.defaultCategory(true);
        require(categoryDraft.category().equals("decorative_parts"), "visible vanilla player selects decorative parts");
        categoryDraft.category("custom");
        categoryDraft.defaultCategory(false);
        require(categoryDraft.category().equals("custom"), "async model initialization cannot overwrite manual category selection");
        categoryDraft.beginSource(Path.of("full.ysm"));
        require(categoryDraft.category().equals("full_model"), "a new source starts with its own category default");
        categoryDraft.categories(List.of("general"));
        require(categoryDraft.category().equals("general"), "older backend category lists remain supported");
        long first = draft.beginSource(Path.of("first.zip"));
        require(draft.metadata(first, free) && !draft.requiresAuthorization(), "free metadata preselects no authorization");
        draft.requiresAuthorization(true);
        draft.displayName("玩家名称");
        draft.description("玩家简介");
        draft.isPublic(false);
        var prepared = new LibraryPackagePreparation.Prepared(free, new CompoundTag(), new byte[]{1, 2, 3}, "synthetic");
        require(draft.prepared(first, prepared) && draft.requiresAuthorization()
                && draft.displayName().equals("玩家名称") && draft.description().equals("玩家简介") && !draft.isPublic(),
                "later preparation cannot overwrite explicit edits or visibility");
        require(!draft.metadata(first, restricted), "late metadata-only result cannot replace prepared source identity");
        draft.category("general");
        draft.tags(" Tag , tag, 第二个 , ,");
        var snapshot = draft.snapshot(limits(64));
        require(snapshot.metadata().get("authors").equals(List.of("原作者"))
                && snapshot.metadata().get("internal_name").equals("内部名称")
                && snapshot.metadata().get("tags").equals(List.of("Tag", "第二个"))
                && draft.uploader().equals(uploader), "readonly origin and normalized tags");
        reject(() -> draft.snapshot(limits(2)));
        draft.displayName("中".repeat(33));
        reject(() -> draft.snapshot(limits(64)));
        draft.displayName("§伪造格式");
        reject(() -> draft.snapshot(limits(64)));
        long second = draft.beginSource(Path.of("second.ysm"));
        require(!draft.prepared(first, prepared), "old source callback discarded");
        draft.requiresAuthorization(false);
        require(draft.metadata(second, restricted) && !draft.requiresAuthorization(),
                "manual edit before metadata arrives also wins");
        long third = draft.beginSource(Path.of("third.ysm"));
        require(draft.metadata(third, restricted) && draft.requiresAuthorization(), "new source gets its own default");
        var formatted = LibraryMetadataReader.fromFigura(
                top.aihmc.omnimorph.parsers.AvatarMetadataParser.read(
                        "{\"name\":\"§b测试模型\",\"authors\":[\"§e作者\"],\"description\":\"§l模型说明§r\"}"),
                "fallback");
        var formattedDraft = new LibraryUploadDraft(uploader);
        long formattedSource = formattedDraft.beginSource(Path.of("formatted.zip"));
        formattedDraft.prepared(formattedSource, new LibraryPackagePreparation.Prepared(
                formatted, new CompoundTag(), new byte[]{1}, "synthetic"));
        formattedDraft.category("general");
        var formattedSnapshot = formattedDraft.snapshot(limits(64));
        require(formattedSnapshot.metadata().get("display_name").equals("测试模型")
                        && formattedSnapshot.metadata().get("authors").equals(List.of("作者"))
                        && formattedSnapshot.metadata().get("description").equals("模型说明"),
                "source formatting does not make an otherwise valid upload impossible");
        System.out.println("PASS upload draft: metadata defaults, explicit edit precedence, source generations and live UTF-8 limits");
    }

    private static LibraryModels.Capabilities limits(long bytes) {
        return new LibraryModels.Capabilities(true, 1, bytes, 8, 1024, 256, 256,
                List.of("zip", "ysm"), List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"), List.of("general"), false);
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("invalid submission accepted");
        } catch (IllegalArgumentException expected) {
            // Current backend limits are applied when taking a submission snapshot.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
