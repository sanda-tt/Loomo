#ifndef _STRING_STRINT_UTIL_H_
#define _STRING_STRINT_UTIL_H_

#include "../system_util.h"
#include <string>
#include <locale>
#include <codecvt>

// 判断输入的字符串是否以指定字符串结尾
inline bool EndsWith(std::string const & value, std::string const & ending)
{
	if (ending.size() > value.size()) return false;
	return std::equal(ending.rbegin(), ending.rend(), value.rbegin());
}

// 替换输入的字符串
inline std::string& StringReplace(std::string &str, const std::string& strOld, const std::string& strNew)
{
	std::string::size_type pos = 0;
	std::string::size_type lenOld = strOld.size();
	std::string::size_type lenNew = strNew.size();
	pos = str.find(strOld, pos);
	while ((pos != std::string::npos))
	{
		str.replace(pos, lenOld, strNew);
		pos = str.find(strOld, pos + lenNew);
	}

	return str;
}

// 汉字（utf-8 编码）根据拼音比较（以便排序）
inline bool u8_zh_CN_less_than(const std::string & s1, const std::string & s2)
{
	const static std::locale l("zh_CN.utf8");
	const static std::collate<char>& zh_CN_collate = std::use_facet<std::collate<char>>(l);

	const char *pb1 = s1.data();
	const char *pb2 = s2.data();
	return (zh_CN_collate.compare(pb1, pb1 + s1.size(), pb2, pb2 + s2.size()) < 0);
}

// 汉字（ANSI 编码）根据拼音比较（以便排序）
inline bool ansi_zh_CN_less_than(const std::string & s1, const std::string & s2)
{
	const static std::locale l("Chinese_china");
	const static std::collate<char>& zh_CN_collate = std::use_facet<std::collate<char>>(l);

	const char *pb1 = s1.data();
	const char *pb2 = s2.data();
	return (zh_CN_collate.compare(pb1, pb1 + s1.size(), pb2, pb2 + s2.size()) < 0);
}

#ifdef _IS_WIN_
inline std::wstring StringToWString(const std::string& str)
{
	if (str.empty())
		return std::wstring();

	LPCSTR pszSrc = str.c_str();
	int nLen = MultiByteToWideChar(CP_ACP, 0, pszSrc, -1, NULL, 0);
	if (nLen == 0)
		return std::wstring();

	wchar_t* pwszDst = new wchar_t[nLen];
	if (!pwszDst)
		return std::wstring();

	MultiByteToWideChar(CP_ACP, 0, pszSrc, -1, pwszDst, nLen);
	std::wstring wstr(pwszDst);
	delete[] pwszDst;
	pwszDst = NULL;

	return wstr;
}

inline std::string WStringToString(const std::wstring& wstr)
{
	if (wstr.empty())
		return std::string();

	LPCWSTR pwszSrc = wstr.c_str();
	int nLen = WideCharToMultiByte(CP_ACP, 0, pwszSrc, -1, NULL, 0, NULL, NULL);
	if (nLen == 0)
		return std::string();

	char* pszDst = new char[nLen];
	if (!pszDst)
		return std::string();

	WideCharToMultiByte(CP_ACP, 0, pwszSrc, -1, pszDst, nLen, NULL, NULL);
	std::string str(pszDst);
	delete[] pszDst;
	pszDst = NULL;

	return str;
}
#else
// c++ 11 version(比 Windows version 稍慢一点点)
inline bool StringToWString(const std::string& src, std::wstring &wstr)
{
	std::locale sys_locale("");
	const char* data_from = src.c_str();
	const char* data_from_end = src.c_str() + src.size();
	const char* data_from_next = 0;

	wchar_t* data_to = new wchar_t[src.size() + 1];
	wchar_t* data_to_end = data_to + src.size() + 1;
	wchar_t* data_to_next = 0;

	wmemset(data_to, 0, src.size() + 1);

	typedef std::codecvt<wchar_t, char, mbstate_t> convert_facet;
	mbstate_t in_state = { 0 };
	auto result = std::use_facet<convert_facet>(sys_locale).in(
		in_state, data_from, data_from_end, data_from_next,
		data_to, data_to_end, data_to_next);
	if (result == convert_facet::ok)
	{
		wstr = data_to;
		delete[] data_to;
		return true;
	}
	delete[] data_to;
	return false;
}

// c++ 11 version(比 Windows version 稍慢一点点)
inline bool WStringToString(const std::wstring& src, std::string &str)
{
	std::locale sys_locale("");

	const wchar_t* data_from = src.c_str();
	const wchar_t* data_from_end = src.c_str() + src.size();
	const wchar_t* data_from_next = 0;

	int wchar_size = 4;
	char* data_to = new char[(src.size() + 1) * wchar_size];
	char* data_to_end = data_to + (src.size() + 1) * wchar_size;
	char* data_to_next = 0;

	memset(data_to, 0, (src.size() + 1) * wchar_size);

	typedef std::codecvt<wchar_t, char, mbstate_t> convert_facet;
	mbstate_t out_state = { 0 };
	auto result = std::use_facet<convert_facet>(sys_locale).out(
		out_state, data_from, data_from_end, data_from_next,
		data_to, data_to_end, data_to_next);
	if (result == convert_facet::ok)
	{
		str = data_to;
		delete[] data_to;
		return true;
	}
	delete[] data_to;
	return false;
}
#endif

// wstring(unicode) to utf-8 string
inline std::string WStringToUTF8String(const std::wstring &wstr)
{
	std::wstring_convert<std::codecvt_utf8<wchar_t>> conv;
	return conv.to_bytes(wstr);
}

// utf-8 string to wstring(unicode)
inline std::wstring UTF8StringToWString(const std::string &u8str)
{
	std::wstring_convert<std::codecvt_utf8<wchar_t> > conv;
	return conv.from_bytes(u8str);
}

// ansi string(vs default(gb2312)) to utf-8 string
inline std::string ANSIStringToUTF8String(const std::string& ansiStr)
{
	auto wstr = StringToWString(ansiStr);
	return WStringToUTF8String(wstr);
}

// utf-8 string to ansi string(vs default(gb2312))
inline std::string UTF8StringToANSIString(const std::string& u8str)
{
	auto wstr = UTF8StringToWString(u8str);
	return WStringToString(wstr);
}

// 将普通 string 转换为 utf-8 字符串可借助本地宽字符串中转
//std::string str1 = "连通";
//std::string str2 = u8"连通";
//std::wstring t1;
//StringToWString(str1, t1);
//std::string t2 = WStringToUTF8String(t1);
//assert(str2 == t2);

#endif