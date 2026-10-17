# 模型库 M0 样例清单

所有样例均为可公开的合成测试数据，不包含玩家模型、账号或凭据。
本清单冻结解析与元数据来源，不能代替后续真实游戏窗口的渲染验收。

| 类别 | 样例/来源 | 用途与边界 |
| --- | --- | --- |
| Figura ZIP | `docs/model-library-fixtures/figura.zip` | avatar.json，名称“示例 Figura”，有序双作者；仅元数据/空模型准备测试 |
| 新 YSM ZIP | `docs/model-library-fixtures/ysm-new.zip` | ysm.json metadata，名称“示例 YSM”，有序双作者；最小空几何，不代表可见材质/动画 |
| 旧 YSM ZIP | `YsmPackageSmokeTest::testOldFolderAndZip` 运行时生成 | main.json 旧路径；缺元数据/空作者回退测试，不持久化不确定 ZIP 时间戳 |
| 加密 YSM 1/2 | `common/src/test/resources/ysm/crypto-vectors/crypto{1,2}.ysm` | 固定解密向量；不能证明复杂模型渲染或内部作者提取通过 |
| 加密 YSM 3 | 同目录 crypto3.ysm / crypto3-compressed.ysm | 合成任意载荷，不是有效完整模型；仅密码学/压缩解析向量 |
| 损坏 YSM | 同目录 crypto3-bad-padding.ysm / crypto3-bad-zstd.ysm | 必须拒绝，不能当作成功模型样例 |
| 分发 NBT | `omnm-cloud/src/service/package.rs::tests::fixture` | 后端合成分发载荷；加密样例 ciphertext 是占位，不等于可解密模型 |

随附 `docs/model-library-fixtures/sha256.json` 记录实际文件原始 SHA-256。
加密向量原有许可证/来源见相邻 README 和 `licenses/YSM-Sparkle-Morpher.txt`。
ZIP 使用固定时间戳、无压缩和 UTF-8 JSON，避免依赖平台压缩实现；选择器输入
ZIP 与最终压缩 NBT 的哈希不混用，后者在后续阶段由真实编译结果确定。

M0 静态检查：Figura 优先 authors 数组，再回退 author；新 YSM 读 metadata；
旧 YSM 读 minecraft:geometry[0].description.ysm_extra_info；加密源必须完整受限
解析后取 manifest，不能以 readListMetadata 的文件名和空作者冒充提取结果。
内部名称单独保留，展示名允许调整；上传者来自后端认证会话。

真实完整 Figura/YSM/加密 YSM 的材质、动画、独立预览和关闭释放检查：
`MANUAL/PENDING`，归入 M4～M7，不阻断 M0 文档检查点。
最小布局合同为 320×180 逻辑像素（1280×720、GUI scale 4），一列卡片；
更大尺寸按计划验证自适应列数与中文。实际字体、焦点、遮挡和截图仍待真实窗口验收。
