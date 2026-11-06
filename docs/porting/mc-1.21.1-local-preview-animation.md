# Minecraft 1.21.1：本地衣柜世界预览动画准备

源方案：26.2提交222e150e9c89246773cdf0610b4ca2f0e978134b；参考26.1.2适配b7757b9f4f07078c11e063c7b1592b522f4c1cde。仅同步关闭衣柜后私有主体动画静止问题及核对实体ID契约，不改Actions、版本或native包。

## 根因与适配

WardrobeScreen交出私有LibraryPreviewSession后，LocalOutfitPreview.tick只驱动tick；私有主体不在AvatarManager正式模型集合，GUI退出后缺少beforeExtract的render/clearAnimations/applyAnimations准备。

1.21.1为立即渲染：LivingEntityRendererMixin.onRender先调用EntityFrame.begin，世界分支调用OutfitRenderBinding.capture。在capture取得本地草稿后、retain之前转发LocalOutfitPreview.beforeExtract，从而在骨骼捕获/绘制之前准备动画。LibraryPreviewSession按YsmRenderFrameClock对活跃本地预览逐帧去重，begin新代际重置；保留borrowedBase跳过分支。

实体ID修复不适用：本版Entity构造器直接执行ENTITY_COUNTER.incrementAndGet赋值，getId直接返回int字段；LibraryPreviewPlayer正常调用super。保持生产代码，不另分配负数ID。新增真实构造回归确认ID正数、不同、稳定。字节码证据build/tmp/preview-entity-bytecode.txt。

## 验证

- RED：新增回归执行本版EntityFrame.begin/end世界渲染入口，原代码在Lua断言world preparation ran失败。日志build/tmp/local-preview-animation-red.log。
- GREEN：完整:common:libraryPreviewRuntimeSmokeTest成功；覆盖detach后的世界准备、同帧GUI/world去重、下一帧继续准备、borrowed base所有权及真实构造ID。日志build/tmp/local-preview-animation-green.log。
- ID测试首次夹具缺少1.21.1 AbstractClientPlayer构造需要的spawn位置/角度，补齐测试Level覆盖后通过；该夹具失败不是产品RED。日志build/tmp/local-preview-id-fixture-failure.log。
- :fabric:assemble :neoforge:assemble均成功，含双端remapJar和sourcesJar；日志build/tmp/local-preview-animation-loaders.log。git diff --check通过。所有编译/测试用JDK21，前台PowerShell Tee日志并核对退出码。
- 游戏/真实GUI画面/GPU像素验收：SKIPPED（用户禁止启动游戏，未执行）；离线Lua与生产入口回归不冒充实机画面。没有修改用户实例或模型、没有启动游戏、没有额外native包或六平台发布。Actions继续仅生产构建，测试仅本地执行。

范围内适配和规定验证完成，无剩余实施阶段；提交推送状态以根AGENT_HANDOFF.md为准。
