#pragma once
#include <opencv2/opencv.hpp>
#include <string>

class TargetDetector
{
public:
    enum DetectorType_
    {
        DETECTOR_TINY_YOLO_V3 = 0,
        DETECTOR_SSD_V1_PPN,
    };

public:
	TargetDetector() = default;
	virtual ~TargetDetector() = default;

public:
    virtual bool IsGood() const = 0;
    virtual void SetTrackEnabled(bool enabled) = 0; // 是否启用跟踪（默认应为启用状态）

    virtual std::vector<cv::Rect> Detect(cv::Mat frame, std::vector<cv::Rect>* vecNonPerson = nullptr,
                                         std::vector<cv::Vec4i>* vecNonPersonClassId = nullptr) = 0;
};

#include "fdssttracker.hpp"

typedef unsigned long long ulonglong;

// Now can only for tiny-yolo v3, SSD-V1-PPN
class TargetDetectorImpl : public TargetDetector
{
public:
	TargetDetectorImpl(DetectorType_ enDetectorType, const std::string& strModelCfgFile,
		const std::string& strModelWeightsFile,
		bool bEnableOpenCL = true);
	virtual ~TargetDetectorImpl();

	bool LoadModel(const std::string& strModelCfgFile,
		const std::string& strModelWeightsFile);
	void UseOpenCL(bool use);

	void SetParams(float fConfidenceThresh, float fNMSThresh);

    virtual void SetTrackEnabled(bool enabled) override { m_bEnableTrack = enabled; }
	bool IsGood() const override { return !m_net.empty(); }

public:
    std::vector<cv::Rect> Detect(cv::Mat frame, std::vector<cv::Rect>* vecNonPerson = nullptr,
                                 std::vector<cv::Vec4i>* vecNonPersonClassId = nullptr) override;

private:
	void PostProcess(std::vector<cv::Mat>& outs, const cv::Size& frameSize, std::vector<cv::Rect>& vecPerson_,
		std::vector<cv::Rect>* vecNonPerson = nullptr, std::vector<cv::Vec4i>* vecNonPersonClassId = nullptr);

	struct TargetCandidate
	{
	    struct Category
        {
            int classId = 0;
            float score = 0.0f;
        };

		cv::Rect rect;
        float confidence = 0.0f;
        Category categories[4]; // 保留得分最高的 4 个分类（DETECTOR_TINY_YOLO_V3 将输出 4 个分类，而 DETECTOR_SSD_V1_PPN 将只输出 1 个分类）
	};

	void DoNMS(const std::vector<TargetCandidate>& input, std::vector<TargetCandidate>& output);

private:
	DetectorType_ m_enDetectorType = DETECTOR_TINY_YOLO_V3;
	cv::dnn::Net m_net;
	std::vector<std::string> m_vecOutLayerName; // 输出层的名字
    static std::string COCO_NAMES_80[80];

    // 跟踪相关
    bool m_bEnableTrack = true;
    bool HOG = true;
    bool FIXEDWINDOW = false;
    bool MULTISCALE = true;
    bool SILENT = true;
    bool LAB = false;
    cv::Ptr<FDSSTTracker> m_pTracker; // fDSST 实为变尺度的 KCF
	//cv::Ptr<cv::Tracker> m_pTracker;
	bool m_bTrackerInitialized = false;
	ulonglong m_ullLastDetectTime = 0; // 上一次检测的时间
    const static int DETECT_INTERVAL = 2 * 1000 * 1000; // 检测间隔（微秒）

protected:
    bool NeedDetect();
    void DetectFailPostProcess(); // 检测失败的处理（适时删除跟踪器、重置计数等）

private:
    const int MODEL_SIZE = 320;

	float m_fConfidenceThresh = 0.7f;
	float m_fNMSThresh = 0.3f;

    ulonglong m_ullDetectFailCnt = 0; // 检测失败计数
};
