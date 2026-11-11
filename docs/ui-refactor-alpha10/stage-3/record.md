# 阶段 3：游戏世界化身可见性

日期：2026-11-09。客户端分支 `main`；后端 `sculptor-for-omni-morph` 分支 `master`。依据：计划书第 5 节（用户补充版）。

## 改动前的问题

1. 需授权模型：世界中能否看到形象，取决于观看者本人是否有授权。没有授权的玩家看不到别人佩戴的已授权模型，与 §5.3 不符。
2. 私有模型：装备时客户端总是发送 `show_private_equipment=false`，后端也把缺省值当作“隐藏”，所以私有形象默认不对他人显示，与 §5.2 不符。

## 实现

- 后端 `Policy::can_render_equipment`：非私有模型改为按佩戴者的使用权判断（佩戴者是上传者，或持有授权），不再看观看者的授权。佩戴者的授权被撤销后，其他人就看不到该形象。私有模型仍只能由上传者佩戴，并遵循其“对他人显示”设置。被屏蔽或删除的模型一律不显示（§5.4）。
- 后端查询授权的两处调用（`distribution.rs`、`outfit_sources.rs`）改为查询佩戴者的授权。
- 后端装备接口：`show_private_equipment` 改为可空。请求中没有显示设置时，保留该模型版本已保存的设置；没有已保存的设置时，私有模型默认显示，公开模型保持原有默认（由衣柜设置单独控制，默认显示）。请求中给出了明确值时照旧写入并原样返回，兼容旧客户端的校验。
- 客户端：装备一个模型时，只有在已知它当前的显示设置时才发送这个值，否则不发送，由服务器保留或使用默认值。缓存中记录服务器返回的实际值。用户主动关闭的“对他人显示”会被保留（§5.6）。
- 未改动：模型库查询和可见性（`permissions` 的 `visible`），私有模型仍不会出现在他人的模型库中（§5.7）；下载、使用、编辑、授权管理的判断（§5.5）；没有新增服务端业务功能（§8）。

## 修改清单（供其他版本移植）

| 仓库 / 文件 | 修改 |
| --- | --- |
| 后端 `src/service/library_policy.rs` | `can_render_equipment` / `can_distribute` 改为按佩戴者授权判断；新增测试 `world_display_follows_the_wearer_authorization_not_the_observer` |
| 后端 `src/service/library/distribution.rs`、`src/storage/outfit_sources.rs` | 授权查询的对象由观看者改为佩戴者 |
| 后端 `src/storage/library_equipment.rs` | `LibraryEquip.show_private_equipment` 改为 `Option<bool>`，新增 `requested_display()`；未指定时 `INSERT OR IGNORE`（私有为 1，公开为 0）；新增 `library_private_display` |
| 后端 `src/api/library/models.rs` | 未指定时返回保存的设置 |
| 后端测试 | 按新规则修改：`subscriptions.rs`（所有观看者可见，撤销观看者授权后仍可见）、`outfit_projection.rs`、`outfit_observers.rs`、`equipment.rs`（默认显示、保留明确隐藏）、`outfit_catalog.rs` |
| 客户端 `LibraryEquipmentService` | 显示设置改为可空，只有明确时才发送 |
| 客户端 `WardrobeScreen` | 装备时只在模型已装备、设置已知时传入显示设置 |
| 客户端 `LibraryEquipmentSmokeTest` | 断言不发送显示设置；模拟服务器按私有默认显示返回 |

## 验证

| 项目 | 结果 |
| --- | --- |
| 后端 RED | 策略测试：未授权观看者看到上传者佩戴的需授权模型的断言失败。装备测试：私有模型默认显示的断言失败（实际为 `false`）。 |
| 后端 GREEN | `cargo test`：204 个通过，0 个失败，1 个忽略，退出码 0。 |
| 客户端 RED | `red.log`：暂存实现改动后，新断言失败。 |
| 客户端 GREEN | `green.log`：装备、预览运行时、模型库目录、衣柜过渡测试与 `:fabric:build` 通过，退出码 0。 |
| 实机多人 | MANUAL/PENDING：两个真实客户端在同一世界互相看到形象的场景没有运行。 |

## Penpot 核对（本阶段重新读取）

| 页面 | 内容 | 结论 |
| --- | --- | --- |
| 54 模型卡片样式 | 状态组合示例：“屏蔽 / 需授权 / 私有”“需授权 / 公开”“私有”“公开” | 与阶段 2 的卡片图标一致，本阶段没有界面改动 |
| 01 衣柜预览 | 预览区状态图标：屏蔽 / 需授权 / 可视性 | 一致，“可视性”图标继续表示“对他人显示”的设置 |

## 已知事项

- 已有的发布记录不批量修改。在本次改动前装备、并已保存为“隐藏”的私有模型，会继续保持隐藏，因为无法区分这是用户的主动选择还是旧的默认值。
- 以公开状态装备、之后改为私有的模型，沿用装备时保存的设置（为 0），因此改为私有后会隐藏，不会自动显示。这是原有测试覆盖的行为，本次没有改变。
