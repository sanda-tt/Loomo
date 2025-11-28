#ifndef _CV_CV_UTIL_H_
#define _CV_CV_UTIL_H_

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
	
// 将 Rect 约束到图像尺寸中
inline cv::Rect ForceRectIntoImage(const cv::Size & imgSz, const cv::Rect & rc)
{
	return cv::Rect(0, 0, imgSz.width, imgSz.height) & rc;
}

// 将 Rect 缩放到指定的比例并约束到图像尺寸中
// ratio > 1 放大，0~1 缩小，不允许为负数
inline cv::Rect ScaleRect(const cv::Size & imgSz, const cv::Rect & rc,
	float ratioX = 2.0f, float ratioY = 2.0f)
{
	const static cv::Rect ERR_RESULT;

	// 不接受负数的缩放比例
	if (ratioX < 0.0f || ratioY < 0.0f)
		return ERR_RESULT;

	// 无需缩放
	if (ratioX >= 0.999999f && ratioX <= 1.000001f &&
		ratioY >= 0.999999f && ratioY <= 1.000001f)
		return ForceRectIntoImage(imgSz, rc);

	cv::Rect dst;
	dst.width = static_cast<int>(rc.width * ratioX);
	dst.height = static_cast<int>(rc.height * ratioY);
	dst.x = rc.x - (dst.width - rc.width) / 2;
	dst.y = rc.y - (dst.height - rc.height) / 2;

	return ForceRectIntoImage(imgSz, dst);
}

// 将图像缩放任意角度（-180 ~ 180，逆时针为正）
inline int Rotate(const cv::Mat& src, cv::Mat& dst, const cv::Point2f& center, const double& angle,
	bool crop = true, int flags = cv::INTER_LINEAR, int borderMode = cv::BORDER_CONSTANT, const cv::Scalar& borderValue = cv::Scalar())
{
	CV_Assert(src.data != NULL && src.rows > 0 && src.cols > 0);
 
	auto rot_matrix = getRotationMatrix2D(center, angle, 1.0);
 
	cv::Size sz;

	if (crop) {
		sz = src.size();
	} else {
		cv::Rect bbox = cv::RotatedRect(center, cv::Size2f(src.cols, src.rows), angle).boundingRect();
 
		double* p = (double*)rot_matrix.data;
		p[2] += bbox.width / 2.0 - center.x;
		p[5] += bbox.height / 2.0 - center.y;
		sz.width = bbox.width;
		sz.height = bbox.height;
	}
 
	cv::warpAffine(src, dst, rot_matrix, sz, flags, borderMode, borderValue);
 
	return 0;
}

#endif