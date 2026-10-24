package top.aihmc.omnimorph.wizards;

import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.figuramc.figura.wizards.AvatarWizard;
import org.figuramc.figura.wizards.WizardEntry;
/** Authoring fields contain resource-local values, never a temporary player bone ID. */
public final class CosmeticWizard extends AvatarWizard {
    private static final WizardEntry ANCHOR = new WizardEntry("COSMETIC_ANCHOR", WizardEntry.Type.TEXT);
    private static final List<WizardEntry> TRANSFORM = List.of(
            text("POSITION_X"), text("POSITION_Y"), text("POSITION_Z"),
            text("ROTATION_X"), text("ROTATION_Y"), text("ROTATION_Z"),
            text("SCALE_X"), text("SCALE_Y"), text("SCALE_Z"));
    private final Path root;
    private CosmeticTemplate template = CosmeticTemplate.HEAD;

    public CosmeticWizard() {
        this(FiguraMod.getFiguraDirectory().resolve("cosmetics"));
    }

    public CosmeticWizard(Path root) {
        this.root = root;
        changeEntry(ANCHOR, template.anchor());
        for (int index = 0; index < TRANSFORM.size(); index++) {
            changeEntry(TRANSFORM.get(index), index < 6 ? "0" : "1");
        }
    }

    private static WizardEntry text(String name) {
        // Do not register accessory-only fields in the existing Figura wizard's global entries.
        return new WizardEntry(name, WizardEntry.Type.TEXT);
    }

    public CosmeticTemplate template() {
        return template;
    }

    public void template(CosmeticTemplate value) {
        template = java.util.Objects.requireNonNull(value);
        anchor(value.anchor());
    }

    public String anchor() {
        return (String) getEntry(ANCHOR, "");
    }

    public void anchor(String value) {
        changeEntry(ANCHOR, value);
    }

    public OutfitConfig.Transform transform() {
        return new OutfitConfig.Transform(vector(0), vector(3), vector(6));
    }

    private OutfitConfig.Vector vector(int offset) {
        return new OutfitConfig.Vector(number(offset), number(offset + 1), number(offset + 2));
    }

    private double number(int index) {
        return Double.parseDouble((String) getEntry(TRANSFORM.get(index), ""));
    }

    @Override
    public Collection<WizardEntry> entries() {
        var entries = new ArrayList<>(List.of(WizardEntry.Meta, WizardEntry.NAME,
                WizardEntry.DESCRIPTION, WizardEntry.AUTHORS, ANCHOR));
        entries.addAll(TRANSFORM);
        return List.copyOf(entries);
    }

    @Override
    public Path targetDirectory() {
        try {
            return root.resolve(YsmAvatarCreator.directoryName((String) getEntry(WizardEntry.NAME, "")));
        } catch (IllegalArgumentException invalid) {
            return root;
        }
    }

    @Override
    public boolean canBuild() {
        try {
            YsmAvatarCreator.directoryName((String) getEntry(WizardEntry.NAME, ""));
            transform();
            return !anchor().isBlank() && anchor().length() <= 128;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> buildAsync() {
        if (!canBuild()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("INVALID_COSMETIC_FORM"));
        }
        String name = (String) getEntry(WizardEntry.NAME, "");
        String authors = (String) getEntry(WizardEntry.AUTHORS, "");
        String description = (String) getEntry(WizardEntry.DESCRIPTION, "");
        CosmeticTemplate selected = template;
        String selectedAnchor = anchor();
        var localTransform = transform();
        return AvatarLoadExecutor.submit(() -> {
            try {
                CosmeticCreator.create(root, name, authors, description, selected, selectedAnchor, localTransform);
                return null;
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    @Override
    public void build() throws java.io.IOException {
        try {
            CosmeticCreator.create(root, (String) getEntry(WizardEntry.NAME, ""),
                    (String) getEntry(WizardEntry.AUTHORS, ""), (String) getEntry(WizardEntry.DESCRIPTION, ""),
                    template, anchor(), transform());
        } catch (java.io.IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new java.io.IOException("COSMETIC_CREATION_FAILED", failure);
        }
    }
}
