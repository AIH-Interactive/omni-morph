# 26.3 修复同步到26.2

2026-11-17。原分支26.2，起点a5bae220；原未跟踪.codex/保留。来源26.3固定9a7a394ab253962c351b0a97800c2cee02411c1b，三项装饰修复源dev e2962c33c7da36fa879b63759dcdf74e052966f1（实现c1084296）。不创建分支、不启动游戏、不修改其他版本。

## 检查点1：本版Mixin适用性与实际应用

26.2原有close()V已正确，不重复修改生产。Minecraft-dev MCP核对本版正常退出exitWorldAndClose调用close；shader源走ShaderManager.getShader(Identifier,ShaderType)->String，原注入为普通实例方法HEAD，26.3的PipelineCache构造器static修复不适用。

移植来源3a104563的双平台成品JAR真实Sponge/MixinExtras验证；另按26.2实际目标增加双平台ShaderManager检查。加载各自完整生产配置、实际Minecraft目标字节码，验证变换后原方法调用注入处理器及生产HTTP清理/Avatar shader源查询。未执行Minecraft静态初始化或启动客户端，不宣称完整Knot/FML启动或GPU像素通过。

mixin-initial.log四项实际Mixin应用通过，18秒。lifecycle-sensitivity-red.log为受控回归敏感性验证：临时将既有close()V改回destroy，双平台真实InvalidInjectionException，26秒、退出1；finally逐字节恢复，不提交临时回退。mixin-green.log四项Mixin+第一人称原生材质+HTTP生命周期全部通过，34秒、退出0。对应日志docs/sync-26.3-fixes/。关闭/ShaderManager生产实现与起点一致，无额外兼容层。

检查点1实施/自动验证完成，提交推送后再开始检查点2。新增任务只在本地使用，生产打包不依赖它们，Actions不改。

## 检查点2：待合并装饰修复

直接合并固定dev e2962c33，保留26.2平台适配。验证本地文件缺失的私有装饰恢复、准备资源的作者名称/默认设置、同UUID复活重绑与脚本实例保留/旧拖尾清理；新测试配旧实现RED后，合并实现GREEN。最终重建双包、核验Windows native、更新交接并提交推送。

完整游戏启动、实际画面/复活及跨维度场景MANUAL/PENDING，不作为受阻条件。
