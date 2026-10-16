# M8 验收矩阵与测量边界

基线：M7 `9c48cea03b26ac52d9f540f637318bcb873041a1` 已独立推送并核对远端。M8 不改生产引擎/GUI/后端或源语料，只补稳定性验证、测量和交付说明。

`AUTO_PASS` 只用于所列自动证据。用户授权的人工项为 `SKIPPED/MANUAL_BY_USER`，需要后端的项为 `SKIPPED/BACKEND_BY_USER`，取消的 GUI 显示为 `SKIPPED/USER_UI_SCOPE`；这些均不等于实机通过。历史实现过程的待办由主计划各阶段最终记录及本表解释。

## 输入到清理的证据

| 层级 / 要求 | 输入→解析/装配→更新/消费→清理的证据 | 状态与限制 |
| --- | --- | --- |
| 发现与容器 | package/dataBoundary/baseline/localCorpus：目录、ZIP、内存源、改扩展名、路径冲突、取消/关闭；45个本地来源分类 | AUTO_PASS；Windows符号链接创建权限受限夹具仍按既有用户授权跳过 |
| crypto与压缩 | crypto：固定1/2/3向量、跨段、真实压缩Zstd块、坏头/长度/hash/padding/压缩负例 | AUTO_PASS；不声称任意未来/历史文件均兼容 |
| binary内部format | binaryInput/binaryFormat/binaryResources：16个非空边界fixture、显式无DWORD、RGBA/几何/关键帧/附属资源字节及预算 | AUTO_PASS；覆盖版本列表见兼容说明和固定manifest |
| 密文/内容身份/隐私 | payload/privateDiagnostics/localCorpus：输出过滤、压缩后检查、从原密文恢复、所有资源等值、匿名错误、源不变 | AUTO_PASS；Java字符串/GC擦除边界保留；用户要求的本地辅助截图不作为可分发载荷 |
| 几何/层级/挂点 | geometry/handLocator/deferredPose/playerTimeline/nativeMaterial：骨骼矩阵、左右手父变换、locator、透明/缩放隐藏、提交时快照 | AUTO_PASS（CPU/原生对象）；真实姿态/遮挡/方向 SKIPPED/MANUAL_BY_USER |
| 动画与controller | animation*/controller*/playerTimeline：pre/post、插值、动态权重、最短旋转、曲线、slot、子控制器、时钟/多pass、事件退出与声音句柄 | AUTO_PASS；不是所有模型所有clip所有时刻的穷举 |
| Molang与函数 | functionContract/functionStage/controllerPhase/filter/perlin/budget/vanillaState：实际Avatar registry、类型/别名、返回与嵌套、状态/实体隔离、预算恢复 | AUTO_PASS；可选第三方接口的中性值不代表桥接；233名称分级见M4清单 |
| 纹理/音频/语言 | materialInput/nativeMaterial/AVIF/audioDecode/language/soundNative：40模型94音频、55字典、62主材质、27AVIF及通道/句柄/重名隔离与关闭 | AUTO_PASS；可听播放、GPU/PBR实机 SKIPPED/MANUAL_BY_USER |
| 控件/动作/属性 | controlRuntime/actionRuntime/nativeMaterial：正式schema与命令、默认值、保存恢复、hold/release、toggle/cooldown/速度、层顺序、width/height/default skin | AUTO_PASS；真实UI交互 SKIPPED/MANUAL_BY_USER；字段表见M5 |
| Figura原预览 | nativeMaterial：原入口内指定clip、独立变量/时钟/几何/材质所有权，关闭后不复活；EntityPreview原交互不增加YSM分支 | AUTO_PASS（引擎）；真实预览/CJK SKIPPED/MANUAL_BY_USER；作者头像/前背景/额外旋转限制 SKIPPED/USER_UI_SCOPE |
| 附件与子实体 | nativeMaterial内SubEntity子用例、soundNative：箭/三叉戟/Boat/tag/乘客、双实例各自变量/时间/controller/事件、引用与共享PCM/图像释放 | AUTO_PASS；原生物理信息沿原版数据包；实机注入与方向 SKIPPED/MANUAL_BY_USER |
| 多人状态 | sync+nativeMaterial内SyncModel/SyncConnection：codec、owner权威、控件/换肤/动作、短动作事件、controller时间、sync、防重放、晚加入/重连/重载与队列预算 | AUTO_PASS（生产状态机CPU回放）；真正两个客户端/账号链 SKIPPED/MANUAL_BY_USER |
| 后端状态 | omnm-cloud认证UUID实时Ping转发已只读核对；离线快照/未见乘坐历史的空载载具归属无权威历史重放 | SKIPPED/BACKEND_BY_USER；后续需求见M7协议记录，不修改后端 |
| 普通Figura共存 | localCorpus：Figura5发现/编译及压缩载荷等值；M8额外20次普通Figura编译，与80次YSM加载交替 | AUTO_PASS；既有BB5 groups跟进和arrow缺素材保留，不声称视觉完全兼容 |
| 稳定性与资源 | stability：冻结100次/每批10次；45来源全部覆盖，原生图像/PCM计数、weak runtime引用、GC后堆及direct pool记录 | AUTO_PASS；详见下节，CPU数据不等于GPU显存或游戏帧时间 |
| 错误/负例 | 全部上述预算/版本/路径/无效引用/未知绑定/过期任务/取消等受控失败；冻结M4扫描保留40/38/2 | AUTO_PASS（保留已知负例）；不改模型使语料变绿 |

## 100次稳定性方法

固定机器为 AMD Ryzen 7 260（8核16线程）、物理内存16,385,531,904字节、Windows 11 26200、JDK25.0.2。测试进程最大堆2 GiB。原始机器信息 `build/reports/ysm/m8-machine.json`；每次记录和10批数据 `m8-stability.json`，统计 `m8-stability-summary.json`。此处引用的报告为本地构建产物，不提交用户模型。

读取冻结 `m0-limits.json` 的100次/10批预算；每5次插入1次Figura，共80次YSM装配（40来源各两次）和20次Figura编译（5来源各四次）。首次从正式data装配，重复时从portable NBT恢复；每个YSM选定一个可用clip做5次实际CPU采样并检查骨骼值有限。分配真实NativeImage、按需PBR/GUI资源及实际PCM，关闭后检查所有观测图像关闭、全部PCM交给释放、实例容器清空；每批最多5次有界GC观察，不保留旧runtime的强引用。没有启动窗口、GPU或OpenAL设备。

“冷”仅指本JVM首次遇见该来源，不清除OS磁盘缓存。两次走相同 `YsmModelData.read` 的 `sourceReadMilliseconds` 可比较首次/重复源读取与解析；两个不同装配路由的总耗时分开列出，其中portable总耗时还含测试准备源载荷，不能当作真实客户端网络缓存加载时延。CPU采样不是帧时间。分位数按排序后nearest-rank计算。

| 测量（毫秒） | 数量 | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| 同路径源读取/解析，首次 | 40 | 124.7459 | 510.8703 | 2350.5473 |
| 同路径源读取/解析，重复 | 40 | 107.0773 | 205.1226 | 2219.2969 |
| 首次data路线总加载 | 40 | 544.5906 | 2052.2050 | 9423.8612 |
| 重复portable路线总加载（含测试准备） | 40 | 465.9815 | 1271.3131 | 11419.1000 |
| 原Figura编译 | 20 | 518.9623 | 1305.1116 | 1834.2925 |
| 单clip CPU采样 | 400 | 0.2905 | 1.3195 | 1.8233 |

240张观测到的原生图像均关闭；188个PCM缓冲共412,889,364字节按拥有关系释放；10批retained runtime均为0。GC后堆第1/5/10批为65,558,280 / 66,301,664 / 66,334,728字节；后5批约33 KiB差值，没有观察到随循环保留旧runtime。direct buffer pool十批均65,536字节；这不是全部native/GPU内存统计。大模型完整装配仍有秒级尾延迟，未设置或伪造游戏加载/帧率性能承诺，也未据此引入GPU/native重写。

首轮已通过所有释放检查；为分开相同源读取与不同装配路由增加阶段计时后，最终稳定性gate再次通过1分43秒/退出0，日志 `m8-stability-gate.log`。首轮日志保留 `m8-stability-first-pass.log`，没有把不同路由的p99解释为冷热加速比。

## 门禁与跳过

最终核心门禁已通过：51个原有smoke/audit、冻结M4 scanner和Fabric build，4分18秒/退出0，67 actionable tasks（56 executed、11 up-to-date），日志 `build/reports/ysm/m8-final-core-gate.log`，UTF-8扫描 `m8-final-scan.txt`。新增stability在相同最终测试源码上已通过，不为凑日志重复100次。已逐项核对两份日志共同覆盖全部52个注册smoke/audit；没有新增生产修正或未解释的回归。两个既有Figura arrow缺素材、两个YSM缺引用、可选模组及参考未解项按已有状态保留。

可复现聚合统计见 [稳定性摘要](YSM-M8稳定性摘要.json)，使用与上表相同的nearest-rank计算。完整逐次原始报告保留在本地build/reports/ysm；未提交用户模型或解密资源。构建产物 `fabric/build/libs/figura-0.1.5+26.1.2-fabric-mc.jar`，SHA-256 `A5CD362757701B3C3546887947E2D75E1718C52287A07E16E5E86335F07EFB4B`；核对包含第一方AVIF适配、固定AVIF独立jar和YSM参考许可证。此处记录构建交付，不创建额外发布标签/发行版。

复现（PowerShell前台，先usejdk 25；各命令立即检查退出码并读取build.log）：

```powershell
& .\gradlew.bat :common:ysmStabilitySmokeTest --console=plain 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
Get-Content build.log -Tail 30
if ($buildExitCode -ne 0) { exit $buildExitCode }

$ysmTasks = [regex]::Matches((Get-Content common/build.gradle -Raw), 'tasks\.register\("(ysm[^\"]*(?:SmokeTest|Audit))"') |
    ForEach-Object { ':common:' + $_.Groups[1].Value } |
    Where-Object { $_ -ne ':common:ysmStabilitySmokeTest' }
$ysmArgs = @($ysmTasks) + @(':common:scanYsmModels', ':fabric:build',
    '-PysmScanBaseline=common/src/test/resources/ysm/m4-corpus-baseline.json',
    '-PysmScanReport=build/reports/ysm/m8-final-scan.txt', '--console=plain')
& .\gradlew.bat @ysmArgs 2>&1 | Tee-Object -FilePath build.log
$buildExitCode = $LASTEXITCODE
Get-Content build.log -Tail 30
if ($buildExitCode -ne 0) { exit $buildExitCode }
```

窗口、分辨率、视距、游戏p50/p95/p99帧时间、显存与可听音频没有测量，均 `SKIPPED/MANUAL_BY_USER`；真实两个客户端同样跳过。GUI不使用Qt offscreen或任何隐藏渲染作为外观证据。后端与取消UI项按各自授权跳过。

用户要求的加密/明文YSM/原Figura随机截图在主计划9.3已有三个 `CAPTURED/STATIC_ONLY` 本地浏览器截图，种子20261012；它们是当时的辅助画面，非生产预览或游戏验收。后续用户要求“预览只用Figura”已落实到生产入口，本轮不增加另一套预览。

## 阶段检查点与完成审计

主分支依次包含M0 `3932b40`、M1 `7882465`、M2 `34dcd14`、M3 `d7b2f51`、M4 `fee8ba0`、M5 `74a3a32`、M6 `be38c31`、M7 `9c48cea`；各阶段自动门禁和独立推送记录见主计划9.1～9.11，M8独立提交本矩阵及稳定性证据后执行同一推送核对。

第1.1节八项目标分别由本表容器、crypto、内存/隐私、部件/子实体、Molang/动画、资源/控件、多人及稳定性行覆盖；第7节矩阵逐层核对，未知参考/可选依赖与已知负例明确保留。M8新增测试/文档之外生产源码、冻结资源及GUI无diff，EntityPreview与M5开始前版本相同。三张历史截图仍在原输出根，未当作游戏视觉PASS。

按第1.2节用户覆盖条款完成自动部分及授权跳过后，M0～M8计划可关闭；这不意味着未测的游戏视觉、听感、真实多人或后端扩展已经实现/通过。不关闭计算机。
