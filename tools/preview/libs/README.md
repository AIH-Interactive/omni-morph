# 预览器 MOD 依赖

以下二进制由用户放入本目录并明确授权提交推送；保留原JAR内许可证、资源和嵌套依赖。版本与许可证字段来自实际JAR元数据，不代表当前工作树编译产物。

| 文件 | 元数据版本 / 许可证 | SHA256 |
| --- | --- | --- |
| omni-morph-1.0.0-alpha.7+26.1.2-fabric-mc.jar | 1.0.0-alpha.7+26.1.2-1b0a7b4 / PolyForm-Noncommercial-1.0.0 | 4cba8f226c72a3694597e24d70f0049428a4444c508b03b12a578c9edb8658ba |
| sparkle-morpher-2.3.2-fa26.1.x.jar | 2.3.2 / MIT | 4964ab9d2d207dd70554d4d2ec021820146d7faeca9fec6270da1ac0f09b5092 |
| openysm-forge-2.6.6.6.jar | 2.6.6.6 / MIT（mods.toml声明） | 86a1b0161df2c8f803f9ccbc53bd0583414c18b1d628e0bec42f2a2297a0aabb |

OMP元数据来源仓库为https://github.com/AIH-Interactive/omni-morph；SPM为https://github.com/sdf123098/Sparkle-Morpher。OpenYSM发布包由用户提供，具体下载地址未记录，不编造来源链接。

Minecraft JAR和共享libraries从用户配置目录读取，不复制进本目录或宿主JAR。构建产物另外打包启动配置使用的Gson，保留其JAR附带许可证元数据。
