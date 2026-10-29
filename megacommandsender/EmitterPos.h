/*
 * EmitterPos.h - Arduino Mega 2560 单板版
 *
 * 单块 FPGA 板 = 256 阵元（16x16）。坐标数组在 EmitterPos.c。
 * 注意：Mega 版内存有限(8KB SRAM)，单板 256 阵元坐标 3KB 可直接放 RAM；
 *       若以后扩到双板 512 阵元，需将 EMITTER_POS 移入 PROGMEM。
 */

#ifndef EMITTERPOS_H_
#define EMITTERPOS_H_

#define N_EMITTERS 256
#define ARRAY_HEIGHT 0.0f    /* 单板在 y=0 平面 */

extern float EMITTER_POS[N_EMITTERS*3];

#endif /* EMITTERPOS_H_ */
