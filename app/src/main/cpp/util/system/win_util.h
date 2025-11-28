#ifndef _SYSTEM_WIN_UTIL_H_
#define _SYSTEM_WIN_UTIL_H_

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <ShlObj.h>
#include <Shlwapi.h>
#pragma comment (lib, "shlwapi.lib")
#include <Windows.h>
#include <string>

// 生成 GUID
inline std::string CreateGUID()
{
	const static int MAX_SIZE = 64;

	char buff[MAX_SIZE] = { 0 };

	GUID guid;
	if (::CoCreateGuid(&guid) == S_OK)
	{
		_snprintf_s(buff, sizeof(buff)
			, "%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X"
			, guid.Data1
			, guid.Data2
			, guid.Data3
			, guid.Data4[0], guid.Data4[1]
			, guid.Data4[2], guid.Data4[3], guid.Data4[4], guid.Data4[5]
			, guid.Data4[6], guid.Data4[7]
		);
	}
	return buff;
}

inline bool GetDiskSpaceB(const char* path, __int64& freeBytes, __int64& freeBytesToCaller, __int64& totalBytes)
{
	if (path == nullptr || strlen(path) < 2)
	{
		return false;
	}

	// 26 个英文字母盘符不够用时，新加的盘只能挂载到文件夹（不考虑这种情况），不能分配新盘符（也就是说盘符只有一位）
	char szDisk[3] = "C:";
	szDisk[0] = *path;

	return (GetDiskFreeSpaceExA(szDisk,
		(PULARGE_INTEGER)&freeBytesToCaller,
		(PULARGE_INTEGER)&totalBytes,
		(PULARGE_INTEGER)&freeBytes) == TRUE);
}

// 获取 path 所在硬盘剩余空间（请确保 path 为包含盘符的完整路径）
inline __int64 GetDiskFreeSpaceB(const char* path)
{
	__int64 freeBytes = 0, freeBytesToCaller = 0, totalBytes = 0;
	if (!GetDiskSpaceB(path, freeBytes, freeBytesToCaller, totalBytes))
		return (__int64)-1;
	else
		return freeBytes;
}

inline __int64 GetDiskFreeSpaceMB(const char* path)
{
	auto sz = GetDiskFreeSpaceB(path);
	return sz < 0 ? sz : (sz / (1024 * 1024));
}

// 获取 path 所在硬盘总空间（请确保 path 为包含盘符的完整路径）
inline __int64 GetDiskTotalSpaceB(const char* path)
{
	__int64 freeBytes = 0, freeBytesToCaller = 0, totalBytes = 0;
	if (!GetDiskSpaceB(path, freeBytes, freeBytesToCaller, totalBytes))
		return (__int64)-1;
	else
		return totalBytes;
}
inline __int64 GetDiskTotalSpaceMB(const char* path)
{
	auto sz = GetDiskTotalSpaceB(path);
	return sz < 0 ? sz : (sz / (1024 * 1024));
}

// 获取可用空间最大的磁盘，返回盘符根目录，如“C:\”
inline std::string GetMaxFreeSpaceDisk(__int64* nFreeMB)
{
	DWORD dwSize = MAX_PATH;
	char szLogicalDrives[MAX_PATH] = { 0 };
	DWORD dwResult = GetLogicalDriveStringsA(dwSize, szLogicalDrives);
	__int64 llMaxSpace = 0;
	std::string strMaxDisk;
	if (dwResult > 0 && dwResult <= MAX_PATH)
	{
		char* szSingleDrive = szLogicalDrives;  //从缓冲区起始地址开始
		while (*szSingleDrive)
		{
			if (GetDriveTypeA(szSingleDrive) == DRIVE_FIXED)
			{
				auto space = GetDiskFreeSpaceMB(szSingleDrive);
				if (space > llMaxSpace)
				{
					llMaxSpace = space;
					strMaxDisk = szSingleDrive;
				}
			}
			// 获取下一个驱动器号起始地址
			szSingleDrive += strlen(szSingleDrive) + 1;
		}
	}
	if (nFreeMB != nullptr)
		*nFreeMB = llMaxSpace;
	return strMaxDisk;
}

// 获取当前系统的 ProgramData 目录
inline std::string GetProgramDataDir()
{
	char szPath[MAX_PATH] = { 0 };
	if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_COMMON_APPDATA, NULL, 0, szPath)))
		return szPath;
	else
		return std::string("C:\\ProgramData");
}

// 获取当前模块完整路径
inline std::string GetCurModulePath()
{
	char szPath[MAX_PATH] = { 0 };
	::GetModuleFileNameA(NULL, szPath, MAX_PATH);
	return szPath;
}
// 获取当前模块文件名（不含路径）
inline std::string GetCurModuleFilename()
{
	char szPath[MAX_PATH] = { 0 };
	::GetModuleFileNameA(NULL, szPath, MAX_PATH);
	::PathStripPathA(szPath);
	return szPath;
}
// 获取当前模块所在路径
inline std::string GetCurModuleDir()
{
	char szPath[MAX_PATH] = { 0 };
	::GetModuleFileNameA(NULL, szPath, MAX_PATH);
	::PathRemoveFileSpecA(szPath);
	return szPath;
}

#endif