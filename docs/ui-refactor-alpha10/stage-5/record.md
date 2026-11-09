# 阶段 5：Alpha.10 发布准备

日期：2026-11-09。分支 `main`。

## 修改

- `gradle.properties`：`mod_version` 改为 `1.0.0-alpha.10`。
- 四份 README：模组版本和 NeoForge 产物文件名改为 alpha.10。
- 四种语言的更新日志：新增 1.0.0-alpha.10，并按 git 历史补写之前缺失的 alpha.7、alpha.8、alpha.9 条目。

## 验证

| 项目 | 结果 |
| --- | --- |
| `:fabric:build` | 退出码 0（`fabric.log`），产物 `omni-morph-1.0.0-alpha.10+26.1.2-fabric-mc.jar`，`fabric.mod.json` 中的版本为 `1.0.0-alpha.10+26.1.2-74d7b6e8`（未使用 `-Prelease`，带提交后缀） |
| `:neoforge:build -Prelease --no-daemon` | 退出码 0（`neoforge.log`），包含 `verifyNeoForgeJar`；产物 `omni-morph-1.0.0-alpha.10+26.1.2-neoforge-mc.jar`，`neoforge.mods.toml` 中的版本为 `1.0.0-alpha.10+26.1.2` |
| 前四个阶段的测试 | 各阶段记录中均已通过；后端 `cargo test` 204 个通过 |
| 实机 GUI、多人、NeoForge + TaCZ 整合包 | MANUAL/PENDING |

## Penpot 最终核对

- 重新读取页面列表，共 56 页。计划列出的页面都在：01、02、03、04、05、06、08、09、10、34、37、38、44、46、47、51、52、53、54、55。第 50 页已删除，没有当作设计依据，也没有恢复。
- 每个页面的逐项核对结果，见阶段 1–4 的记录。
