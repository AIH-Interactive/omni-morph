# 渐变拖尾

目标OMM alpha.12 / 26.1.2。默认类别`Trail`，挂点`tailtip`。修改script.lua中的accessory.options、Blockbench模型或textures/art.svg。

API：Particle.setQuads、Particle.setQuadsTransform、particle.vertices.color。模型坐标为像素，粒子为方块，动画时间为秒，寿命滑块单位为 tick。自动TICK/POST_RENDER驱动。

贴图由自绘SVG的rect/circle/polygon原语转为64×64 PNG，并嵌入bbmodel。可编辑源码随包提供。
加载/数值检查见总P5验收记录；游戏外观MANUAL/PENDING。

绑定起点：`Cosmetic` 根骨骼；原网格保留在其下。脚本路径为 `models.model.model.Cosmetic`。
粒子位置在 POST_RENDER 从该根骨骼的实际世界矩阵采样，跟随挂载与骨骼编辑。
寿命滑块范围为 1–300 tick，默认 20 tick；修改后用于新建的拖尾粒子。
