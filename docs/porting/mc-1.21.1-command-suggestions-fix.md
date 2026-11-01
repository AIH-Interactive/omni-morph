# Minecraft 1.21.1 聊天补全 Mixin 修复

## 故障与根因

用户实际实例在打开聊天时无法变换 class_4717，失败回调为 Fabric CommandSuggestionsMixinFabric.addFiguraSuggestions。实际 mod 构建版本为 1.0.0-alpha.7+1.21.1-8c5a926。

完整日志在最终 InjectionError 前明确记录 LVT 不兼容：回调期望 String / StringReader / boolean / boolean / int / String / int / Collection，实际可用的中间 boolean 少一个。此前的全量 CAPTURE_FAILSOFT 仍受 defaultRequire=1 约束，未注入回调后最终崩溃；No refMap loaded 不是已证实根因。实际交付 JAR 的 method_23934 和 SharedSuggestionProvider 调用签名均与实例 Minecraft 字节码匹配。

## 最小修复及覆盖

- Fabric 只通过 MixinExtras @Local 绑定实际使用的字符串（ordinal 1）、最后单词索引（int ordinal 1）和候选 Collection，不再捕获整段未使用局部变量；不降低注入 require、不禁用表情补全。
- NeoForge 对应回调原本已采用这三个显式绑定，本次检查通过，无需修改其生产代码。
- common SuggestionsListMixin 不使用此类局部变量捕获；其 render 回调参数和 drawString ModifyArg 调用/参数/返回类型已对照真实 Minecraft 字节码检查通过。本次没有证据表明同一路径的 common 存在同一故障。

## 验证

- 新增 commandSuggestionsMixinSmokeTest，使用真实 Minecraft 方法、调用指令、LVT 和 stack-map frame 存活信息核对 Fabric/NeoForge 回调绑定，并核对 common 候选列表两个渲染钩子。
- 原 Fabric 回调在该检查中失败（build/tmp/command-suggestions-red.log）；最小修复后 Fabric、NeoForge、common 均通过（build/tmp/command-suggestions-common-green.log）。该字节码检查不冒充完整 Mixin 应用或游戏验收。
- Java21 全量测试编译、Fabric/NeoForge remapJar 通过，build/tmp/command-suggestions-green.log。minecraft-dev 对修复 Fabric Mixin 的分析通过；actionlint 通过。
- CI 基础构建增加此回归检查，避免只验证打包而遗漏该路径。
- 完整客户端聊天/表情 UI 人工验收：SKIPPED（MANUAL，用户要求跳过；未验证），不阻塞。

## 交付状态

修复提交 5f3aa264c989b73ce385f015c530e207b966346e 已推送当前 1.21.1 分支。[CI run 41294216406](https://github.com/AIH-Interactive/omni-morph/actions/runs/41294216406) completed/success，24/24 通过，两个基础构建均执行了新回归检查。六平台原生库、最终 JAR 五平台 Java21 JNI / Java25 拒绝检查和 Android ELF/导出/CPU 检查通过；未将 Android 设备 JNI 或游戏运行计为 PASS。

两份最终 JAR 已下载并核对源码提交、CI run、ABI、六平台库、哈希、许可与加载器元数据。另通过最终字节码核对，两加载器的回调均只含三个 @Local 绑定，未启用全量局部捕获，GPU 后端仍在。报告 build/reports/acceleration/command-fix-ci-artifacts.json、command-fix-final-classes.json；日志 build/tmp/command-fix-artifacts.log。

| 产物（build/distributions/1.21.1-chatfix/） | 字节 | SHA256 |
| --- | ---: | --- |
| omni-morph-1.0.0-alpha.7+1.21.1-fabric-mc.jar | 17190301 | 446f47faddd3cf9aceb041c476ba06d54629d489f8339484b4483f51ea66c00a |
| omni-morph-1.0.0-alpha.7+1.21.1-neoforge-mc.jar | 16792500 | 27751b974c44e922343ad4545a0a394b114e5160a2db494f0fc89f493d417ff8 |

本任务实施和规定自动验证完成，无剩余实施。版本保持 1.0.0-alpha.7；未启动/关闭游戏，未改写用户 mods。完整客户端聊天/表情人工验收按要求跳过，不阻塞。最终记录提交不改变上述 CI 产物构建源。
