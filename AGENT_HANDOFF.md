# 当前交接：26.2 合并 dev Alpha.12

2026-11-17。当前分支26.2跟踪origin/26.2；起点3bd248f2，直接合并dev固定8cc2ede97a3af5cb109444586a05167322b58fb5，未新建分支。当前实施、自动验证及双release包完成，待合并提交和推送检查点；详细范围、适配及失败日志见docs/dev-alpha12-26.2/record.md。

已通过：12个相关smoke与双包1m52s；修正新增Minecraft关闭Mixin目标为26.2 close()V后，HTTP及双包重建38秒通过；Python七项通过；发布任务链无测试；产物CRC/元数据/模板/Windows native及无旧加速类检查通过。受控discard标记回退使实际材质调用链RED，finally恢复后GREEN。

原未跟踪.codex/保留；不改其他版本、后端、Actions，不恢复自定义GPU/JNI/SIMD。游戏/实际Mixin加载/GPU/GUI/双客户端MANUAL/PENDING，未擅启游戏。不因人工项标记受阻。

下一步最小任务：审查暂存diff、完成当前merge提交并推送26.2，核对远端；将最终代码提交和包哈希更新本手册及artifacts.json，交付本地路径。当前可靠额度剩余55%，未触发Luna。

持续约束：CodeGraph优先；新PowerShell先usejdk25/java -version；原生Windows前台Tee构建并立即检查退出码；禁止WSL、非Windows本地构建、Qt offscreen和擅自启动游戏；保留用户修改。旧Alpha.11交付和接力记录见3bd248f2版本手册，本轮只合并当前26.2，不自动向其他版本转发新任务。
