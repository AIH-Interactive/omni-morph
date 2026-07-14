package org.figuramc.figura.avatar.control;

import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@LuaWhitelist
@LuaTypeDoc(
        name = "AvatarControl",
        value = "avatar_control"
)
public class AvatarControlDefinition {
    private final String id;
    private final AvatarControlType type;
    private String title;
    private String page = "root";
    private String category;
    private Object defaultValue;
    private Object value;
    private double min;
    private double max = 1d;
    private double step = 0.05d;
    private final List<String> options = new ArrayList<>();
    private final LinkedHashMap<String, String> optionCommands = new LinkedHashMap<>();
    private String description;
    private String binding;
    private String targetPage;
    private LuaFunction onChange;
    private LuaFunction onPress;

    public AvatarControlDefinition(@LuaNotNil String id, @LuaNotNil AvatarControlType type) {
        this.id = id;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public AvatarControlType type() {
        return type;
    }

    public String page() {
        return page;
    }

    public Object value() {
        return value == null ? defaultValue : value;
    }

    public boolean hasStoredValue() {
        return value != null || defaultValue != null;
    }

    public List<String> options() {
        return options;
    }

    public String binding() {
        return binding;
    }

    public String targetPage() {
        return targetPage;
    }

    public String description() {
        return description;
    }

    public Map<String, String> optionCommands() {
        return optionCommands;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public LuaFunction onChange() {
        return onChange;
    }

    public LuaFunction onPress() {
        return onPress;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_id")
    public String getId() {
        return id;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_type")
    public String getType() {
        return type.name().toLowerCase();
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_title")
    public String getTitle() {
        return title == null ? id : title;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "title"
            ),
            aliases = "title",
            value = "avatar_control.set_title"
    )
    public AvatarControlDefinition setTitle(String title) {
        this.title = title;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_title(String title) {
        return setTitle(title);
    }

    @LuaWhitelist
    public AvatarControlDefinition title(String title) {
        return setTitle(title);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_description")
    public String getDescription() {
        return description;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "description"
            ),
            aliases = "description",
            value = "avatar_control.set_description"
    )
    public AvatarControlDefinition setDescription(String description) {
        this.description = description;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_description(String description) {
        return setDescription(description);
    }

    @LuaWhitelist
    public AvatarControlDefinition description(String description) {
        return setDescription(description);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_page")
    public String getPage() {
        return page;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "page"
            ),
            aliases = "page",
            value = "avatar_control.set_page"
    )
    public AvatarControlDefinition setPage(String page) {
        this.page = page == null || page.isBlank() ? "root" : page;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_page(String page) {
        return setPage(page);
    }

    @LuaWhitelist
    public AvatarControlDefinition page(String page) {
        return setPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_category")
    public String getCategory() {
        return category;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "category"
            ),
            aliases = "category",
            value = "avatar_control.set_category"
    )
    public AvatarControlDefinition setCategory(String category) {
        this.category = category;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_category(String category) {
        return setCategory(category);
    }

    @LuaWhitelist
    public AvatarControlDefinition category(String category) {
        return setCategory(category);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_binding")
    public String getBinding() {
        return binding;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "binding"
            ),
            aliases = "binding",
            value = "avatar_control.set_binding"
    )
    public AvatarControlDefinition setBinding(String binding) {
        this.binding = binding == null || binding.isBlank() ? null : binding;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_binding(String binding) {
        return setBinding(binding);
    }

    @LuaWhitelist
    public AvatarControlDefinition binding(String binding) {
        return setBinding(binding);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_target_page")
    public String getTargetPage() {
        return targetPage;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "page"
            ),
            aliases = "targetPage",
            value = "avatar_control.set_target_page"
    )
    public AvatarControlDefinition setTargetPage(String page) {
        this.targetPage = page == null || page.isBlank() ? null : page;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_target_page(String page) {
        return setTargetPage(page);
    }

    @LuaWhitelist
    public AvatarControlDefinition targetPage(String page) {
        return setTargetPage(page);
    }

    @LuaWhitelist
    public AvatarControlDefinition target_page(String page) {
        return setTargetPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_default")
    public Object getDefault() {
        return defaultValue;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Object.class,
                    argumentNames = "value"
            ),
            aliases = "default",
            value = "avatar_control.set_default"
    )
    public AvatarControlDefinition setDefault(Object value) {
        this.defaultValue = normalizeValue(value);
        if (this.value == null)
            this.value = this.defaultValue;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_default(Object value) {
        return setDefault(value);
    }

    @LuaWhitelist
    public AvatarControlDefinition defaultValue(Object value) {
        return setDefault(value);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_value")
    public Object getValue() {
        return value();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Object.class,
                    argumentNames = "value"
            ),
            aliases = "value",
            value = "avatar_control.set_value"
    )
    public AvatarControlDefinition setValue(Object value) {
        this.value = normalizeValue(value);
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_value(Object value) {
        return setValue(value);
    }

    @LuaWhitelist
    public AvatarControlDefinition value(Object value) {
        return setValue(value);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_min")
    public double getMin() {
        return min;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_max")
    public double getMax() {
        return max;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_step")
    public double getStep() {
        return step;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.set_min")
    public AvatarControlDefinition setMin(double min) {
        this.min = min;
        if (this.max < min)
            this.max = min;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_min(double min) {
        return setMin(min);
    }

    @LuaWhitelist
    public AvatarControlDefinition min(double min) {
        return setMin(min);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.set_max")
    public AvatarControlDefinition setMax(double max) {
        this.max = Math.max(min, max);
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_max(double max) {
        return setMax(max);
    }

    @LuaWhitelist
    public AvatarControlDefinition max(double max) {
        return setMax(max);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.set_step")
    public AvatarControlDefinition setStep(double step) {
        if (step > 0d)
            this.step = step;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_step(double step) {
        return setStep(step);
    }

    @LuaWhitelist
    public AvatarControlDefinition step(double step) {
        return setStep(step);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {Double.class, Double.class, Double.class},
                    argumentNames = {"min", "max", "step"}
            ),
            aliases = "range",
            value = "avatar_control.set_range"
    )
    public AvatarControlDefinition setRange(double min, double max, Double step) {
        this.min = min;
        this.max = Math.max(min, max);
        this.step = step == null || step <= 0d ? this.step : step;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_range(double min, double max, Double step) {
        return setRange(min, max, step);
    }

    @LuaWhitelist
    public AvatarControlDefinition range(double min, double max, Double step) {
        return setRange(min, max, step);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_options")
    public List<String> getOptions() {
        return new ArrayList<>(options);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "option"
            ),
            aliases = "option",
            value = "avatar_control.add_option"
    )
    public AvatarControlDefinition addOption(String option) {
        if (option != null)
            options.add(option);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.get_option_commands")
    public Map<String, String> getOptionCommands() {
        return new LinkedHashMap<>(optionCommands);
    }

    @LuaWhitelist
    public Map<String, String> get_option_commands() {
        return getOptionCommands();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {String.class, String.class},
                    argumentNames = {"option", "command"}
            ),
            aliases = "optionCommand",
            value = "avatar_control.add_option_command"
    )
    public AvatarControlDefinition addOptionCommand(String option, String command) {
        addOption(option);
        if (option != null && command != null && !command.isBlank())
            optionCommands.put(option, command);
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition add_option_command(String option, String command) {
        return addOptionCommand(option, command);
    }

    @LuaWhitelist
    public AvatarControlDefinition optionCommand(String option, String command) {
        return addOptionCommand(option, command);
    }

    @LuaWhitelist
    public AvatarControlDefinition option_command(String option, String command) {
        return addOptionCommand(option, command);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = List.class,
                    argumentNames = "options"
            ),
            aliases = "options",
            value = "avatar_control.set_options"
    )
    public AvatarControlDefinition setOptions(List<?> values) {
        options.clear();
        optionCommands.clear();
        if (values != null) {
            for (Object value : values) {
                if (value != null)
                    options.add(value.toString());
            }
        }
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_options(List<?> values) {
        return setOptions(values);
    }

    @LuaWhitelist
    public AvatarControlDefinition options(List<?> values) {
        return setOptions(values);
    }

    @LuaWhitelist
    public AvatarControlDefinition add_option(String option) {
        return addOption(option);
    }

    @LuaWhitelist
    public AvatarControlDefinition option(String option) {
        return addOption(option);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.remove_option")
    public AvatarControlDefinition removeOption(String option) {
        options.remove(option);
        optionCommands.remove(option);
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition remove_option(String option) {
        return removeOption(option);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_control.clear_options")
    public AvatarControlDefinition clearOptions() {
        options.clear();
        optionCommands.clear();
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition clear_options() {
        return clearOptions();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onChange",
            value = "avatar_control.set_on_change"
    )
    public AvatarControlDefinition setOnChange(LuaFunction function) {
        this.onChange = function;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_on_change(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    public AvatarControlDefinition onChange(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    public AvatarControlDefinition on_change(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onPress",
            value = "avatar_control.set_on_press"
    )
    public AvatarControlDefinition setOnPress(LuaFunction function) {
        this.onPress = function;
        return this;
    }

    @LuaWhitelist
    public AvatarControlDefinition set_on_press(LuaFunction function) {
        return setOnPress(function);
    }

    @LuaWhitelist
    public AvatarControlDefinition onPress(LuaFunction function) {
        return setOnPress(function);
    }

    @LuaWhitelist
    public AvatarControlDefinition on_press(LuaFunction function) {
        return setOnPress(function);
    }

    private Object normalizeValue(Object value) {
        if (type == AvatarControlType.TOGGLE && value instanceof Boolean)
            return value;
        if ((type == AvatarControlType.SLIDER || type == AvatarControlType.NUMBER) && value instanceof Number n)
            return Math.max(min, Math.min(max, n.doubleValue()));
        if (type == AvatarControlType.ENUM && value != null)
            return value.toString();
        return value;
    }

    @Override
    public String toString() {
        return "Avatar Control (" + id + ")";
    }
}
