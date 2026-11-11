# 1.20.1 当前交接：后续Alpha.10 UI同步

## 本版历史

此前Alpha.9前置+完整Alpha.10已在代码9fa3196d97f8819de52ae083f23198c058c41f60、交付2afa4aaff00e9b2ce777232ff2c032673038bdfe完成并推送。旧证据docs/alpha10-port/record.md、artifacts.json；旧包build/distributions/alpha10-port，SHA256 3c34c5ca931831e9cb355d3b8be5056f8bbbf214338b1e05a1e8ab2de02dc29f仍保留。旧粒子坐标/TCCL/1024帧原生分配稳定2097200字节的修复及加速撤销未覆盖。

## 当前任务与完成内容

用户授权逐级通信实施26.3→26.2→1.21.1→1.20.1，本版是最后一站。在当前1.20.1分支直接合并源8faa4b5954be798d9a0979e14537b5a583d1d9b8（主UI245d4f3a），共同祖先8879d121。不重复旧移植。

最终80×98卡与80×80方封面、仅水平居中/垂直顶部、480×252三卡、独立形象/装饰列表和操作/右侧Tab、紧凑装饰选择/挂载、独立信息公告等宽布局、展开导航固定底部四状态、集合资源复用和resource_kind请求均完成适配。后端e04c1347已由其他会话完成，本版不修改或部署。

本版Java17、Gradle JDK21、Forge47.1.3/历史NeoForge47.1.106共用forge项目保留；普通渲染/RenderLifetime/AVIF与专用加速撤销、构建依赖和Actions不变。仅必要GuiGraphics/drawString/ResourceLocation/旧blit、Mth.clamp、输入签名和新测试Screen.minecraft绑定适配。全部代码/日志证据见docs/alpha10-ui-sync/record.md。

## 已验证

regression.log：common生产/全部测试与forge编译，14项功能/导航输入/布局/目录HTTP/预览/缓存/上传/普通渲染回归及Forge remapJar通过58秒退出0。neoforge.log：生产/测试编译、NeoForge remapJar、16模块/13桥接、AVIF实际解码、98Mixin静态契约及8typed Local通过12秒退出0。生产依赖图各16任务，无测试/JNI/探针；JSON/YAML/actionlint/diff及无冲突检查通过。初始API编译失败已解决，负向夹具故意异常非未处理失败。

候选包CRC/14嵌套依赖与jarjar清单/元数据/Java17/Windows AVIF AMD64 PE/新Tab/footer/horizontalInset且无verticalInset/无测试或专用加速均通过，候选不是最终交付。代码提交推送后必须重建最终包并保存code SHA、版本、CRC/SHA和绝对路径；这一步尚在收尾，将在完成后追加。

## 人工与接续约束

真实GUI/CJK像素、游戏/Mixin运行时、TaCZ整合包及双客户端多人为SKIPPED（MANUAL，用户原有决定；未验证），源用户验收不当作本版PASS。26.3导航问题不在本轮范围，不推断本版同故障。可选voicechat/renderRaw静态缺失是既有边界。

先读AGENTS.md和本手册、docs/alpha10-ui-sync/record.md，核对Git。不reset/abort/切换分支/覆盖其他修改，不改其他工作区、后端/wiki，不创建聊天或继续委派。原.codex/未跟踪保留。不启动游戏/覆盖mods/触发Actions。所有build/test/package前台PowerShell Tee build.log并立即捕获退出码读取日志；不可并发Gradle。保留旧产物。本轮无额外开发阶段，最终打包交付后链条结束。

可靠额度接口周窗口10080分钟已用20%、剩余80%，未触发Luna阈值；当前Agent整理任务完成交接。当前工具未提供minecraft-dev入口，不冒称调用。
