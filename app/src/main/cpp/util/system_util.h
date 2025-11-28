#ifndef _SYSTEM_UTIL_H_
#define _SYSTEM_UTIL_H_

#if defined (_MSC_VER) || defined (_WIN32) || defined (_WIN64)
#define _IS_WIN_
#include "system/win_util.h"
#include "system/system_util.h"
#elif defined(__ANDROID__)
#define _IS_ANDROID_
#endif

typedef unsigned long long uint64;

// Prints to the provided buffer a nice number of bytes (KB, MB, GB, etc)
// 请为 buf 分配足够内存（至少 32B）
inline void PrettyBytes(char* buf, uint64 bytes)
{
	const char* suffixes[7];
	suffixes[0] = "B";
	suffixes[1] = "KB";
	suffixes[2] = "MB";
	suffixes[3] = "GB";
	suffixes[4] = "TB";
	suffixes[5] = "PB";
	suffixes[6] = "EB";
	uint64 s = 0; // which suffix to use
	double count = static_cast<double>(bytes);
	while (count >= 1024 && s < 7)
	{
		s++;
		count /= 1024;
	}
	if (count - floor(count) == 0.0)
#ifdef _IS_WIN_
		sprintf_s(buf, 32, "%d %s", (int)count, suffixes[s]);
#else
		sprintf(buf, "%d %s", (int)count, suffixes[s]);
#endif
	else
#ifdef _IS_WIN_
		sprintf_s(buf, 32, "%.1f %s", count, suffixes[s]);
#else
		sprintf(buf, "%.1f %s", count, suffixes[s]);
#endif
}
#endif