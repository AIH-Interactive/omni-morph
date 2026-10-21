# 装饰系统 v1 契约（阶段一）

本文件锁定实施计划第 3、7、8 节的实现细节；不表示阶段二及以后的运行时已实现。客户端保持 alpha.3，阶段六统一调整版本。

## JSON 与资源

独立 JSON schemaVersion=1，线上的字段使用 snake_case。冻结 Protobuf v1 的文件、哈希、验证器及旧 Ping 均不修改。示例在 `protocol/outfit-v1/duplicate-instances.json`，后端保留相同字节的独立测试副本。

用途为 AVATAR/ACCESSORY/OUTFIT；格式为 FIGURA/YSM_PLAIN/YSM_ENCRYPTED。OUTFIT 不使用模型格式、NBT 正文或 `.oma/.omc/.oms`，而使用配置内容表。未声明用途的旧模型默认 AVATAR。来源、用途、格式、权限、候选关系相互独立。

LOCAL 引用为稳定 UUID、格式及 SHA256，无绝对路径；CLOUD 为规范 origin（无结尾斜线、凭证、路径、查询）、资源 UUID、精确正修订号、SHA256 及格式。本人预设保存允许稳定本地引用；云端发布/应用阶段另外检查合法可分发依赖，不隐式上传本地文件。

本地资源根为 OmniMorph 数据根下 `cosmetics/`；下载缓存为 `cache/cosmetic/*.oms`。不得混用目录。`cosmetic.json` 复用 Avatar 的 name/authors/version/description/autoScripts/autoAnims 公共字段；默认符号唯一字段为 `cosmetic_anchor`。缺失默认符号/自定义未知符号留待绑定。双 manifest 无明确用途时拒绝；明确用途才选择对应 manifest。未知分类保留原标识，显示通用标签。

配置保存 schema_version、revision、可空 base、instances、virtual_anchors、show_equipment 和 display_ordinals。后者是每个资源身份的编号水位，删除最后一个副本也保留；instance_id 才是永久身份。控制项/Lua 声明状态只允许 bool/有限数值/短字符串。controls 键的完整作用域为账号/装扮 + instance_id + control_id；资源修订用于定义兼容检查。草稿与观察端恢复不保存本地正式偏好、不触发主动修改回调。

BoneRef 为 instance_id（null 为基础）、完整 path 段（name + 同名兄弟 sibling_ordinal）、skeleton_hash。锚点联合为 STANDARD/BASE_BONE/ACCESSORY_BONE/VIRTUAL/AUTO_TRAIL/UNRESOLVED。手动绑定不回退。虚拟锚点 UUID 可改名，父级为基础或装饰 BoneRef，不支持虚拟节点嵌套。依赖必须无环，无自引用、缺失实例或丢失所属实例的骨骼引用。

绑定配置按基础资源身份 + skeleton_hash 保存；原版基础使用固定 vanilla 骨架版本。预设内 UUID 保持一致，导入其他草稿时重分配全部实例/虚拟 UUID 并重写内部引用与冲突编号。编辑器选择、树开关、搜索/折叠状态不进入运行时配置。

## 坐标、预算与错误

适配器输出实体根局部空间、列向量矩阵；位置单位为模型单位（16/方块），角度为度，局部矩阵固定 `T * Rx * Ry * Rz * S`，缩放必须为正。组合为实体外层 × 目标姿态 × 锚点偏移 × 实例偏移 × 装饰姿态。原生 Figura/YSM 的轴及枢轴转换须由阶段二的原生姿态用例验证；阶段一矩阵测试仅冻结局部配置乘法约定，不冒充已验证原生适配。

硬上限：32 实例、128 虚拟点、层级深度 64、单资源节点 8192、整套节点 32768、内部覆盖 1024、配置 65536 UTF-8 字节、8 拖尾/每条 256 采样。位置各轴 ±4096、旋转各轴 ±36000、缩放 [0.001,64]。现有三角形/纹理/Lua/粒子/声音权限按穿戴者汇总；服务器只能收紧。无效值/图/预算明确拒绝，不静默截断。

沿用 HTTP 错误：INVALID_REQUEST（结构/值/图）、CONFLICT（CAS/幂等参数冲突）、NOT_FOUND（本人作用域外预设）、UNAUTHENTICATED、FORBIDDEN、RATE_LIMITED、INTERNAL。Apply 阶段补充冲突的最新 revision 响应；目前不开放 Apply。

## 接口与迁移

capabilities.outfits_v1 分别声明 configuration/presets/apply/events。阶段一只开放前两项。缺失能力保持旧单形象行为，不能显示组合已同步。

- `GET /api/library/v1/outfits/current`：本人持久配置；尚无组合配置时返回 revision=0 空配置，现有正式单形象仍由旧装备接口管理，阶段五才做兼容归一化。
- `GET/POST /api/library/v1/outfit-presets`、`GET /:id`：仅本人；保存携带 operation_id、preset_id、expected_revision、name、configuration。同操作同载荷返回原结果；不同载荷冲突；最多 64 本人预设。
- `PUT outfits/current`、观察者快照及 outfit-events：阶段五接通；复用身份、订阅、取消及权限边界。

数据库 6→7：迁移前备份，事务重建 library_models 以允许空格式（保留旧三格式 CHECK、全部列、索引、外键与策略 view），添加用途/版本装饰声明、配置、预设/依赖及目录预设配置表。旧库用途默认 AVATAR；in_outfit 不改为实例槽位。失败回滚并恢复连接的 foreign_keys 设置；不改已发布 SQL 的含义。装饰骨骼声明按 CLIENT_EXTRACTED 保存；仅容器可读取不证明服务端验证了加密骨架或编辑能力。

## Lua、粒子与创建契约

Lua API：cosmetic_controls 与 avatar_controls 同名方法/链式/类型/回调，容器按实例隔离；cosmetic_animations:setPage(page)、cosmetic_animations:onEvent(callback)。事件给出动画名、类型/标记、时间、播放代际，通过有界队列在下一 tick 派发。时序为 Lua/controls → Molang → 姿态/挂点 → 绘制。Java 暴露受限实例句柄，不向脚本暴露其他作者 Avatar 或 host 权限；Rust 不运行脚本。

在既有 particles 增加 newTexturedParticle(texture,pos,velocity)，复用 LuaParticle 常规属性。纹理限本资源授权句柄；emissive 默认 false。精灵图参数：frameWidth/frameHeight 或 columns/rows（矛盾拒绝）、frameCount、startFrame/endFrame（0 起始闭区间）、frameTimeTicks>0、playback=once/loop/pingPong、initialFrame、randomStart、frame、paused、单元间距/边距。左上角起按行排列；once 停末帧，寿命独立；按 tick+partial tick 计时。共享纹理上传，实例粒子归属独立；仅同步授权事件/种子，不同步逐粒子位置。第 5.4 节全部渲染、生命周期和总预算约束保留。

创建 `+` 位于形象集，筛选 `+` 位于搜索区，tooltip 不同。三个菜单项：Figura 原向导；YSM 完整复制模板后只改 metadata.name/tips/authors，保留 credits/license；装饰写 cosmetic.json + 根名固定为 Cosmetic 的模型模板 + PNG + Lua。九类分类、左右目标、拖尾单空骨骼例外及 SVG→PNG 资产严格依计划第 6.4 节。失败只清本次新建目录，源资源 hash 不变。

`protocol/outfit-v1/template-manifest.json` 固定 ysm-default-1 的 42 文件 SHA256/字节数与 28 个 main 基础动作白名单。手部动作按 arm 渲染域精确选择，fp.arm 的 parallel 不自动注入。作者动作/显式空动画/禁用优先；角色映射先精确骨骼再显式角色，缺骨不猜测。有效 clip 表须携带模板版本/结构签名；不能将扩展索引塞入旧协议。最小辅助表达式依赖闭包由阶段二按真实片段解析锁定，不能整表自动播放。

真实 GUI/双客户端/动态渲染/性能项为 MANUAL/PENDING；数据、HTTP、迁移和矩阵测试各自只证明实际覆盖内容。
