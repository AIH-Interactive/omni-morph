# 采样灯饰

目标OMM alpha.12 / 26.1.2。默认类别`Back`，挂点`back_anchor`。修改script.lua中的accessory.options、Blockbench模型或textures/art.svg。

API：Animation.setKeyframeBoundary、Animation.sample。模型坐标为像素，粒子为方块，时长为秒。自动TICK/RENDER驱动。

贴图由自绘SVG的rect/circle/polygon原语转为64×64 PNG，并嵌入bbmodel。可编辑源码随包提供。
加载/数值检查见总P5验收记录；游戏外观MANUAL/PENDING。
