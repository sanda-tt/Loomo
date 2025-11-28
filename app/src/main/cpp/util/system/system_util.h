#pragma once
#include <stdio.h>
#include <stdint.h>

#if defined(_WIN32)
#include <windows.h>
#include <wincrypt.h>
#endif

/********* 以下内容来自 UUID4 *********/
#define UUID4_VERSION "0.1.0"
#define UUID4_LEN 37

enum {
	UUID4_ESUCCESS = 0,
	UUID4_EFAILURE = -1
};

static uint64_t _SYSTEM_UTIL_seed_[2];
static int _SYSTEM_UTIL_seeded_ = 0;

static uint64_t xorshift128plus(uint64_t *s) {
	/* http://xorshift.di.unimi.it/xorshift128plus.c */
	uint64_t s1 = s[0];
	const uint64_t s0 = s[1];
	s[0] = s0;
	s1 ^= s1 << 23;
	s[1] = s1 ^ s0 ^ (s1 >> 18) ^ (s0 >> 5);
	return s[1] + s0;
}

static int init_seed(void) {
#if defined(__linux__) || defined(__APPLE__) || defined(__FreeBSD__)
	int res;
	FILE *fp = fopen("/dev/urandom", "rb");
	if (!fp) {
		return UUID4_EFAILURE;
	}
	res = fread(_SYSTEM_UTIL_seed_, 1, sizeof(_SYSTEM_UTIL_seed_), fp);
	fclose(fp);
	if (res != sizeof(_SYSTEM_UTIL_seed_)) {
		return UUID4_EFAILURE;
	}
#elif defined(_WIN32)
	int res;
	HCRYPTPROV hCryptProv;
	res = CryptAcquireContext(
		&hCryptProv, NULL, NULL, PROV_RSA_FULL, CRYPT_VERIFYCONTEXT);
	if (!res) {
		return UUID4_EFAILURE;
	}
	res = CryptGenRandom(hCryptProv, (DWORD) sizeof(_SYSTEM_UTIL_seed_), (PBYTE)_SYSTEM_UTIL_seed_);
	CryptReleaseContext(hCryptProv, 0);
	if (!res) {
		return UUID4_EFAILURE;
	}
#else
#error "unsupported platform"
#endif
	return UUID4_ESUCCESS;
}

// 请确保 dst 至少有 UUID4_LEN 个字节
inline int uuid4_generate(char* dst) {
	static const char *tpl = "xxxxxxxx-xxxx-xxxx-yxxx-xxxxxxxxxxxx";
	static const char *chars = "0123456789ABCDEF";
	union { unsigned char b[16]; uint64_t word[2]; } s;
	const char *p;
	int i, n;
	/* _SYSTEM_UTIL_seed_? */
	if (!_SYSTEM_UTIL_seeded_) {
		do {
			int err = init_seed();
			if (err != UUID4_ESUCCESS) {
				return err;
			}
		} while (_SYSTEM_UTIL_seed_[0] == 0 && _SYSTEM_UTIL_seed_[1] == 0);
		_SYSTEM_UTIL_seeded_ = 1;
	}
	/* get random */
	s.word[0] = xorshift128plus(_SYSTEM_UTIL_seed_);
	s.word[1] = xorshift128plus(_SYSTEM_UTIL_seed_);
	/* build string */
	p = tpl;
	i = 0;
	while (*p) {
		n = s.b[i >> 1];
		n = (i & 1) ? (n >> 4) : (n & 0xf);
		switch (*p) {
		case 'x': *dst = chars[n];              i++;  break;
		case 'y': *dst = chars[(n & 0x3) + 8];  i++;  break;
		default: *dst = *p;
		}
		dst++, p++;
	}
	*dst = '\0';
	/* return ok */
	return UUID4_ESUCCESS;
}
/********* End Of UUID4 *********/
