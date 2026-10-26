# YSM 玩家资源激活范围修复

日期：2026-10-26。单阶段任务，自动验收已完成；游戏画面 MANUAL/PENDING。

## 问题与证据

用户报告 `refs/avatars/16_tactics` 相比参考项目多出胸前浅色枪和脚下青色条纹。
实际资源分别为 `gun` 和 `dingwei`，都由作者身体动画 `parallel0` 控制显隐。
先通过两侧 CodeGraph 对照，再用真实目录经过正式加载器和播放器建立 RED。

- 新格式读取器把所有 `.animation.json` 汇入玩家资源，包括箭矢和载具。
- 它还激活未在 `ysm.json` 声明的控制器；本模型备用控制器占据 `player.parallel0`，
  初始 `no_backpack` 没有动画且不是 builtin，阻止身体 `parallel0` 自动播放。
- 同名合并按骨骼数量和事件数量评分。身体 `parallel0` 得分 6，箭矢版本得分 24，
  身体显隐指令会被箭矢片段替换。两条路径独立存在，不能只修其中一条。
- 参考 `YSMFolderDeserializer.parseMainEntity` 读取玩家明确声明的动画/控制器；
  `ClientModelBundleAssembler` 分别组装玩家、投射物与载具；`ModelAssemblyFactory`
  在声明顺序上使用 `putAll` 合并作者动画。

## 已实施

1. 新格式玩家动画及控制器只激活清单声明的资源；保留已有显式别名字段和缺失资源警告。
   不再通过目录或约定文件名补入未声明的玩家动画和控制器。
2. 同名作者动画由后声明的资源覆盖，包括显式空动画，不再按内容大小评分。
   默认动画 fallback 的准备、空手部占位策略和显式禁用规则保持原实现。
3. 旧格式资源发现路径未改；子实体独立资源索引和装配未改。
4. 不改用户模型、默认动画资源、版本、后端，不添加模型名/哈希/作者特化。

## 验证

- RED：`build/reports/outfit/resource-scope-red.log`。真实目录加载后，声明范围、身体片段、
  builtin 槽、枪/定位条纹连续三次采样显隐、同名覆盖与显式空片段断言失败。
- GREEN：`build/reports/outfit/resource-scope-final.log`，`BUILD SUCCESSFUL in 1m 27s`，26 tasks。
  命令：`./gradlew.bat :common:ysmResourceScopeSmokeTest :common:ysmPackageSmokeTest
  :common:ysmDefaultAnimationSmokeTest :common:ysmMaterialNativeSmokeTest
  :common:ysmHandAnimationSmokeTest :fabric:build --console=plain`。
- ResourceScope 验证真实模型默认隐藏、变量开枪/关枪、定位条纹持续隐藏、箭矢资源保留，
  以及较小的后声明动画和显式空动画都能覆盖早先动画。
- Package 覆盖文件夹和带包装目录的 ZIP，确保未声明动画/控制器不激活；旧格式回归通过。
- DefaultAnimation、MaterialNative、HandAnimation 全部通过，后者包含实际末影龙娘/芙宁娜；
  Fabric 构建附带 LibraryPreviewRuntime 也通过。
- 中途新增变量切换断言漏捕获 ParseException，已修正测试回调；一次材质夹具失败记录于
  `resource-scope-fixture-failure.log`，其新格式清单未声明使用的动画，已补声明后重跑通过。
- JDK25，前台 PowerShell Tee build.log，立即检查退出码、读取日志；git diff --check 通过。

CPU 骨骼显隐证据不等于 GPU 画面或真实输入通过。用户重启客户端并重新加载
`16_tactics` 后确认默认不显示枪/条纹、作者枪开关有效，同时兼验持矛。
没有剩余自动实施阶段；不扩展修复历史独立失败项。
