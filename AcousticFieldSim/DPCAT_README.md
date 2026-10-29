# DPCAT 使用与交付说明

此版本在用户提供的 `AcousticFieldSim_MegaFPGA_250000.zip` 上新增独立 DPCAT 模块。保留 Focus、BFGS、Gor'kov、Reflector、旧动画和 MegaFPGA；没有改换能器类、传播公式、反射板设置、固件或串口命令格式。原压缩包与工作区原项目未覆盖。

**本版本完成的是离散声压目标优化、轨迹生成、预览、保存和逐帧发送的软件链路。尚未证明这些目标就是稳定液滴声陷，也没有做真实硬件发送或液滴融合实验。** C2 最大化目标声压；液滴稳定悬浮还需要声辐射力与重力平衡、三维恢复性及有限尺寸/形变验证。不能将优化 cost 下降、CV 降低或界面的 Trap 名称当成液滴稳定性证明。

实施前分析见 `DPCAT_ANALYSIS.md`；完整修改与新增文件清单见 `DPCAT_CHANGES.md`；测试结果见 `verification/DPCAT_TEST_REPORT.md`。

## 1. 启动与加载

1. 完整解压新压缩包，保留 `dist`、`jars` 目录相对位置。
2. 双击 `START_DPCAT.bat`。需安装 Java 11 或更新版本；本机实测 JDK 25 可启动。也可使用原 `START_Reflector.bat`。
3. 使用原 `File → Load` 功能加载现有 `.xml.gz` 仿真。菜单的实际文字沿用旧工程。
4. 主菜单 `DPCAT → Open DPCAT optimizer` 打开窗口；原 Algorithms 窗口也有 DPCAT 菜单。

NetBeans 仍可直接打开含 `build.xml` 和 `nbproject` 的目录。独立编译及测试：

```text
python build_reflector.py --test
```

旧文件若未持久化 amplitude，当前基线项目会按默认 amplitude=0 加载，DPCAT 将报告无活动阵元。请在原 Trans 面板为确实安装的换能器设置正幅值；不要将未安装的逻辑地址启用。算法不会擅自打开通道。随附示例输入来自现有192阵元几何，另在副本中显式设置这192个已存在阵元的 amplitude=1，源文件保持不变。

## 2. 反射板和两个目标

1. 在原 Reflector 面板勾选 `Enable reflection`。
2. `Gap from array (mm)` 输入 **75**，点击 `Place below (-Y)`。
3. 若阵列平均 Y=0，反射面即 Y=-75 mm；若阵列有平移，则以当前实际阵列位置为准。DPCAT 不写死该数值。
4. DPCAT 点击 `Refresh Reflector Status` 核对 Enabled、Y、实际 Gap、|R|、反射附加相位。
5. 输入融合中心 X/Y/Z，单位均为 mm。也可选中一个原有控制点，点击 `Use selected point`。`Use array/plate midpoint` 只提供几何中点初值，**并不查找稳定声节点**。
6. 选择 `A: Two Point Traps`、Merge axis=`X`、Initial separation=`14`。目标是 `(xc-7,yc,zc)` 和 `(xc+7,yc,zc)` mm。Z 方向同理沿 Z 对称。

这些标记表示声压采样/约束位置。若希望它们代表液滴中心，必须先验证该声场是否在这些位置产生适当声力；特别是 C2 声压峰值位置不能直接等同于水滴中心。

## 3. 三种目标函数

对每个位置 n，复声压 `p_n = Re_n + j Im_n`。

```text
C1 = sum_n |p_n - p_target,n|

meanP = sum_n |p_n| / N
stdP  = sqrt(sum_n (|p_n| - meanP)^2 / N)
C2-paper      = -sum_n |p_n| + alpha * stdP
C2-normalized = -meanP + beta * stdP / (meanP + epsilon)
epsilon = 1e-12

d(a,b) = min(|a-b|, 32-|a-b|)
Ctotal = Cacoustic + lambdaPhase * sum_i d(code_i,previousCode_i)^2
```

- C1 使用复数差的模；绝非 `||p|-target|`。
- C1 简单参考模式：第一帧使用当前相位投影至32档后的复声场，乘 `C1 reference amplitude scale`；后续移动位置但保持第一帧参考复数。目标数量变化时，按归一化采样索引选择最近的首帧参考，保持被选中的复相位。相关构造在 `DpcatTargets.reference()`，可独立替换。**这不是论文双主动阵列的完整 target construction 复现**，尤其模式C的拓扑切换仍属实验参数。
- 因此，C1、Current、scale=1的第一帧 cost=0、不改变相位是正常现象；它保留当前场，不会凭空产生一个优质初始声陷。可先用已验证的声场作为参考，或先运行一次离散 C2 来生成声压候选再研究。
- α、β 默认1，可调整。C2/C2N 总目标降低不保证 CV 单独降低；高声压与均匀性存在取舍。日志如实报告优化前后 CV。
- C2N 只有均匀性项做相对归一化，并非整个目标无量纲；声压尺度改变后 β 仍需重调。声压沿用原模型的 amplitude×power 与传播单位，不能替代实测标定。
- λ 默认0；31→0按距离1计。λ过大可能使相位几乎不动。warm start与相位罚项都不保证声陷拓扑或液滴轨迹连续。
- `Restarts` 只影响独立优化/序列第一帧；第一轮按选定初值，其余轮随机，返回最小 cost。后续帧强制以上一帧最优结果为唯一初值，避免每帧随机重启。随机种子可复现。
- `Previous frame` 取本窗口上次优化的最后一帧；反射板/几何/校准/幅值变化后不能沿用过时结果。单独重新生成序列时会重新捕获首帧C1参考。

## 4. 单主动阵列＋被动反射板与离散搜索

```text
r_i' = (x_i, 2H-y_i, z_i)
G_ni = G_direct,i(r_n) + |R| exp(j theta_R) G_image,i(r_n)
p_n  = sum_i A_i G_ni exp(j 2*pi*k_i/32)
A_i  = transducer.amplitude * transducer.power
```

复传递系数直接调用原 `AcousticPropagation.transfer()`，包括原 sinc 方向性、声速、阵元频率、真实与镜像源路径、反射幅值和附加相位。镜像辐射法向也按原代码反射。只有 amplitude>0 的真实阵元拥有优化变量；镜像没有独立相位。

计算流程：

1. 在界面线程读取声场模型快照，校验地址唯一且处于0–255、参数有限。
2. 生成两点/宽区/双宽区采样位置；只对活动阵元建立 `G[n][i]*A[i]` 矩阵。
3. 相位初值转为0–31档；搜索全过程都在这32档上，不先做连续优化。
4. 逐阵元尝试其余31档，使用 `p_new = p_old + G*A*(exp(j phi_new)-exp(j phi_old))`，每次候选 O(Ntarget)，不重新求192路传播。
5. 接受严格降低 cost 的最佳候选；一轮无变化或达到 maxSweeps 时结束。`Converged=false` 表示达迭代上限，而非已达全局最优。
6. 每一帧重建其位置对应的传播矩阵；沿用前帧相位、生成统计和完整256通道编码。
7. 完成后确认当前模型没有变化，再只更新活动阵元 phase；禁用阵元 phase/amplitude 不动。

镜像为无限平面的一次反射近似。未加入有限板衍射、腔内多次往返、液滴声散射耦合、声流、形变、空气膜排液或液桥动力学。

## 5. 相位编码和校准独立性

```text
优化档 k ∈ {0,...,31}
phi_rad = 2*pi*k/32
Transducer.phase = phi_rad/pi = k/16
FPGA_code = wrap32(round(16*(phase + phaseCorrection)))
amplitude<=0 或缺失地址 → FPGA_code=32 (OFF)
```

`phaseCorrection` 与 `phase` 的内部单位都是 π。DPCAT 仅复制校准值供导出/发送校验，**从不调用原阵元的 setPhaseCorrection()**，也不把校准吸收到优化相位中。物理硬件码与优化码可因校准不同；CSV分别输出两者。

这是原工程约定：仿真场使用优化 phase；发送码叠加补偿以抵消硬件偏差。如果校准不是整数档，最终硬件量化仍存在最多半档（5.625°）的舍入误差，实际相位还取决于实测校准准确性。单纯模型不能保证实物相位完全一致。

Mega 只有开/关幅值，正 amplitude 的不同数值不会按比例输出。为匹配当前硬件，建议已安装阵元 amplitude=1，幅值差异通过标定的 power 表达；本模块不会擅自改 amplitude。

## 6. 序列、Wide Trap和速度

默认可编辑列表：`14,12,10,9.6,9.4,9.2,9.0,8.8` mm。Generate Sequence以列表为准；修改 Initial/Final/Steps 后需点击 `Build linear separation list` 更新列表。Optimize Current只取Initial separation。

- A：两个点按列表靠近。
- B：两个宽区按列表靠近，各自宽度W、采样间隔不大于spacing。
- Single Wide：单个中心宽区。例如W=8 mm、spacing=1 mm，生成9点。
- C：先完成A的接近；随后用transitionSteps帧拓宽两个区域，宽度终值为max(W,最终间距)，保证连接；再形成宽度`最终间距+拓宽宽度`的中心宽区；最后用transitionSteps帧收缩到单点。阶段变化不等于已观察到液桥形成。

不整除的宽度/间隔会均匀分配小间距以包括两个端点；重叠点去重，避免重复加权。零间距两点合成一个采样点。目标点上限1024，单条输入列表上限500帧。

点击 Generate 后，可用 Frame 下拉框逐帧检查。`Add Sequence To Animation` 将每帧原始相位/幅值、dwell和目标/统计元数据写入原 Animation 结构；保存整个Simulation `.xml.gz` 后可恢复。DPCAT动画标志阻止普通interpolate插帧；旧动画默认不受影响。

对对称两滴，`separation` 是两目标间距，所以每滴位移为 `|d[k+1]-d[k]|/2`，**单滴理论速度为该位移除以帧间时间**；`|delta d|/delta t`是间距闭合速度。实际液滴速度需相机测量。

## 7. 预览与Mega/FPGA发送

1. `Preview Field` 将当前帧应用到原阵元，用现有声压切片渲染器显示XY或XZ截面，并绘制目标小球。未重写渲染器。
2. 为避免旧Misc设置二次改相位，精确预览时关闭Misc的幅值离散化，关闭相位离散化或使用16的正整数倍（原界面是每π的档数）。
3. `Preview Discrete Sequence` 离线逐帧预览，不连接串口。
4. 在原 `Devs` 面板选择 **MegaFPGA**、Connect，等待 **Mega ready**。PC→Mega及Mega→FPGA均保持250000 baud。
5. `Send Current Frame` 发送当前选中帧的完整状态；`Play Sequence To Mega / FPGA` 发送整个序列。继续使用原 `encodePattern()`、`phases=...`、`phases ok`；不增加第二套协议、不发额外switch。
6. 序列独占当前Mega连接，等上一帧ACK后才发下一帧；期间普通GUI发送不会覆盖待发序列。ACK超时/断线停止序列。单个Mega连接才支持此播放路径。
7. `Frame dwell after ACK` 是**Mega确认后的最小保持时间**，不是严格固定帧周期。实际周期还包括串口传输、Mega处理、线程调度。256路文本帧本身可达数十毫秒，不能用5 ms设置宣称200 Hz完整更新。
8. `phases ok`只确认Mega命令处理，不是FPGA硬件ACK，也不是声场或液滴已经稳定的反馈。
9. `Stop / Cancel` 停止后续帧，已经送出的帧无法撤回；不会自动把所有阵元关闭。旧Anim页Stop按钮也会停止DPCAT播放。Anim页播放DPCAT动画时，有设备则走此可靠发送流程，无设备则离线预览；不会使用旧的全局等待/插值播放。
10. 播放结束后 `Export Playback Timing CSV` 输出实际提交、Mega ACK、保持结束时间。停止或失败时逐帧进度仍留在日志中；完整CSV导出当前仅在成功播放完成后提供。停止后重新生成或预览当前帧可恢复场景相位。

## 8. 导出内容

- Phase CSV：表头 + 256数据行，`index,phase_code,phase_internal,phase_correction,active,optimized_code,amplitude,power`。phase_code是实际校准后发送码；optimized_code是不含校准的搜索码。缺失或禁用地址为32/OFF。不要把phase_code当成phase_internal导入后再次叠加校准。
- Sequence CSV：每帧间距、左右XYZ、中心、宽度、所有目标坐标、C1参考复数、dwell、计划timestamp、cost、mean、std、CV、循环相位变化、目标函数参数、反射板参数与`phase_0`至`phase_255`。
- 每个Sequence CSV附带 `.model.xml.gz` 声源几何/幅值/相位校准快照，支持复现。CSV并非新增协议；若由其他FPGA播放程序使用，其phase列已包含校准。
- Animation `.xml.gz`保存原帧相位和幅值、离散标志、逐帧dwell、采样与统计元数据；原工程会排序恢复阵元与帧号。模型指纹检测反射板、几何、频率、声速、幅值、功率和校准变化，阻止发送过时序列。

## 9. 可运行示例

`examples/dpcat/example_192_DPCAT_75mm.xml.gz` 含现有192阵元几何（原地址64–255）、75 mm反射板和三条动画：

- `example_A_C1_sequence.csv`：8帧，14→12→10→9.6→9.4→9.2→9.0→8.8 mm。
- `example_TWO_WIDE_C2_sequence.csv`：8帧，两宽区，每区8 mm、采样间距≤1 mm。
- `example_POINT_WIDE_SINGLE_C2_sequence.csv`：17帧，8帧接近+4帧拓宽+1帧中心宽区+4帧收缩。

中心约`(21,-37.5,0)` mm，单帧保持50 ms。A先用离散C2（alpha=10）建立声压参考，再用C1维持参考复数；B/C用C2（alpha=10）。所有例子仅离线计算。示例输入来自 `emptyboard192_with_phaseCorrection.xml.gz` 的副本，显式启用其192个既有阵元；未改变原文件和校准。

复算（在工程目录；Windows）：

```text
java -cp "dist/AcousticField3D.jar;jars/*" acousticfield3d.algorithms.dpcat.DpcatExample examples/dpcat/array192_enabled_example.xml.gz examples/dpcat
```

## 10. 仍需实验确认的参数与结论边界

反射系数的幅相及有效反射面位置、声速与环境变化、每阵元功率与校准残差、阵列朝向/有效孔径、alpha/beta/lambda、C1初始参考场、实际稳定节点高度、液滴体积/粘度/表面张力、接近与拓宽轨迹、合并后承载能力、串口时序及真实液滴速度，都需要实验确认。

本版本没有模拟气膜破裂、液桥形成或融合后的形变；没有加入液滴三维势阱与重力稳定约束。**不能把这个软件交付解释为已经实现两滴稳定融合。** 论文参数来自双侧主动阵列，本工程的单板被动反射结构必须重新验证。参考：

- Ultraino：https://github.com/asiermarzo/Ultraino
- SonicSurface：https://github.com/upnalab/SonicSurface
- Li等，2025，Coalescence of multiple pairs of levitated droplets using dual-side phased arrays：https://doi.org/10.1016/j.ultsonch.2025.107327
