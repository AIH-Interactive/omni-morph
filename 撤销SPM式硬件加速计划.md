# 1.20.1 撤销 SPM 式硬件加速计划

用户授权四阶段串行实施；保持1.20.1分支，版本alpha.8。JDK21运行Gradle，Java17目标与离线回归；Forge47.1.3/历史NeoForge47.1.106经shared forge项目验证。

1. 迁出RenderOwner/RenderLifetime，保留owner/lease/frame/resource失效、可空lease与退役不可复活。
2. 撤Figura/YSM加速路由及专用网格缓存，恢复普通顶点提交；以禁止后端调用的混合绘制测试RED/GREEN验证。
3. 删除专用后端、硬件设置/翻译/状态控件、mixin/访问开放、native发行/测试与tools；保留业务访问开放及AVIF。Actions改普通生产编译打包上传，不运行测试。
4. 保留业务与只读真实语料回归，alpha.8测试JAR/SHA、最终交接。严格scanner既有问题单列，不扩大修解析器。

每阶段自动验证→额度→diff/status/check→独立提交推送并核对→下一阶段。人工实机全部SKIPPED（用户决定，未执行），不启动游戏。不得误删粒子acceleration、OutfitNativeRenderer、蒙皮/预算/挂点显隐/法线UV、缓存加密、云协议、音频等业务。参考1.21.1 c73c5f0c/9de3ab64/7d59c3c2/74f64875、26.1.2 fce9c5b5/d9efc440/3735d7b0/5a45a5ac/85df3159，按本版1.20.1接口适配。

进度：四阶段自动验证完成。阶段一01cad40d、阶段二c66b722e、阶段三8b0ae11d已推送核对；本提交为第四阶段测试过滤/最终交付记录。详细结果见docs/acceleration-revert/stage-1.md～stage-4.md，测试JAR/SHA见stage-4-artifacts.txt。人工实机SKIPPED（用户决定未执行）；严格scanner既有53/47/6失败单列，不记PASS。
