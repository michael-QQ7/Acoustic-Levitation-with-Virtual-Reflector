# DPCAT 实施前代码分析

基线：用户提供的 AcousticFieldSim_MegaFPGA_250000.zip，独立解压，不覆盖原项目。2026-09-27。

## 工程与入口

Java 11 / NetBeans Ant 项目，src 中包含 194 个源文件和资源。src/acousticfield3d/AcousticField3D.java 启动 MainForm；math、scene、renderer 为原数学、场景和 OpenGL 渲染；algorithms 为 CPU 场计算与优化；simulation 为持久化数据；gui/panels 和 gui/misc 为界面；protocols 和 utils/uartComm 为硬件链路。jars 为现有 JOGL、串口依赖。build_reflector.py 支持独立编译与原有回归测试。

| 功能 | 已核对的代码入口 |
|---|---|
| CPU 复声压 / 力 / Gor'kov | algorithms/CalcField.java |
| 单阵元复传递函数 | algorithms/AcousticPropagation.java，transfer() 输出 double[2] |
| 缓存场 / 空间导数 | CachedPointFieldCalc.java、PropagationDerivatives.java |
| 换能器 | simulation/Transducer.java |
| 声速、阵列、动画、反射板 | simulation/Simulation.java |
| 反射板模型 / GUI | simulation/ReflectorSettings.java、gui/panels/ReflectorPanel.java |
| Focus / BFGS | SimplePhaseAlgorithms.java、BFGSOptimization.java、algorithms/bfgs/ |
| 优化 GUI | gui/misc/AlgorithmsForm.java |
| 动画 | simulation/Animation.java、AnimKeyFrame.java、gui/panels/AnimPanel.java、workers/PlayerThread.java |
| 相位与校准 GUI | gui/panels/TransducersPanel.java |
| Mega / FPGA | protocols/MegaFPGA.java、gui/panels/TransControlPanel.java |
| 声场切片 | gui/panels/RtSlicePanel.java、renderer/Renderer.java、资源 shaders |
| .gz 加载保存 | MainForm.loadSimulation、utils/FileUtils（gzip + JavaBeans XML） |

## 已确认的约定

- 单主动阵列，反射使用 y'=2H-y 的镜像源，并反转辐射法向的 Y 分量；反射系数为 magnitude*exp(i*phaseDegrees*pi/180)。镜像没有独立优化变量。
- transfer() 已返回包含方向性、路径和复反射系数的 Re/Im，不含驱动幅值和驱动相位；DPCAT 应直接复用。真实幅值在原场模型中是 amplitude*power。
- Transducer.phase 和 phaseCorrection 单位均为 π：phase=2 表示 2π。空间为 m，频率 Hz，反射相位独立使用 degree。
- Transducer.calcDiscPhase(s,32) = wrap(round(s*16),32)。getDiscPhase() 对 phase+phaseCorrection 编码；MegaFPGA.encodePattern() 调用它。
- 256 地址由 orderNumber 映射，不是 driverPinNumber。地址缺失或 amplitude<=0 为 OFF=32；amplitude>0 为活动通道。原 Mega 链路只支持 ON/OFF 幅值。
- 原 CPU / 渲染场只用 phase，不加校准项；校准项是发送时硬件补偿。DPCAT 保持此语义，并导出优化 code 与实际发送 code 两列，避免混淆。任意非整档 correction 最后仍有最多半档量化残差。
- 原动画按完整帧播放，但 Animation.interpolate() 可人工插帧；原 PlayerThread 使用全局 waitTime，未保存逐帧 ms。需为 DPCAT 增加持久化离散标志与专用逐帧路径，旧动画默认保持原语义。
- 原 MegaFPGA.pending 只保留最新待发帧。DPCAT 必须有 ACK 完成通知与独占的可靠逐帧发送路径，继续复用 encodePattern/request，保持 phases=... 和 phases ok、250000/250000 不变。

## 物理边界与实施决定

严格实现用户给定 C1/C2/C2N；它们是声压目标函数，不是重力平衡或三维势阱稳定性约束。尤其 C2 在目标位置最大化压力，不能据此宣称水滴就在该点稳定悬浮。界面、导出和文档均区分“目标/声场候选”和“实验已验证声陷”。目标生成单独封装；C1 用第一帧参考复场并保持相位参考，不声称完整复现论文 dual-side 的构造。

实施顺序：独立 core 与固定目标测试 → 三目标函数 → warm-start/轨迹/导出 → GUI → 离散 Animation 与 ACK 播放 → 全量回归、示例及交付。每个阶段编译。
