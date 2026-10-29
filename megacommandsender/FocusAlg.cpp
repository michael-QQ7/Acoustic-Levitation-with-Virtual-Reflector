/*
 * FocusAlg.c - Arduino Mega 2560 版
 *
 * 精简自 SonicSurface 原版 FocusAlg.c：
 *  - 保留 simpleFocusAt / multiFocusAt（距离-相位延迟，O(N_EMITTERS)，无需大数组）
 *  - 移除 ibp_*（IBP 复数迭代需要 tpA/tpB 传播子数组 ~16KB+4KB，Mega 8KB SRAM 装不下；
 *          且 AVR 无硬件浮点，复数迭代一个周期要几秒，不可用）
 *
 * 常量与原版一致：
 *   SOUND_SPEED = 346 m/s  (25°C 空气)
 *   FREQ        = 40000 Hz
 *   PHASE_RES   = 32       (5-bit 相位量化，32=off)
 *   波长 = 346/40000 = 8.65mm
 */

#include "FocusAlg.h"
#include "EmitterPos.h"
#include <Arduino.h>

#define PHASE_RES 32

#define SOUND_SPEED 346
#define FREQ 40000

float vectorDist(const float vecA[], const float vecB[]){
	const float diffX = vecA[0] - vecB[0];
	const float diffY = vecA[1] - vecB[1];
	const float diffZ = vecA[2] - vecB[2];
	return sqrtf( diffX*diffX + diffY*diffY + diffZ*diffZ);
}

/* 单焦点：每个阵元的相位 = 使其波前在目标点同相所需的延迟 */
void simpleFocusAt( float const pointPos[3], byte phases[] ){
	const float wavelength = SOUND_SPEED / (float) FREQ;
	for (int i = 0; i < N_EMITTERS; ++i) {
		const float dist = vectorDist(& EMITTER_POS[i*3] , pointPos);
		const float lambdas = dist / wavelength;
		const float targetPhase = 1.0 - (lambdas - (int)lambdas);
		phases[i] = (byte)(targetPhase * PHASE_RES);
	}
}

/* 多焦点(轮询)：把阵元按 i%nPoints 分给各个焦点，每个焦点各取相位延迟。
 * 比 IBP 快一个数量级，但焦点间会互相干扰，适合近距离/演示。 */
void multiFocusAt(const int nPoints,  float const pointPos[],  byte phases[] ){
	const float wavelength = SOUND_SPEED / (float) FREQ;
	for (int i = 0; i < N_EMITTERS; ++i) {
		const int targetPoint = i % nPoints;
		const float dist = vectorDist(& EMITTER_POS[i*3] , & pointPos[targetPoint*3]);
		const float lambdas = dist / wavelength;
		const float targetPhase = 1.0 - (lambdas - (int)lambdas);
		phases[i] = (byte)(targetPhase * PHASE_RES);
	}
}
