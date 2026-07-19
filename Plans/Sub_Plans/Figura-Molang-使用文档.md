# Figura Molang 支持 — 使用文档

> **适用版本**：Minecraft 26.1.2 (Fabric/Forge/NeoForge)  
> **Molang 引擎**：自研原生运行时（参考 YSM Yes Steve Model）  
> **文档更新**：2026-06-30

---

## 目录

1. [概述](#1-概述)
2. [自动 Molang 支持（无需代码）](#2-自动-molang-支持无需代码)
3. [Lua API 总览](#3-lua-api-总览)
4. [query.* 变量参考](#4-query-变量参考)
5. [math.* 函数参考](#5-math-函数参考)
6. [v./c./t. 变量系统](#6-vct-变量系统)
7. [完整示例](#7-完整示例)
8. [调试与故障排除](#8-调试与故障排除)

---

## 1. 概述

Molang 是 Minecraft 基岩版使用的表达式语言，Blockbench 导出的 `.bbmodel` 文件中广泛使用。Figura Molang 支持实现了完整的 Molang 解析和求值引擎，使 `.bbmodel` 中的 Molang 表达式能自动生效。

### 1.1 数据流

```
.bbmodel JSON (含 Molang 表达式)
  → BlockbenchParser2 (isMolang 检测 → 保留原始字符串)
  → NBT (mau/mbw/msd/mld 键)
  → Avatar.loadAnimations (编译 AST → 注入 Animation)
  → Animation.tick (每帧动态求值 offset/blend/delay)
  → Keyframe.evaluateCode (预编译 AST → 原生浮点求值)
```

### 1.2 什么是 Molang？

Molang 是一种轻量级表达式语言，Blockbench 用它来定义：

- **动画偏移** (`anim_time_update`)：控制动画播放速度
- **混合权重** (`blend_weight`)：控制动画之间的过渡
- **关键帧数据** (`data_points`)：控制骨骼位置/旋转/缩放的动态值

示例：
```
Math.sin(query.anim_time * 20) * 30
v.speed > 0.5 ? 1.0 : 0.0
query.anim_time * 2.0
```

---

## 2. 自动 Molang 支持（无需代码）

当加载含 Molang 表达式的 `.bbmodel` 时，系统**自动启用**原生求值路径，无需用户编写任何额外代码。

```lua
-- 直接播放，关键帧中的 Molang 由原生引擎自动求值
local walk = animations["model"]["walk"]
walk:play()

-- 自动检测哪些是 Molang，哪些是 Lua，哪些是纯数字
-- "Math.sin(query.anim_time * 20)" → 原生 Molang 引擎
-- "return someLuaFunction()" → 原 Lua 路径（向后兼容）
-- "0.5" → 纯数字快速路径
```

### .bbmodel 中自动检测的场景

| 字段 | NBT 键 | 自动处理 |
|------|--------|---------|
| `anim_time_update` | `mau` | `Animation.tick()` 每帧动态求值 → 更新 `offset` |
| `blend_weight` | `mbw` | `Animation.tick()` 每帧动态求值 → 更新 `blend` |
| `start_delay` | `msd` | `Animation.tick()` 每帧动态求值 → 更新 `startDelay` |
| `loop_delay` | `mld` | `Animation.tick()` 每帧动态求值 → 更新 `loopDelay` |
| `data_points[*].x/y/z` | 关键帧 `aCode`/`bCode` | `Keyframe.getTargetA/B()` 三路径求值 |

---

## 3. Lua API 总览

### 3.1 动画级 API — `anim:*`

所有方法通过单个动画实例调用：

```lua
local anim = animations["model"]["walk"]
```

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `anim:evalMolang(expr)` | `float` | 在当前动画上下文中求值 Molang 表达式 |
| `anim:getMolangVar(name)` | `float?` | 读取 `v.*` 变量值，无变量返回 `nil` |
| `anim:setMolangVar(name, val)` | `Animation` | 写入 `v.*` 变量，返回 self 以链式调用 |
| `anim:setMolangVars(table)` | `Animation` | 批量设置 `v.*` 变量 |

```lua
-- 示例
local val = anim:evalMolang("Math.sin(query.anim_time * 20)")
anim:setMolangVar("speed", 2.0)
anim:setMolangVar("phase", 0.5)
anim:setMolangVars({ speed = 1.0, phase = 0.0 })
```

### 3.2 全局 API — `animations:*`

通过全局 `animations` API 表调用：

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `animations:evalMolang(expr)` | `float` | 全局求值 Molang 表达式 |
| `animations:getMolangVar(name)` | `float?` | 读取 `v.*` 变量 |
| `animations:setMolangVar(name, val)` | `AnimationAPI` | 写入 `v.*` 变量 |
| `animations:setMolangVars(table)` | `AnimationAPI` | 批量设置 `v.*` 变量 |
| `animations:getMolangVars()` | `table` | 获取所有 `v.*` 变量快照 |

```lua
-- 示例
animations:setMolangVar("global_speed", 1.5)
local vars = animations:getMolangVars()
for name, val in pairs(vars) do
    print(name .. " = " .. val)
end
```

---

## 4. query.* 变量参考

所有 `query.*` 变量每帧自动更新，无需手动设置。

### 4.1 时间

| 变量 | 类型 | 说明 | 范围 |
|------|------|------|------|
| `query.anim_time` | float | 当前动画时间（秒） | ≥ 0 |
| `query.life_time` | float | 动画生命周期（秒） | ≥ 0 |
| `query.delta_time` | float | 帧间时间差（秒） | ~0.016 |
| `query.time_of_day` | float | 标准化游戏内时间 | 0.0 ~ 1.0 |
| `query.time_stamp` | float | 原始游戏时间刻 | ≥ 0 |
| `query.moon_phase` | float | 月相索引 | 0 ~ 7 |
| `query.frame_count` | float | 帧计数器（递增） | ≥ 0 |

### 4.2 位置

| 变量 | 类型 | 说明 |
|------|------|------|
| `query.position(axis)` | float | 位置向量，`axis`=0 返回 x，1 返回 y，2 返回 z |
| `query.position_x` | float | X 坐标 |
| `query.position_y` | float | Y 坐标 |
| `query.position_z` | float | Z 坐标 |
| `query.position_delta(axis)` | float | 位置变化量，`axis`=0/1/2 |
| `query.position_delta_x` | float | 上一帧以来 X 变化 |
| `query.position_delta_y` | float | 上一帧以来 Y 变化 |
| `query.position_delta_z` | float | 上一帧以来 Z 变化 |
| `query.distance_from_camera` | float | 到摄像机距离 |
| `query.rotation_to_camera(axis)` | float | 摄像机旋转，`axis`=0 返回 xRot，1 返回 yRot |

### 4.3 移动

| 变量 | 类型 | 说明 |
|------|------|------|
| `query.ground_speed` | float | 水平地面速度 |
| `query.vertical_speed` | float | 垂直速度 |
| `query.yaw_speed` | float | 偏航角速度 |
| `query.walk_distance` | float | 累计行走距离 |
| `query.modified_distance_moved` | float | 修正移动距离 |

### 4.4 旋转

| 变量 | 类型 | 说明 |
|------|------|------|
| `query.body_x_rotation` | float | 身体水平旋转 |
| `query.body_y_rotation` | float | 身体垂直旋转（偏航） |
| `query.head_x_rotation` | float | 头部俯仰角 |
| `query.head_y_rotation` | float | 头部偏航角 |
| `query.cardinal_facing_2d` | float | 基本方向（0=南，1=西，2=北，3=东） |

### 4.5 布尔状态（返回 0 或 1）

| 变量 | 1 的条件 |
|------|---------|
| `query.is_on_ground` | 在地面上 |
| `query.is_jumping` | 跳跃中 |
| `query.is_sneaking` | 潜行中 |
| `query.is_sprinting` | 疾跑中 |
| `query.is_swimming` | 游泳中 |
| `query.is_in_water` | 在水中 |
| `query.is_in_water_or_rain` | 在水中或雨里 |
| `query.is_on_fire` | 着火 |
| `query.is_riding` | 在乘骑 |
| `query.has_rider` | 被骑乘 |
| `query.is_sleeping` | 睡觉中 |
| `query.is_spectator` | 旁观者模式 |
| `query.is_first_person` | 第一人称视角 |
| `query.is_using_item` | 使用物品中 |
| `query.is_swinging` | 挥动手臂 |
| `query.is_eating` | 进食中 |
| `query.is_playing_dead` | 装死中 |
| `query.has_cape` | 有披风 |

### 4.6 生命

| 变量 | 类型 | 说明 |
|------|------|------|
| `query.health` | float | 当前生命值 |
| `query.max_health` | float | 最大生命值 |
| `query.hurt_time` | float | 受击剩余时间 |

### 4.7 动画/物品

| 变量 | 类型 | 说明 |
|------|------|------|
| `query.all_animations_finished` | 0/1 | 所有动画播放完毕 |
| `query.any_animation_finished` | 0/1 | 任一动画播放完毕 |
| `query.swing_time` | float | 挥动时间 |
| `query.attack_time` | float | 攻击时间 |
| `query.item_in_use_duration` | float | 物品使用时长 |
| `query.item_max_use_duration` | float | 物品最长使用时长 |
| `query.item_remaining_use_duration` | float | 物品剩余使用时长 |
| `query.equipment_count` | float | 装备栏物品数 |
| `query.max_durability(slot)` | float | 装备栏 slot 的最大耐久 |
| `query.remaining_durability(slot)` | float | 装备栏 slot 的剩余耐久 |
| `query.player_level` | float | 经验等级 |
| `query.cape_flap_amount` | float | 披风飘动幅度 |

### 4.8 生物群系/方块/物品标签查询

这些函数使用 Minecraft 的 Tag 系统：

| 函数 | 说明 |
|------|------|
| `query.biome_has_all_tags("tag1", "tag2", ...)` | 当前生物群系拥有全部标签 |
| `query.biome_has_any_tag("tag1", "tag2", ...)` | 当前生物群系拥有任一标签 |
| `query.relative_block_has_all_tags(x, y, z, "tag1", ...)` | 相对位置方块拥有全部标签 |
| `query.relative_block_has_any_tag(x, y, z, "tag1", ...)` | 相对位置方块拥有任一标签 |
| `query.is_item_name_any("minecraft:stick", ...)` | 主手物品名称匹配 |
| `query.equipped_item_all_tags(slot, "tag1", ...)` | 装备栏 slot 物品拥有全部标签 |
| `query.equipped_item_any_tag(slot, "tag1", ...)` | 装备栏 slot 物品拥有任一标签 |

Tag 格式支持完整路径（`"minecraft:is_ocean"`）和简写（`"is_ocean"`）。

Slot 编号：
- 0 = 主手
- 1 = 副手
- 2 = 靴子
- 3 = 护腿
- 4 = 胸甲
- 5 = 头盔

```lua
-- 示例
local inOcean = anim:evalMolang("query.biome_has_any_tag('is_ocean', 'is_river')")
local isStone = anim:evalMolang("query.relative_block_has_any_tag(0, -1, 0, 'minecraft:stone')")
```

### 4.9 几何类型

| 变量 | 说明 |
|------|------|
| `query.geometry_is_model` | 总是 1（Figura 模型） |
| `query.geometry_is_entity` | 总是 1 |
| `query.geometry_is_block` | 总是 0 |
| `query.geometry_is_flat` | 总是 0 |

---

## 5. math.* 函数参考

### 5.1 三角函数

| 函数 | 说明 |
|------|------|
| `math.sin(x)` | 正弦（**度数**，非弧度） |
| `math.cos(x)` | 余弦（度数） |
| `math.asin(x)` | 反正弦（返回值：度数） |
| `math.acos(x)` | 反余弦（返回值：度数） |
| `math.atan(x)` | 反正切（返回值：度数） |
| `math.atan2(y, x)` | 反正切（返回值：度数） |

> **注意**：Molang 的三角函数使用**度数**而非弧度，与 Blockbench 一致。
> 例如 `math.sin(90)` = 1.0，`math.cos(0)` = 1.0。

### 5.2 基础运算

| 函数 | 说明 |
|------|------|
| `math.abs(x)` | 绝对值 |
| `math.ceil(x)` | 向上取整 |
| `math.clamp(x, min, max)` | 限制范围 |
| `math.exp(x)` | e^x |
| `math.floor(x)` | 向下取整 |
| `math.ln(x)` | 自然对数 |
| `math.max(a, b, ...)` | 最大值 |
| `math.min(a, b, ...)` | 最小值 |
| `math.mod(a, b)` | 取模 |
| `math.pow(a, b)` | a^b |
| `math.round(x)` | 四舍五入 |
| `math.sqrt(x)` | 平方根 |
| `math.trunc(x)` | 截断小数 |

### 5.3 插值与混合

| 函数 | 说明 |
|------|------|
| `math.lerp(a, b, t)` | 线性插值：`a + t * (b - a)` |
| `math.lerprotate(a, b, t)` | 角度插值（处理 360° 环绕） |
| `math.hermite_blend(t)` / `math.hermite(t)` | Hermite 平滑：`t²(3-2t)` |
| `math.min_angle(a, b)` | 最小角度差 |

### 5.4 随机

| 函数 | 说明 |
|------|------|
| `math.random()` | 随机数 [0, 1) |
| `math.random(lo, hi)` | 随机数 [lo, hi) |
| `math.random_integer(lo, hi)` | 随机整数 [lo, hi] |
| `math.die_roll(sides)` | 骰子投掷 [1, sides] |
| `math.die_roll_integer(sides)` | 骰子投掷整数 |

### 5.5 常量

| 名称 | 值 |
|------|-----|
| `math.pi` | 3.1415927 |
| `math.e` | 2.7182817 |

---

## 6. v./c./t. 变量系统

### 6.1 变量作用域

| 作用域 | Molang 前缀 | 生命周期 | 用途 |
|--------|-------------|---------|------|
| **变量** | `v.` / `variable.` | 跨帧/跨动画持久 | 共享状态（速度、阶段、计数） |
| **上下文** | `c.` / `context.` | 跨帧持久 | 控制器变量（循环计数等） |
| **临时** | `t.` / `temp.` | 仅当前帧 | 帧内计算结果 |

### 6.2 通过 Lua 设置变量

```lua
-- 设置 v.* 变量（.bbmodel 中可以用 v.speed）
anim:setMolangVar("speed", 2.0)

-- 批量设置
anim:setMolangVars({
    speed = 1.0,
    phase = 0.5,
    wobble = 1,
})

-- 在 .bbmodel 中即可使用：
-- blend_weight = "v.speed > 0.5 ? 1.0 : 0.0"
```

### 6.3 在 .bbmodel 中使用

一旦通过 Lua 设置了 `v.*` 变量，就可在 `.bbmodel` 的 Molang 表达式中直接引用：

**.bbmodel 动画级：**
```json
"anim_time_update": "query.anim_time * v.speed",
"blend_weight": "v.speed > 0.5 ? 1.0 : 0.0"
```

**.bbmodel 关键帧级：**
```json
"data_points": [{
    "x": "Math.sin(query.anim_time * v.speed * 20) * 30",
    "y": 0,
    "z": 0
}]
```

---

## 7. 完整示例

### 7.1 基本用法 — 速度控制

```lua
-- 通过 v.speed 控制动画速度
local walk = animations["model"]["walk"]
walk:play()
walk:setMolangVar("speed", 1.0)

events.TICK:register(function()
    local p = player:getPlayer()
    if not p then return end
    walk:setMolangVar("speed", p:getSpeed())
end)
```

对应的 `.bbmodel` 中：
```json
"anim_time_update": "query.anim_time * v.speed"
```

### 7.2 动画状态机

```lua
-- 共享变量
local shared = {
    speed = 0,
    is_jumping = 0,
}

-- 启动所有动画（使用 Molang blend_weight 自动过渡）
local idle = animations["model"]["idle"]
local walk = animations["model"]["walk"]
local jump = animations["model"]["jump"]

idle:play()
walk:play()
jump:play()

-- 通过全局 API 设置（所有动画共享 v.*）
animations:setMolangVars(shared)

events.TICK:register(function()
    local p = player:getPlayer()
    if not p then return end

    local speed = p:getSpeed()
    shared.speed = speed
    shared.is_jumping = p:isJumping() and 1 or 0
end)
```

对应 `.bbmodel`：
```json
"idle": { "blend_weight": "v.speed > 0.1 ? 0.0 : 1.0" },
"walk": { "blend_weight": "v.speed > 0.1 && v.speed < 0.5 ? 1.0 : 0.0" },
"jump": { "blend_weight": "v.is_jumping ? 1.0 : 0.0" }
```

### 7.3 波浪浮动效果

```lua
local idle = animations["model"]["idle"]
idle:play()
idle:setMolangVar("phase", 0)
idle:setMolangVar("amplitude", 2.0)

events.RENDER:register(function(delta)
    idle:setMolangVar("phase", idle:getMolangVar("phase") + delta * 2)
end)
```

对应 `.bbmodel` 关键帧：
```json
"data_points": [{
    "x": 0,
    "y": "Math.sin(query.anim_time * v.phase) * v.amplitude",
    "z": 0
}]
```

### 7.4 直接求值

```lua
-- 运行时动态计算 Molang 值
events.RENDER:register(function(delta)
    local anim = animations["model"]["idle"]
    local height = anim:evalMolang("Math.sin(query.anim_time * 2) * 0.5")
    vanilla_model.body:setPos(0, height, 0)
end)
```

### 7.5 调试输出

```lua
-- Molang 表达式中的 query.debug_output 可输出调试信息
-- 在 .bbmodel 中使用（不会影响返回值）：
--   query.debug_output("anim_time = " + query.anim_time) + 0.0
```

---

## 8. 调试与故障排除

### 8.1 表达式求值测试

```lua
-- 直接测试任何 Molang 表达式
local result = anim:evalMolang("1 + 2 * 3")
print(result)  -- 输出 7.0

-- 测试是否有异常
local ok, result = pcall(anim.evalMolang, anim, "Math.sin(query.anim_time)")
if ok then
    print("Result: " .. result)
else
    print("Error: " .. result)
end
```

### 8.2 检查变量

```lua
-- 查看所有 v.* 变量
local vars = animations:getMolangVars()
for name, val in pairs(vars) do
    print(name .. " = " .. val)
end

-- 检查单个变量
local speed = anim:getMolangVar("speed")
print(speed)
```

### 8.3 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 关键帧返回 0 | Molang 表达式语法错误 | 检查是否包含 `Math.`(大写 M) 而非 `math.` |
| `query.*` 返回 0 | 未在动画上下文中求值 | 使用 `anim:evalMolang()` 而非 `animations:evalMolang()` |
| `v.*` 变量无效 | 变量未设置或被覆盖 | 用 `getMolangVar` 检查当前值 |
| `.bbmodel` Molang 不生效 | NBT 中缺少 `mau`/`mbw` 等字段 | 确保 `.bbmodel` 包含 Molang 表达式且重新加载 |
| `blend_weight` 无效果 | 多动画未同时播放 | 确保目标动画都调用了 `:play()` |

### 8.4 Molang 语法速查

| 语法 | 示例 | 说明 |
|------|------|------|
| 算术 | `1 + 2 * 3` | 标准四则运算 |
| 比较 | `a > b`, `a == b`, `a != b` | 返回 0 或 1 |
| 逻辑 | `a && b`, `a \|\| b`, `!a` | 短路求值 |
| 三元 | `a ? b : c` | 仅求值选中的分支 |
| 条件 | `a ? b` | 不带 else 的条件 |
| 空合并 | `a ?? b` | a 为 null 时取 b |
| 箭头 | `entity -> math.sin(this * 20)` | 切换求值上下文 |
| 作用域 | `{ v.x = 1; v.x + 2 }` | 返回最后一条表达式的值 |
| 赋值 | `v.speed = 1.5` | 写入 v.* 变量 |
| 函数 | `math.sin(90)` | 函数调用 |
| 字段 | `query.anim_time` | 命名空间访问 |
| 注释 | `// 单行` `/* 多行 */` | 支持，自动剥离 |
| `return` | `{ return v.x }` | 从作用域提前返回值 |

### 8.5 Molang 与 Lua 的区别

| 对比 | Molang | Lua |
|------|--------|-----|
| 三角函数 | 度数 `math.sin(90)` | 弧度 `math.sin(math.pi/2)` |
| 三元 | `a ? b : c` | `a and b or c` |
| 关键字 | `true` `false` | `true` `false` |
| 注释 | `//` `/* */` | `--` `--[[ ]]` |
| 空合并 | `a ?? b` | `a or b` |
| 数组索引 | `arr[0]` | `arr[1]` |
| 分号 | 语句间可选分号 | 无分号 |
| 类型 | 主要是 float | 多种类型 |

---

> *Figura Molang 支持 — 基于 YSM (Yes Steve Model) 的自研 Molang 引擎实现*
