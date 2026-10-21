-- Built-in outfit orchestration. Author scripts never receive cosmetic_native or this Globals table.
local declarations = cosmetic_native:getDeclarations()
local mounted = {}
local failed = {}
local controller = {}

local function target(declaration)
    if declaration.anchor_type == "BASE_BONE" or declaration.anchor_type == "ACCESSORY_BONE" then
        return declaration.bone
    end
    if declaration.anchor_type == "STANDARD" then
        local candidates = cosmetic_native:findBones(declaration.anchor_name)
        if #candidates == 1 then
            return candidates[1]
        end
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
            end
        end
    end
end

function controller.frame(delta)
    for _, declaration in ipairs(declarations) do
        local id = declaration.id
        mounted[id] = false
        if declaration.enabled and not failed[id] then
            local bone = target(declaration)
            -- A hand-only pass must not detach valid body/other-hand instances from ticking.
            mounted[id] = bone and cosmetic_native:hasBone(bone) or false
            local matrix = bone and cosmetic_native:boneMatrix(bone)
            if matrix then
                if cosmetic_native:prepare(id, delta) then
                    cosmetic_native:emit(id, matrix:rightMultiply(declaration.transform))
                    mounted[id] = true
                else
                    failed[id] = true
                end
            end
        end
    end
end

return controller
