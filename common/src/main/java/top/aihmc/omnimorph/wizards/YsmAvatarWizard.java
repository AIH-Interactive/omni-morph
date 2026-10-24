package top.aihmc.omnimorph.wizards;

import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.figuramc.figura.wizards.AvatarWizard;
import org.figuramc.figura.wizards.WizardEntry;
/** Metadata-only form; the complete bundled YSM package is prepared on the model worker. */
public final class YsmAvatarWizard extends AvatarWizard {
    private final Path root;

    public YsmAvatarWizard() {
        this(FiguraMod.getFiguraDirectory().resolve("avatars"));
    }

    public YsmAvatarWizard(Path root) {
        this.root = root;
    }

    @Override
    public Collection<WizardEntry> entries() {
        return List.of(WizardEntry.Meta, WizardEntry.NAME, WizardEntry.DESCRIPTION, WizardEntry.AUTHORS);
    }

    @Override
    public boolean canBuild() {
        try {
            YsmAvatarCreator.directoryName((String) getEntry(WizardEntry.NAME, ""));
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public Path targetDirectory() {
        String name = (String) getEntry(WizardEntry.NAME, "");
        try {
            return root.resolve(YsmAvatarCreator.directoryName(name));
        } catch (IllegalArgumentException invalid) {
            return root;
        }
    }

    @Override
    public CompletableFuture<Void> buildAsync() {
        // Snapshot on the UI thread; edits/resizes cannot alter an accepted creation job.
        String name = (String) getEntry(WizardEntry.NAME, "");
        String authors = (String) getEntry(WizardEntry.AUTHORS, "");
        String description = (String) getEntry(WizardEntry.DESCRIPTION, "");
        return AvatarLoadExecutor.submit(() -> {
            try {
                YsmAvatarCreator.create(root, name, authors, description);
                return null;
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    @Override
    public void build() throws IOException {
        YsmAvatarCreator.create(root, (String) getEntry(WizardEntry.NAME, ""),
                (String) getEntry(WizardEntry.AUTHORS, ""),
                (String) getEntry(WizardEntry.DESCRIPTION, ""));
    }
}
