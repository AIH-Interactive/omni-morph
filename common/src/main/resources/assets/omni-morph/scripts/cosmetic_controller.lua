-- Built-in outfit orchestration. Author scripts never receive cosmetic_native or this Globals table.
local declarations = cosmetic_native:getDeclarations()
local mounted = {}
local failed = {}
local attempted = {}
local sampled = {}
local by_id = {}
local tails = {}
local selected_tail = {}
local selection_done = {}
local allow_fallback = false
local controller = {}

for _, declaration in ipairs(declarations) do
    by_id[declaration.id] = declaration
    if declaration.enabled and declaration.category == "Tail" then
        tails[#tails + 1] = declaration
    end
end
table.sort(tails, function(first, second) return first.id < second.id end)

local function would_cycle(child_id, parent_id)
    local seen = {}
    while parent_id do
        if parent_id == child_id or seen[parent_id] then
            return true
        end
        seen[parent_id] = true
        local parent = by_id[parent_id]
        parent_id = parent and (selected_tail[parent.id] or parent.parent_id or parent.preferred_tail)
    end
    return false
end

local function exported_tip(declaration, candidate)
    if not candidate or not candidate.enabled or candidate.category ~= "Tail"
            or would_cycle(declaration.id, candidate.id) then
        return nil
    end
    local candidates = cosmetic_native:findExportedAnchors(candidate.id, "tailtip")
    return #candidates == 1 and candidates[1] or nil
end

local function choose_tail(declaration)
    if not selection_done[declaration.id] then
        selection_done[declaration.id] = true
        if declaration.preferred_tail then
            local candidate = by_id[declaration.preferred_tail]
            if exported_tip(declaration, candidate) then
                selected_tail[declaration.id] = candidate.id
            end
        else
            for _, candidate in ipairs(tails) do
                if exported_tip(declaration, candidate) then
                    selected_tail[declaration.id] = candidate.id
                    break
                end
            end
        end
    end
    local id = selected_tail[declaration.id]
    if id and not failed[id] and cosmetic_native:isHealthy(id) and (not allow_fallback or mounted[id]) then
        cosmetic_native:recordAutomaticTail(declaration.id, id)
        return exported_tip(declaration, by_id[id]), id
    end
    cosmetic_native:recordAutomaticTail(declaration.id, "")
    return nil
end

local function unique(candidates)
    return #candidates == 1 and candidates[1] or nil
end

local function target(declaration)
    if declaration.anchor_type == "BASE_BONE" or declaration.anchor_type == "ACCESSORY_BONE"
            or declaration.anchor_type == "VIRTUAL" then
        return declaration.bone, declaration.parent_id
    end
    if declaration.anchor_type == "STANDARD" then
        return unique(cosmetic_native:findAnchors(declaration.anchor_name))
    end
    if declaration.anchor_type == "AUTO_TRAIL" then
        local bone, parent = choose_tail(declaration)
        if bone then
            return bone, parent
        end
        return unique(cosmetic_native:findAnchors("tailtip"))
                or unique(cosmetic_native:findAnchors("hip"))
                or unique(cosmetic_native:findBaseBones("Body"))
                or cosmetic_native:avatarRoot()
    end
    return nil
end

function controller.tick()
    for _, declaration in ipairs(declarations) do
        local id = declaration.id
        if declaration.enabled and mounted[id] and not failed[id] then
            if not cosmetic_native:tick(id) then
                failed[id] = true
                mounted[id] = false
                cosmetic_native:resetTrail(id)
            end
        end
    end
end

function controller.beginFrame()
    mounted = {}
    attempted = {}
    sampled = {}
    allow_fallback = false
end

function controller.frame(delta)
    for _, declaration in ipairs(declarations) do
        local id = declaration.id
        if declaration.enabled and not failed[id] and not attempted[id] then
            local bone, parent = target(declaration)
            -- A hand-only pass must not detach valid body/other-hand instances from ticking.
            local parent_ready = not parent or (mounted[parent] and cosmetic_native:isHealthy(parent))
            mounted[id] = parent_ready and bone and cosmetic_native:hasBone(bone) or false
            local matrix = mounted[id] and cosmetic_native:boneMatrix(bone)
            if matrix then
                attempted[id] = true
                if cosmetic_native:prepare(id, delta) then
                    local mount = matrix:rightMultiply(declaration.transform)
                    cosmetic_native:emit(id, mount)
                    if declaration.category == "Trail" then
                        -- Sampling choices belong to Lua; native storage enforces negotiated capacity.
                        cosmetic_native:sampleTrail(id, bone, mount, 0.1, 1, 12, 8)
                        sampled[id] = true
                    end
                    mounted[id] = true
                else
                    failed[id] = true
                    mounted[id] = false
                end
            end
        end
    end
end

function controller.fallbackFrame(delta)
    allow_fallback = true
    controller.frame(delta)
end

function controller.endFrame()
    for _, declaration in ipairs(declarations) do
        if declaration.category == "Trail" and not sampled[declaration.id] then
            cosmetic_native:resetTrail(declaration.id)
        end
    end
end

return controller
