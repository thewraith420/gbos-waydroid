/* The patch's arithmetic, compiled like the kernel compiles it. math.inc comes from `gbos-vexemu.py math`. */
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
typedef uint8_t u8; typedef uint16_t u16; typedef uint32_t u32; typedef uint64_t u64;
typedef int8_t s8; typedef int16_t s16; typedef int32_t s32; typedef int64_t s64;
#define S32_MAX INT32_MAX
#define S32_MIN INT32_MIN
#define __init
#define __ro_after_init
#define core_initcall(f)
#define X86_FEATURE_AVX2 1
#define X86_FEATURE_AES 2
#define X86_FEATURE_PCLMULQDQ 3
#ifdef NATIVE
#define boot_cpu_has(x) 1
#else
#define boot_cpu_has(x) 0
#endif
#include "ops.h"
#include "math.inc"
void w_vnni(u32 *d,const u32 *acc,const u32 *a,const u32 *b,int lanes,int op){ churro_vex_vnni_exec(d,acc,a,b,lanes,op); }
void w_madd52(u64 *d,const u64 *acc,const u64 *a,const u64 *b,int lanes,bool hi){ churro_vex_madd52(d,acc,a,b,lanes,hi); }
void w_gfni(u8 *d,const u8 *a,const u8 *b,int len,int op,u8 imm){ churro_vex_gfni(d,a,b,len,op,imm); }
void w_pclmul(u64 *d,const u64 *a,const u64 *b,int lanes,u8 imm){ churro_pclmul_exec(d,a,b,lanes,imm); }
void w_aes_init(void){ churro_aes_init(); }
void w_aes(u8 *d,const u8 *st,const u8 *key,int lanes,int op){ churro_vex_aes(d,st,key,lanes,op); }
void w_sha(u32 *d,const u32 *s1,const u32 *s2,const u32 *wk,int op,u8 imm){ churro_sha_exec(d,s1,s2,wk,op,imm); }
