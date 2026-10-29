/*
 * FocusAlg.h - Arduino Mega 2560 版
 *
 * 精简自 SonicSurface 原版 FocusAlg.h：
 *  - 保留 simpleFocusAt / multiFocusAt（快速算法，内存占用小，Mega 可跑）
 *  - 移除 ibp_* 系列（IBP 需要大传播子数组 ~26KB，Mega 仅 8KB SRAM，且无浮点单元，慢到不可用）
 */

#ifndef FOCUSALG_H_
#define FOCUSALG_H_

#include <Arduino.h>

#define MAX_POINTS 4

void simpleFocusAt( float const pointPos[3], byte phases[] );
void multiFocusAt(const int nPoints,  float const pointPos[],  byte phases[] );

#endif /* FOCUSALG_H_ */
