package org.figuramc.figura.avatar.control;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.storage.StringPool;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.Varargs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class AvatarControlRuntime {
    private final LinkedHashMap<String, AvatarControlDefinition> controls = new LinkedHashMap<>();
    private LuaFunction onChange;

    public AvatarControlDefinition register(AvatarControlDefinition control) {
        controls.put(control.id(), control);
        return control;
    }

    public AvatarControlDefinition get(String id) {
        return controls.get(id);
    }

    public AvatarControlDefinition remove(String id) {
        return controls.remove(id);
    }

    public void clear() {
        controls.clear();
    }

    public Collection<AvatarControlDefinition> all() {
        return controls.values();
    }

    public Map<String, AvatarControlDefinition> asMap() {
        return new LinkedHashMap<>(controls);
    }

    public LuaFunction onChange() {
        return onChange;
    }

    public void setOnChange(LuaFunction onChange) {
        this.onChange = onChange;
    }

    public void syncAll(Avatar avatar) {
        for (AvatarControlDefinition control : controls.values())
            syncMolangValue(avatar, control);
    }

    public Object getValue(String id) {
        AvatarControlDefinition control = controls.get(id);
        return control == null ? null : control.getValue();
    }

    public boolean setValue(Avatar avatar, String id, Object value) {
        AvatarControlDefinition control = controls.get(id);
        if (control == null)
            return false;
        control.setValue(value);
        LuaFunction interceptor = onChange();
        if (avatar != null && interceptor != null) {
            Varargs result = avatar.run(interceptor, avatar.tick, control.id(), control.getValue(), control);
            if (result != null && result.arg(1).isboolean() && result.arg(1).checkboolean())
                return true;
        }
        syncMolangValue(avatar, control);
        LuaFunction onChange = control.onChange();
        if (avatar != null && onChange != null)
            avatar.run(onChange, avatar.tick, control.getValue(), control);
        return true;
    }

    public boolean press(Avatar avatar, String id) {
        AvatarControlDefinition control = controls.get(id);
        if (control == null)
            return false;
        LuaFunction onPress = control.onPress();
        if (avatar != null && onPress != null)
            avatar.run(onPress, avatar.tick, control);
        return true;
    }

    private static void syncMolangValue(Avatar avatar, AvatarControlDefinition control) {
        if (avatar == null || avatar.getMolangContext() == null || control == null)
            return;
        Object value = control.getValue();
        String command = value == null ? null : control.optionCommands().get(value.toString());
        if (command != null) {
            applyMolangAssignments(avatar, command);
            return;
        }
        setMolangVariable(avatar, control.binding(), value);
    }

    private static void applyMolangAssignments(Avatar avatar, String command) {
        for (String statement : command.split(";")) {
            int equals = statement.indexOf('=');
            if (equals <= 0)
                continue;
            String name = statement.substring(0, equals).trim();
            String rawValue = statement.substring(equals + 1).trim();
            try {
                setMolangVariable(avatar, name, Double.parseDouble(rawValue));
            } catch (NumberFormatException ignored) {
                if ("true".equalsIgnoreCase(rawValue))
                    setMolangVariable(avatar, name, true);
                else if ("false".equalsIgnoreCase(rawValue))
                    setMolangVariable(avatar, name, false);
            }
        }
    }

    private static void setMolangVariable(Avatar avatar, String name, Object value) {
        if (name == null || name.isBlank() || value == null || avatar.getMolangContext() == null)
            return;
        String varName = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
        float number;
        if (value instanceof Number n)
            number = n.floatValue();
        else if (value instanceof Boolean b)
            number = b ? 1f : 0f;
        else
            return;
        avatar.getMolangContext().variables.setScoped(StringPool.computeIfAbsent(varName), number);
    }
}
