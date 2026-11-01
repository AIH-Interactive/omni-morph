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

修复和自动回归完成，双加载器完整 CI 产物待构建。当前分支 1.21.1，版本保持 1.0.0-alpha.7；不自动启动/关闭游戏，不改写用户 mods。
