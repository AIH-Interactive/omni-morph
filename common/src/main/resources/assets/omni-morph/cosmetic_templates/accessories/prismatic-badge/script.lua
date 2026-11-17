-- The mesh's authored normals and UV2 become native shader attributes.
accessory = {options={speed=0.3}, time=0}
local badge = models.model.model.Cosmetic.Badge
badge:setShaderMaterial({
    vertex_shader=[[
#version 330
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection { mat4 ProjMat; };
in vec3 Position;
in vec3 Normal;
in vec2 SecondaryUV;
in float MaterialTime;
out vec3 prismColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    float light = 0.35 + 0.65 * abs(normalize(Normal).z);
    vec3 hue = 0.5 + 0.5 * cos(6.283185 * (vec3(SecondaryUV.x + MaterialTime) + vec3(0.0, 0.33, 0.67)));
    prismColor = light * hue * (0.65 + 0.35 * SecondaryUV.y);
}
]],
    fragment_shader=[[
#version 330
in vec3 prismColor;
out vec4 outputColor;
void main() { outputColor = vec4(prismColor, 1.0); }
]],
    textures={}, blend="OPAQUE", cull=false, depth_test=true, depth_write=true
})

function accessory.update(time)
    badge:setMaterialTime(time * accessory.options.speed)
end

events.TICK:register(function()
    accessory.time = accessory.time + 1/20
end)
events.RENDER:register(function(delta)
    accessory.update(accessory.time + delta/20)
end)
accessory.update(0)
