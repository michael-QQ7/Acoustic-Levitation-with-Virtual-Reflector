# KT40-T1011 / 17 Vpp 正弦驱动参数更新
## 使用
1. 解压工程，在 NetBeans 中打开 AcousticFieldSim 文件夹。
2. Clean and Build，再 Run。完整源码已用 Java 17 的 --release 11 编译通过；未进行桌面图形界面和硬件联机测试。
3. 新建换能器默认 FR=40000 Hz、W=0.010 m、P=1.28；新增窗口的第一个预设已改为 KT40-T1011。
4. 已有 .sim / emptyboard 阵列会保留文件中的参数。打开后全选换能器，将 W 改为 0.010、P 改为 1.28、FR 改为 40000，每项输入后按 Enter，再保存。需要全幅发射的通道设 A=1；应关闭的通道保持 A=0。
## 修改文件
- src/acousticfield3d/simulation/Transducer.java
- src/acousticfield3d/gui/AddTransducersForm.java
- src/acousticfield3d/gui/AddTransducersForm.form
- src/acousticfield3d/gui/panels/TransducersPanel.java
- src/acousticfield3d/gui/panels/TransducersPanel.form
对应 .form 已同步，NetBeans 重新生成界面时不会恢复旧默认值。
## 模型含义
以厂家最低 SPL 108 dB（10 Vrms，0.30 m）作为估计基准：
P = 20e-6 × 10^(108/20) × 0.30 × (17/20) ≈ 1.281 Pa peak @ 1 m。
其中正弦驱动 Vrms=17/(2√2)，声压峰值=√2×声压 RMS；假设线性电压响应和 1/r 扩散，忽略空气吸收。
P 不是瓦特，1.28 也不是实测标定值。W=0.010 是采用名义外径作为有效孔径的近似。
本次保留原 sinc 指向性模型，没有实现圆活塞 2J1(x)/x 模型，也没有按 75° 波束角拟合。
阵列坐标、相位算法、相位补偿、串口协议和波特率保持原工程设置。导出的旧 Matlab 计算模板未修改。
原 build 缓存未打包，避免运行旧 class；请按以上步骤重新构建。
