#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <signal.h>
#include <fcntl.h>
#include <ucontext.h>
typedef uint8_t u8;
static u8 data[96] __attribute__((aligned(32)));
static int pass, fail;
static void check(const char *n, const u8 *got, const u8 *want, int len){
  if(!memcmp(got,want,len)) pass++; else { fail++; printf("FAIL %s: got %02x%02x%02x%02x.. want %02x%02x%02x%02x..\n", n, got[0],got[1],got[2],got[3],want[0],want[1],want[2],want[3]); }
}
/* every test: ymm1 = a (src1), operand = data+off, result in ymm2; reference from register form with ymm3 */
static const char *cur;
#define VEXTEST(name, INSN, memop, DP, RP, ...) do { cur=name; u8 got[32], want[32], a[32]; \
  for(int i=0;i<32;i++) a[i]=(u8)(i*37+5); \
  register u8 *p asm("r9") = data; (void)p; \
  asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\t" INSN " " memop ", %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])" \
     :: [a]"r"(a),[o]"r"(got),[d]"r"(DP) , ##__VA_ARGS__ : "memory","ymm1","ymm2"); \
  asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\tvmovdqu (%[d]),%%ymm3\n\t" INSN " %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])" \
     :: [a]"r"(a),[o]"r"(want),[d]"r"(RP):"memory","ymm1","ymm2","ymm3"); \
  check(name,got,want,32); } while(0)
u8 rodata_const[32] __attribute__((aligned(32))) = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32};
static void segv(int s, siginfo_t *si, void *c){ char b[160]; int n=snprintf(b,sizeof b,"SEGV in test %s addr=%p rip=%llx\n",cur,si->si_addr,((ucontext_t*)c)->uc_mcontext.gregs[REG_RIP]); write(2,b,n); _exit(78);}
static void sigill(int s){ write(2,"FAIL SIGILL\n",12); _exit(77); }
int main(void){
  signal(SIGILL, sigill); { struct sigaction sa={0}; sa.sa_sigaction=segv; sa.sa_flags=SA_SIGINFO; sigaction(SIGSEGV,&sa,0);}
  for(int i=0;i<96;i++) data[i]=(u8)(i*91+13);
  memcpy(data, rodata_const, 32);
  /* base reg only; disp8; disp32; SIB w/ index+scale; r12 base (needs SIB), r13 base (needs disp), rbp, rsp-relative */
  register u8 *r12 asm("r12") = data; register u8 *r13 asm("r13") = data; long idx = 3, ix11 = 1;
  VEXTEST("base rdx",   "vgf2p8affineqb $0x35,", "(%[d])", data, data);
  VEXTEST("disp8 -32",  "vgf2p8affineqb $0x35,", "-32(%[d])", data+64, data+32);
  VEXTEST("disp32 +40", "vgf2p8affineqb $0x35,", "0x1000(%[d])", data-0x1000+40, data+40);
  VEXTEST("r12 base",   "vgf2p8affineqb $0x35,", "(%[x])", data, data, [x]"r"(r12));
  VEXTEST("r13 base d8","vgf2p8affineqb $0x35,", "0(%[y])", data, data, [y]"r"(r13));
  VEXTEST("sib idx*4",  "%{vex%} vpdpwssd", "(%[d],%[i],4)", data, data+12, [i]"r"(idx));
  VEXTEST("sib r11*8+d8","%{vex%} vpdpbusd", "8(%[d],%[i],8)", data, data+16, [i]"r"(ix11));
  VEXTEST("pclmul",     "vpclmulqdq $0x11,", "(%[d]) ", data, data);
  VEXTEST("aes",        "vaesenc", "(%[d])", data, data);
  VEXTEST("gfmulb",     "vgf2p8mulb", "(%[d])", data, data);
  { u8 got[32], want[32], a[32]; for(int i=0;i<32;i++) a[i]=(u8)(i*37+5); long three=3;
    asm volatile("mov %[i],%%r10\n\tmov %[d],%%r13\n\tvmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\t%{vex%} vpdpwssd 4(%%r13,%%r10,4), %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(got),[d]"r"(data),[i]"r"(three):"memory","r10","r13","ymm1","ymm2");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\tvmovdqu (%[d]),%%ymm3\n\t%{vex%} vpdpwssd %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(want),[d]"r"(data+16):"memory","ymm1","ymm2","ymm3");
    check("sib base r13 index r10 (REX.X/B)", got, want, 32); }
  /* rip-relative */
  { u8 got[32], want[32], a[32]; for(int i=0;i<32;i++) a[i]=(u8)(i*37+5);
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvgf2p8affineqb $0x35, rodata_const(%%rip), %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(got):"memory","ymm1","ymm2");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu rodata_const(%%rip),%%ymm3\n\tvgf2p8affineqb $0x35, %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(want):"memory","ymm1","ymm2","ymm3");
    cur="rip";check("rip-relative + imm8", got, want, 32);
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\t%{vex%} vpdpwssd rodata_const(%%rip), %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(got):"memory","ymm1","ymm2");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[a]),%%ymm2\n\tvmovdqu rodata_const(%%rip),%%ymm3\n\t%{vex%} vpdpwssd %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(want):"memory","ymm1","ymm2","ymm3");
    check("rip-relative no imm", got, want, 32); }
  /* high registers as dst/src1/rm */
  { u8 got[32], want[32], a[32]; for(int i=0;i<32;i++) a[i]=(u8)(i*37+5);
    asm volatile("vmovdqu (%[a]),%%ymm9\n\tvmovdqu (%[d]),%%ymm13\n\tvgf2p8affineqb $0x35, %%ymm13, %%ymm9, %%ymm15\n\tvmovdqu %%ymm15,(%[o])"::[a]"r"(a),[d]"r"(data),[o]"r"(got):"memory","ymm9","ymm13","ymm15");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[d]),%%ymm3\n\tvgf2p8affineqb $0x35, %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[d]"r"(data),[o]"r"(want):"memory","ymm1","ymm2","ymm3");
    check("ymm9/ymm13/ymm15 regs", got, want, 32); }
  /* legacy SHA with memory operand (16B aligned), incl. rip-relative and xmm8+ */
  { u8 got[16], want[16]; 
    asm volatile("movdqu (%[d]),%%xmm4\n\tsha256msg1 rodata_const(%%rip), %%xmm4\n\tmovdqu %%xmm4,(%[o])"::[d]"r"(data+32),[o]"r"(got):"memory","xmm4");
    asm volatile("movdqu (%[d]),%%xmm4\n\tmovdqa rodata_const(%%rip),%%xmm5\n\tsha256msg1 %%xmm5, %%xmm4\n\tmovdqu %%xmm4,(%[o])"::[d]"r"(data+32),[o]"r"(want):"memory","xmm4","xmm5");
    check("sha256msg1 rip mem", got, want, 16);
    asm volatile("movdqu (%[d]),%%xmm10\n\tsha1msg2 (%[e]), %%xmm10\n\tmovdqu %%xmm10,(%[o])"::[d]"r"(data+32),[e]"r"(data),[o]"r"(got):"memory","xmm10");
    asm volatile("movdqu (%[d]),%%xmm10\n\tmovdqa (%[e]),%%xmm11\n\tsha1msg2 %%xmm11, %%xmm10\n\tmovdqu %%xmm10,(%[o])"::[d]"r"(data+32),[e]"r"(data),[o]"r"(want):"memory","xmm10","xmm11");
    check("sha1msg2 xmm10/xmm11 mem", got, want, 16); }
  /* operand page not yet resident: fresh anonymous mapping (zero page, read fault) and a file mapping */
  { u8 *m = mmap(0, 8192, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0); u8 got[32], want[32], a[32]; for(int i=0;i<32;i++) a[i]=(u8)(i*37+5);
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvgf2p8mulb 4096(%[m]), %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[m]"r"(m),[o]"r"(got):"memory","ymm1","ymm2");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvpxor %%ymm3,%%ymm3,%%ymm3\n\tvgf2p8mulb %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[o]"r"(want):"memory","ymm1","ymm2","ymm3");
    check("non-resident anon page", got, want, 32);
    int fd = open("/proc/self/exe", O_RDONLY); u8 *f = mmap(0, 8192, PROT_READ, MAP_PRIVATE, fd, 0); memcpy(got, f+4096, 32);
    u8 g2[32], w2[32];
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvgf2p8mulb 8192(%[m]), %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[m]"r"(f-4096+0),[o]"r"(g2):"memory","ymm1","ymm2");
    asm volatile("vmovdqu (%[a]),%%ymm1\n\tvmovdqu (%[f]),%%ymm3\n\tvgf2p8mulb %%ymm3, %%ymm1, %%ymm2\n\tvmovdqu %%ymm2,(%[o])"::[a]"r"(a),[f]"r"(got),[o]"r"(w2):"memory","ymm1","ymm2","ymm3");
    check("non-resident file page", g2, w2, 32); }
  printf("ADDR: %d passed, %d failed\n", pass, fail);
  return fail ? 1 : 0;
}
