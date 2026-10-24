# 装饰系统 v1 契约（阶段一）

本文件锁定实施计划第 3、7、8 节的实现细节；不表示阶段二及以后的运行时已实现。客户端保持 alpha.3，阶段六统一调整版本。

## JSON 与资源

独立 JSON schemaVersion=1，线上的字段使用 snake_case。冻结 Protobuf v1 的文件、哈希、验证器及旧 Ping 均不修改。示例在 `protocol/outfit-v1/duplicate-instances.json`，后端保留相同字节的独立测试副本。

用途为 AVATAR/ACCESSORY/OUTFIT；格式为 FIGURA/YSM_PLAIN/YSM_ENCRYPTED。OUTFIT 不使用模型格式、NBT 正文或 `.oma/.omc/.oms`，而使用配置内容表。未声明用途的旧模型默认 AVATAR。来源、用途、格式、权限、候选关系相互独立。

LOCAL 引用为稳定 UUID、格式及 SHA256，无绝对路径；CLOUD 为规范 origin（无结尾斜线、凭证、路径、查询）、资源 UUID、精确正修订号、SHA256 及格式。本人预设保存允许稳定本地引用；云端发布/应用阶段另外检查合法可分发依赖，不隐式上传本地文件。

阶段五的本人私有来源绑定使用认证 `PUT /api/library/v1/outfits/resources/local`：请求为 `reference`（仅 LOCAL）、`asset_id`、正 `asset_revision`、`raw_sha256`、`resource_kind`（AVATAR/ACCESSORY）及 `accessory_metadata`（AVATAR 为 null，ACCESSORY 必填）。LOCAL 的 content_hash 表示客户端来源身份，raw_sha256 表示已经通过原生上传检查的分发正文，二者不能相互替代。绑定只接受本人已有且未删除/封禁的固定原生修订；社区库来源及其装备别名不能转为 LOCAL。相同来源四元组（本人、UUID、来源 hash、格式）只允许重试同一绑定，改变正文或声明返回冲突；装饰声明统一标为 CLIENT_EXTRACTED，不声称验证加密骨骼。schema 9 独立来源表保留这些关系，每次内部 Apply 在同一事务中重检修订、正文 hash、用途、格式、当前预算和封禁；本地基础投影保留实际原生资产 ID/修订。绑定不会发表到目录或改变装备。客户端显式上传接续、公开 Apply 和观察者分发仍须阶段五后续接通，当前 apply 能力保持 false。

本地资源根为 OmniMorph 数据根下 `cosmetics/`；下载缓存为 `cache/cosmetic/*.oms`。不得混用目录。`cosmetic.json` 复用 Avatar 的 name/authors/version/description/autoScripts/autoAnims 公共字段；默认符号唯一字段为 `cosmetic_anchor`。缺失默认符号/自定义未知符号留待绑定。双 manifest 无明确用途时拒绝；明确用途才选择对应 manifest。未知分类保留原标识，显示通用标签。

配置保存 schema_version、revision、可空 base、instances、virtual_anchors、show_equipment 和 display_ordinals。后者是每个资源身份的编号水位，删除最后一个副本也保留；instance_id 才是永久身份。控制项/Lua 声明状态只允许 bool/有限数值/短字符串。controls 键的完整作用域为账号/装扮 + instance_id + control_id；资源修订用于定义兼容检查。草稿与观察端恢复不保存本地正式偏好、不触发主动修改回调。

BoneRef 为 instance_id（null 为基础）、完整 path 段（name + 同名兄弟 sibling_ordinal）、skeleton_hash。锚点联合为 STANDARD/BASE_BONE/ACCESSORY_BONE/VIRTUAL/AUTO_TRAIL/UNRESOLVED。手动绑定不回退。虚拟锚点 UUID 可改名，父级为基础或装饰 BoneRef，不支持虚拟节点嵌套。依赖必须无环，无自引用、缺失实例或丢失所属实例的骨骼引用。

装饰元数据的 `editable_bones` 是可选的、默认空的资源相对 BoneRef 列表，逐节点声明可编辑范围；必须同时声明 `has_bones` 和 `edit_bones`，引用不含玩家 instance_id，hash 与资源声明一致，不接受重复路径。范围数量/深度沿用 `[outfit.limits]` 的资源节点/深度上限；实际覆盖仍按整套 max_bone_overrides 限制。`exported_bones` 只声明对外挂载插槽，不能代替编辑授权。应用内部覆盖还必须通过加载后的路径/hash、原生运行时支持及当前用户资源编辑策略校验；缺失范围保持只读。

内部覆盖是当前实例原生骨骼枢轴处追加的局部 T/Rx/Ry/Rz/S 矩阵，使用统一模型单位；作者姿态/动画先求值，覆盖再影响网格、子骨骼和导出挂点。清空覆盖恢复作者姿态，不修改资源文件或原始拓扑，也不把内部骨骼当作基础目标。

绑定配置按基础资源身份 + skeleton_hash 保存；原版基础使用固定 vanilla 骨架版本。预设内 UUID 保持一致，导入其他草稿时重分配全部实例/虚拟 UUID 并重写内部引用与冲突编号。编辑器选择、树开关、搜索/折叠状态不进入运行时配置。

## 坐标、预算与错误

阶段五旧原生 Protobuf Equip/clear 使用整套替换语义：新操作在原生装备/操作回执的同一 SQLite 事务中写入仅基础或空基础配置，清空实例、虚拟锚点和编号水位，递增配置修订；重复操作只返回旧回执，不覆盖较新的装扮。配置写失败、预算拒绝或修订溢出会回滚装备、来源绑定和操作记录。现有本人原生绑定优先复用；没有来源 UUID 的旧客户端使用按本人/原生资产/修订确定的稳定 UUID 和原生正文 hash，明确不表示已恢复原始本地来源树。生产 Protobuf 适配器传入当前 outfit.limits；此接续尚不代表旧 HTTP 上传/删除和云端目录装备入口已全部归一化。

普通原生 Delete 仅在删去当前基础时，同事务清空装扮配置并推进修订；删除未装备资源不重置整套配置，原操作重放也不覆盖后续配置。旧 HTTP 清除走完整替换：有本人原生资源时保留原删除/回收路径并在同事务清空全部装备与配置；当前为社区来源别名或没有本地 avatar 文件时，使用原 Equip clear 事务清除，不删除目录资源或来源别名资产。两条生产删除适配器均传当前 outfit.limits。旧 HTTP 的导入/上传和启用路径仍需后续核对，不把清除路径通过当作全部旧写入完成。

旧导入在实际替换基础或 force_equipped 主动启用时，使用原生装备的共同事务归一化配置；相同正文的非强制读取不重置已有装扮。生产导入传入当前预算。归一化仅核对本人原生身份，保持旧写入和本人读取的既有封禁语义；它不授予观察者分发权。显式私有来源绑定、现代 Apply 及分发检查仍执行当前封禁，不因旧写入生成 LOCAL 投影而放行。旧固定 .moon 文件发布失败恢复、独立启用入口与云端来源上下文仍属于阶段五待验收边界。

旧非临时文件发布已改为同一存储发布锁下的可恢复替换：先保留原文件，再在现有 metadata 表记入本人/操作关联的待恢复记录，替换固定文件；原生装备、装扮配置提交时在同一 SQLite 事务中删除该记录。提交失败恢复原文件，首次上传失败则移除未提交的固定文件；启动时重复执行同样恢复。没有待恢复记录的遗留清理文件不回滚已提交版本。恢复备份保留到记录删除成功，恢复本身可再次重试。读取时的旧快照在发布锁内重核当前固定文件 hash，防止读取并激活已经回滚的临时替换。原临时上传路径保持；旧管理员/内部上传不再额外重复导入，独立启用入口及云端来源上下文继续阶段五。

旧 `POST /api/equip` 接受原客户端的 `[{owner,id}]` 选择列表，遵守既有单基础数量与原生资源 ID 范围；一项本人资源执行完整基础替换，空列表执行完整清除，均复用当前预算和同事务配置归一化。目录来源别名仍须经原目录授权装备入口，不能通过旧原生入口直接启用。首次旧文件导入若已完成替换，本次请求不再重复推进配置修订；已入库的原生资源可在没有旧文件时启用。无正文的历史调用继续只发送通知，不把通知误当选择/清除。云端目录装备来源归一化仍待阶段五后续接通。

旧目录装备接口已在原权限/来源别名/展示意图/幂等事务中写入仅基础的 CLOUD 配置，清除旧装饰并推进配置修订。引用使用目录资源 UUID、精确修订、原生正文 SHA256 和格式，不转为 LOCAL。原操作指纹和回执保持，重放不覆盖后来的装扮；预算或配置写失败回滚装备与全部来源/配置写入。公共模型展示沿用资源设置及原默认可见，私有模型沿用本人展示意图。

LibraryAccess 的原认证适配器为所有本机库请求发送固定端点的 x-omnimorph-origin，调用方不能通过可变请求头覆盖。后端目录装备优先使用原 Host 匹配校验的显式源站；缺少该头的旧请求使用唯一 Host 与单一 http/https 的 X-Forwarded-Proto，原生直连监听缺省 http。代理须保留外部 Host 并覆盖正确外部协议；该协议只标记来源命名空间，不用于认证、授权或选择其他主机。异常/重复源站或代理协议拒绝，其他身份与资源策略保持原路径。

内部整套 Apply 在准备和提交事务内分别读取精确修订的装饰声明；本地声明来自本人私有绑定，云端声明来自对应元数据修订，均先执行原使用权限和预算检查。同一完整资源引用在单次检查中只读取一次，再按 instanceId 校验各副本。supported_bases 按 VANILLA/FIGURA/YSM 家族匹配；内部覆盖要求 has_bones、edit_bones 及 editable_bones 的完整路径/同级序号/骨架 hash；装饰挂点及虚拟父级仅允许 exported_bones 的同样精确引用。display_bones 只控制树显示，不能隐式授予或撤销导出/编辑能力。停用实例仍检查声明和保留的覆盖，停用父级的合法绑定可保留，其实际渲染由运行时停用规则决定；拖尾实例预算只计启用且声明为 Trail 的实例。基础骨架、实际节点存在性和解密后骨架仍由客户端加载检查，服务器不把 CLIENT_EXTRACTED 声明升级为实际骨架验证。Apply 公开能力继续关闭直到本阶段其余链路完成。

阶段五内部观察者快照按一次 SQLite 读取快照解析配置、当前原生基础及双方权限。穿戴者先保有使用权，观察者再以自己的实际授权判断；私有来源仅按当前装扮展示意图允许渲染，不获得目录查看或获取资格。隐藏配置、基础不可用或旧原生投影不一致时只保留版本/空投影。实例过滤不改 UUID、显示序号或可见来源编号水位；手动父级/虚拟挂点缺失时逐级退休依赖，自动拖尾仅撤销失效优先实例。固定来源去重且资源 ID 不随仅配置变化而改变，不序列化 canonical native descriptor；本人配置管理仍返回完整原配置。该方法尚未接公开快照/正文/事件路由；显式私有来源旧正文读取边界、当前配置作用域、逐段授权及来源身份预热是公开分发前的必要检查点，不能用元数据投影代替正文授权。apply/events 继续关闭。

schema 10 的 outfit_private_sources 只记录本人显式绑定的原生修订，绑定与标记同事务；自动旧归一化及迁移不推断历史私有意图。观察者通过既有原生正文入口读取此类修订时，须仍在当前获准装扮投影内，初始检查及每段均复核当前上限、展示、实例启停/依赖和权限；本人读取保持原私有资源行为。内部快照的来源检验在锁外计算实际正文身份，最终配置/权限检查要求已验证身份并重新执行封禁。该接续尚未提供正式私有上传准备：正式客户端必须在正文首次提交前声明用途，并与原生提交原子写入私有标记，不能用先普通上传、后绑定的窗口声称全程私有。公开快照、按配置修订作用域读取及事件仍未开启。

正式私有来源的后端准备入口为 POST /api/library/v1/outfits/resources/uploads，请求含 operation_id、LOCAL reference、resource_kind、accessory_metadata、raw_sha256、byte_length、可选 content_sha256/schema_sha256；YSM 两类摘要均必填。预留在 schema 11 中冻结输入，正文经返回的 relative_path PUT（application/octet-stream），随后 POST /api/library/v1/outfits/resources/uploads/:operation/commit 返回 LocalBinding。重复预留返回 completed=true 和 resource 时可复用已提交源；原操作不得换输入。原生首次提交与正文验证身份、绑定、私有标记共用事务，旧原生入口不能绕过用途，重复完成释放多余 grant；不生成目录/集合条目或改变装备。使用现有原生上传权限、字节及账户资产预算，并在 grant/提交重核当前装饰声明预算。普通操作回执退役后，持久预留与固定源仍可恢复；CLIENT_EXTRACTED 不表示实际骨架已验证。客户端尚未调用该入口，公开整套 Apply/快照/作用域正文及事件仍未开启。

观察者读取接口为 GET /api/library/v1/outfits/players/:owner，要求普通玩家认证及 x-omnimorph-origin/Host 匹配。schema_version=1 的快照包含 owner、revision、equipment_revision、可空 configuration、resources；每个资源含 id/reference/resource_kind/accessory_metadata、原生 distribution_format、byte_length、raw_sha256/content_sha256/schema_sha256、relative_path，不序列化 canonical native descriptor。relative_path 指向 GET /api/library/v1/outfits/players/:owner/resources/:id?revision=N；资源 ID 按穿戴者与固定引用/用途生成，不因仅配置变化而变。正文仅为当前该修订内获准依赖，修订改变409，失效/无权404；每段复核原会话、当前双方权限/封禁/上限及相同 descriptor。渲染读取不创建目录下载回执或授权入集，私有目录仍保持原策略。客户端尚未调用；仅配置变化可在重新授权后复用同一摘要的已缓存正文。缺失现代配置的旧账户须沿用原 profile 基础加载，不能直接视为用户清除。公开 Apply 和状态/事件继续未开启。

PUT /api/library/v1/outfits/current 只操作当前普通认证玩家，输入 operation_id、expected_revision、configuration，并要求规范源站。成功响应 schema_version=1、operation_id、applied（configuration/equipment_revision）、current_revision；applied 是原不可变操作回执，current_revision 是响应准备时的最新配置修订。客户端必须验证操作与修订，回执已落后不得激活，并重新取得当前状态；旧回执重放不会恢复旧配置或再次广播基础兼容变更。冲突409包含原错误字段、request_id、current_revision 和本人当前 configuration，禁止自动合并/覆盖草稿。该路由尚未由正式客户端调用，apply/events 继续 false。Apply 请求/回执/冲突信封限 MAX_JSON_BYTES+4096，快照限 MAX_JSON_BYTES+8192+MAX_INSTANCES*(MAX_JSON_BYTES+4096)；配置仍严格受当前 max_json_bytes 约束，其他 API 仍保持原64KiB，不以信封容量扩大配置能力。

客户端 Apply 传输已有 LibraryClient.applyOutfit：冻结输入并按当前能力/上限提交，使用独立有界信封，普通 JSON 和旧协议限制保持。Accepted 精确绑定操作、准备配置/下一修订及最新修订，完整字段与64位资源修订须相符；落后回执不能激活。Conflict 独立保留本人当前配置用于检查，不自动覆盖草稿，也不以旧配置适合协议硬边界推断符合新动态预算。激活守卫只是必要条件，正式控制器仍须持有合法原生准备、权限和当前代际。仅对所选 tagged 分支补规范 null，计入提交后修订的字节预算，不改变本地预设格式。客户端传输已做生产适配器本机 HTTP 组件验证，尚未接正式原子激活、私有来源准备/观察者运行时或事件，apply/events 仍关闭。

适配器输出实体根局部空间、列向量矩阵；位置单位为模型单位（16/方块），角度为度，局部矩阵固定 `T * Rx * Ry * Rz * S`，缩放必须为正。组合为实体外层 × 目标姿态 × 锚点偏移 × 实例偏移 × 装饰姿态。原生 Figura/YSM 的轴及枢轴转换须由阶段二的原生姿态用例验证；阶段一矩阵测试仅冻结局部配置乘法约定，不冒充已验证原生适配。

硬上限：32 实例、128 虚拟点、层级深度 64、单资源节点 8192、整套节点 32768、内部覆盖 1024、配置 65536 UTF-8 字节、8 拖尾/每条 256 采样。位置各轴 ±4096、旋转各轴 ±36000、缩放 [0.001,64]。现有三角形/纹理/Lua/粒子/声音权限按穿戴者汇总；服务器只能收紧。无效值/图/预算明确拒绝，不静默截断。

用户追加：运行时装饰预算从后端独立 `[outfit.limits]` 读取，经 `capabilities.outfits_v1.limits` 下发；缺省键沿用默认值。支持单独收紧实例/虚拟点/层级/节点/覆盖/配置字节/控制项/Lua 声明状态/预设/编号水位/导出骨骼/拖尾/变换及动态光源。计数为零可关闭对应容量；深度、配置最小字节及采样缓冲必须有效。无效配置明确拒绝；按既有配置监听流程重载后，后续请求使用新限制。静态安全校验仍保留，用户配置不能越过 v1 硬边界。

沿用 HTTP 错误：INVALID_REQUEST（结构/值/图）、CONFLICT（CAS/幂等参数冲突）、NOT_FOUND（本人作用域外预设）、UNAUTHENTICATED、FORBIDDEN、RATE_LIMITED、INTERNAL。Apply 阶段补充冲突的最新 revision 响应；目前不开放 Apply。

## 接口与迁移

OutfitApplyResources 只在显式应用准备中协调固定配置依赖，不提交配置或激活模型。开始及重试协商当前配置/Apply 能力与 outfit.limits；LOCAL 引用按资源去重，包括停用副本，优先读取本人已有固定绑定，未绑定才读取设备原来源并私有上传，上传确认后读取服务器固定正文。准备数据以受保护缓存及当前鉴权为准，源文件随后变化不替换已经冻结的上传输入。复用 OutfitPresetResources 的云端固定依赖/修订/格式检查和逐来源错误清单，每个失败指明基础或受影响 instance_id，含失败来源的结果 ready=false。固定本地输入和错误为该准备代际私有，不影响预设原本地/云端行为。重试退休原结果、重新解析所有来源；当前会话保留已冻结操作供重试，未完成操作的跨进程恢复仍待接。账号、草稿、连接或关闭使旧回调/结果失效；完整依赖就绪仍不能代替原生骨架准备、CAS确认及正式发布。

OutfitPreparation 原入口保留 DRAFT 及本地导出/编辑默认策略，正式重载要求显式 OWNER/OBSERVER、调用方持有的准备基础，以及基于当前授权来源的导出/内部编辑策略；不由作者声明自行授予云端能力。OWNER 的账号须匹配穿戴者。正式准备核对 AVATAR 基础的存活、固定来源身份和三格式，装饰须区分 Figura/明文YSM/加密YSM；不依赖 GUI 的只读骨骼开关授予能力。OutfitComposition.validatePreparedBase 在原内部覆盖校验外，核对实际基础/实例总节点、作者支持基础、实际骨架 hash 与所有导出/编辑路径（包括无用户覆盖的声明），元数据缺失或过期拒绝提交前准备。准备完成前不执行装饰 Lua，不注册正式玩家；失败关闭本次组合，基础仍归调用方所有。缺失手动挂点继续按既定控制器暂停依赖，自动模式才可使用既定回退；此原生校验不代替来源权限重检、Apply 回执代际守卫或正式激活。

固定本人正文为 POST /api/library/v1/outfits/resources/local/body，请求为 reference/resource_kind/raw_sha256，不接受其他 owner 或原生路径。绑定和正文摘要不符返回409，未绑定/删除返回404，封禁等沿用既有错误；本人未穿戴、停用或已移除的来源仍可按当前授权准备。响应为 application/octet-stream、实际 Content-Length、x-omnimorph-sha256 和 private,no-store，正文不创建目录 transfer、下载计数或装备。原 DownloadPermit（全局32/每玩家2）、每段32KiB/30秒预算保持；每段重新验证当前会话、绑定、封禁/声明预算和同一 descriptor，撤销停止后续段。

OutfitSourceService 使用原 LibraryClient TRANSFER 与会话退休检查，先本人解析绑定，再校验缓存/读取正文，原生 NBT 解析后重新解析绑定。缓存复用 ModelCacheCodec 的受保护容器及既有按用途的 avatars/*.oma、cosmetic/*.oms；文件名按源站、服务实例、账号、固定来源/格式/用途、原生资产/修订及 raw hash 计算，无新的明文正文或缓存后缀。缓存命中仍需当前授权，来源不变的配置变化不要求重传；损坏容器或摘要不符重取，正文头/摘要/NBT格式/用途/装饰声明不符或最终鉴权失败时不发布缓存。解析核对 YSM 密文可分发形态，但不据此声称实际骨架/运行时已准备。返回 NBT 独立复制，不加载正式 Avatar 或启动 Lua；正式准备/确认/激活控制器仍须后续接入，不能以固定正文缓存完成替代阶段五完成。

本人既有来源解析为 POST /api/library/v1/outfits/resources/local/resolve，请求仅 reference（固定 LOCAL）与 resource_kind（AVATAR/ACCESSORY），owner 来自原认证，不接受指定他人身份或管理员替代。只读复用 schema 9 的现有绑定和当前原生身份/封禁/声明预算校验，不生成操作、上传预留、目录或装备；绑定不存在返回200和 resource:null，已有绑定但原生删除/封禁、用途冲突或预算拒绝仍是原错误，不能降格为空后重复上传。响应 resource 为原 LocalBinding，资产名和正整数修订允许现有旧归一化绑定，不要求与本次新上传操作派生名相同。客户端 resolveOutfitSource 严格核对引用/用途/正文摘要形状/声明及当前预算，缺 resource、错误数值和 HTTP 失败均不当作未绑定；uploadOutfitSource 预留前查询，固定正文和声明相符直接复用，已绑定但正文不同返回 CONFLICT，必须改为准备已绑定的固定正文，不能换绑。独立客户端不需要保存原上传操作来找回已完成绑定；未完成上传仍须保留原冻结操作用于重试。固定正文授权加载/缓存和正式原子激活尚未由本接口完成。

客户端私有来源准备复用 LocalAvatarSource 的固定来源索引、LocalCosmeticInventory/LocalCosmeticSource 的固定声明和前后 hash 复核，不要求依赖已在当前启用快照。OutfitPrivatePreparation 仅在 worker 解析并返回独立 NBT 和冻结上传；复用 LibraryPackagePreparation 的有界分发压缩及 LibraryUploadIdentity 的 YSM 摘要计算，不改本地源文件、原生元数据或密文。OutfitPrivateUpload 固定操作 UUID、LOCAL 引用、用途/声明、raw/content/schema 摘要及正文，网络重试必须复用同一对象；不把分发 raw hash 当作来源 content_hash。LibraryClient.uploadOutfitSource 使用原 TRANSFER、认证与会话退休检查，先协商 Apply/配置能力和 outfit.limits，再走私有预留/正文/提交；已完成预留只核验绑定，不重传正文。预留路径必须严格等于当前操作的本站相对路径；回执须匹配引用、用途、声明、正文 hash、操作派生资产名及整数修订1。私有原生上传按服务端原生 grant 字节预算，不使用社区库 max_upload_bytes 替代。该组件不应用配置、不激活模型；跨重启/再次应用的既有绑定恢复及正式运行时控制器尚须接通，不能把同操作重试支持当作完整私有来源缓存。客户端目前仍尊重关闭的 apply/events，未作实际 Rust Listener 或真实 GUI/GPU 整链路声明。

阶段五接续：内部 Apply 事务输入为 operation_id、expected_revision、configuration，配置自身 revision 必须等于 CAS；返回 configuration（新修订）与 equipment_revision。配置、来源别名/展示设置及基础旧投影共用现有 SQLite/manifest 提交，基础未变化不推进旧装备修订；同操作重放不覆盖更晚配置，失败不得发布候选投影。此为尚未路由的内部实现，能力 apply 保持 false；本地私有绑定、作者能力、旧写归一化和授权同步接通前不开放组合应用，不改变下列阶段五公开接口仍待接通的状态。

capabilities.outfits_v1 分别声明 configuration/presets/apply/events。阶段一只开放前两项。缺失能力保持旧单形象行为，不能显示组合已同步。

- `GET /api/library/v1/outfits/current`：本人持久配置；尚无组合配置时返回 revision=0 空配置，现有正式单形象仍由旧装备接口管理，阶段五才做兼容归一化。
- `GET/POST /api/library/v1/outfit-presets`、`GET /:id`：仅本人；保存携带 operation_id、preset_id、expected_revision、name、configuration。同操作同载荷返回原结果；不同载荷冲突；最多 64 本人预设。
- `PUT outfits/current`、观察者快照及 outfit-events：阶段五接通；复用身份、订阅、取消及权限边界。

数据库 6→7：迁移前备份，事务重建 library_models 以允许空格式（保留旧三格式 CHECK、全部列、索引、外键与策略 view），添加用途/版本装饰声明、配置、预设/依赖及目录预设配置表。旧库用途默认 AVATAR；in_outfit 不改为实例槽位。失败回滚并恢复连接的 foreign_keys 设置；不改已发布 SQL 的含义。装饰骨骼声明按 CLIENT_EXTRACTED 保存；仅容器可读取不证明服务端验证了加密骨架或编辑能力。

## Lua、粒子与创建契约

Lua API：cosmetic_controls 与 avatar_controls 同名方法/链式/类型/回调，容器按实例隔离；cosmetic_animations:setPage(page)、cosmetic_animations:onEvent(callback)。事件给出动画名、类型/标记、时间、播放代际，通过有界队列在下一 tick 派发。时序为 Lua/controls → Molang → 姿态/挂点 → 绘制。Java 暴露受限实例句柄，不向脚本暴露其他作者 Avatar 或 host 权限；Rust 不运行脚本。

动画通知当前接线：`cosmetic_animations:onEvent(function)` 替换本实例唯一监听，`onEvent(nil)` 注销并清空待派发事件，`getEventError()` 返回最近拒绝/失败原因。每条回调收到新的表 `{animation,type,marker,time,generation}`，时间以秒计，代际来自本次播放；不包含 Lua/Molang 源码。待派发队列数量受本连接 `maxParticleCallbacksPerTick` 约束，通知执行与高级粒子 tick 回调共用穿戴者的该次数账本，并共同受既有整套 tick Lua 指令预算限制；字符串复用 `maxStateStringBytes` 的 UTF-8 上限，零次额度拒绝注册。队列满时丢弃新通知并给出明确原因，不阻塞原生动画；下一 tick 仅派发入场前的有限候选，回调新产生的通知最早再下一 tick 执行。更换监听、重载、播放代际失效、VM 替换或实例退休清除/拒绝旧通知，不退款已消费次数。

已接入 Figura 原代码关键帧（type=`keyframe`、marker 为空）及一次/保持播放首次到端点（type=`end`），保留原代码关键帧执行方式；自然结束保留本代通知，手动停止/重启使旧代失效。队列在生产 Avatar.tick 的 tick 指令重置后调用，真实游戏时序仍 MANUAL/PENDING。跨端授权回放和隔离预览继续在既定检查点处理；setPage 属于阶段四的轮盘入口登记，阶段三未提前实现界面。

阶段四轮盘入口接线：装饰脚本可使用自己的 `action_wheel` 创建页面/动作，原有 host 判定不变，脚本仍不能借此执行全局玩家轮盘或读取其选中状态。`cosmetic_animations:setPage(page)` / `set_page(page)` 接受本实例 Page 对象或登记页名称；nil 撤销显式入口并恢复该实例既有 currentPage，未知名称/非法类型原子拒绝。入口绑定当前 Lua runtime；关闭后不再提供页面。普通 Figura 动画文件不产生推测动作；未显式登记的原生 YSM 只有在既有默认轮盘规则包含真实附加动画动作时才参加聚合，不能以仅有“设置”的页面冒充动画入口。

`OutfitActionWheel` 已提供组合内宿主导航、资源→副本分组和原始 Action 的逐实例执行；宿主入口/返回追加在作者槽位之后，满8槽通过原有 Page 分组继续，作者原始页面/动作不被覆盖。执行、全局点击和滚动回调都使用真实所属 Avatar 与既有指令预算；原生 hold 使用已有 Press 对象，松开只停止本次按压启动的播放代际。组合关闭、目标移除/失效及基础脚本切页清理相应导航/按压。阶段四已接衣柜实例菜单的轮盘预览、同一隔离模型、原轮盘绘制及鼠标/数字键/滚轮输入；匹配松开清理按压，回调产生的持久控制值只保存到本实例草稿，不准备/装备或广播。轮盘打开的实例/基础设置页返回同一预览，已禁用或关闭的旧控件拒绝改值。正式玩家主轮盘的组合来源绑定仍依赖阶段五原子发布；原生 JVM 输入/屏幕路由测试不代表真实 Windows 窗口、Mixin 输入或 HUD/GPU 像素验收。

YSM 从已有初始事件及 YsmAnimationEventWindow 经过窗口产生 timeline/sound/particle 通知；marker 仅取原事件对象显式的 `marker` 参数，未声明则为空，绝不回传 timeline 的 Molang 表达式。例如既有 timeline 对象可用 `{event="v.flash=1;",marker="sparkle"}` 分别声明原 Molang 动作和通知标识。通知只排队，原声音/粒子/Molang 仍走原处理路径；因组装时没有 Lua 监听而已经发生的初始事件不补发。ONCE/HOLD_ON_LAST_FRAME 首次到端点产生一次 end，随后自然淡出/移除仍可递送该通知。停止、主动淡出/控制器退休或重置撤销该播放的排队通知；主动淡出中不再生产新 Lua 通知，原生姿态继续按既有逻辑淡出。控制器对同一时钟逐帧刷新权重保持通知代际和结束标记；显式直接 play、建立新槽位或取消后重新激活才开始新通知代际。该内部通知代际不修改原播放时钟/同步 playbackGeneration。已关闭/被替换运行时、子实体和 observerControlled 投影不从本地源派发这些通知；观察端授权事件将在阶段五统一接入。

在既有 particles 增加 newCustomParticle(texture,pos,velocity)，此前拟定 newTexturedParticle 作为同一实现的便捷入口，复用 LuaParticle 常规属性并保持旧 API 行为。纹理限本资源授权句柄；emissive 默认 false。精灵图参数：frameWidth/frameHeight 或 columns/rows（矛盾拒绝）、frameCount、startFrame/endFrame（0 起始闭区间）、frameTimeTicks>0、playback=once/loop/pingPong、initialFrame、randomStart、frame、paused、单元间距/边距。左上角起按行排列；once 停末帧，寿命独立；按 tick+partial tick 计时。共享纹理上传，实例粒子归属独立；仅同步授权事件/种子，不同步逐粒子位置。第 5.4 节全部渲染、生命周期和总预算约束保留。

用户增量 CustomParticle：Billboard/Ribbon/Layered；多纹理层、平铺/拉伸、自由方向、加速度与旋转等属性，内置螺旋/环绕/追踪/贝塞尔运动，Lua 控制行为、Java 负责渲染/UV/纹理/生命周期。高级 Lua 回调同时受既有指令预算及穿戴者汇总 tick/帧次数限制。Ribbon 的 width/length 以方块计，lifetime 以 tick 计；uvRepeat 是每方块弧长重复数，uvOffset 是纹理周期偏移；taper/fade 默认 false，分别为尾端渐缩和年龄渐隐；emissive 与可选 light 独立，light 映射既有动态光源配置。平铺按世界弧长连续，stretch 映射保留轨迹全长；退化段/裁剪/传送/切世界必须有界且可清理。

控制器 TrailBuffer 与 CustomParticle Ribbon 使用 OutfitTrailBudget 的同一穿戴者槽位账本；控制器仅在有效世界采样时取得槽位，GUI/手部 pass 不取得或清除世界槽位。目标失效、世界变化、采样过期、失败或关闭组合释放所属槽位，不释放其他来源。独立 Ribbon 退出模式/移除释放自己的槽位。顶点账本同样按穿戴者与全局渲染帧共享，实际提交后才扣费；移除、注销和释放槽位不能返还本帧已提交顶点。

控制器绘制桥：CustomParticle 的 `setAnchorTrail(true)` / `isAnchorTrail()` 显式选择所属 Trail 装饰的控制器历史，仅在 Ribbon 模式读取该历史。普通 Avatar/没有 Trail 历史能力的实例拒绝启用，不能指定或借用其他实例。默认 false；既有独立粒子行为不变。开启后不采集第二份历史，也不再占独立轨迹槽位；多个绘制粒子可共用一条控制器历史，仍分别占粒子数量、层数、面数、顶点及光源额度。关闭链接若需要新的独立槽位但容量已满则原子拒绝；移除一个绘制粒子不能释放控制器所属槽位。

链接模式下，控制器 Lua 决定目标、采样间隔/距离、源历史保留时间和传送阈值；`setRibbon` 的宽度、可见长度/寿命裁剪、UV、渐缩/渐隐与材质设置仍作用于取得的有界历史，不反向重写共享采样策略。粒子自身的原生总寿命继续到期；独立运动坐标不替换锚点历史。缺少/过期/换世界的历史不绘制也不在原粒子位置生成替代轨迹；恢复有效采样后可继续绘制。非自发光采用轨迹头部原生光照；可选动态光源跟随该头部，等待有效历史时保留已预留的光源额度而不发布照明。冻结的原生提交只重放不可变顶点、UV、颜色和纹理引用，不重新执行 Lua 或采样。原生顶点/提交已自动验证，真实游戏/GPU 画面仍 MANUAL/PENDING；装饰 particles 全局入口须等隔离预览实现后开放。

精灵图边距/间距键固定为 marginX/marginY、spacingX/spacingY，单位像素，两侧使用相同边距。playback 缺省 loop，单帧图不随时间改变；randomStart=true 时私有种子选择播放范围内起点，覆盖 initialFrame 的播放起点，但仍校验 initialFrame 合法。手动 frame 只接受当前播放闭区间内的帧；暂停时手动改帧保持暂停，恢复继续分数相位。纹理退休后新消费者不得引用，已有消费者及其在途帧按持有句柄延迟回收；帧结束后必须释放自己的句柄。

newCustomParticle/newTexturedParticle 接受本实例纹理名或 FiguraTexture、位置 FiguraVec3 和可省略的速度 FiguraVec3；与 newParticle 一样立即尝试生成，原 API 保留。新增粒子控制为 setSpriteSheet(table)、setFrame/getFrame、setPaused/isPaused、setEmissive/isEmissive；所有模式共用播放基础。新粒子默认寿命 20 ticks、速度零、重力零、关闭碰撞、自发光关闭；未成功入队时 isAlive=false，已移除不可重新生成。使用原生成权限并另计穿戴者存活数量；清理只作用于对应执行实例，在途帧可继续持有退休纹理直至帧结束。

Layered：setLayers(table) 接受从 1 开始的连续非空层数组，按当前 max_particle_layers 在分配层列表前校验；成功自动切到 LAYERED。setRenderMode/getRenderMode 可在 BILLBOARD/LAYERED 间切换（设置不区分大小写，读取大写）；未配置层列表时不得切到 Layered。每层字段为 texture（缺省原构造纹理）、spriteSheet（缺省继承公共播放）、uv={u0,u1,v0,v1}（当前选中帧内 [0,1] 的正面积裁剪）、color（复制 Vec3/Vec4，缺省白）、alpha（[0,1]，缺省 1）、blend（alpha/additive/opaque，缺省 alpha）、emissive（缺省继承公共开关）。公共颜色/alpha 与层颜色/alpha 相乘；独立精灵图使用私有时钟，公共暂停作用于所有已配置层，公共 setFrame 只设置公共播放。当前 uv 裁剪不冒充后续无缝平铺/拉伸。

setBlendMode/getBlendMode 设置 Billboard 基础层的混合模式；Layered 的各层由其 blend 字段决定。alpha 使用原生 source-alpha 混合，additive 为 RGB srcAlpha/ONE，opaque 关闭混合；均保留原生深度测试、关闭深度写入并使用同一透明阶段，避免混合模式改变作者层序。按层序调整的只是自定义批次槽位，原生批次相对顺序不变；不能以 CPU 层序测试替代实际 GPU 叠加验收。层配置/模式切换须先验证并持有全部新纹理，再原子替换；失败释放临时持有并保留旧状态。共享运动/寿命/一个存活粒子名额，每个被移除层的在途帧仍保留纹理至帧结束。

粒子渲染复杂度沿用既有 COMPLEXITY 的“一个实际提交多边形面”单位：Billboard 一面，Layered 每层一面；在纹理上传和原生 quad 提取前整次申请，不足以绘制完整层列表时本次不提交，也不销毁粒子。被接受的提交扣入个人与整套共享面数，重复实际提交再次扣账，原生计数重置不返还已花额度。普通 Avatar 仅在持有自定义粒子时取得面数账本，不据此绑定 Lua 初始化/tick 指令作用域；最后一个粒子移除后账本保留到帧末，避免同帧随后绘制模型绕过预算。没有模型可见的穿戴者仍按渲染帧更新额度。

Ribbon 几何以接受样本的累计世界弧长为基准，环形淘汰/寿命过期不重置该 UV 相位；世界/目标/传送重置则清零。length 与 lifetime 同时裁剪实际段并在边界插值，未来时间点裁剪到当前绘制时刻。repeat 按弧长乘 uvRepeat 加 uvOffset，stretch 将当前可见轨迹映射到一段纹理并忽略 uvRepeat；uvOffset 可正可负。每跨一个纹理周期先分割四边形，再用 [0,1] UV 映射到选中的纹理帧，不能仅对端点取模造成跨帧插值。taper 在当前可见尾端为零宽、头端为完整 width，fade 按样本年龄/lifetime 衰减。实际顶点和面成本包含全部层及平铺产生的细分，在顶点数组生成前检查限额；失败不得静默截断已配置样式。

Ribbon 几何/采样配置使用不可变值及部分更新；宽度、长度、寿命和重复数为有限正数，偏移为有限有符号数，taper/fade 只接受布尔值。sampleDistance 可为零，sampleInterval/teleportDistance 必须有限且为正。读取固定 Lua 原始字段，不调用作者元方法，也不遍历任意长度配置表；返回配置为独立副本。缓冲可额外保留一个采样间隔供寿命边界插值，可见部分仍严格按 lifetime 裁剪。配置解析与几何预检只能证明相应覆盖，真实画面仍须独立验证。

当前 Lua 入口为 `setRibbon(table)` 配置、`setRenderMode("RIBBON")` 选择模式、`getRibbon()` 读取副本、`getRenderError()` 读取最近提取错误；未出错返回 nil。Ribbon 的 lifetime 是历史保留时间，粒子自身生命周期仍由既有 setLifetime 控制。emissive 映射已有 setEmissive；light 为独立动态光源的布尔/配置表别名，默认关闭，各模式和分层共用同一份光源。选择 Ribbon 后修改层列表保持该模式；共享粒子位置/寿命和轨迹，各层只影响材质。冻结状态走原生粒子任意四边形缓冲及原生材质绘制路径，重放不重复采样。先整次扣除包含全部层的顶点与面数，拒绝不花额度，成功提交后同帧移除不返还，下一帧补充。GPU 运行及画面证据以进度记录为准。

新增后端 `[outfit.limits]` 默认/客户端安全硬边界：max_custom_particles=512/4096（穿戴者存活总数）、max_particle_layers=4/8（每粒子）、max_ribbon_vertices=4096/32768（穿戴者每帧，含层）、max_particle_callbacks_per_tick=64/1024、max_particle_callbacks_per_frame=32/512；均允许 0 禁用。动态光源继续沿用 max_dynamic_lights 等已有键。缺省向后兼容，全部实例汇总，旧权限/速率/复杂度仍共同限制；阶段三必须另证实际消费与归还，契约本身不表示执行已实现。

CustomParticle 运动入口为 `setAcceleration(vec3 | x,y,z)` / `getAcceleration()`、`setMotion(table)` / `getMotionMode()` / `getMotionError()`。加速度单位为方块/tick²，在 free 模式每 tick 先加到速度，再执行原生重力/阻力/物理；向量复制，返回值也为副本。内置模式每 tick 定成本求值，不运行 Lua 回调、不随绘制 FPS 推进；模式替换重新从当前 age 计时，不重置粒子寿命。显式曲线开始时定位到其 t=0，失效配置先拒绝，原运动不变；更换运动清空 Ribbon 历史，避免把不同曲线接成长线。

| mode | 设置与行为 |
| --- | --- |
| free | 原有速度/重力/物理与新增加速度；切回时保留上一 tick 的速度和原物理配置 |
| orbit / helix | center 为世界坐标（默认配置时粒子位置）；axis 为有限非零向量（默认 Y）；radius 默认 .5 且大于零；angularVelocity 为度/tick（默认 15，可负）；phase 为度（默认 0）。helix 的 rise 为方块/tick（默认 .05，可负），orbit 不沿轴推进 |
| bezier | 四个世界坐标 Vector3 组成的密集 points 列表；duration 默认 20 tick 且有限正数。loop 默认 false，到达末点后停留至普通生命周期结束；true 按 duration 循环。控制点复制为不可变值 |
| tracking | target 为固定 Vector3 或现有 EntityAPI 句柄；speed 默认 .1 方块/tick 且有限正数，不越过终点。实体必须在粒子世界当前加载，持续匹配对象身份、ID 和 UUID；不重绑定被复用的 ID。onTargetLost 默认 remove，也可 stop，后者冻结并立即释放目标引用，原生寿命照常到期 |

内置轨迹为显式位置控制，不叠加 free 模式的加速度/重力/碰撞；原生移动中“碰撞后停止”的状态也不能阻止曲线位置更新。free 模式继续遵循原生碰撞状态。数值溢出时只移除该粒子并记录 NON_FINITE_MOTION；目标失效记录 TARGET_UNAVAILABLE，正常无错为 nil。只接受固定参数/已有句柄，不接受作为轨迹或目标的 Lua 函数；高级回调仍需后续次数与指令预算实现。

`setRotation/getRotation` 使用角度，`setAngularVelocity/getAngularVelocity` 使用度/tick；值必须有限。Billboard/Layered 是绕视线的原生 roll，Ribbon 是绕轨迹切线旋转横截面，默认零仍朝向摄像机。角速度跨 360 度时保留本 tick 的正确插值方向，返回角度按 360 取余；转动不改变 Ribbon 的弧长 UV、预算或相邻段共享边界。真实轨迹/转动画面与性能仍独立验收。

高级回调入口为 `setTickCallback(function | nil)`、`setFrameCallback(function | nil)` 与 `getCallbackError()`；nil 注销，设置方法可链式调用。回调参数为 `(particle, delta)`，tick 的 delta=1，frame 为提取时的 partial tick。tick 回调在内置运动/原生 tick 前执行；frame 回调在粒子引擎提取前执行，整帧只派发一次固定存活快照，期间新建粒子的 frame 回调等到下一帧。相同粒子在同 tick/帧只尝试一次，先标记再执行；回调替换/自移除/重入或保留帧重复绘制不能重复执行。移除/过期/清理解除函数与预算引用。

调用总次数按穿戴者、tick/frame 两个独立账本计入后端上限；零值拒绝启用，正常数量超限只跳过本次回调，粒子运动/寿命继续。次数在真正执行前扣除，同 tick/帧移除再注册不返还。每次实际执行还必须有现有 TICK_INST 或 RENDER_INST 余量，并经所属 FiguraLuaRuntime 的 VM 钩子执行；副本共用原装扮指令预算，普通 Avatar 显式启用回调后才绑定粒子 Lua 账本，个人计数重置不退款。普通脚本/init 中途首次注册也保留外层 VM 钩子并计入外层全部成功或失败指令，嵌套调用只计费一次。MC tick 开始时推进回调时钟，避免在原生粒子 tick 与随后 Avatar tick 之间重置额度；未绘制主体也能在新 tick/帧恢复正确额度。

超限分别报告 CALLBACK_TICK_LIMIT / CALLBACK_FRAME_LIMIT / CALLBACK_INSTRUCTION_LIMIT；无活动 Lua 所有者为 CALLBACK_OWNER_INACTIVE，异常为 CALLBACK_ERROR；正常为 nil。VM 异常沿用现有脚本失败清理，不能中断粒子引擎或停用其他执行实例。正式基础 Avatar 离开装扮作用域后的持续绑定/协商限制仍随既定阶段五正式运行时接通；当前受控同装扮副本与普通 Avatar 用例不冒充这一完整应用路径已通过。隔离预览通过自己的 scope 派发回调；装饰 Lua 已开放 particles，全局 host/net/world/file 等权限边界不变。

用户追加：粒子 `dynamicLight` 独立开关默认 false，`lightLevel` 默认 15（0—15），`lightRadius` 默认 7.5 方块；与 full-bright/emissive 无绑定。光源影响实际邻近世界/实体渲染，跟随粒子位置，不改服务器光照数据。后端 `[outfit.limits]` 配置 dynamic_lighting_enabled（默认 true）、max_dynamic_lights（每穿戴者默认 8，安全边界 32）、max_dynamic_light_level（默认/最大 15）、max_dynamic_light_radius（默认 16 方块，安全边界 32）。移除/过期/禁用/重载/传送/世界切换/断线时按实例清理。阶段一仅锁定参数/预算，阶段三实现渲染及设置项；真实明暗场景验证记 MANUAL/PENDING。

动态灯 Lua 方法为 `setDynamicLight/isDynamicLight`、`setLightLevel/getLightLevel`、`setLightRadius/getLightRadius`；设置方法可链式调用。亮度必须为整数，半径为有限正数且不超过 32。Ribbon 的 `light=true/false` 控制同一开关，`light={enabled, level, radius}` 部分更新；表的 enabled 省略时视为启用，读取返回独立表。关闭时可准备合法硬边界内的设置，启用及运行中必须通过后端当前开关/数量/亮度/半径校验；请求超限明确拒绝，不暗中缩短半径或降低亮度。配置收紧使现有光源不再合法时停用该源，保留粒子本身的普通寿命。

原生路径对世界方块/流体的 LevelRenderer.getLightCoords 及 EntityRenderer.getPackedLightCoords 叠加径向衰减的客户端方块光，多个源取最大值，保留原天空光和更亮的原生光。RenderSectionRegion 通过自己的 level 访问器匹配世界，未知/其他世界不受影响；不调用服务端光引擎写入。不可变分区索引快照供异步区块编译查询，查询只访问本节候选源。区块使用稳定 tick 位置，移动按逻辑 tick 合并标记旧/新范围重建；实体使用每帧插值头端位置，多次渲染不重复标记地形。源范围加一个方块的采光边界，单源安全最大半径下不超过 216 节；零亮度无地形重建。设置变化/移除即时发布新快照和相应重建请求，批量实例/世界清理合并处理。

当前自动证据为光场数值、生产采光钩子方法、区域世界识别、原生粒子生命周期与地形标脏调用记录；尚未用真实游戏窗口证明 Mixin 变换、区块重建完成、shader 输出及帧时间。上述实际画面和性能保持 MANUAL/PENDING，不能用自发光截图或纯光照数值代替。

创建 `+` 位于形象集，筛选 `+` 位于搜索区，tooltip 不同。三个菜单项：Figura 原向导；YSM 完整复制模板后只改 metadata.name/tips/authors，保留 credits/license；装饰写 cosmetic.json + 根名固定为 Cosmetic 的模型模板 + PNG + Lua。九类分类、左右目标、拖尾单空骨骼例外及 SVG→PNG 资产严格依计划第 6.4 节。失败只清本次新建目录，源资源 hash 不变。

`protocol/outfit-v1/template-manifest.json` 固定 ysm-default-1 的 42 文件 SHA256/字节数与 28 个 main 基础动作白名单。手部动作按 arm 渲染域精确选择，fp.arm 的 parallel 不自动注入。作者动作/显式空动画/禁用优先；角色映射先精确骨骼再显式角色，缺骨不猜测。有效 clip 表须携带模板版本/结构签名；不能将扩展索引塞入旧协议。最小辅助表达式依赖闭包由阶段二按真实片段解析锁定，不能整表自动播放。

隔离粒子生命周期已接线：LibraryPreviewBinding 为自己拥有的预览 Avatar/装饰分配 PreviewParticleScope。每个 scope 使用私有预算 UUID，不能因共享玩家 UUID 与真实装备或其他预览争用粒子/轨迹/回调计数。已存在世界粒子的 Avatar 不能被收编；绑定前创建的粒子句柄不能借新 scope 进入世界引擎。预览的 CustomParticle 仍用原配置/运动/生命周期及纹理租约，但由 scope 逐 tick 推进，世界回调/世界 Ribbon 提交跳过它；局部 frame 回调需由自己的预览绘制路径调用。运动不查询真实碰撞，采光不查询真实世界光照；动态光开关和数量在 scope 内验证与占位，不注册 ParticleDynamicLights 世界光场。

预览重载/清理以 Avatar 对象身份清所属粒子，不按相同玩家 UUID 清世界粒子。组合退休立即注销所属发射者，即使保留帧还没完成也不能重新生成；窗口/世界会话关闭清整个 scope，已提取帧仍依纹理租约寿命释放。原生粒子工厂可能在构造/推进时产生二次世界副作用，因此隔离 scope 不调用这些工厂：保留可链式配置但不存活的 NoRenderParticle 句柄，isPresent 返回 false；真实世界中的旧粒子工厂行为不变。

GUI 粒子使用 PreviewParticleFrame 冻结提取后的原生 PARTICLE 顶点。Billboard 调用原生旋转四边形写入器，Ribbon 复用世界网格/分层 UV 写入器；提取时沿用面数/顶点预检和纹理帧租约。已提交 EntityRenderState 捕获实体原点，并只缓存一次该视图的几何；延迟重绘不能再次采样、执行 Lua 或扣预算。朝向使用视图纯旋转，外层模型矩阵负责 PiP 缩放/Z 翻转与面板偏移。Figura 独立预览直接绘制，原生实体预览经 custom geometry 队列按作者层号提交，均复用原粒子 pipeline/纹理采样器，不向世界引擎提交。

绑定隔离 scope 的组合只在 FIGURA_GUI 帧采样控制器轨迹，使用 scope 的私有轨迹额度；原世界组合仍只在原世界模式采样。保留帧重放不追加历史，退休后不能复活历史；linked Ribbon 读取同一份只读轨迹。当前自动证据覆盖原生顶点格式、坐标/旋转/Z 翻转、分层提交和独立控制器源；真实粒子预览像素仍为 MANUAL/PENDING。

LibraryPreviewSession 使用专用工厂：独立模型采用私有默认粒子速率权限，当前玩家副本保留原权限包并在异步 load 前标记预览粒子归属。绑定 scope 前不得激活自定义世界粒子，原生粒子工厂也不调用；准备失败/取消、排队 clean、资源重载和重复清理不按共享玩家 UUID 清世界粒子。原 createLibraryPreview 评估器工厂仍保持零粒子权限。生产 Avatar 初始化队列在 binding 建立后运行作者脚本，装饰的 particles API 服从原速率权限、实例归属及同装扮预算。

YSM 纹理名只从已加载包内的 texture_entries、当前原生纹理和既有 GUI 图像表解析；禁止外部路径/URL 读取。当前原生纹理直接复用；非当前纹理首次使用时创建 FiguraTexture，按同一已加载数据对象缓存，包内 ID/路径别名共享句柄，逐帧提取不重新解码。运行时退休关闭缓存，存活粒子及提取帧通过既有租约延迟最终释放。自动测试覆盖 Figura/YSM 原生动画通知到作者 Lua/newCustomParticle 的预览和世界两条路径、包内纹理别名、GPU 资源释放、副本隔离和撤销；不替代真实客户端画面或阶段五授权跨端验收。

真实 GUI/双客户端/动态渲染/性能项为 MANUAL/PENDING；数据、HTTP、迁移和矩阵测试各自只证明实际覆盖内容。

基础资源来源附着于成功准备的 Avatar，发布时与该运行时一起切换；待加载路径、界面选中项、装备别名和展示状态不能代替来源证据。重新 load/clean 清除来源，替换 NBT 对象使旧绑定失效。正式模型库启用保留已授权 origin、资源 ID、固定修订、原始正文 hash 和格式，原有 CAS/身份复核及缓存规则不变；没有权威映射的旧缓存加载保持来源未知。

预设依赖准备由 OutfitPresetResources 按固定 ResourceRef 去重并顺序读取，复用现有模型工作线程、模型库 preview 授权/缓存和本地原生编译。云端基础必须为 AVATAR，实例必须为 ACCESSORY；原始 hash、格式、origin 和修订不能被当前最新版替换。一个失败不隐藏其他依赖的结果，Issue 关联基础标记及所有受影响 instanceId；只有依赖全部可读且来源会话仍有效时 ready=true，该标记不代替骨骼/挂载校验或正式应用。读取结果每次返回独立 NBT，操作关闭/账号或编辑代际退休后不可继续取数据，取消同时关闭正在传输的句柄。

设备本地预设使用数据根目录 outfit-presets/<UUID>.json，保存 schema、名称、独立预设修订和 OutfitConfig，不占用模型缓存后缀、不内嵌模型。更新检查 expectedRevision，写入采用原子替换；配置复用 OutfitCodec/协商预算，数量使用 maxPresets。坏文档在列表中保留明确错误，不静默隐藏或覆盖；导入仍由 OutfitDraft 重写实例/虚拟 ID 和内部引用。衣柜已有本地/个人云端来源、命名保存/覆盖、分页列表、独立预览和导入草稿；封面和投稿仍未完成。

个人云端预设使用现有经过认证的本人 outfit-presets 接口，与社区资源投稿分开；配置可以保留本人设备的本地资源 ID，但不会使其他设备自动拥有依赖，不上传源路径或模型正文。CloudOutfitPresets 在 LibraryClient 的受限 worker 上解码并检查文档 ID、正整数修订、名称/配置预算；保存确认还核对修订、名称及配置。保存后响应失败可显式重试，operationId、presetId、expectedRevision 和完整名称/配置保持不变，后续表单修改不进入该次重试。切换来源/退出关闭旧句柄；失效账号不能通过旧导入按钮使用已准备的个人云端文档，即使文档只含本地依赖。

装饰投稿使用 cosmetic.json 目录/ZIP 的原生 Accessory 正文，模型格式独立保留 FIGURA/YSM_PLAIN/YSM_ENCRYPTED；目录的上传文件描述使用 ZIP 后缀，不将用途编码成模型格式。投稿可修改 category、cosmetic_anchor、default_transform；作者及能力声明保持原值。完整源清单与规范化 accessory_metadata 分别冻结，后者不含公共作者字段，并显式填齐后端能力 DTO；两者解析后的能力语义须一致，空 editable_bones 可以在后端响应中省略。编辑写入不可变分发副本，最终正文、长度和哈希对应同一声明；重试沿用冻结输入。预览使用独立 Cosmetic 组合和既有权限/预算，不装备或广播。提交及发送前按当前协商预算验证默认变换和声明数量，封面继续复用既有服务。加密包仅为明确的装饰对象清单接纳包装脚本/声音/资源，原生解密字段及模型/贴图明文限制不变。

Outfit 社区投稿的后端分支为 POST /api/library/v1/uploads/outfit：共同目录和既有策略/封面/入集表配合 library_outfit_contents 原子发布，模型格式为空，不创建模型正文修订。依赖必须是当前源站可获权、类型及固定修订/hash/格式匹配的云端资源；不自动映射、上传或公开本地/外站/私有依赖。操作 ID 重试返回原结果；GET /api/library/v1/models/:id/outfit?revision=N 按当前 preview 权限返回固定配置，不产生正式下载回执。

客户端仅在 outfits_v1.publication=true 时开放第三类用途，缺字段按不支持处理。投稿读取小型配置/已保存预设，使用既有依赖准备器及独立组合预览；无法分发的本地/外站引用不隐式发表。JSON 发表请求附带当前规范化源站，重试固定全部输入/操作 ID；已确认发表后的封面走原图片接口、原操作和原 CAS，失败不重新发表配置。取消封面重试保留已发表资源并明确提示；来源/会话退役不发布旧准备结果。共同卡片的配置获取/依赖预览与入集消费仍待阶段四后续检查点，不以已接投稿表单代替完整资源消费验收。

后端 Outfit 正式获取使用既有传输凭证、发送状态、摘要确认、下载计数与集合入集接口，响应 application/json 和实际 UTF-8 字节长度/hash，不产生模型文件格式或正文修订。预览不得确认或入集，正式入集依赖当前账号/资源修订/策略的已完成下载回执；每段及确认时复核权限。schema 8 的回执仍以共同目录为外键，并约束精确模型正文或配置修订的存在及引用期间的删除/改号，原模型回执/计数/去重数据保留。客户端不得用压缩 NBT 或 .oma/.omc/.oms 假装承载该 JSON；客户端消费流程仍在阶段四接续。

本地库存及预设依赖扫描共用原模型 worker；每次扫描先合并设备索引中已有的 UUID，使保留的旧库存对象不会替换另一预设扫描新分配的 ID。不改资源正文、UUID 含义或引用版本，也不引入另一套库存或缓存。

客户端共同获取入口按 OUTFIT 返回独立 configuration/outfitLimits，nbt 为空；每次读取取得当前配置能力/预算与资源权限，JSON 的媒体类型、UTF-8、长度/摘要和配置校验均通过才可使用。缓存独立位于 outfit-configurations/，数据后缀 .configuration.json，索引 .json，使用既有缓存字节保护；预览与正式正文/待确认回执分离，丢入集响应按原完成操作恢复。共同目录预览复用固定依赖准备器，并在独立实体上准备基础/装饰组合；共享普通/展开预览面板给出每个失败资源及受影响副本的滚动清单，清空/退出拒绝旧代际。该入口不注册正式玩家模型或持久权限项；账户形象集导入和管理/回收配置消费仍在阶段四接续。

管理员回收项包含独立 resource_kind，旧记录缺省 AVATAR；LIBRARY/OUTFIT 的 model_type 为空，其他模型保持原格式。原 LIBRARY_RECYCLE 预览路由支持 application/json 配置正文，HEAD/GET 及逐段复核当前管理员、回收状态和摘要；客户端按当前配置预算读取并用原依赖准备器/独立实体预览，清空退役旧请求和组合。恢复保留配置，永久清除擦除配置正文、保留历史回执所需的修订身份，已恢复/清除的旧预览不可再使用，原操作重放不重复变更。Outfit 没有原生模型格式，公共卡片不绘制 Figura/YSM 格式徽标。

形象集中的 Outfit 选择复用独立配置获取/预览，不修改草稿或直接装备；选中时原候选按钮变为显式导入入口，只有当前完整配置预览可导入。导入复用原草稿快照、ID 重写和资源登记，保留当前装备可见性，成功后进入装扮编辑；取消恢复原草稿/基础。准备和发布之间检查选择、账号、客户端、预览文档及草稿代际，清空或退出后旧结果失效，不把配置当 Avatar 候选。

OutfitPresetsScreen 复用 AbstractPanelScreen 的背景、上下文菜单、tooltip 和输入，使用自己的 LibraryPreviewSession；父衣柜的未应用草稿/预览会话保留。保存使用当前衣柜草稿，选择预设仅预览，来源未解析的临时基础引用不能保存。只有固定依赖和原生组合准备都成功才允许导入；失败项按基础或每个副本的 ordinal/instanceId 显示，原文档和草稿保留。导入后取消恢复原基础 NBT/来源标记；关闭、账号或草稿代际变化拒绝旧控件/异步结果，展开预览的放弃同样结束该会话。自动控件派发与确定性字体度量不是实际窗口、字体或 GPU 的验收证据。

本地基础资源的设备索引为数据根目录下 avatar-resource-ids.json：保存规范文件/ZIP 内目录 URI 到稳定 UUID 的映射，URI 不进入 ResourceRef 或预设。目录/普通归档按排序后的资源名与原始字节计算 hash；加密 YSM 按原始密文计算 SHA-256，不为标识而解密。加载前捕获、原生编译后复核，源变化拒绝该次发布。索引不可用可继续旧模型加载，但来源保持未知；未知来源的编辑器临时引用不能作为可重载依赖。重载 ZIP 引用时只关闭本次拥有的文件系统，借用的库存文件系统保持开放。此来源映射不表示预设依赖准备、保存/导入或正式装扮应用已经完成。

正式驻留接口由 UserData.publishOutfit 接收已核对回执与正式准备的候选，在客户端线程发布 OutfitState(configuration, base, composition)。调用方仍负责服务端 CAS、回执当前性及会话权限；接口检查加载代际、穿戴者、固定基础来源及配置内容（仅允许服务端分配 revision），拒绝比驻留配置更旧的 revision。基础对象相同不清理或重载其 Lua/YSM；失败保留旧驻留且只清理本次拥有的候选。旧组合沿用 Frame 租约延迟释放，原单形象替换/clear 会退休组合；正式 tick 在同一组合预算中只 tick 基础一次，原版基础允许 base=null。当前仅有实际 UserData 原生 CPU 组件证据，尚无衣柜 Apply→CAS→激活或世界渲染整链路证据。

OutfitApplyService 是显式应用控制器：冻结配置和操作 ID，固定来源汇总成功后在原模型 worker 构造正式 OWNER 组合及必要的新基础，相同固定基础来源借用驻留 Avatar；源声明、实际骨架、作者导出/编辑和当前动态预算继续校验。提交前及回执后复核能力/预算，CAS 成功还须当前 revision 等于回执、当前配置完全相同且当前账号/装备修订匹配。有效结果经捕获的 OutfitLoad 用户/代际和原连接选择守卫发布；失败或冲突不替换驻留，冲突只返回待 UI 处理。会话内重试重新鉴权/准备但保留同一操作与 CAS 配置，防止丢回执导致重复提交。仅有实际 Java 控制器、HTTP 契约夹具与原生驻留的集成证据；真实 Rust Listener、世界画面和跨端同步仍未据此验收。

衣柜的正式草稿基线来自当前服务器整套配置与真实 revision，固定依赖读取后再复核配置相同才安装；失败保持原编辑基线，原版旧账户的 revision 0 单形象捕获路径保持，未知来源仍不能 Apply/导出。固定来源的 NBT/作者声明可供原预览与编辑器读取，不要求原本地文件存在。正式 Apply 与重试分别进入新操作/相同冻结操作，成功后重新读取当前编辑基线；成功提交后的刷新失败明确提示已应用，不能假报回滚。Conflict 保留编辑和原配置，禁用继续覆盖，用户确认放弃未应用编辑后才显式刷新，不自动合并。取消仅取消未完成操作和草稿编辑；已提交服务端状态不会被宣称撤销。连接/账号/世界/页面及编辑代际继续约束异步结果，来源错误在原预览滚动区逐基础/副本展示。能力未协商或 Apply=false 时正式入口不可用，离线草稿仍沿原预览/预设路径。

OutfitRenderBinding 在实体状态提取时冻结正式 OutfitState、实体和组合 Frame；若状态属于隔离预览则完全沿原 LibraryPreviewBinding，预览无基础/无组合也不能回退到正式装扮。AvatarManager 的状态查询优先取已捕获基础/实体，原版正式组合允许基础为 null。世界及第一人称共用原组合原生矩阵/姿态与绘制入口，不修改 YSM 本体手部或动画行为。UserData 替换/清空时保留被本帧引用的旧基础，组合继续使用原 Frame 租约；GameRenderer.render finally 在普通/无GUI/异常结束路径关闭正式帧并清理已退休基础。编译和 CPU 原生生命周期回归不替代实际 Mixin 注入、GPU 或世界/双手画面验收。

正式全局事件在组合 Lua/Molang 预算内先执行基础，再分发到可信控制器当前已挂载且已初始化的存活副本；停用、未解析或失败副本不因此启动作者脚本。先结束活动集合查询再调用作者，嵌套声音/资源分发不重入控制器。正式 WORLD_RENDER/POST_WORLD_RENDER 沿原全局入口，逐视图保留 RENDER 和动画准备，DRAFT 仍沿原预览行为。主 ActionWheel 对正式驻留采用原组合投影与实际所有者；热栏、滚轮、释放均接通，基础回调在没有可用装饰动画页时也不能回退到无组合预算的入口。无正式驻留时保留原单形象入口。正式控件/持久值与跨端状态/事件尚待阶段五后续接续。

正式控件沿原 AvatarControlsScreen.open 进入捕获当前本地 OutfitState 的页面，草稿专用入口保持优先。只编辑当前基础或启用、存活的 OWNER 副本；观察者/退休副本不得回退到普通可编辑页面。基础/实例值和按钮回调共用组合预算，控件定义与实际所有者一致才执行。账号、世界、连接 epoch、驻留配对或页面生命周期变化使旧回调失效；副本值按原 CosmeticInstanceContext 隔离，装饰不进入旧按模型共享的轮盘布局编辑。本入口不承担尚未接通的跨端状态持久化/恢复，不能将上下文值当已提交服务器的配置值。

OutfitObserverSnapshot 是授权渲染投影，来源集合必须与投影配置的基础/启用实例完整固定引用及用途相符，同资源不同修订分别保留。资源 URL 必须精确匹配穿戴者、配置修订与资源 ID，不接受外站地址；读取不会获取目录权或触发入集。revision 0 标记旧账号降级条件，现代隐藏配置不能回退原基础。OutfitObserverSources 使用已有保护缓存；即使命中也在解析前后重新鉴权并比较当前完整投影，配置或权限变化拒绝旧结果。正文缓存按源站/服务实例/观察账号/穿戴者/固定来源与摘要隔离，配置修订改变不要求重复下载未变正文。该读取组件尚未代表观察者正式驻留、事件订阅或跨端状态恢复已接通。

OutfitObserverService 的调用方必须提供当前连接/用户映射/订阅身份守卫；服务同时约束会话、世界、远端穿戴者和原生加载代际。授权来源只用于 OBSERVER 原生组合，基础保持非 host，发布前重新核对完整投影与最终上限。相同固定基础借用旧运行时，失败不清理借用基础；新基础与组合遵守候选所有权。当前隐藏可退休旧驻留，较旧可见/隐藏快照不得影响较新驻留；LEGACY 结果交回原 profile 协调器。该组件尚待连接订阅调度、策略/上限通知和状态/事件接线，不替代这些阶段五要求。

NetworkStuff 对已订阅且有实体的远端用户调用观察者服务；原 profile 的现代分支仅更新徽章，只有明确 LEGACY 才加载其旧基础。实体迟到时由 tick 接续；刷新先退休旧请求代际，旧 profile/body 不能覆盖新组合。观察者记录还核对用户映射、连接、世界、实体和订阅身份，离开/取消/服务退休释放正式驻留；显式刷新只取消候选，保留可复用基础。临时请求失败20 tick后恢复，tick 每次最多补充两个加载，永久格式/摘要/权限错误不自动循环。独立 outfit-events、策略/上限通知及实例状态/控件恢复仍须接通，不能用现有旧 changed 通知替代。

### 阶段五：独立修订通知线协议（2026-10-24）

后端新增 `GET /api/library/v1/outfit-events` WebSocket，子协议 `omnimorph.outfit.v1`，使用现有 Figura Bearer 或 Protobuf scoped Bearer/epoch 请求头，要求规范 `x-omnimorph-origin`；拒绝 query 参数，token 不进入 URL。此检查点只实现配置失效通知，尚无实例状态写入，客户端未接入，`events` 仍 false。

- 客户端文本 JSON 字段为 `schema_version=1`、正整数 `connection_epoch`、`type` 和可选 `owners` UUID数组；拒绝未知字段、重复 UUID、超出128项及超过16KiB的消息。连接 epoch 不超过有符号64位正整数；Protobuf HELLO 必须匹配已有认证 epoch。首帧10秒内必须为无 owners 的 HELLO，后续支持 SUBSCRIBE、UNSUBSCRIBE、无 owners 的 HEARTBEAT。
- 服务端每帧含 `schema_version`、`connection_epoch`、连接内递增 `sequence` 和字符串 `server_epoch`。既有随机 u64 服务器 epoch 作为不透明十进制字符串，避免 Java 有符号 long 溢出。HELLO_ACK 另含现有 `instance_id`，不创建新身份。
- SUBSCRIBE/UNSUBSCRIBE 回复 ACK（command 指明操作）。初始订阅顺序直接发送每个 owner 的 CHANGED，不把128项初始通知塞进32项队列；重复订阅可重新取得当前修订。CHANGED 仅含 owner、revision、equipment_revision，不携带资源名称、固定引用、脚本或正文。发送前读取当前观察者投影，原认证在读取前和发送前均复核；通知后由 HTTP 授权快照决定可见内容。
- 复用 EventHub 的提交后/策略变更失效广播、SessionRegistry 的每账号8连接限制、每连接32消息/256KiB共享队列及每账号32消息/秒、256KiB/秒预算。只转换 AvatarChanged，不转发旧管理字节、Figura ping 或冻结 YSM 消息。队列溢出或广播缺口要求 RESYNC_REQUIRED（code=SNAPSHOT_REQUIRED）并关闭；普通无效命令返回 ERROR，其他可恢复错误返回 RESYNC_REQUIRED。封禁/认证失效直接关闭，不能借错误响应继续泄漏消息。
- 每20秒认证心跳并比较当前后端 outfit.limits，变化发送 LIMITS_CHANGED，客户端需重新读取能力/预算及当前快照；60秒无有效存活响应关闭，单次发送最多等待5秒。旧已在途帧可能先到，客户端仍必须在缺号/重同步/断线后重建快照基线，不能仅依赖事件保存状态。连接退休只删除自身注册及订阅 worker，不退休原主连接的 Protobuf 凭据。

该协议的真实后端 WebSocket 夹具已有7项通过；完整 Java 通道、自动重连/基线恢复和受限实例状态仍待阶段五后续检查点，不能以本段声明其完成。

### 阶段五：Java 修订通道组件接续（2026-10-24）

LibraryAccess 新增固定同源 outfit-events 连接，原 wire adapter 的认证闭包只在内部为握手写请求头，不向 GUI/脚本公开凭据；每个 access 最多一个事件连接，拒绝/退休的握手释放名额。CloudSocket 增加明确文本模式，保留旧二进制构造与冻结消息语义，共用32项/256KiB写队列、分片大小、5秒写超时及实时源站权限检查。

LibraryClient 持有 OutfitEvents，close 时退休。服务由客户端 tick 驱动，仅在 capabilities.enabled、configuration、events 同时支持时连接，缺失能力不连接。文本每帧16KiB；客户端收件队列最多132帧/256KiB，容纳完整128玩家初始通知及 ACK，每 tick 最多派发32帧，超限恢复。所有业务回调发生在调用 tick 的客户端线程；过期建连代际不能继续投递。

解析严格验证字段、数值类型/64位范围、schema、连接 epoch、逐帧 sequence、字符串 server epoch 和 HELLO 顺序；新 HELLO/断线/缺号/实例不符/RESYNC_REQUIRED/超时重新建立通知基线，采用1～30秒有界退避。服务不按 revision 去重，同修订策略失效仍发 changed 回调；未知 owner（当前集合外且非本人）不驱动加载。HEARTBEAT 按20秒周期发送，收到 HEARTBEAT 不立即回发，避免命令回复形成循环。LIMITS_CHANGED 发专门回调，由协调器重新读预算。

身份沿用原命名空间：Figura 的 LibrarySession.instance 为 `figura:<origin>`，通过已有认证和同源握手接受服务端实例；Protobuf 为 `<origin>/<instanceId>`，与 HELLO 的服务实例组合后核对，也接受直接实例标识的既有组件会话。每次重连都要求重新取授权快照，服务自身只输出失效/恢复回调，不假称已安装正式配置。

原 Figura/Protobuf 适配器→真实 Rust Listener 已验证完整128玩家通知、HTTP 提交后的失效、实际事件 socket 断开及超时/退避/重新握手/通知基线、原主连接认证保留和退休清理。后续正式协调及实例状态已按下节接通，S5 自动门禁后公开 apply/events 能力。真实 Minecraft 双客户端仍 MANUAL/PENDING。

### 阶段五：正式实例状态（2026-10-24）

- `GET /api/library/v1/outfits/players/{owner}/instances/{instance}/state` 按当前观察者投影读取；`POST /api/library/v1/outfits/instances/{instance}/state` 仅写入认证本人。复用原 Bearer/epoch、origin、库 CONTROL lane 和共享账号预算，不新增认证/协议框架。
- JSON 顶层为 `outfit_revision, instance_id, resource, revision, state`；state 为 `generation, sequence, controls, lua_state, animation, template_version, template_signature`。animation 为 nullable 的既有有界 YSM M7 full snapshot Base64；模板字段成对为空或版本（≤64字节）与64位小写十六进制签名。固定资源/作者索引/content/schema 不相容时拒绝消费，不改冻结 v1 字段。
- 单实例 revision CAS；同 generation 的 sequence 连续，新 generation 从1开始；完全相同的上一请求可重放回执。保存到现有 metadata 表，配置 revision 改变回到声明基线；双副本分别存储。读写均在同一数据库快照检查当前资源使用/展示/观察权限。
- controls/Lua state 只接受布尔、有限数值、受限字符串，使用当前 `outfit.limits` 条目数与字符串预算；state 序列化≤64KiB。Lua 显式 `cosmetic_state:get/set` 声明 scalar，不遍历作者全局/函数/对象图；OBSERVER 输入不写回。
- 提交后发送 `STATE_CHANGED`（公共帧头加 owner/instance_id/outfit_revision/state_revision），不在 WS 广播正文。发送前重新授权；旧 Figura Ping/Protobuf 事件适配器忽略新业务事件。客户端按在途通知修订继续读取，旧回应不清除较新失效。
- 新驻留、断线/缺号/epoch变化后读取授权配置和实例基线；基线跳过历史一次性事件，正常增量仅在对应实例执行，粒子由本地授权事件生成。OWNER 动画继续本地时间线，持久 scalar 从基线恢复；OBSERVER 通过原 YsmSyncModel 应用动画/控件，不产生 OWNER 回传。
