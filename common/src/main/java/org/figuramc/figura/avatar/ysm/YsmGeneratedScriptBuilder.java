package org.figuramc.figura.avatar.ysm;

import java.util.*;

public final class YsmGeneratedScriptBuilder {
    private static final Map<String, List<String>> STATE_CANDIDATES = Map.of(
            "death", List.of("death", "die"),
            "sleep", List.of("sleep", "sleeping"),
            "fly", List.of("elytra", "flying", "fly"),
            "swim", List.of("swim", "swimming"),
            "sneak", List.of("sneak", "crouch", "sneaking", "crouching"),
            "run", List.of("run", "sprint", "running", "sprinting"),
            "walk", List.of("walk", "walking"),
            "fall", List.of("falling", "fall"),
            "jump", List.of("jump_start", "jump"),
            "idle", List.of("idle", "stand", "default")
    );

    private YsmGeneratedScriptBuilder() {
    }

    public static String build(String modelName, Set<String> animationNames) {
        List<String> baseAnimations = animationNames.stream()
                .filter(name -> name.startsWith("pre_parallel"))
                .sorted()
                .toList();

        Map<String, String> states = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : STATE_CANDIDATES.entrySet()) {
            String name = findAnimation(animationNames, entry.getValue());
            if (name != null)
                states.put(entry.getKey(), name);
        }

        StringBuilder script = new StringBuilder();
        script.append("vanilla_model.PLAYER:setVisible(false)\n\n");
        script.append("local MODEL = '").append(lua(modelName)).append("'\n");
        script.append("local A = animations[MODEL] or {}\n");
        script.append("local active_main = nil\n\n");
        script.append("""
                local function get_anim(name)
                    return name and A[name] or nil
                end

                local function play(name, priority, blend)
                    local anim = get_anim(name)
                    if not anim then return false end
                    anim:setPriority(priority or 0)
                    if blend ~= nil then anim:setBlend(blend) end
                    if not anim:isPlaying() then anim:play() end
                    return true
                end

                local function stop(name)
                    local anim = get_anim(name)
                    if anim and not anim:isStopped() then anim:stop() end
                end

                local function set_var(name, value)
                    animations:setMolangVar(name, value or 0)
                end

                """);

        for (String base : baseAnimations)
            script.append("play('").append(lua(base)).append("', 0)\n");
        if (!baseAnimations.isEmpty())
            script.append("\n");

        appendStateTable(script, states);
        script.append("""
                local function choose_state()
                    local death = animations:evalMolang('query.is_playing_dead')
                    if death ~= 0 and STATES.death then return STATES.death, 3 end
                    local sleep = animations:evalMolang('query.is_sleeping')
                    if sleep ~= 0 and STATES.sleep then return STATES.sleep, 3 end
                    local swimming = animations:evalMolang('query.is_swimming')
                    if swimming ~= 0 and STATES.swim then return STATES.swim, 2 end
                    local on_ground = animations:evalMolang('query.is_on_ground')
                    if on_ground == 0 then
                        local vertical = animations:evalMolang('query.vertical_speed')
                        if vertical < -0.01 and STATES.fall then return STATES.fall, 2 end
                        if STATES.jump then return STATES.jump, 2 end
                    end
                    local sneaking = animations:evalMolang('query.is_sneaking')
                    if sneaking ~= 0 and STATES.sneak then return STATES.sneak, 1 end
                    local sprinting = animations:evalMolang('query.is_sprinting')
                    if sprinting ~= 0 and STATES.run then return STATES.run, 1 end
                    local speed = animations:evalMolang('query.ground_speed')
                    if speed > 0.01 and STATES.walk then return STATES.walk, 1 end
                    return STATES.idle, 1
                end

                events.TICK:register(function()
                    set_var('ysm_speed', animations:evalMolang('query.ground_speed'))
                    set_var('ysm_on_ground', animations:evalMolang('query.is_on_ground'))
                    set_var('ysm_sneaking', animations:evalMolang('query.is_sneaking'))
                    set_var('ysm_sprinting', animations:evalMolang('query.is_sprinting'))
                    set_var('ysm_swimming', animations:evalMolang('query.is_swimming'))
                    set_var('ysm_using_item', animations:evalMolang('query.is_using_item'))
                    set_var('ysm_first_person', animations:evalMolang('query.is_first_person'))

                    local next_state, priority = choose_state()
                    if next_state ~= active_main then
                        stop(active_main)
                        active_main = next_state
                    end
                    play(active_main, priority or 1)
                end)
                """);
        return script.toString();
    }

    public static String buildWheelBootstrap() {
        return """
                pcall(function()
                local root = action_wheel:newPage('YSM')
                local pages = { root = root }
                local slots = { root = 1 }
                local linked_pages = {}

                local function normalize_page(name)
                    if name == nil or name == '' then return 'root' end
                    return tostring(name)
                end

                local function normalize_key(value)
                    if value == nil then return '' end
                    return string.lower(tostring(value)):gsub('[%s%-]+', '_')
                end

                local function is_extra_animation(id, action)
                    local id_key = normalize_key(id)
                    local page_key = normalize_key(action:getPage())
                    local animation_key = normalize_key(action:getAnimation())
                    return id_key:find('extra_animation', 1, true) ~= nil
                        or page_key:find('extra_animation', 1, true) ~= nil
                        or animation_key:find('extra_animation', 1, true) ~= nil
                end

                local function next_slot(name)
                    name = normalize_page(name)
                    local slot = slots[name] or 1
                    slots[name] = slot + 1
                    return slot
                end

                local function page_for(name)
                    name = normalize_page(name)
                    local page = pages[name]
                    if page ~= nil then return page end

                    page = action_wheel:newPage('YSM: ' .. name)
                    pages[name] = page
                    slots[name] = 2
                    page:newAction(1)
                        :title('Back')
                        :item('minecraft:arrow')
                        :onLeftClick(function()
                            action_wheel:setPage(root)
                        end)
                    return page
                end

                root:newAction(next_slot('root'))
                    :title('Settings')
                    :item('minecraft:comparator')
                    :controlsPage('root')

                for id, action in pairs(ysm_actions:getActions()) do
                    if is_extra_animation(id, action) then
                        local action_page = normalize_page(action:getPage())
                        if normalize_key(action_page):find('extra_animation', 1, true) == nil then
                            action_page = 'extra_animation'
                        end

                        if action_page ~= 'root' and not linked_pages[action_page] then
                            linked_pages[action_page] = true
                            local target_page = action_page
                            root:newAction(next_slot('root'))
                                :title('Extra Animation')
                                :item('minecraft:book')
                                :onLeftClick(function()
                                    action_wheel:setPage(page_for(target_page))
                                end)
                        end

                        local animation = action:getAnimation()
                        if animation ~= nil and string.sub(tostring(animation), 1, 1) == '#' then
                            page_for(action_page):newAction(next_slot(action_page))
                                :title(action:getTitle())
                                :item('minecraft:comparator')
                                :controlsPage(string.sub(tostring(animation), 2))
                        else
                            page_for(action_page):newAction(next_slot(action_page))
                                :title(action:getTitle())
                                :item('minecraft:armor_stand')
                                :ysmAction(id)
                        end
                    end
                end

                action_wheel:setPage(root)
                end)
                """;
    }

    private static void appendStateTable(StringBuilder script, Map<String, String> states) {
        script.append("local STATES = {\n");
        for (Map.Entry<String, String> entry : states.entrySet())
            script.append("    ").append(entry.getKey()).append(" = '").append(lua(entry.getValue())).append("',\n");
        script.append("}\n\n");
    }

    private static String findAnimation(Set<String> animationNames, List<String> candidates) {
        for (String candidate : candidates) {
            for (String name : animationNames) {
                String normalized = normalize(name);
                if (normalized.equals(candidate) || normalized.endsWith("." + candidate) || normalized.endsWith("_" + candidate))
                    return name;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String lua(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
