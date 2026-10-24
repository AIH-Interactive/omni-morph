package top.aihmc.omnimorph.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;

import org.figuramc.figura.config.ConfigManager;
import org.figuramc.figura.config.ConfigType;
/** Existing enum widget, stable disk names; frozen legacy numeric mapping: 0=Figura, 1=Protobuf. */
public final class CloudProtocolConfig extends ConfigType.EnumConfig implements ConfigType.SerializableConfig {
    private final Runnable changed;

    public CloudProtocolConfig(String name, ConfigType.Category category, Runnable changed) {
        super(name, category, 1, 2);
        this.changed = changed;
    }

    public CloudProtocol.Kind kind() {
        return value == 0 ? CloudProtocol.Kind.FIGURA_CLOUD : CloudProtocol.Kind.PROTOBUF;
    }

    @Override
    public Integer parseValue(String input) {
        return switch (input) {
            case "0", "FIGURA_CLOUD" -> 0;
            case "1", "PROTOBUF" -> 1;
            default -> {
                FiguraMod.LOGGER.warn("Unknown cloud_protocol setting; restoring Protobuf");
                yield 1;
            }
        };
    }

    @Override
    public void setValue(String input) {
        int next = parseValue(input);
        boolean modified = value != next;
        value = tempValue = next;
        if (modified && !ConfigManager.isInitializing()) {
            onChange();
        }
    }

    @Override
    public void onChange() {
        changed.run();
    }

    @Override
    public void setDefault() {
        setValue("PROTOBUF");
    }

    /** A missing field is always the new default, including config reload in the same process. */
    public void prepareLoad() {
        value = tempValue = defaultValue;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(kind().name());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            FiguraMod.LOGGER.warn("Invalid cloud_protocol setting; restoring Protobuf");
            setValue("PROTOBUF");
        } else {
            setValue(element.getAsString());
        }
    }
}
