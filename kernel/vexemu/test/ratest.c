/* Run-ahead test: real SHA-NI SHA-256 and AVX-VNNI Adler-32 loops (the compiler's own instruction streams, with the
 * loads/shuffles/adds between the emulated instructions) against plain C references. Passes natively on a CPU that has
 * the instructions, and in the VM with gbos_vexemu loaded - with run-ahead on or off, results must be identical. */
#include <immintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

static const uint32_t K[64] = {
 0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
 0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
 0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
 0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
 0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
 0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2 };
static const uint32_t H0[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};

#define ROR(x,n) (((x)>>(n))|((x)<<(32-(n))))
static void sha_ref_block(uint32_t *h, const uint8_t *p) {
  uint32_t w[64], a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],hh=h[7];
  for (int i=0;i<16;i++) w[i]=p[4*i]<<24|p[4*i+1]<<16|p[4*i+2]<<8|p[4*i+3];
  for (int i=16;i<64;i++){ uint32_t s0=ROR(w[i-15],7)^ROR(w[i-15],18)^(w[i-15]>>3), s1=ROR(w[i-2],17)^ROR(w[i-2],19)^(w[i-2]>>10); w[i]=w[i-16]+s0+w[i-7]+s1; }
  for (int i=0;i<64;i++){ uint32_t t1=hh+(ROR(e,6)^ROR(e,11)^ROR(e,25))+((e&f)^(~e&g))+K[i]+w[i], t2=(ROR(a,2)^ROR(a,13)^ROR(a,22))+((a&b)^(a&c)^(b&c));
    hh=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2; }
  h[0]+=a;h[1]+=b;h[2]+=c;h[3]+=d;h[4]+=e;h[5]+=f;h[6]+=g;h[7]+=hh;
}
__attribute__((target("sha,ssse3,sse4.1")))
static void sha_ni_blocks(uint32_t *h, const uint8_t *p, size_t nblk) {
  const __m128i MASK = _mm_set_epi64x(0x0c0d0e0f08090a0bULL, 0x0405060700010203ULL);
  __m128i tmp = _mm_loadu_si128((const __m128i*)&h[0]), st1 = _mm_loadu_si128((const __m128i*)&h[4]), st0, w[4];
  tmp = _mm_shuffle_epi32(tmp, 0xB1); st1 = _mm_shuffle_epi32(st1, 0x1B);
  st0 = _mm_alignr_epi8(tmp, st1, 8); st1 = _mm_blend_epi16(st1, tmp, 0xF0);
  while (nblk--) {
    __m128i s0 = st0, s1 = st1;
    for (int i = 0; i < 16; i++) {
      if (i < 4) w[i] = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(p + 16*i)), MASK);
      else w[i&3] = _mm_sha256msg2_epu32(_mm_add_epi32(_mm_sha256msg1_epu32(w[i&3], w[(i+1)&3]), _mm_alignr_epi8(w[(i+3)&3], w[(i+2)&3], 4)), w[(i+3)&3]);
      __m128i msg = _mm_add_epi32(w[i&3], _mm_loadu_si128((const __m128i*)&K[4*i]));
      st1 = _mm_sha256rnds2_epu32(st1, st0, msg);
      msg = _mm_shuffle_epi32(msg, 0x0E);
      st0 = _mm_sha256rnds2_epu32(st0, st1, msg);
    }
    st0 = _mm_add_epi32(st0, s0); st1 = _mm_add_epi32(st1, s1); p += 64;
  }
  tmp = _mm_shuffle_epi32(st0, 0x1B); st1 = _mm_shuffle_epi32(st1, 0xB1);
  st0 = _mm_blend_epi16(tmp, st1, 0xF0); st1 = _mm_alignr_epi8(st1, tmp, 8);
  _mm_storeu_si128((__m128i*)&h[0], st0); _mm_storeu_si128((__m128i*)&h[4], st1);
}

static uint32_t adler_ref(const uint8_t *p, size_t n) { uint32_t a=1,b=0; while(n--){ a=(a+*p++)%65521; b=(b+a)%65521; } return b<<16|a; }
__attribute__((target("avx2,avxvnni")))
static uint32_t adler_vnni(const uint8_t *p, size_t n) {   /* n multiple of 32 */
  uint32_t a = 1, b = 0;
  const __m256i W = _mm256_setr_epi8(32,31,30,29,28,27,26,25,24,23,22,21,20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1), Z = _mm256_setzero_si256();
  while (n) {
    size_t chunk = n < 32*100 ? n : 32*100; n -= chunk;
    __m256i vs1 = Z, vs2 = Z, vprev = Z;
    for (size_t i = 0; i < chunk; i += 32) {
      __m256i v = _mm256_loadu_si256((const __m256i*)(p + i));
      vprev = _mm256_add_epi32(vprev, vs1);                     /* 32 * (running a) handled below */
      vs1 = _mm256_add_epi32(vs1, _mm256_sad_epu8(v, Z));
      vs2 = _mm256_dpbusd_avx_epi32(vs2, v, W);
    }
    uint32_t s1[8], s2[8], pr[8];
    _mm256_storeu_si256((__m256i*)s1, vs1); _mm256_storeu_si256((__m256i*)s2, vs2); _mm256_storeu_si256((__m256i*)pr, vprev);
    uint64_t sum1 = 0, sum2 = 0, sumprev = 0;
    for (int i = 0; i < 8; i++) { sum1 += s1[i]; sum2 += s2[i]; sumprev += pr[i]; }
    /* b += chunk*a_old + weighted sum; here vprev accumulated vs1 *before* each block: sum of running byte sums */
    b = (uint32_t)((b + (uint64_t)(chunk) * a + 32 * sumprev + sum2) % 65521);
    a = (uint32_t)((a + sum1) % 65521); p += chunk;
  }
  return b << 16 | a;
}


/* ---- SHA-1 (sha1rnds4/sha1nexte/sha1msg1/sha1msg2 + pxor/pshufb/movdqa ...) ---- */
static void sha1_ref_block(uint32_t *h, const uint8_t *p) {
  uint32_t w[80], a=h[0],b=h[1],c=h[2],d=h[3],e=h[4];
  for (int i=0;i<16;i++) w[i]=p[4*i]<<24|p[4*i+1]<<16|p[4*i+2]<<8|p[4*i+3];
  for (int i=16;i<80;i++) { uint32_t x=w[i-3]^w[i-8]^w[i-14]^w[i-16]; w[i]=(x<<1)|(x>>31); }
  for (int i=0;i<80;i++){ uint32_t f,k; if(i<20){f=(b&c)|(~b&d);k=0x5a827999;} else if(i<40){f=b^c^d;k=0x6ed9eba1;} else if(i<60){f=(b&c)|(b&d)|(c&d);k=0x8f1bbcdc;} else {f=b^c^d;k=0xca62c1d6;}
    uint32_t t=((a<<5)|(a>>27))+f+e+k+w[i]; e=d;d=c;c=(b<<30)|(b>>2);b=a;a=t; }
  h[0]+=a;h[1]+=b;h[2]+=c;h[3]+=d;h[4]+=e;
}
__attribute__((target("sha,ssse3,sse4.1")))
static void sha1_ni_blocks(uint32_t *h, const uint8_t *p, size_t nblk) {
  const __m128i MASK = _mm_set_epi64x(0x0001020304050607ULL, 0x08090a0b0c0d0e0fULL);
  __m128i abcd = _mm_shuffle_epi32(_mm_loadu_si128((const __m128i*)h), 0x1B), e0 = _mm_set_epi32(h[4], 0, 0, 0), e1, w[4];
  while (nblk--) {
    __m128i abcd_s = abcd, e0_s = e0;
    for (int i = 0; i < 20; i++) {                         /* 4 rounds per step */
      if (i < 4) w[i] = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(p + 16*i)), MASK);
      else w[i&3] = _mm_sha1msg2_epu32(_mm_xor_si128(_mm_sha1msg1_epu32(w[i&3], w[(i+1)&3]), w[(i+2)&3]), w[(i+3)&3]);
      if (i == 0) e0 = _mm_add_epi32(e0, w[0]); else e0 = _mm_sha1nexte_epu32(e1, w[i&3]);
      e1 = abcd;
      switch (i / 5) {
        case 0: abcd = _mm_sha1rnds4_epu32(abcd, e0, 0); break;
        case 1: abcd = _mm_sha1rnds4_epu32(abcd, e0, 1); break;
        case 2: abcd = _mm_sha1rnds4_epu32(abcd, e0, 2); break;
        default: abcd = _mm_sha1rnds4_epu32(abcd, e0, 3); break;
      }
    }
    e0 = _mm_sha1nexte_epu32(e1, e0_s); abcd = _mm_add_epi32(abcd, abcd_s); p += 64;
  }
  _mm_storeu_si128((__m128i*)h, _mm_shuffle_epi32(abcd, 0x1B)); h[4] = _mm_extract_epi32(e0, 3);
}

/* ---- VAES + VPCLMULQDQ with stores: 256-bit forms against the 128-bit AES-NI / PCLMULQDQ instructions ---- */
__attribute__((target("avx2,vaes,vpclmulqdq,aes,pclmul")))
static void aes_vaes(uint8_t *out, const uint8_t *in, size_t n, const uint8_t *rk /* 11 x 16 */) {   /* n multiple of 32 */
  __m256i k[11]; for (int i = 0; i < 11; i++) k[i] = _mm256_broadcastsi128_si256(_mm_loadu_si128((const __m128i*)(rk + 16*i)));
  for (size_t i = 0; i < n; i += 32) {
    __m256i x = _mm256_xor_si256(_mm256_loadu_si256((const __m256i*)(in + i)), k[0]);
    for (int r = 1; r < 10; r++) x = _mm256_aesenc_epi128(x, k[r]);
    x = _mm256_aesenclast_epi128(x, k[10]);
    _mm256_storeu_si256((__m256i*)(out + i), x);
  }
}
__attribute__((target("aes,pclmul")))
static void aes_ni(uint8_t *out, const uint8_t *in, size_t n, const uint8_t *rk) {
  for (size_t i = 0; i < n; i += 16) {
    __m128i x = _mm_xor_si128(_mm_loadu_si128((const __m128i*)(in + i)), _mm_loadu_si128((const __m128i*)rk));
    for (int r = 1; r < 10; r++) x = _mm_aesenc_si128(x, _mm_loadu_si128((const __m128i*)(rk + 16*r)));
    _mm_storeu_si128((__m128i*)(out + i), _mm_aesenclast_si128(x, _mm_loadu_si128((const __m128i*)(rk + 160))));
  }
}
__attribute__((target("avx2,vpclmulqdq,pclmul")))
static void clmul_v(uint8_t *out, const uint8_t *a, const uint8_t *b, size_t n) {
  for (size_t i = 0; i < n; i += 32) {
    __m256i x = _mm256_loadu_si256((const __m256i*)(a + i)), y = _mm256_loadu_si256((const __m256i*)(b + i));
    __m256i lo = _mm256_clmulepi64_epi128(x, y, 0x00), hi = _mm256_clmulepi64_epi128(x, y, 0x11), mid = _mm256_clmulepi64_epi128(x, y, 0x10);
    _mm256_storeu_si256((__m256i*)(out + i), _mm256_xor_si256(_mm256_xor_si256(lo, hi), mid));
  }
}
__attribute__((target("pclmul")))
static void clmul_n(uint8_t *out, const uint8_t *a, const uint8_t *b, size_t n) {
  for (size_t i = 0; i < n; i += 16) {
    __m128i x = _mm_loadu_si128((const __m128i*)(a + i)), y = _mm_loadu_si128((const __m128i*)(b + i));
    _mm_storeu_si128((__m128i*)(out + i), _mm_xor_si128(_mm_xor_si128(_mm_clmulepi64_si128(x, y, 0x00), _mm_clmulepi64_si128(x, y, 0x11)), _mm_clmulepi64_si128(x, y, 0x10)));
  }
}

int main2(int *failp) {
  int fail = 0;
  for (int t = 0; t < 4; t++) {                                     /* SHA-1 */
    size_t nblk = t == 3 ? 2048 : (size_t)(1 + t * 29), len = nblk * 64;
    uint8_t *buf = malloc(len); for (size_t i = 0; i < len; i++) buf[i] = rand() >> 4;
    uint32_t hr[5] = {0x67452301,0xefcdab89,0x98badcfe,0x10325476,0xc3d2e1f0}, hn[5]; memcpy(hn, hr, 20);
    for (size_t b = 0; b < nblk; b++) sha1_ref_block(hr, buf + 64*b);
    sha1_ni_blocks(hn, buf, nblk);
    if (memcmp(hr, hn, 20)) { printf("FAIL sha1 %zu blocks\n", nblk); fail++; } else printf("ok   sha1 %zu blocks\n", nblk);
    free(buf);
  }
  for (int t = 0; t < 3; t++) {                                     /* VAES vs AES-NI, VPCLMULQDQ vs PCLMULQDQ */
    size_t len = 32 * (size_t)(t == 2 ? 4096 : 5 + t * 40); uint8_t rk[176], *in = malloc(len), *o1 = malloc(len), *o2 = malloc(len), *in2 = malloc(len);
    for (int i = 0; i < 176; i++) rk[i] = rand() >> 3; for (size_t i = 0; i < len; i++) { in[i] = rand() >> 2; in2[i] = rand() >> 6; }
    aes_vaes(o1, in, len, rk); aes_ni(o2, in, len, rk);
    if (memcmp(o1, o2, len)) { printf("FAIL vaes %zu bytes\n", len); fail++; } else printf("ok   vaes %zu bytes\n", len);
    clmul_v(o1, in, in2, len); clmul_n(o2, in, in2, len);
    if (memcmp(o1, o2, len)) { printf("FAIL vpclmulqdq %zu bytes\n", len); fail++; } else printf("ok   vpclmulqdq %zu bytes\n", len);
    free(in); free(in2); free(o1); free(o2);
  }
  *failp += fail; return fail;
}

int main(void) {
  int fail = 0;
  srand(12345);
  for (int t = 0; t < 6; t++) {
    size_t nblk = t == 5 ? 4096 : (size_t)(1 + t * 37), len = nblk * 64;
    uint8_t *buf = malloc(len); for (size_t i = 0; i < len; i++) buf[i] = rand() >> 3;
    uint32_t hr[8], hn[8]; memcpy(hr, H0, 32); memcpy(hn, H0, 32);
    for (size_t b = 0; b < nblk; b++) sha_ref_block(hr, buf + 64*b);
    sha_ni_blocks(hn, buf, nblk);
    if (memcmp(hr, hn, 32)) { printf("FAIL sha256 %zu blocks\n", nblk); fail++; } else printf("ok   sha256 %zu blocks\n", nblk);
    free(buf);
  }
  for (int t = 0; t < 5; t++) {
    size_t len = 32 * (size_t)(t == 4 ? 40000 : 3 + t * 211); uint8_t *buf = malloc(len); for (size_t i = 0; i < len; i++) buf[i] = rand() >> 5;
    uint32_t r = adler_ref(buf, len), v = adler_vnni(buf, len);
    if (r != v) { printf("FAIL adler32 %zu bytes: want %08x got %08x\n", len, r, v); fail++; } else printf("ok   adler32 %zu bytes\n", len);
    free(buf);
  }
  main2(&fail);
  printf("RATEST: %s\n", fail ? "FAILED" : "all passed");
  return fail ? 1 : 0;
}
