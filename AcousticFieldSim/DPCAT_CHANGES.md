# DPCAT 文件修改清单

基线压缩包 SHA-256：`55bcd599da6bef66a0e1c99ece25945e5e0293e138f34c96ea4706bdc0fc2769`。

2026-09-28续接：保留现有实现，仅在DpcatPanel增加新播放清除旧计时；扩展DpcatPanelTest、DpcatTransportTest的异常/取消验证；更新本清单及测试报告。最终重新构建与回归通过。

## 修改的原文件

| 文件 | 职责/改动 |
|---|---|
| `build_reflector.py` | 将DPCAT测试加入原有便携构建/回归流程。 |
| `dist/AcousticField3D.jar` | 重新编译的Java11兼容可运行程序。 |
| `src/acousticfield3d/gui/MainForm.java` | 新增DPCAT入口与独立窗口。 |
| `src/acousticfield3d/gui/misc/AlgorithmsForm.java` | 在原算法窗口增加DPCAT菜单，保留原算法。 |
| `src/acousticfield3d/gui/panels/AnimPanel.java` | 添加离散序列到原动画列表；播放/停止转交DPCAT播放器。 |
| `src/acousticfield3d/gui/panels/TransControlPanel.java` | 提供单Mega连接访问；保留原发送操作。 |
| `src/acousticfield3d/protocols/MegaFPGA.java` | 原编码/命令不变，增加ACK完成通知与离散播放独占通道，防止丢帧。 |
| `src/acousticfield3d/simulation/Animation.java` | 持久化离散标志/模型指纹；禁止DPCAT普通插帧，应用前校验。 |
| `src/acousticfield3d/simulation/AnimKeyFrame.java` | 持久化逐帧ms和元数据；复制帧保留duration等信息。 |
| `src/acousticfield3d/workers/PlayerThread.java` | 旧播放器跳过DPCAT，避免绕过可靠播放路径。 |

## 新增文件

| 文件 | 职责/内容 |
|---|---|
| `DPCAT_ANALYSIS.md` | 实施前项目结构、入口和单位/协议分析。 |
| `DPCAT_CHANGES.md` | 本清单。 |
| `DPCAT_README.md` | 公式、编码、GUI操作、示例、物理限制和实验参数。 |
| `START_DPCAT.bat` | Windows便携启动入口。 |
| `examples/dpcat/EXAMPLE.txt` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/array192_enabled_example.xml.gz` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_192_DPCAT_75mm.xml.gz` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_A_C1_sequence.csv` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_A_C1_sequence.csv.model.xml.gz` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_A_frame0_phases.csv` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_POINT_WIDE_SINGLE_C2_sequence.csv` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_POINT_WIDE_SINGLE_C2_sequence.csv.model.xml.gz` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_TWO_WIDE_C2_sequence.csv` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `examples/dpcat/example_TWO_WIDE_C2_sequence.csv.model.xml.gz` | 可运行离线示例数据、模型快照或示例来源说明。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatConfig.java` | 算法参数、三目标函数枚举与输入校验。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatExample.java` | 基于既有.gz生成三种离线示例；不打开串口。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatExport.java` | 完整256通道相位CSV、序列CSV、元数据和模型快照。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatMatrix.java` | 调用现有传播函数建立复矩阵；完整复压求和。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatOptimizer.java` | 32-state直接搜索、增量更新、目标函数、warm/随机初值与相位连续性罚项。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatSequence.java` | 前帧warm start、每帧结果与原Animation桥接。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatSnapshot.java` | 模型快照、动态active集合、phase/code转换、校准保护和原帧映射。 |
| `src/acousticfield3d/algorithms/dpcat/DpcatTargets.java` | X/Z目标、单/双宽区、三段轨迹和独立C1参考构造。 |
| `src/acousticfield3d/gui/panels/DpcatPanel.java` | 参数、异步优化、选帧/切片/标记、动画、导出、发送与取消。 |
| `src/acousticfield3d/workers/DpcatPlayback.java` | 完整帧校验、逐帧ACK/保持、实际计时、停止和连接租约释放。 |
| `test/acousticfield3d/algorithms/dpcat/DpcatCoreTest.java` | 核心公式、增量、反射高度、相位/OFF/校准和取消测试。 |
| `test/acousticfield3d/algorithms/dpcat/DpcatSequenceTest.java` | 轨迹、warm-vs-random、宽区、.gz和CSV、计时测试。 |
| `test/acousticfield3d/gui/panels/DpcatDesktopSmoke.java` | 可选真实JOGL/主窗口/.gz启动测试，无硬件发送。 |
| `test/acousticfield3d/gui/panels/DpcatPanelTest.java` | Swing真实控件和后台优化、切片、参数校验测试，生成截图。 |
| `test/acousticfield3d/protocols/DpcatTransportTest.java` | 模拟串口验证无跳帧、ACK超时、所有权及保持时间。 |
| `verification/DPCAT_TEST_REPORT.md` | 验证结论与实测/仿真边界。 |
| `verification/dpcat-desktop-smoke.log` | 实际运行日志。 |
| `verification/dpcat-example.log` | 实际运行日志。 |
| `verification/dpcat-panel.png` | 实际GUI渲染截图。 |
| `verification/dpcat-tests.log` | 实际运行日志。 |
| `verification/dpcat/sequence_roundtrip.xml.gz` | 自动测试生成的CSV、计时或.gz往返验证数据。 |
| `verification/dpcat/test_phases.csv` | 自动测试生成的CSV、计时或.gz往返验证数据。 |
| `verification/dpcat/test_preview_timing.csv` | 自动测试生成的CSV、计时或.gz往返验证数据。 |
| `verification/dpcat/test_sequence.csv` | 自动测试生成的CSV、计时或.gz往返验证数据。 |
| `verification/dpcat/test_sequence.csv.model.xml.gz` | 自动测试生成的CSV、计时或.gz往返验证数据。 |

## 保持原样的关键模块

`Transducer.java`、`Simulation.java`、`AcousticPropagation.java`、`ReflectorSettings.java`、`ReflectorPanel.java`、`CalcField.java`、`CachedPointFieldCalc.java`、`SimplePhaseAlgorithms.java`、`BFGSOptimization.java`、所有原shader、固件/波特率/命令格式均未改动。
.form文件、原NetBeans项目结构和依赖保留；新增界面用程序式Swing实现。

`build/`是本机中间编译目录，不纳入便携交付。
