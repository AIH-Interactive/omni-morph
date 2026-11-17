# 26.3 修复同步到26.2

2026-11-17。原分支26.2，起点a5bae220；原未跟踪.codex/保留。来源26.3固定9a7a394ab253962c351b0a97800c2cee02411c1b，三项装饰修复源dev e2962c33c7da36fa879b63759dcdf74e052966f1（实现c1084296）。不创建分支、不启动游戏、不修改其他版本。

## 检查点1：本版Mixin适用性与实际应用

26.2原有close()V已正确，不重复修改生产。Minecraft-dev MCP核对本版正常退出exitWorldAndClose调用close；shader源走ShaderManager.getShader(Identifier,ShaderType)->String，原注入为普通实例方法HEAD，26.3的PipelineCache构造器static修复不适用。

移植来源3a104563的双平台成品JAR真实Sponge/MixinExtras验证；另按26.2实际目标增加双平台ShaderManager检查。加载各自完整生产配置、实际Minecraft目标字节码，验证变换后原方法调用注入处理器及生产HTTP清理/Avatar shader源查询。未执行Minecraft静态初始化或启动客户端，不宣称完整Knot/FML启动或GPU像素通过。

mixin-initial.log四项实际Mixin应用通过，18秒。lifecycle-sensitivity-red.log为受控回归敏感性验证：临时将既有close()V改回destroy，双平台真实InvalidInjectionException，26秒、退出1；finally逐字节恢复，不提交临时回退。mixin-green.log四项Mixin+第一人称原生材质+HTTP生命周期全部通过，34秒、退出0。对应日志docs/sync-26.3-fixes/。关闭/ShaderManager生产实现与起点一致，无额外兼容层。

检查点1提交ea65211763fb14a7d7e49bb70e03f36a9942f3f4已推送并核对远端，之后开始检查点2。新增任务只在本地使用，生产打包不依赖它们，Actions不改。

## 检查点2：装饰修复已实施并验证

直接三方merge固定dev e2962c33（实现c1084296）；源码和测试自动合并，仅交接冲突按本版状态解决，完整冲突先备份至.gradle/sync-handoff-conflict-backup.md。保留全部26.2适配、依赖、关闭和ShaderManager实现，不整份套用26.3源码。

三项根因与修复：

- 本地文件缺失时，预览只查本地而不读取随形象发布的私有源。OutfitPresetResources在明确缺失时接入已有OutfitSourceService，继续验证账户/会话、服务端授权、哈希、元数据和受保护缓存；固定输入或已修改本地文件的错误保持原行为。
- 已准备装饰资源离开源预览后丢失，名称/作者默认设置也没有读取其NBT元数据。WardrobeScreen保留准备资源并通过draftMetadata读取cosmetic_json；实例名及作者默认设置继续复用原消费者。
- 死亡后同UUID玩家实体已替换，组合仍保留被移除旧实体。OutfitComposition在tick/提取时重绑相同UUID新实体，更新已初始化Lua玩家API，保留原运行实例和脚本状态，并清理旧拖尾。

### 本版RED/GREEN

1. accessory-red.log：使用三方合并后的测试与合并前ea652117生产源码，名称断言和私有源OUTFIT_PRESET_DEPENDENCY_MISSING均失败；28秒、两任务非零退出。三份生产源码先完整字节备份，finally逐字节恢复并检查。
2. respawn-red.log：保留资源/名称修复，仅临时使用旧OutfitComposition，实际初始化装饰脚本后将旧玩家标记KILLED并提供同UUID替身；断言组合未重绑而失败，28秒。finally恢复合并源码。它是实体生命周期夹具，不宣称实际游戏复活画面。
3. accessory-green.log：全部三方合并实现恢复，libraryPreviewRuntimeSmokeTest、libraryPackagePreparationSmokeTest、双端MinecraftLifecycleMixin/ShaderManagerMixin、firstPersonMaterial、performanceHttp共8入口全部通过，1m35s、退出0、32任务。包含名称、同UUID实体/玩家API恢复、原实例保留、Figura/加密YSM ACCESSORY真实回环HTTP及缺绑定/撤权后的缓存拒绝，和原有私有聚合/预览回归。
4. production-tasks.log：双remapJar dry-run2秒退出0，不含测试依赖；未改或触发Actions。双最终包CRC/嵌套依赖、26.2/Java25/loader依赖、全模板、新方法、Windows amd64/arm64 AVIF DLL、无测试/旧加速/Renderpearl类检查通过。最终生产代码提交9435863fd5e3e8011fd1adb107484fec2c45e753已推送并核对远端一致；双亲为ea652117和源dev e2962c33。提交后post-commit-packaging.log双包20任务全部up-to-date、3秒退出0；最终哈希记录于artifacts.json/artifacts.log。

一次自动审批拒绝未备份冲突手册的准备命令，命令未执行；先逐字节备份并整理手册、测试仅回退且完整恢复生产源码后，安全验证已通过。无待批准操作。其余日志中的创作失败/权限拒绝/损坏数据为断言覆盖的失败路径；既有PIP/scaleWidth/scaleHeight提示不扩大修改。

当前实施与自动检查全部完成，待当前合并提交推送和最终交付记录。除原.codex/外无新增用户资料变更。完整客户端启动、GPU/GUI画面、游戏实际复活与跨维度MANUAL/PENDING，未启动游戏，不作为受阻。

## 最终交付

两个检查点ea652117、9435863f已分别提交推送；本节与交接/成品清单仅为收尾文档，不改变生产包。保持原未跟踪.codex/。游戏完整启动、真实画面/复活/跨维度MANUAL/PENDING，不等待人工或据此标记受阻。当前可靠额度剩余45%，未达Luna阈值。无后续实施，不自行移植其他版本。
