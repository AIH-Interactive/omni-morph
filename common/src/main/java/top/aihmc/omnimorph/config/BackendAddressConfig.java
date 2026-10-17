package top.aihmc.omnimorph.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;

/** Reuses the original string editor; a stored debug origin survives permission revocation. */
public class BackendAddressConfig extends ConfigType.StringConfig implements ConfigType.SerializableConfig {
    public BackendAddressConfig(String name, ConfigType.Category category, String defaultValue) {
        super(name, category, defaultValue, InputType.ENDPOINT);
    }

    @Override
    public String parseValue(String address) {
        if (!CloudEndpoint.valid(address, Configs.DEBUG_MODE.value)) {
            throw new IllegalArgumentException("backend.endpoint.invalid");
        }
        return address.trim();
    }

    @Override
    public void setValue(String address) {
        try {
            String next = parseValue(address);
            boolean modified = !value.equals(next);
            value = tempValue = next;
            if (modified && !ConfigManager.isInitializing()) {
                onChange();
            }
        } catch (IllegalArgumentException rejected) {
            tempValue = value;
            FiguraMod.LOGGER.warn("Backend address rejected by endpoint policy");
        }
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(value);
    }

    @Override
    public void deserialize(JsonElement element) {
        try {
            String stored = element.getAsString().trim();
            CloudEndpoint.parse(stored, () -> Configs.DEBUG_MODE.value);
            // Retain an explicitly saved HTTP origin even with Debug off. Transport refuses it,
            // and the original field shows its invalid state until the user enables Debug or edits it.
            value = tempValue = stored;
        } catch (Exception invalid) {
            FiguraMod.LOGGER.warn("Invalid stored backend origin; keeping the existing address");
        }
    }
}
