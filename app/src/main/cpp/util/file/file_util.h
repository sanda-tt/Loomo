#ifndef _FILE_FILE_UTIL_H_
#define _FILE_FILE_UTIL_H_

#include "../system_util.h"
#include "../string_util.h"
#include <string>
#include <fstream>
#include <vector>
#include <algorithm>

#ifdef _IS_WIN_
#include <io.h>
#include <direct.h>
#endif

typedef unsigned char uchar;

#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif // !MAX

// 获取文件内容
template <class T>
bool GetFileContent(const std::string& strFilename, T& content)
{
	std::ifstream ifs(strFilename, std::ios::binary);
	if (!ifs.is_open())
	{
		return false;
	}

	content.clear();

	ifs.seekg(0, ifs.end);
	auto fileSize = (size_t)ifs.tellg();
	if (fileSize == 0) // 空文件
		return true;

	auto elemSize = sizeof(std::remove_reference<decltype(content)>::type::value_type);
	content.resize((fileSize + elemSize - 1) / elemSize); // 向上取整

	ifs.seekg(0, ifs.beg);
	ifs.read((char*)(content.data()), fileSize);

	return true;
}

// 根据斜线将输入的路径分割为文件夹（最后一级，输出不含最后的斜线）和文件名
inline bool SplitPath(const std::string& strPath, std::string& strDir, std::string& strFilename)
{
	auto pos1 = strPath.rfind('/');
	auto pos2 = strPath.rfind('\\');
	if (pos1 == std::string::npos && pos2 == std::string::npos)
		return false;

	auto pos = pos1;
	if (pos1 != std::string::npos && pos2 == std::string::npos)
		pos = pos1;
	else if (pos1 == std::string::npos && pos2 != std::string::npos)
		pos = pos2;
	else
		pos = MAX(pos1, pos2);

	// 文件名（也有可能是最后一级文件夹）：strImageFolder.substr(pos+1)
	// 上一级文件夹
	strDir = strPath.substr(0, pos);
	strFilename = strPath.substr(pos + 1, strPath.length() - pos + 1);
	return true;
}

// 根据点将文件名分割成 prefix 和 suffix（扩展名）
// 认为输入的 filename 就是一个单独的文件名，不含路径
inline void SplitFilename(const std::string& filename, std::string& prefix, std::string& suffix)
{
	auto pos = filename.rfind('.');
	if (pos == std::string::npos)
	{
		prefix = filename;
		suffix.clear();
	}
	else
	{
		prefix = filename.substr(0, pos);
		suffix = filename.substr(pos + 1, filename.length() - pos + 1);
	}
}

inline bool IsImageFilename(const std::string& strFilename)
{
	std::string str = strFilename;
	std::transform(strFilename.begin(), strFilename.end(), str.begin(), [](char c) { return (char)tolower(c); });
	const static std::string IMAGE_EXT[] = { ".jpg", ".jpeg", ".bmp", ".png", ".tif" };

	return std::any_of(std::begin(IMAGE_EXT), std::end(IMAGE_EXT),
		[&str](const std::string& ext) {
		return EndsWith(str, ext);
	});
}

#ifdef _IS_WIN_
// 按需递归创建 path 中的各级目录（不创建文件）
// 如果 path 是目录则必须在最后加上斜杠或者设置 isDir，否则最后一级当作文件处理，不进行创建
// 如果结尾为斜杠，则 isDir 无效，path 直接认定为目录
// 反复对同一文件夹下的文件调用，最好直接传入该文件夹路径，并置 isDir 为 true，以减少部分检查
inline bool CreateDirIfNeed(const char* path, bool isDir = false)
{
	const static int MAX_BUFF_LEN = 1000;

	// 指定路径已经存在，直接认为成功
	if (_access(path, 0) == 0) // 部分特殊情况未考虑，如：已经存在的 path 是一个文件，而调用方希望是一个文件夹
		return true;

	const char* tag = path;
	while (true)
	{
		if (*tag == '\\' || *tag == '/' || (*tag == 0 && isDir))
		{
			char buff[MAX_BUFF_LEN] = { 0 };
			memcpy_s(buff, MAX_BUFF_LEN, path, tag - path);
			//buff[tag - path + 1] = '\0';
			if (_access(buff, 6) == -1)
			{
				// 目录创建失败（可能是么有权限，或者有一个与该文件夹同名的文件存在）
				if (_mkdir(buff) == -1)
				{
					return false;
				}
			}
		}
		if (*tag == 0)
		{
			break;
		}
		++tag;
	}
	return true;
}

// 获取文件夹下的所有文件名
inline void GetFilenamesInDir(const std::string& strDirPath, std::vector<std::string>& vecFilenames, bool bRecursive = false)
{
	// 文件句柄
	intptr_t hFile = 0;
	// 文件信息
	struct _finddata_t fileinfo;
	std::string p;
	if ((hFile = _findfirst(p.assign(strDirPath).append("\\*").c_str(), &fileinfo)) != -1)
	{
		do
		{
			//如果是目录，则迭代之
			if (fileinfo.attrib & _A_SUBDIR)
			{
				if (bRecursive
					&& strcmp(fileinfo.name, ".") != 0
					&& strcmp(fileinfo.name, "..") != 0)
					GetFilenamesInDir(p.assign(strDirPath).append("\\").append(fileinfo.name), vecFilenames, bRecursive);
			}
			else //如果不是目录，则直接加入
			{
				vecFilenames.push_back(p.assign(strDirPath).append("\\").append(fileinfo.name));
			}
		} while (_findnext(hFile, &fileinfo) == 0);
		_findclose(hFile);
	}
}

inline void GetImageFilenamesInDir(std::string strFolderPath, std::vector<std::string>& vecFilenames, bool bRecursive = false)
{
	GetFilenamesInDir(strFolderPath, vecFilenames, bRecursive);
	auto it = std::remove_if(vecFilenames.begin(), vecFilenames.end(),
		[](const std::string& str) { return !IsImageFilename(str); });
	vecFilenames.resize(std::distance(vecFilenames.begin(), it));
}
#endif

#endif