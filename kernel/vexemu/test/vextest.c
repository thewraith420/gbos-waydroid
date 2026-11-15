/* Test of an in-kernel #UD emulator for newer x86 instructions. Runs as PID 1 in a VM whose CPU lacks them (QEMU TCG,
 * -cpu Skylake-Client), or natively (then the CPU executes them and the test checks the test).
 * Expected values come from the arithmetic that was validated against real hardware (math.inc).
 *   vextest            run what this CPU can run natively (skips AVX-IFMA if absent)
 *   vextest --all      run everything (use inside the VM)                                              */
#define _GNU_SOURCE
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/mman.h>
#include <sys/reboot.h>
#include <linux/reboot.h>
#include <cpuid.h>
typedef uint8_t u8; typedef uint32_t u32; typedef uint64_t u64;
#include "ops.h"
void w_vnni(u32*,const u32*,const u32*,const u32*,int,int);
void w_madd52(u64*,const u64*,const u64*,const u64*,int,bool);
void w_gfni(u8*,const u8*,const u8*,int,int,u8);
void w_pclmul(u64*,const u64*,const u64*,int,u8);
void w_aes_init(void);
void w_aes(u8*,const u8*,const u8*,int,int);
void w_sha(u32*,const u32*,const u32*,const u32*,int,u8);

static u64 sd=0x9E3779B97F4A7C15ULL;
static u64 rnd(void){ sd^=sd<<13; sd^=sd>>7; sd^=sd<<17; return sd; }
static void fill(void*p,int n){ u8*b=p; for(int i=0;i<n;i++){ u64 r=rnd(); int m=r&7; b[i]=m==0?0:m==1?0xff:m==2?0x80:m==3?0x7f:(u8)(r>>8);} }
static long pass,fail; static bool all;
static void verdict(const char*what,bool ok){ if(ok) pass++; else { fail++; if(fail<=12) printf("FAIL  %s\n",what);} }

/* all 16 ymm registers, so we can prove untouched ones stay untouched */
static u8 bank_in[16][32] __attribute__((aligned(32))), bank_out[16][32] __attribute__((aligned(32)));
#define LOADALL "vmovdqu 0(%[b]),%%ymm0\n\tvmovdqu 32(%[b]),%%ymm1\n\tvmovdqu 64(%[b]),%%ymm2\n\tvmovdqu 96(%[b]),%%ymm3\n\t" \
 "vmovdqu 128(%[b]),%%ymm4\n\tvmovdqu 160(%[b]),%%ymm5\n\tvmovdqu 192(%[b]),%%ymm6\n\tvmovdqu 224(%[b]),%%ymm7\n\t" \
 "vmovdqu 256(%[b]),%%ymm8\n\tvmovdqu 288(%[b]),%%ymm9\n\tvmovdqu 320(%[b]),%%ymm10\n\tvmovdqu 352(%[b]),%%ymm11\n\t" \
 "vmovdqu 384(%[b]),%%ymm12\n\tvmovdqu 416(%[b]),%%ymm13\n\tvmovdqu 448(%[b]),%%ymm14\n\tvmovdqu 480(%[b]),%%ymm15\n\t"
#define STOREALL "vmovdqu %%ymm0,0(%[o])\n\tvmovdqu %%ymm1,32(%[o])\n\tvmovdqu %%ymm2,64(%[o])\n\tvmovdqu %%ymm3,96(%[o])\n\t" \
 "vmovdqu %%ymm4,128(%[o])\n\tvmovdqu %%ymm5,160(%[o])\n\tvmovdqu %%ymm6,192(%[o])\n\tvmovdqu %%ymm7,224(%[o])\n\t" \
 "vmovdqu %%ymm8,256(%[o])\n\tvmovdqu %%ymm9,288(%[o])\n\tvmovdqu %%ymm10,320(%[o])\n\tvmovdqu %%ymm11,352(%[o])\n\t" \
 "vmovdqu %%ymm12,384(%[o])\n\tvmovdqu %%ymm13,416(%[o])\n\tvmovdqu %%ymm14,448(%[o])\n\tvmovdqu %%ymm15,480(%[o])\n\t"
#define CLOB "memory","ymm0","ymm1","ymm2","ymm3","ymm4","ymm5","ymm6","ymm7","ymm8","ymm9","ymm10","ymm11","ymm12","ymm13","ymm14","ymm15"
static u8 mem_operand[32] __attribute__((aligned(32)));

/* One test = (instruction text with %%ymm D S1 S2 filled by the macro, expected-result computation). */
#define REGTEST(NAME, INSN, D, S1, S2, EXPECT) do { \
    fill(bank_in,512); \
    u8 *a=bank_in[S1], *b=bank_in[S2], *c=bank_in[D]; (void)a;(void)b;(void)c; \
    asm volatile(LOADALL INSN " %%ymm" #S2 ", %%ymm" #S1 ", %%ymm" #D "\n\t" STOREALL :: [b]"r"(bank_in),[o]"r"(bank_out): CLOB); \
    u8 want[32]; EXPECT; \
    bool ok=!memcmp(bank_out[D],want,32); \
    for(int r=0;r<16;r++) if(r!=D && memcmp(bank_out[r],bank_in[r],32)) ok=false; \
    char t[96]; snprintf(t,sizeof t,"%s ymm%d,ymm%d,ymm%d%s",NAME,D,S1,S2,ok?"":"  (dst or a bystander register wrong)"); verdict(t,ok); } while(0)
#define MEMTEST(NAME, INSN, D, S1, EXPECT) do { \
    fill(bank_in,512); fill(mem_operand,32); \
    u8 *a=bank_in[S1], *b=mem_operand, *c=bank_in[D]; (void)a;(void)b;(void)c; \
    asm volatile(LOADALL INSN " 0(%[m]), %%ymm" #S1 ", %%ymm" #D "\n\t" STOREALL :: [b]"r"(bank_in),[o]"r"(bank_out),[m]"r"(mem_operand): CLOB); \
    u8 want[32]; EXPECT; \
    bool ok=!memcmp(bank_out[D],want,32); \
    for(int r=0;r<16;r++) if(r!=D && memcmp(bank_out[r],bank_in[r],32)) ok=false; \
    char t[96]; snprintf(t,sizeof t,"%s ymm%d,ymm%d,[mem]",NAME,D,S1); verdict(t,ok); } while(0)

#define E_VNNI(op)  w_vnni((u32*)want,(u32*)c,(u32*)a,(u32*)b,8,op)
#define E_GF(op,imm) w_gfni(want,a,b,32,op,imm)
#define E_AES(op)   w_aes(want,a,b,2,op)
#define E_CLMUL(imm) w_pclmul((u64*)want,(u64*)a,(u64*)b,2,imm)
#define E_IFMA(hi)  w_madd52((u64*)want,(u64*)c,(u64*)a,(u64*)b,4,hi)

static void family_tests(int rounds){
  for(int i=0;i<rounds;i++){
    /* register numbers span xmm/ymm 0..15 so VEX.R/X/B and vvvv decoding are exercised */
    REGTEST("vpdpbusd",   "%{vex%} vpdpbusd",   1,2,3,  E_VNNI(CHURRO_VPBUSD));
    REGTEST("vpdpbusds",  "%{vex%} vpdpbusds",  8,9,10, E_VNNI(CHURRO_VPBUSDS));
    REGTEST("vpdpwssd",   "%{vex%} vpdpwssd",   15,0,14,E_VNNI(CHURRO_VPWSSD));
    REGTEST("vpdpwssds",  "%{vex%} vpdpwssds",  4,12,7, E_VNNI(CHURRO_VPWSSDS));
    REGTEST("vgf2p8mulb", "vgf2p8mulb",       11,11,13,E_GF(CHURRO_GFMULB,0));
    MEMTEST("vpdpbusd",   "%{vex%} vpdpbusd",   6,5,   E_VNNI(CHURRO_VPBUSD));
    MEMTEST("vpdpwssds",  "%{vex%} vpdpwssds",  13,8,  E_VNNI(CHURRO_VPWSSDS));
    REGTEST("vaesenc",    "vaesenc",          2,3,4,  E_AES(CHURRO_VAESENC));
    REGTEST("vaesenclast","vaesenclast",      9,10,11,E_AES(CHURRO_VAESENCLAST));
    REGTEST("vaesdec",    "vaesdec",          14,1,15,E_AES(CHURRO_VAESDEC));
    REGTEST("vaesdeclast","vaesdeclast",      0,7,6,  E_AES(CHURRO_VAESDECLAST));
    MEMTEST("vaesenc",    "vaesenc",          10,3,   E_AES(CHURRO_VAESENC));
    REGTEST("vpclmulqdq $0x00,","vpclmulqdq $0x00,", 5,6,7, E_CLMUL(0x00));
    REGTEST("vpclmulqdq $0x11,","vpclmulqdq $0x11,", 12,13,14,E_CLMUL(0x11));
    REGTEST("vpclmulqdq $0x10,","vpclmulqdq $0x10,", 3,1,2, E_CLMUL(0x10));
    MEMTEST("vpclmulqdq $0x01,","vpclmulqdq $0x01,", 8,2, E_CLMUL(0x01));
    REGTEST("vgf2p8affineqb $0x63,","vgf2p8affineqb $0x63,", 1,2,3, E_GF(CHURRO_GFAFQ,0x63));
    REGTEST("vgf2p8affineqb $0x00,","vgf2p8affineqb $0x00,", 9,8,10, E_GF(CHURRO_GFAFQ,0x00));
    REGTEST("vgf2p8affineinvqb $0xa5,","vgf2p8affineinvqb $0xa5,", 15,14,13, E_GF(CHURRO_GFAINVQ,0xa5));
    MEMTEST("vgf2p8affineinvqb $0x63,","vgf2p8affineinvqb $0x63,", 4,5, E_GF(CHURRO_GFAINVQ,0x63));
    MEMTEST("vgf2p8mulb", "vgf2p8mulb",       7,0,    E_GF(CHURRO_GFMULB,0));
    if(all){
      REGTEST("vpmadd52luq","%{vex%} vpmadd52luq",1,2,3,  E_IFMA(false));
      REGTEST("vpmadd52huq","%{vex%} vpmadd52huq",12,5,9, E_IFMA(true));
    }
  }
}
/* SHA-NI is legacy-encoded (SSE): xmm only, implicit xmm0 */
static void sha_tests(int rounds){
  for(int i=0;i<rounds;i++){
    u32 s1[4],s2[4],wk[4],want[4],got[4]; fill(s1,16);fill(s2,16);fill(wk,16);
#define SHATEST(NAME,INSN,OP,IMM) do{ u32 d[4]; memcpy(d,s1,16); \
      asm volatile("vmovdqu (%[w]),%%xmm0\n\tvmovdqu (%[d]),%%xmm9\n\tvmovdqu (%[s]),%%xmm12\n\t" INSN "\n\tvmovdqu %%xmm9,(%[o])" \
        ::[w]"r"(wk),[d]"r"(s1),[s]"r"(s2),[o]"r"(got):"memory","xmm0","xmm9","xmm12"); \
      w_sha(want,s1,s2,wk,OP,IMM); (void)d; verdict(NAME " xmm9,xmm12",!memcmp(got,want,16)); }while(0)
    SHATEST("sha256rnds2","sha256rnds2 %%xmm0, %%xmm12, %%xmm9",CHURRO_SHA256RNDS2,0);
    SHATEST("sha256msg1","sha256msg1 %%xmm12, %%xmm9",CHURRO_SHA256MSG1,0);
    SHATEST("sha256msg2","sha256msg2 %%xmm12, %%xmm9",CHURRO_SHA256MSG2,0);
    SHATEST("sha1nexte","sha1nexte %%xmm12, %%xmm9",CHURRO_SHA1NEXTE,0);
    SHATEST("sha1msg1","sha1msg1 %%xmm12, %%xmm9",CHURRO_SHA1MSG1,0);
    SHATEST("sha1msg2","sha1msg2 %%xmm12, %%xmm9",CHURRO_SHA1MSG2,0);
    SHATEST("sha1rnds4 $2","sha1rnds4 $2, %%xmm12, %%xmm9",CHURRO_SHA1RNDS4,2);
  }
}
/* a long dependent chain: exercises the kernel's run-ahead path and accumulates a checksum we can predict */
static void loop_test(int n){
  u32 acc[8],a[8],b[8],ref[8]; fill(acc,32);fill(a,32);fill(b,32); memcpy(ref,acc,32);
  for(int i=0;i<n;i++){ u32 t[8]; w_vnni(t,ref,a,b,8,CHURRO_VPWSSD); memcpy(ref,t,32); }
  u8 *pa=(u8*)a,*pb=(u8*)b,*pc=(u8*)acc;
  asm volatile("vmovdqu (%[c]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\tvmovdqu (%[b]),%%ymm3\n\t"
               "1: %{vex%} vpdpwssd %%ymm3,%%ymm2,%%ymm1\n\tdec %[n]\n\tjnz 1b\n\t"
               "vmovdqu %%ymm1,(%[c])":[n]"+r"(n):[a]"r"(pa),[b]"r"(pb),[c]"r"(pc):"memory","ymm1","ymm2","ymm3","cc");
  verdict("vpdpwssd dependent loop",!memcmp(acc,ref,32));
}
static void stress(int kids,int iters){
  for(int k=0;k<kids;k++){ pid_t p=fork(); if(p==0){ sd+=k*7919; pass=fail=0; family_tests(iters); sha_tests(iters); _exit(fail?1:0);} }
  int bad=0; for(int k=0;k<kids;k++){ int st; wait(&st); if(!WIFEXITED(st)||WEXITSTATUS(st)) bad++; }
  verdict("concurrent processes (preemption / FPU state)",bad==0);
}
static void sigill(int s){ printf("FAIL  SIGILL: an instruction was not emulated\n"); fflush(stdout);
  if(getpid()==1){ sync(); reboot(LINUX_REBOOT_CMD_POWER_OFF);} _exit(77); }
int main(int argc,char**argv){
  setvbuf(stdout,0,_IONBF,0);
  all = argc>1 && !strcmp(argv[1],"--all");
  unsigned a,b,c,d; __cpuid_count(7,0,a,b,c,d);
  printf("CPU: gfni=%d vaes=%d vpclmulqdq=%d sha=%d  (a CPU that lacks them makes the kernel emulate them)\n",!!(c&(1<<8)),!!(c&(1<<9)),!!(c&(1<<10)),!!(b&(1<<29)));
  __cpuid_count(7,1,a,b,c,d); bool have_ifma=a&(1<<23), have_vnni=a&(1<<4); printf("     avx_vnni=%d avx_ifma=%d\n",have_vnni,have_ifma);
  if(!all && !have_ifma) printf("     (native run: AVX-IFMA tests skipped on this CPU)\n");
  signal(SIGILL,sigill);
  w_aes_init();
  family_tests(300); sha_tests(300);
  loop_test(200000);
  stress(6,60);
  printf("RESULT: %ld passed, %ld failed\n",pass,fail);
  if(getpid()==1){ sync(); reboot(LINUX_REBOOT_CMD_POWER_OFF); }
  return fail!=0;
}
