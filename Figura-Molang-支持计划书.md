# Figura Molang 支持实现计划书

> **目标**：为 Figura 添加完整的 Molang 表达式支持，使其能识别 `.bbmodel` 中的 Molang 表达式并正确求值，同时提供基于 Molang 驱动的动画播放 API。
>
> **参考项目**：YSM (Yes Steve Model) — 拥有自研的完整 Molang 词法→语法→求值引擎。
>
> **Minecraft 版本**：26.1.2 (Fabric)
> **项目结构**：common/fabric/forge/neoforge 多平台

---

## 目录

1. [背景与现状分析](#1-背景与现状分析)
2. [参考实现：YSM Molang 引擎架构](#2-参考实现ysm-molang-引擎架构)
3. [实现策略](#3-实现策略)
4. [原生 Molang 运行时](#4-原生-molang-运行时)
5. [Lua API 设计](#5-lua-api-设计)
6. [需修改的文件清单](#6-需修改的文件清单)
7. [风险与注意事项](#7-风险与注意事项)

---

## 1. 背景与现状分析

### 1.1 .bbmodel 中的 Molang 字段

Blockbench 导出的 `.bbmodel` 在以下位置包含 Molang 表达式：

| 字段 | 位置 | Figura 当前处理方式 | 问题 |
|------|------|--------------------|------|
| `anim_time_update` | `Animation` 级别 | `parseFloatOr()` → 降级为静态浮点 `offset` | Molang 表达式完全丢失 |
| `blend_weight` | `Animation` 级别 | `parseFloatOr()` → 降级为静态浮点 `blend` | Molang 表达式完全丢失 |
| `start_delay` | `Animation` 级别 | `parseFloatOr()` → 降级为静态浮点 | Molang 表达式完全丢失 |
| `loop_delay` | `Animation` 级别 | `parseFloatOr()` → 降级为静态浮点 | Molang 表达式完全丢失 |
| `data_points[*].x/y/z` | `Keyframe` 级别 | 字符串保留，通过 luaj 求值 | Molang 语法 ≠ Lua 语法，静默返回 0 |

### 1.2 关键发现

1. **关键帧数据已保留字符串**：`BlockbenchCommonTypes.Keyframe.Keyframe3.Data.toNBT()` 中的 `kfData()` 方法正确地将非数字字符串保留为 `StringTag`，经过 NBT 往返后，`FiguraModelPartReader.parseKeyframeData()` 也正确地将字符串传递给 `Keyframe` 的 `aCode`/`bCode`。

2. **动画级字段完全丢失**：`BlockbenchParser2.AnimationRepresentation.load()` 使用 `parseFloatOr()` 处理 `anim_time_update`/`blend_weight` 等字段，所有 Molang 表达式在这一步就被丢弃了。

3. **现有求值路径只认 Lua**：`Keyframe.parseStringData()` 尝试用 luaj 引擎求值字符串，但 `Math.sin(query.anim_time)` 不是有效 Lua，会静默返回 0。

4. **YSM 有完整实现**：YSM 项目在 `com.elfmcys.yesstevemodel.molang` 包下有自研的 Lexer→Parser (Pratt)→Evaluator (Visitor) 完整流水线，可以直接参考移植。

### 1.3 数据流对比

**Figura 当前数据流**：
```
.bbmodel JSON
  → BlockbenchParser2 (parseFloatOr → Molang 丢失)
  → NBT
  → Avatar.loadAnimations() (纯数值)
  → AnimationPlayer.tick() (数值插值)
  → Keyframe.parseStringData() (尝试 Lua 求值，Molang 失败)
```

**YSM 数据流（目标）**：
```
.bbmodel JSON
  → JsonKeyFrameUtils (Molang 字符串→ IValue 编译)
  → BoneKeyFrame (运行时 evaluator 求值)
  → AnimationControllerInstance.process() (IValue.evalAsFloat)
```

---

## 2. 参考实现：YSM Molang 引擎架构

### 2.1 架构总览

YSM 的 Molang 实现分两层：

```
┌─────────────────────────────────────────────────┐
│   GeckoLib 集成层 (com.elfmcys.yesstevemodel     │
│     .geckolib3.core.molang)                      │
│   - MolangParser (GeckoLib 包装器)               │
│   - IValue / FloatValue / MolangValue            │
│   - PrimaryBinding (query.*, math.*, v.*, c.*)  │
│   - QueryBinding / MathBinding                   │
│   - VariableStorage 四层体系                      │
├─────────────────────────────────────────────────┤
│   核心引擎层 (com.elfmcys.yesstevemodel.molang)   │
│   - MolangLexer → Token 流                        │
│   - MolangParser (Pratt) → AST                    │
│   - ExpressionEvaluator (Visitor) → 求值          │
│   - ObjectBinding 系统                            │
└─────────────────────────────────────────────────┘
```

### 2.2 核心引擎组件

| 组件 | 文件 | 说明 |
|------|------|------|
| **词法分析器** | `MolangLexerImpl.java` | 流式 token 化，38 种 TokenKind |
| **解析器** | `MolangParserImpl.java` | 递归下降 + Pratt 算符优先级解析 |
| **AST 节点** | `Expression` 接口 + 14 个子类 | Visitor 模式 |
| **求值器** | `ExpressionEvaluatorImpl.java` | Visitor 实现，含原生浮点优化路径 |
| **绑定系统** | `ObjectBinding.java` | 命名空间解析 (math.*, query.* 等) |

### 2.3 关键设计要点

- **Pratt 解析**：算符优先级从 1（赋值 `=`）到 2800（一元 `!` `-`）
- **双通道求值**：`evalFloat()` / `evalBool()` 零装箱路径，遇到非算术节点回退到 `visit()`
- **除零安全**：`x/0 = 0`（Molang 规范）
- **变量四层存储**：`v.*` 模型级、`c.*` 控制器级、`t.*` 临时级、外部变量
- **字符串池化**：`StringPool` 将字符串映射到 int ID，减少 GC

---

## 3. 实现策略

直接实现**原生 Molang 运行时**，一次性到位。参考 YSM 的自研引擎（Lexer → Pratt Parser → AST → Visitor Evaluator），在 Figura 中建立独立的 Molang 求值引擎，不依赖 luaj。

```
┌──────────────────────────────────────┐
│     原生 Molang 运行时 (一次性实现)     │
│                                      │
│  - 独立求值，不依赖 Lua               │
│  - 完整 Molang 语法 + 脚本支持        │
│  - 常量折叠 + 解析器池化              │
│  - 原生浮点路径，零装箱               │
│  - ~3000 行，1-2 周                  │
└──────────────────────────────────────┘
```

### 与原方案对比

| 维度 | 转译器方案 (已放弃) | 原生引擎方案 (选定) |
|------|-------------------|-------------------|
| 实现复杂度 | 低 (~500 行) | 中 (~3000 行) |
| 性能 | 依赖 luaj，有装箱开销 | 原生浮点路径，零装箱 |
| 功能完整性 | 基础 Molang 语法 | 完整 Molang 语法 + 脚本 |
| 语法支持 | ❌ 无 `->` `{ }` `break` | ✅ 完整支持 |
| 长期维护 | 转译边界 Bug 多 | 引擎稳定后几乎免维护 |
| 依赖 | luaj (已有) | 无新增依赖 |
| 交付时间 | 2-3 天 | 1-2 周 |

---

## 4. 原生 Molang 运行时

### 4.1 架构

参照 YSM 的核心引擎实现，在 Figura 中建立自研的原生 Molang 运行时，不依赖 luaj。

```
┌──────────────────────────────────────────┐
│          Molang 表达式字符串               │
│               ↓                          │
│     MolangLexer (流式词法分析)              │
│               ↓                          │
│     MolangParser (Pratt 递归下降解析)      │
│               ↓                          │
│     List<Expression> (AST)               │
│               ↓                          │
│     ExpressionEvaluator (Visitor 求值)    │
│               ↓                          │
│     float / boolean / String              │
└──────────────────────────────────────────┘
```

### 4.2 包结构

```
common/src/main/java/org/figuramc/figura/molang/
├── MolangEngine.java              # 入口接口
├── MolangEngineImpl.java          # 实现
├── lexer/
│   ├── MolangLexer.java           # 词法分析器接口
│   ├── MolangLexerImpl.java       # 实现
│   ├── Token.java                 # Token 封装
│   ├── TokenKind.java             # Token 类型枚举
│   ├── Cursor.java                # 位置跟踪
│   └── Characters.java            # 字符工具类
├── parser/
│   ├── MolangParser.java          # 解析器接口
│   ├── MolangParserImpl.java      # Pratt 解析实现
│   ├── ParseException.java        # 解析异常
│   └── ast/
│       ├── Expression.java        # AST 根接口
│       ├── ExpressionVisitor.java # Visitor 接口
│       ├── FloatExpression.java   # 浮点字面量
│       ├── StringExpression.java  # 字符串字面量
│       ├── IdentifierExpression.java  # 标识符
│       ├── VariableExpression.java    # 变量引用
│       ├── AssignableVariableExpression.java # 可赋值变量
│       ├── BinaryExpression.java  # 二元运算
│       ├── UnaryExpression.java   # 一元运算
│       ├── CallExpression.java    # 函数调用
│       ├── TernaryConditionalExpression.java # 三元 a?b:c
│       ├── StructAccessExpression.java  # a.b 字段访问
│       └── ExecutionScopeExpression.java  # { } 作用域
├── runtime/
│   ├── ExpressionEvaluator.java   # 求值器接口
│   ├── ExpressionEvaluatorImpl.java # Visitor 求值器
│   ├── Function.java              # 函数接口
│   ├── Variable.java              # 只读变量
│   ├── AssignableVariable.java    # 可写变量
│   ├── Struct.java                # 结构体接口
│   ├── HashMapStruct.java         # 通用结构体实现
│   ├── ExecutionContext.java      # 执行上下文
│   └── binding/
│       ├── ObjectBinding.java     # 绑定接口
│       ├── MolangBindings.java    # 顶层绑定 (query/math/v/c/t)
│       ├── MathFunctions.java     # math.* 函数实现
│       ├── QueryVariables.java    # query.* 变量
│       └── ValueConversions.java  # 类型转换工具
└── storage/
    ├── VariableStorage.java       # v.* / variable.* 存储
    ├── TempVariableStorage.java   # t.* / temp.* 栈式存储
    └── StringPool.java            # 字符串池化
```

### 4.3 与 YSM 的关键差异

| 方面 | YSM | Figura |
|------|-----|------------------|
| 实体类型 | `AnimationContext<?>` 泛型 | `Avatar` + 上下文 |
| 变量作用域 | 四层 (v/c/t/foreign) | 三层 (v/c/t)，简化 |
| query.* 实现 | 大量 Minecraft 绑定 | 适配 Figura 的 Avatar 模型系统 |
| 存储后端 | `PooledStringHashMap` | `HashMap` (初期简化) |
| 动画集成 | `AnimationControllerInstance` | `AnimationPlayer` (复用现有播放器) |
| Molang 编译时机 | 解析时立即编译为 IValue | 延迟编译 + 缓存 |

### 4.4 Molang 环境上下文

```java
public class MolangContext {
    // 实时更新的变量
    public float anim_time;       // 当前动画时间 (秒)
    public float life_time;       // 动画生命周期 (秒)
    public float delta_time;      // 帧时间差 (秒)
    public float time_of_day;     // 游戏内时间
    public float moon_phase;      // 月相
    public float frame_count;     // 帧计数
    
    // 模型状态
    public Avatar avatar;
    public Animation animation;
    
    // 变量存储
    public final VariableStorage variables = new VariableStorage();     // v.*
    public final VariableStorage controller = new VariableStorage();   // c.*
    public final TempVariableStorage temp = new TempVariableStorage(); // t.*
}
```

### 4.5 集成到 AnimationPlayer

修改 `AnimationPlayer.tick()`，在对每个关键帧求值时，使用 Molang 原生求值路径：

```java
// AnimationPlayer.tick() 中
if (MolangRuntime.isMolangExpression(currentFrame)) {
    float value = MolangRuntime.evaluate(currentFrame, molangContext);
    // 使用原生求值结果
} else {
    // 原有插值逻辑
}
```

### 4.6 动画级字段的原生求值

在 `Animation.tick()` 中，对 `anim_time_update`、`blend_weight` 等使用原生求值：

```java
// Animation.tick() 中
if (hasMolangAnimTimeUpdate) {
    this.offset = molangEngine.evaluateAsFloat(animTimeUpdateAst, molangContext);
}
if (hasMolangBlendWeight) {
    this.blend = molangEngine.evaluateAsFloat(blendWeightAst, molangContext);
}
```

关键点：Molang 表达式在解析时编译为 AST 并缓存，运行时仅重复求值（不重复解析）。

### 4.7 性能优化

| 优化 | 说明 |
|------|------|
| **AST 缓存** | 每个 Molang 表达式解析一次，缓存 AST |
| **常量折叠** | `1+2*3` → 编译时求值 → `FloatExpression(7)` |
| **原生浮点路径** | `evalFloat()` 跳过装箱，直接递归求值 |
| **解析器池化** | 复用 `MolangLexer` 和 `MolangParser` 实例 |
| **字符串池化** | 用 `StringPool` 将标识符映射为 int ID |

---

## 5. Lua API 设计

### 5.1 API 总览

| Lua 方法 | 说明 | 适用场景 |
|---------|------|---------|
| `anim:play()` | **自动检测 Molang** — 原生引擎自动求值 | 加载含 Molang 的 `.bbmodel` |
| `anim:playMolang(ctx?)` | 显式模式，指定原生 Molang 上下文 | 需要自定义 v/c/t 变量 |
| `anim:evalMolang(expr)` | 直接执行单条 Molang 表达式，原生引擎求值 | 调试/动态计算 |
| `anim:getMolangVar(name)` | 读取 v.*、c.*、t.* 变量 | 变量查询 |
| `anim:setMolangVar(name, val)` | 写入变量 | 运行时驱动动画 |
| `anim:setMolangVars(table)` | 批量设置变量 | 批量更新 |
| `anim:getMolangVars()` | 获取所有变量快照 | 调试 |
| `anim:onMolangVarChange(name, cb)` | 注册变量变化回调 | 监听/联动 |

### 5.2 自动模式 — `anim:play()`

当 `.bbmodel` 中的关键帧包含 Molang 表达式时，`play()` 自动检测并使用原生引擎求值：

```lua
-- === 场景 1：.bbmodel 自带 Molang，自动处理 === --
local walk = animations["model"]["walk"]

-- 直接播放，Blockbench 关键帧中的 Molang 由原生引擎自动求值
-- 如 keyframe data_points = ["Math.sin(query.anim_time * 20)", 0, 0]
-- → 原生引擎每帧求值 math.sin(query.anim_time * 20)，无需用户干预
walk:play()

-- === 场景 2：自动 + 变量覆盖 === --
walk:play()
walk:setMolangVar("speed", 2.0)
-- .bbmodel 中的 "Math.sin(query.anim_time * v.speed)" 使用 speed=2.0
```

### 5.3 显式模式 — `anim:playMolang(ctx?)`

指定原生 Molang 上下文并启动播放：

```lua
-- === 场景 1：提供自定义 Molang 变量表 === --
local ctx = {
    -- v.* / variable.* 变量（模型级别，跨动画共享）
    v = {
        speed = 1.0,
        phase = 0.5,
        wobble = 1,
    },

    -- c.* / context.* 变量（仅在此动画实例内有效）
    c = {
        loop_count = 0,
    },

    -- t.* / temp.* 临时变量（仅在当前求值帧内有效）
    t = {},
}

-- 使用自定义上下文播放
walk:playMolang(ctx)

-- === 场景 2：无参数调用（使用运行时默认上下文） === --
walk:playMolang()

-- === 场景 3：多动画联动（共享 v.* 变量） === --
local sharedVars = { speed = 0, phase = 0 }

local walk = animations["model"]["walk"]
local run = animations["model"]["run"]
local idle = animations["model"]["idle"]

-- 所有动画共享同一组 v.* 变量
walk:playMolang({ v = sharedVars })
run:playMolang({ v = sharedVars })
idle:playMolang({ v = sharedVars })

-- 更新一次，三个动画同时响应
events.TICK:register(function()
    sharedVars.speed = avatar:getSpeed()
    sharedVars.phase = sharedVars.phase + 0.05
end)
```

### 5.4 直接求值 — `anim:evalMolang(expr)`

原生引擎直接求值单条 Molang 表达式，结果返回 Lua number：

```lua
-- === 场景 1：在 TICK 中实时计算 Molang 值 === --
events.TICK:register(function()
    local sinVal = animations["model"]["walk"]:evalMolang("Math.sin(query.anim_time * 20)")
    -- 原生引擎直接求值，零装箱

    -- 三元表达式
    local blend = animations["model"]["walk"]:evalMolang("v.speed > 0.5 ? 1.0 : 0.0")
end)

-- === 场景 2：Molang 表达式驱动的自定义动画 === --
events.RENDER:register(function(delta)
    local anim = animations["model"]["idle"]
    local height = anim:evalMolang("Math.sin(query.anim_time * 2 + v.phase) * 0.5")

    -- 将 Molang 计算结果用于其他 Lua 逻辑
    vanilla_model.body:setPos(0, height, 0)
end)

-- === 场景 3：完整语法测试 === --
-- 箭头运算符
local target = anim:evalMolang("query.anim_time -> math.sin(this * 20)")

-- 作用域块
local scoped = anim:evalMolang("{ v.x = 1; v.y = 2; v.x + v.y }")

-- 空合并
local fallback = anim:evalMolang("v.non_existent ?? 42")

-- 布尔表达式
local hasFinished = anim:evalMolang("query.all_animations_finished")
```

### 5.5 完整工作流示例

```lua
-- === 完整的 Molang 动画控制示例 === --

-- 1. 跨动画共享的 v.* 变量
local animVars = { speed = 0, isGrounded = 1, health = 20 }

-- 2. 初始化所有动画
local stateMachine = {
    idle = animations["model"]["idle"],
    walk = animations["model"]["walk"],
    run = animations["model"]["run"],
    jump = animations["model"]["jump"],
}

for name, anim in pairs(stateMachine) do
    anim:playMolang({ v = animVars })
end

-- 3. 状态切换：Molang blend_weight 自动处理过渡
--    .bbmodel 中 walk 的 blend_weight = "v.speed > 0.1 ? 1.0 : 0.0"
--    .bbmodel 中 run 的 blend_weight  = "v.speed > 0.5 ? 1.0 : 0.0"
events.TICK:register(function()
    local speed = avatar:getSpeed()
    animVars.speed = speed
    animVars.isGrounded = avatar:isOnGround()
    animVars.health = avatar:getHealth()
end)

-- 4. Molang 驱动的代码帧（Blockbench timeline 通道）
--    .bbmodel 中 timeline 关键帧脚本为 "v.health <= 0 ? v.death_state = 1 : 0"
--    Phase 2 原生引擎直接执行 Molang 赋值语句

-- 5. 动态创建 Molang 表达式（Phase 2 编译缓存）
local customExpr = "Math.sin(query.anim_time * v.speed * 2) * 10"
local height = jump:evalMolangNative(customExpr)
```

### 5.6 Molang 变量管理 API

所有变量操作方法在 Phase 1 和 Phase 2 中一致：

```lua
-- === 变量读写 === --

-- 读取 v.* 变量
local speed = anim:getMolangVar("speed")      -- 对应 Molang v.speed
-- 返回 nil 如果变量不存在

-- 写入 v.* 变量
anim:setMolangVar("speed", 1.5)               -- 设置 Molang v.speed = 1.5
anim:setMolangVar("phase", math.sin(time))    -- 接受 Lua number

-- === 作用域指定 === --

-- 读取不同作用域的变量
anim:getMolangVar("c.loop_count")             -- 读取 c.* 上下文变量
anim:getMolangVar("t.frame_temp")             -- 读取 t.* 临时变量
anim:setMolangVar("c.loop_count", 5)          -- 写入上下文变量
anim:setMolangVar("t.value", 0)               -- 写入临时变量

-- === 批量操作 === --

-- 批量设置变量（等效于多次 setMolangVar）
anim:setMolangVars({
    speed = 2.0,
    phase = 1.5,
    ["c.state"] = "running",
})

-- 获取所有变量快照（调试用）
local snapshot = anim:getMolangVars()
-- 返回 { v = { speed = 2.0, ... }, c = { ... }, t = { ... } }

-- === 变量事件绑定 === --

-- 注册变量变化的回调（Phase 2 特有）
anim:onMolangVarChange("speed", function(newVal, oldVal)
    print("Speed changed from " .. oldVal .. " to " .. newVal)
end)
```

### 5.7 事件集成 — 完整示例

#### 5.7.1 动态 Molang 变量驱动

```lua
-- === 实时更新 Molang 变量，影响 .bbmodel 内所有 Molang 表达式 === --

-- 准备工作：加载模型
local model = animations["MyModel"]

-- 启动所有动画的原生 Molang 模式
for name, anim in pairs(model) do
    if type(anim) == "table" and anim.playMolang then
        anim:playMolang()
    end
end

-- 每刻更新变量
events.TICK:register(function()
    local player = player:getPlayer()
    if not player then return end

    -- 更新所有动画共享的 Molang 变量
    for name, anim in pairs(model) do
        if type(anim) == "table" and anim.setMolangVar then
            -- query 类变量由运行时自动更新，用户只需更新自定义 v.* 变量
            anim:setMolangVar("time_of_day", player:getLevel():getDayTime() / 24000)
            anim:setMolangVar("is_rain", player:getLevel():isRaining() and 1 or 0)
            anim:setMolangVar("health", player:getHealth())
        end
    end
end)
```

#### 5.7.2 Molang 条件动画切换

```lua
-- === 使用 Molang blend_weight 实现动画状态机 === --

-- .bbmodel 中的动画配置：
--   idle.blend_weight  = "v.is_moving ? 0.0 : 1.0"
--   walk.blend_weight   = "v.is_moving && v.speed < 0.5 ? 1.0 : 0.0"
--   run.blend_weight    = "v.speed >= 0.5 ? 1.0 : 0.0"
--   jump.blend_weight   = "v.is_jumping ? 1.0 : 0.0"

local modelAnims = animations["Character"]
local shared = { is_moving = 0, speed = 0, is_jumping = 0 }

for _, anim in pairs(modelAnims) do
    anim:playMolang({ v = shared })
end

events.TICK:register(function()
    local p = player:getPlayer()
    if not p then return end

    shared.speed = p:getSpeed()
    shared.is_moving = shared.speed > 0.01 and 1 or 0
    shared.is_jumping = p:isJumping() and 1 or 0
end)
```

#### 5.7.3 Molang 表达式调试

```lua
-- === 运行时调试 Molang 表达式 === --

events.RENDER:register(function(delta)
    local anim = animations["model"]["walk"]
    if not anim:isPlaying() then return end

    -- 测试 Molang 表达式求值结果
    local testExprs = {
        "Math.sin(query.anim_time * 20)",
        "v.speed > 0.5 ? 1.0 : 0.0",
        "query.life_time",
        "v.phase ?? 0",
    }

    for _, expr in ipairs(testExprs) do
        local result
        if anim.evalMolangNative then  -- Phase 2
            result = anim:evalMolangNative(expr)
        else  -- Phase 1
            result = anim:evalMolang(expr)
        end
        print("[Molang Debug] " .. expr .. " = " .. tostring(result))
    end
end)
```

### 5.8 .bbmodel 自动 Molang 支持

当加载的 `.bbmodel` 中包含 Molang 表达式时，系统自动启用相应的求值路径，用户无需手动调用专用 API：

```lua
-- === 场景 1：完全自动（推荐） === --
-- .bbmodel 中的 keyframe data_points 含 "Math.sin(query.anim_time)"
-- play() 自动检测并处理
local walk = animations["model"]["walk"]
walk:play()

-- === 场景 2：自动 + 手动变量覆盖 === --
-- 自动处理关键帧 Molang，但用户可以覆盖部分变量
walk:play()
walk:setMolangVar("speed", 2.0)

-- 此时 .bbmodel 中的 "Math.sin(query.anim_time * v.speed)" 会使用
-- 用户设置的 speed=2.0 而非默认值

-- === 场景 3：自动检测逻辑 === --
-- 加载时检测规则（无需用户代码）：
--   1. NBT 中存在 "mau"/"mbw" 等 Molang 字段 → 启用动态求值
--   2. Keyframe aCode/bCode 含 Molang 语法特征 → 启用 Molang 转译/原生求值
--   3. 否则 → 纯数值/纯 Lua，走原路径（完全向后兼容）
```



---

## 6. 需修改的文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1~30 | `common/src/main/java/org/figuramc/figura/molang/**/*.java` | **批量新建** | 完整 Molang 引擎（Lexer→Parser→AST→Evaluator→Binding→Storage） |
| 31 | `common/src/main/java/org/figuramc/figura/animation/Keyframe.java` | 修改 | `parseStringData()` 增加原生 Molang 求值路径 |
| 32 | `common/src/main/java/org/figuramc/figura/animation/Animation.java` | 修改 | 集成原生求值器，Molang 动态 offset/blend |
| 33 | `common/src/main/java/org/figuramc/figura/animation/AnimationPlayer.java` | 修改 | 支持原生 Molang 求值路径 |
| 34 | `common/src/main/java/org/figuramc/figura/parsers/BlockbenchParser2.java` | 修改 | 保留 Molang 原始字符串到 NBT |
| 35 | `common/src/main/java/org/figuramc/figura/parsers/BlockbenchCommonTypes.java` | 修改 | NBT 序列化增加 Molang 字段（mau/mbw/msd/mld） |
| 36 | `common/src/main/java/org/figuramc/figura/avatar/Avatar.java` | 修改 | 从 NBT 读取 Molang 字段，管理 Molang 上下文 |
| 37 | `common/src/main/java/org/figuramc/figura/lua/api/AnimationAPI.java` | 修改 | 新增 `playMolang()`、`evalMolang()` 等 Lua API |

### 无需 Mixin

Molang 支持完全在 Figura 自有代码中实现，**不涉及任何 Minecraft 核心类的 Mixin 注入**。所有修改都在 Figura 的 `animation`、`parsers`、`molang` 包中完成。

Molang 支持完全在 Figura 自有代码中实现，**不涉及任何 Minecraft 核心类的 Mixin 注入**。所有修改都在 Figura 的 `animation`、`parsers`、`molang` 包中完成。

---

## 7. 风险与注意事项

### 7.1 语法兼容性风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| Molang `?:` 三元短路语义 | 确保三元 `a?b:c` 仅对 a 为 true 时求值 b | 原生 TernaryExpression，严格遵循短路语义 |
| Molang `??` 空合并 | `a ?? b` 仅当 a 为 null 时取 b | 原生支持，参考 YSM 实现 |
| Molang `->` 箭头 | `entity->name` 链式上下文切换 | 原生支持，创建子 Evaluator |
| Molang `{ }` 作用域 | 多条语句 + 末尾表达式返回值 | 原生 ExecutionScopeExpression |
| Molang 除零返回 0 | 与 Java 不同 | Evaluator 中显式检查除零 |
| Molang `Math.*` 大写 | 与 Java `Math.*` 同名但语义不同 | `Math.sin` 通过 ObjectBinding 解析，不冲突 |

### 7.2 引擎稳定性

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| 自研引擎 Bug | 新引擎可能有未发现的求值错误 | 充分测试，对照 YSM 和 Molang 规范验证 |
| 浮点精度差异 | Molang 与 Java float 精度可能不同 | 统一使用 float，参考原版 Molang 行为 |
| 内存泄漏 | 解析器/AST 缓存无限增长 | 限制缓存大小，支持按动画生命周期释放 |

### 7.3 兼容性风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| 现有纯 Lua 表达式 | 用户已有 `.bbmodel` 中使用 Lua 语法的关键帧 | 增加 Molang 检测逻辑，非 Molang 走原路径 |
| NBT 格式变更 | 现有 `.figura` 模型文件不包含 Molang 字段 | 向后兼容：无 Molang 字段时使用旧逻辑 |
| `.bbmodel` V4 vs V5 | 不同版本的解析器不同 | 两个解析器都需要修改 |

### 7.4 Molang 检测策略

为区分 Molang 表达式和纯 Lua 表达式（Blockbench 中两者都可能出现），使用以下启发式检测：

```java
public static boolean isMolang(String expr) {
    if (expr == null || expr.isEmpty()) return false;
    
    // 1. 纯数字 → 不是 Molang
    try { Float.parseFloat(expr); return false; } catch (Exception ignored) {}
    
    // 2. 包含 Molang 特有语法
    return expr.contains("Math.")          // Math.sin, Math.cos 等
        || expr.contains("query.")         // query.xxx
        || expr.contains("q.")             // q.xxx 别名
        || expr.contains("variable.")      // variable.xxx
        || expr.contains("??")             // 空合并运算符
        || expr.matches(".*\\?[^=].*:.*")  // 三元条件 (排除 ?=)
        || expr.contains("->")             // 箭头运算符
        ;
}
```

### 7.5 建议的实施顺序

```
Week 1: 核心引擎
  Day 1-2: 词法分析器 (Lexer) — MolangLexerImpl, TokenKind, Token, Cursor
  Day 3-4: 解析器 (Parser + AST) — MolangParserImpl, 14 种 Expression 节点
  Day 5-6: 求值器 (Evaluator + Binding) — ExpressionEvaluatorImpl, ObjectBinding
  Day 7: 集成测试 + Bug 修复

Week 2: 集成与 API
  Day 1-2: 改造 AnimationPlayer / Keyframe — 接入原生求值路径
  Day 3: Blockbench 解析器改造 — 保留 Molang 原始字符串到 NBT
  Day 4: Animation / Avatar 改造 — 动态求值 offset/blend
  Day 5: Lua API — playMolang(), evalMolang(), 变量管理 API
  Day 6: 性能优化 — 常量折叠、AST 缓存、字符串池化
  Day 7: 全面测试 + 文档
```

---

## 附录 A：YSM 参考文件索引

以下文件位于 `d:\UserData\Desktop\Project\fig\YSM\`，可作为直接参考：

### 核心引擎 (com.elfmcys.yesstevemodel.molang)

| 文件 | 参考价值 |
|------|---------|
| `molang/MolangEngine.java` | 引擎接口定义 |
| `molang/lexer/MolangLexerImpl.java` | 完整词法分析实现 |
| `molang/lexer/TokenKind.java` | 38 种 Token 类型定义 |
| `molang/parser/MolangParserImpl.java` | Pratt 解析实现 |
| `molang/parser/ast/*.java` | 14 种 AST 节点 |
| `molang/runtime/ExpressionEvaluatorImpl.java` | Visitor 求值器 + 原生浮点路径 |
| `molang/runtime/binding/ObjectBinding.java` | 绑定系统接口 |

### GeckoLib 集成层

| 文件 | 参考价值 |
|------|---------|
| `geckolib3/core/molang/MolangParser.java` | GeckoLib 包装器 (IValue 体系) |
| `geckolib3/core/molang/value/IValue.java` | 编译后值接口 |
| `geckolib3/core/molang/binding/PrimaryBinding.java` | 顶层命名空间绑定 |
| `geckolib3/core/molang/builtin/MathBinding.java` | math.* 函数注册 |
| `geckolib3/core/molang/builtin/QueryBinding.java` | query.* 变量注册 |
| `geckolib3/core/molang/storage/VariableStorage.java` | 变量存储实现 |

### 动画集成

| 文件 | 参考价值 |
|------|---------|
| `geckolib3/core/keyframe/bone/RawBoneKeyFrame.java` | 关键帧 Molang IValue 存储 |
| `geckolib3/core/keyframe/Vector3v.java` | 三维向量 Molang 求值 |
| `geckolib3/core/controller/AnimationControllerInstance.java` | Molang 驱动动画播放 |
| `geckolib3/util/json/JsonKeyFrameUtils.java` | JSON→关键帧解析 (含 Molang) |

---

## 附录 B：.bbmodel Molang 表达式示例

```json
{
  "animations": [{
    "name": "walk",
    "loop": "loop",
    "length": 1.0,
    "anim_time_update": "query.anim_time * 2.0",
    "blend_weight": "v.speed > 0.5 ? 1.0 : 0.0",
    "animators": {
      "uuid-xxx": {
        "name": "",
        "type": "bone",
        "keyframes": [{
          "channel": "rotation",
          "interpolation": "linear",
          "time": 0.0,
          "data_points": [{
            "x": "Math.sin(query.anim_time * 20) * 30",
            "y": 0,
            "z": 0
          }]
        }]
      }
    }
  }]
}
```

---

*计划版本：v1.0*
*最后更新：2026-06-29*
