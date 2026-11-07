package top.aihmc.omnimorph.avatar.control;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaFunction;
import top.aihmc.omm.preview.PreviewLua;
import top.aihmc.omm.preview.PreviewSettings;

/** Standalone owner adapter retaining AvatarControlRuntime's value, interceptor, binding and callback order. */
public final class PreviewControlRuntime {
    private final LinkedHashMap<String, AvatarControlDefinition> definitions = new LinkedHashMap<>();
    private LuaFunction onChange;
    private long revision;
    private final java.util.Set<String> initiallyHydrated = new java.util.HashSet<>();
    private final PreviewSettings settings;

    public PreviewControlRuntime() {
        this(null);
    }

    public PreviewControlRuntime(PreviewSettings settings) {
        this.settings = settings;
    }

    public AvatarControlDefinition register(PreviewLua owner, AvatarControlDefinition control) {
        if (settings != null)
            settings.applySavedValue(control);
        AvatarControlDefinition previous = definitions.put(control.id(), control);
        if (previous != null && previous != control) {
            previous.onDefinitionChanged(null);
        }
        control.onDefinitionChanged(() -> revision++);
        revision++;
        return control;
    }

    public AvatarControlDefinition register(AvatarControlDefinition control) {
        return register(null, control);
    }

    public long revision() {
        return revision;
    }

    public AvatarControlDefinition get(String id) {
        return definitions.get(id);
    }

    public Collection<AvatarControlDefinition> all() {
        return definitions.values();
    }

    public Map<String, AvatarControlDefinition> asMap() {
        return new LinkedHashMap<>(definitions);
    }

    public Object getValue(String id) {
        AvatarControlDefinition control = get(id);
        return control == null ? null : control.getValue();
    }

    public AvatarControlDefinition remove(String id) {
        AvatarControlDefinition removed = definitions.remove(id);
        if (removed != null) {
            removed.onDefinitionChanged(null);
            revision++;
        }
        return removed;
    }

    public void clear() {
        for (AvatarControlDefinition control : definitions.values()) {
            control.onDefinitionChanged(null);
        }
        definitions.clear();
        revision++;
    }

    public LuaFunction onChange() {
        return onChange;
    }

    public void setOnChange(LuaFunction function) {
        onChange = function;
    }

    public void syncAll(PreviewLua owner) {
        for (AvatarControlDefinition control : definitions.values()) {
            if (initiallyHydrated.remove(control.id())) {
                continue;
            }
            sync(owner, control);
        }
    }

    public void hydrateInitial(PreviewLua owner) {
        for (AvatarControlDefinition control : definitions.values()) {
            if (settings != null && settings.applySavedValue(control))
                continue;
            if (control.initialValueFromBinding() && hydrate(owner, control)) {
                initiallyHydrated.add(control.id());
            }
        }
    }

    public void hydrateLiveValues(PreviewLua owner) {
        for (AvatarControlDefinition control : definitions.values()) {
            if (!control.persistent() || control.liveBinding()) {
                hydrate(owner, control);
            }
        }
    }

    private boolean hydrate(PreviewLua owner, AvatarControlDefinition control) {
        if (control.binding() == null || control.binding().isBlank()) {
            return false;
        }
        Object value;
        try (var scope = owner.molang.executionBudget().begin()) {
            value = owner.molang.evaluator().evalAll(owner.molang.compile(control.binding()), true);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Invalid model control binding", failure);
        }
        if (value == null) {
            return false;
        }
        switch (control.type()) {
            case TOGGLE -> {
                if (value instanceof Boolean enabled) {
                    control.setValue(enabled);
                } else if (value instanceof Number number) {
                    control.setValue(number.floatValue() > 0);
                } else {
                    return false;
                }
            }
            case SLIDER, NUMBER -> {
                if (!(value instanceof Number number)) {
                    return false;
                }
                control.setValue(number.doubleValue());
            }
            case ENUM ->
                control.setValue(value instanceof Number number ? Math.round(number.floatValue()) : value.toString());
            case TEXT, COLOR, KEYBIND -> control.setValue(value.toString());
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean setValue(PreviewLua owner, String id, Object value) {
        AvatarControlDefinition control = get(id);
        if (control == null) {
            return false;
        }
        control.setValue(value);
        if (onChange != null) {
            var result = owner.run(onChange, control.id(), control.getValue(), control);
            if (result.arg1().isboolean() && result.arg1().checkboolean()) {
                return true;
            }
        }
        sync(owner, control);
        if (settings != null)
            settings.save(control);
        if (control.onChange() != null) {
            owner.run(control.onChange(), control.getValue(), control);
        }
        return true;
    }

    public boolean press(PreviewLua owner, String id) {
        AvatarControlDefinition control = get(id);
        if (control == null) {
            return false;
        }
        if (control.onPress() != null) {
            owner.run(control.onPress(), control);
        }
        return true;
    }

    public boolean reset(PreviewLua owner, String id) {
        AvatarControlDefinition control = get(id);
        return control != null && setValue(owner, id, control.getDefault());
    }

    public void resetAll(PreviewLua owner) {
        for (AvatarControlDefinition control : definitions.values()) {
            if (control.hasStoredValue() && control.type() != AvatarControlType.LABEL
                && control.type() != AvatarControlType.SEPARATOR && control.type() != AvatarControlType.BUTTON) {
                setValue(owner, control.id(), control.getDefault());
            }
        }
    }

    private void sync(PreviewLua owner, AvatarControlDefinition control) {
        Object value = control.getValue();
        String command = control.optionCommands().get(String.valueOf(value));
        if (command == null && value != null && !control.optionCommands().isEmpty()) {
            try {
                int index = Math.round(Float.parseFloat(value.toString()));
                if (index >= 0 && index < control.options().size()) {
                    command = control.optionCommands().get(control.options().get(index));
                }
            } catch (NumberFormatException namedOption) {
                // Named options already used the direct command lookup above.
            }
        }
        if (command != null) {
            execute(owner, command);
            return;
        }
        if (control.binding() == null || control.binding().isBlank()) {
            return;
        }
        float number;
        if (value instanceof Number numeric) {
            number = numeric.floatValue();
        } else if (value instanceof Boolean enabled) {
            number = enabled ? 1 : 0;
        } else {
            return;
        }
        String path = control.binding().trim().toLowerCase(java.util.Locale.ROOT);
        if (path.startsWith("v.")) {
            path = path.substring(2);
        } else if (path.startsWith("variable.")) {
            path = path.substring("variable.".length());
        }
        owner.molang.variables.setPath(path, number);
        if (path.startsWith("roaming.")) {
            String alias = path.substring("roaming.".length()).replace('.', '_');
            owner.molang.variables.setPath(alias, number);
            owner.molang.variables.setPath("show_" + alias, number);
        }
    }

    private void execute(PreviewLua owner, String command) {
        try {
            owner.molang.evaluate(owner.molang.compile(command));
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Invalid model control command", failure);
        }
    }
}
