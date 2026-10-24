package org.figuramc.figura.config;

import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.utils.ColorUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public enum InputType {
    ANY(s -> true),
    INT(s -> {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }),
    POSITIVE_INT(s -> {
        try {
            Integer i = Integer.parseInt(s);
            return i >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }),
    FLOAT(s -> {
        try {
            Float f = Float.parseFloat(s);
            return !f.isInfinite() && !f.isNaN();
        } catch (Exception ignored) {
            return false;
        }
    }),
    POSITIVE_FLOAT(s -> {
        try {
            Float f = Float.parseFloat(s);
            return !f.isInfinite() && !f.isNaN() && f >= 0f;
        } catch (Exception ignored) {
            return false;
        }
    }),
    HEX_COLOR(s -> ColorUtils.userInputHex(s, null) != null),
    FOLDER_PATH(s -> {
        try {
            return s.isBlank() || Files.isDirectory(Path.of(s.trim()));
        } catch (Exception ignored) {
            return false;
        }
    }),
    IP(ServerAddress::isValidAddress),
    ENDPOINT(s -> top.aihmc.omnimorph.backend2.protocol.CloudEndpoint.valid(s, Configs.DEBUG_MODE.value), TextField.HintType.ANY);

    public final Predicate<String> validator;
    public final TextField.HintType hint;

    InputType(Predicate<String> predicate) {
        this(predicate, null);
    }

    InputType(Predicate<String> predicate, TextField.HintType hint) {
        this.validator = predicate;
        this.hint = hint == null ? TextField.HintType.valueOf(this.name()) : hint;
    }
}
