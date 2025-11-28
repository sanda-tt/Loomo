#include "TargetDetector.h"
#include "util/cv_util.h"
#include <fstream>
#include <opencv2/core/mat.hpp>

std::string TargetDetectorImpl::COCO_NAMES_80[80] = {"person", "bicycle", "car", "motorbike", "aeroplane", "bus", "train", "truck",
                                                     "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
                                                     "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
                                                     "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
                                                     "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
                                                     "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
                                                     "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
                                                     "chair", "sofa", "pottedplant", "bed", "diningtable", "toilet", "tvmonitor", "laptop",
                                                     "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
                                                     "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"};

#ifdef _WINDOWS
#include <Windows.h>
#endif // _WINDOWS

// 微秒计时（Windows 上只能精确到毫秒）
inline ulonglong CurTick() {
#ifdef _WINDOWS
    return GetTickCount64() * 1000;
#else
    timeval tv;
    gettimeofday(&tv, nullptr);
    return tv.tv_sec * (ulonglong)1000000 + tv.tv_usec;
#endif
}

TargetDetectorImpl::TargetDetectorImpl(DetectorType_ enDetectorType, const std::string & strModelCfgFile,
                                       const std::string & strModelWeightsFile,	bool bEnableOpenCL)
	: m_enDetectorType(enDetectorType)
{
    if (LoadModel(strModelCfgFile, strModelWeightsFile))
		UseOpenCL(bEnableOpenCL);
}

TargetDetectorImpl::~TargetDetectorImpl()
{
}

bool TargetDetectorImpl::LoadModel(const std::string & strModelCfgFile, const std::string & strModelWeightsFile)
{
	try {
	    if (m_enDetectorType == DETECTOR_TINY_YOLO_V3)
		    m_net = cv::dnn::readNetFromDarknet(strModelCfgFile, strModelWeightsFile);
	    else if (m_enDetectorType == DETECTOR_SSD_V1_PPN)
	        m_net = cv::dnn::readNetFromTensorflow(strModelWeightsFile, strModelCfgFile);
	} catch (cv::Exception ex) {
		return false;
	}

	if (m_net.empty())
		return false;
	m_vecOutLayerName = m_net.getUnconnectedOutLayersNames();
	return true;
}

void TargetDetectorImpl::UseOpenCL(bool use)
{
	if (!m_net.empty())
		m_net.setPreferableTarget(use ? cv::dnn::DNN_TARGET_OPENCL : cv::dnn::DNN_TARGET_CPU);
}

void TargetDetectorImpl::SetParams(float fConfidenceThresh, float fNMSThresh)
{
	m_fConfidenceThresh = fConfidenceThresh;
	m_fNMSThresh = fNMSThresh;
}

int rectCenterDist(const cv::Rect& rc1, const cv::Rect&rc2) {
	int centerX1 = rc1.x + rc1.width / 2;
	int centerY1 = rc1.y + rc1.height / 2;
	int centerX2 = rc2.x + rc2.width / 2;
	int centerY2 = rc2.y + rc2.height / 2;
	return sqrt((centerX1 - centerX2) * (centerX1 - centerX2) + (centerY1 - centerY2) * (centerY1 - centerY2));
}

inline bool TrackResultValid(const cv::Rect2d& rcTracker, const cv::Size& imgSize)
{
    const static int EXT = 5;
    return (rcTracker.x >= -EXT && rcTracker.y >= -EXT &&
        rcTracker.x + rcTracker.width <= imgSize.width + EXT && rcTracker.y + rcTracker.height <= imgSize.height + EXT);
}

std::vector<cv::Rect> TargetDetectorImpl::Detect(cv::Mat frame, std::vector<cv::Rect>* vecNonPerson, std::vector<cv::Vec4i>* vecNonPersonClassId) {
	const static std::vector<cv::Rect> EMPTY_RESULT;

	if (frame.empty() || !IsGood())
		return EMPTY_RESULT;

    cv::Mat gray;
    cv::cvtColor(frame, gray, cv::COLOR_BGR2GRAY);

	// 不需要检测，直接跟踪
	if (!NeedDetect()) {
        cv::Rect2d rc;
        rc = m_pTracker->update(gray);
        //if (!TrackResultValid(rc, frame.size()))
        ////if (!m_pTracker->update(frame, rc))
        //    return EMPTY_RESULT;
        return std::vector<cv::Rect>(1, rc);
	}

	m_ullLastDetectTime = CurTick(); // 设置上一次检测时间

	cv::Mat inputBlob;

	if (m_enDetectorType == DETECTOR_TINY_YOLO_V3) {
        inputBlob = cv::dnn::blobFromImage(frame, 1 / 255.F, cv::Size(MODEL_SIZE, MODEL_SIZE),
                                           cv::Scalar(), true, false);
        m_net.setInput(inputBlob, "data");
    }
	else if (m_enDetectorType == DETECTOR_SSD_V1_PPN) {
        cv::dnn::blobFromImage(frame, inputBlob, 1.0, cv::Size(MODEL_SIZE, MODEL_SIZE),
                               cv::Scalar(), true, false);
        m_net.setInput(inputBlob);
    }

//    if (m_net.getLayer(0)->outputNameToIndex("im_info") != -1)  // Faster-RCNN or R-FCN
//    {
//        resize(frame, frame, cv::Size(MODEL_SIZE, MODEL_SIZE));
//        cv::Mat imInfo = (cv::Mat_<float>(1, 3) << MODEL_SIZE, MODEL_SIZE, 1.6f);
//        m_net.setInput(imInfo, "im_info");
//    }

    std::vector<cv::Mat> outputs;
    m_net.forward(outputs, m_vecOutLayerName);
    if (outputs.empty())
    {
        DetectFailPostProcess();
        return EMPTY_RESULT;
    }

    std::vector<cv::Rect> vecPerson;
    PostProcess(outputs, frame.size(), vecPerson, vecNonPerson, vecNonPersonClassId);

	// 未启用跟踪，直接使用检测结果
	if (!m_bEnableTrack)
	    return vecPerson;

    if (vecPerson.empty())
    {
        DetectFailPostProcess();
        return EMPTY_RESULT;
    }

	cv::Rect rcBestDetectTarget;

	if (m_pTracker != nullptr && m_bTrackerInitialized) {
		// 跟踪器已经存在，并且已经初始化了，那么要从所有检测到的目标中选择与当前跟踪的最有可能为同一人的目标
		cv::Rect2d rcTracker;
        rcTracker = m_pTracker->update(gray);
        if (TrackResultValid(rcTracker, gray.size())) {
		//if (m_pTracker->update(frame, rcTracker)) {
			// 检测到的目标（可能多个）中与跟踪目标重叠最大的一个
			auto pMaxOverlap = std::max_element(vecPerson.begin(), vecPerson.end(), [rcTracker](const cv::Rect& rc1, const cv::Rect& rc2) {
				return (((cv::Rect)rcTracker) & rc1).area() > (((cv::Rect)rcTracker) & rc2).area();
			});
			// 重叠面积
			int nOverlapArea = (((cv::Rect)rcTracker) & (*pMaxOverlap)).area();
			if (nOverlapArea > 0) {
				rcBestDetectTarget = *pMaxOverlap;
			} else { // 所有目标与跟踪目标均无重叠，则取中心距离最近的目标
				auto pNearest = std::max_element(vecPerson.begin(), vecPerson.end(), [rcTracker](const cv::Rect& rc1, const cv::Rect& rc2) {
					int trackerCenterX = rcTracker.x + rcTracker.width / 2;
					int trackerCenterY = rcTracker.y + rcTracker.height / 2;
					int detectCenterX1 = rc1.x + rc1.width / 2;
					int detectCenterY1 = rc1.y + rc1.height / 2;
					int detectCenterX2 = rc2.x + rc2.width / 2;
					int detectCenterY2 = rc2.y + rc2.height / 2;
					int dist1 = (trackerCenterX - detectCenterX1) * (trackerCenterX - detectCenterX1) + (trackerCenterY - detectCenterY1) * (trackerCenterY - detectCenterY1);
					int dist2 = (trackerCenterX - detectCenterX2) * (trackerCenterX - detectCenterX2) + (trackerCenterY - detectCenterY2) * (trackerCenterY - detectCenterY2);
					return dist1 < dist2;
				});

				auto dist = rectCenterDist(*pNearest, rcTracker);
				if (dist < 2 * rcTracker.width) { // 最近的目标都距离太远，那么也不要
					rcBestDetectTarget = *pNearest;
				}
			}
		} else {
            rcBestDetectTarget = vecPerson[0]; // 跟踪失败，直接使用最大的目标作为跟踪目标
		}
	} else {
		rcBestDetectTarget = vecPerson[0]; // 没有其他信息，直接使用最大的目标作为跟踪目标
	}

	vecPerson.clear();

	if (rcBestDetectTarget.width > 10 && rcBestDetectTarget.height > 10) {
		// 只保留选择的最佳检测目标
		vecPerson.push_back(rcBestDetectTarget);

		// 删掉重建跟踪器，因为没法把最新的结果告诉跟踪器去更新，所以只能每次检测都删掉重建跟踪器
//            m_pTracker = cv::TrackerMOSSE::create();
        //m_pTracker = cv::TrackerKCF::create();
        m_pTracker = cv::makePtr<FDSSTTracker>(HOG, FIXEDWINDOW, MULTISCALE, LAB);
        //m_pTracker->init(frame, rcBestDetectTarget);
        m_pTracker->init(rcBestDetectTarget, gray);
		m_bTrackerInitialized = true;
	}

    if (vecPerson.empty())
        DetectFailPostProcess();
    else
        m_ullDetectFailCnt = 0;

	return vecPerson;
}

/* Return the indices of the top N values of vector v. */
template <class T>
std::vector<int> Argmax(T* first, T* last, int N) {
    std::vector<std::pair<T, int> > pairs;
    for (T* t = first; t != last; ++t)
        pairs.push_back(std::make_pair(*t, t - first));

    std::partial_sort(pairs.begin(), pairs.begin() + N, pairs.end(), std::greater<>());

    std::vector<int> result;
    for (int i = 0; i < N; ++i)
        result.push_back(pairs[i].second);

    return result;
}

int Coco80ToCoco90(int coco80)
{
    if (coco80 >= 73)
        return coco80 + 10;
    else if (coco80 >= 62)
        return coco80 + 9;
    else if (coco80 >= 61)
        return coco80 + 8;
    else if (coco80 >= 60)
        return coco80 + 6;
    else if (coco80 >= 40)
        return coco80 + 5;
    else if (coco80 >= 26)
        return coco80 + 4;
    else if (coco80 >= 24)
        return coco80 + 2;
    else if (coco80 >= 11)
        return coco80 + 1;
    else
        return coco80;
}

void TargetDetectorImpl::PostProcess(std::vector<cv::Mat>& outs, const cv::Size& frameSize,
	std::vector<cv::Rect>& vecPerson, std::vector<cv::Rect>* vecNonPerson, std::vector<cv::Vec4i>* vecNonPersonClassId)
{
	vecPerson.clear();
	if (vecNonPerson != nullptr)
        vecNonPerson->clear();
	if (vecNonPersonClassId != nullptr)
	    vecNonPersonClassId->clear();

	// 为了按类别做 NMS，所以需要按类别来存储
	std::map<int, std::vector<TargetCandidate>> mapResult;

	if (m_enDetectorType == DETECTOR_TINY_YOLO_V3) {
        // tiny-yolo v3 有两个不同尺度（尺度即为 out 的行数）的输出，分别为 507 和 2028
        for (auto &out : outs) {
            // Mat 内的数据类型为 FLOAT32
            // 每行为一条预测结果，其宽度为“5 + 类别数”
            // 前 5 个元素依次为：bbox 中心的 x、bbox 中心的 y、bbox 宽度、bbox 高度、置信度
            // 类别数，coco 为 80 个类别（见文件“coco.names”），所以，out 的宽度为 85
            int nClassCnt = out.cols - 5; // 类别数
            for (int r = 0; r < out.rows; ++r) {
                auto data = out.ptr<float>(r);
                int centerX = (int) ((*data) * frameSize.width);
                ++data;
                int centerY = (int) ((*data) * frameSize.height);
                ++data;
                int width = (int) ((*data) * frameSize.width);
                ++data;
                int height = (int) ((*data) * frameSize.height);
                ++data;
                float confidence = *data;
                ++data;
                if (confidence < m_fConfidenceThresh)
                    continue;

                // 选出最大的 N 种分类（很有可能只有一种的得分大于 0，其他都是 0）
                std::vector<int> maxK = Argmax(data, data + nClassCnt, 4);
                auto maxClassPtr = data + maxK[0]; // 得分最高的分类
//			auto maxClassPtr = std::max_element(data, data + nClassCnt);
                if (*maxClassPtr < m_fConfidenceThresh)
                    continue;

                TargetCandidate candidate;
                for (int i = 0; i < 4; ++i) {
                    candidate.categories[i].classId = Coco80ToCoco90(maxK[i]); // 把 YOLO 的 80 类映射成 COCO 90 类
                    candidate.categories[i].score = *(data + maxK[i]);
                }
                candidate.confidence = confidence;
                candidate.rect = cv::Rect(centerX - width / 2, centerY - height / 2,
                                          width, height);

                mapResult[candidate.categories[0].classId].push_back(std::move(candidate)); // 按得分最高的类别进行归类
            }
        }
    } else if (m_enDetectorType == DETECTOR_SSD_V1_PPN) {
        for (size_t k = 0; k < outs.size(); k++)
        {
            float* data = (float*)outs[k].data;
            for (size_t i = 0; i < outs[k].total(); i += 7)
            {
                float confidence = data[i + 2];
                if (confidence < m_fConfidenceThresh)
                    continue;

                int left   = (int)data[i + 3];
                int top    = (int)data[i + 4];
                int right  = (int)data[i + 5];
                int bottom = (int)data[i + 6];
                int width  = right - left + 1;
                int height = bottom - top + 1;
                if (width * height <= 1)
                {
                    left   = (int)(data[i + 3] * frameSize.width);
                    top    = (int)(data[i + 4] * frameSize.height);
                    right  = (int)(data[i + 5] * frameSize.width);
                    bottom = (int)(data[i + 6] * frameSize.height);
                    width  = right - left + 1;
                    height = bottom - top + 1;
                }

                int nClassId = (int)(data[i + 1]) - 1; // Skip 0th background class id.

                TargetCandidate candidate;
                for (int i = 0; i < 4; ++i) {
                    candidate.categories[i].classId = nClassId;
                    candidate.categories[i].score = confidence;
                }
                candidate.confidence = confidence;
                candidate.rect = ForceRectIntoImage(frameSize, cv::Rect(left, top, width, height));

                mapResult[nClassId].push_back(std::move(candidate)); // 按得分最高的类别进行归类
            }
        }
	}

	std::vector<TargetCandidate> vecPerson_, vecNonPerson_;

	if (vecNonPerson == nullptr) // 仅分析行人目标
	{
		// 确实检测到了行人
		if (mapResult.count(0) > 0 && !mapResult[0].empty())
		{
		    // 仅对行人目标做 NMS
			DoNMS(mapResult.at(0), vecPerson_);
		}
	}
	else
	{
        // 依次对每种目标 NMS，而不是把所有目标混在一起做 NMS，因为不同类型的目标位置可能有较大重叠
        // 比如一个人坐在椅子上抱着一只猫，这三个目标可能完全重叠，如果不加区分全部做 NMS，可能只保留一个了
        for (auto& it : mapResult)
        {
            decltype(vecNonPerson_) cur;
            DoNMS(it.second, cur);
            if (it.first == 0)
                vecPerson_ = cur;
            else
                vecNonPerson_.insert(vecNonPerson_.end(), cur.begin(), cur.end());
        }
	}

	// 行人结果按面积排序
    vecPerson.reserve(vecPerson_.size());
	std::for_each(vecPerson_.begin(), vecPerson_.end(), [&](const TargetCandidate& a){
        vecPerson.push_back(a.rect);
	});
	std::sort(vecPerson.begin(), vecPerson.end(), [](const cv::Rect& rc1, const cv::Rect& rc2) {
	   return rc1.area() > rc2.area();
	});

	// 非行人结果按置信度排序
    if (vecNonPerson != nullptr)
    {
        std::sort(vecNonPerson_.begin(), vecNonPerson_.end(), [](const TargetCandidate& a, const TargetCandidate& b) {
            return a.confidence >= b.confidence;
        });
        vecNonPerson->reserve(vecNonPerson_.size());
        vecNonPersonClassId->reserve(vecNonPerson_.size());
        std::for_each(vecNonPerson_.begin(), vecNonPerson_.end(), [&](const TargetCandidate& a) {
           vecNonPerson->push_back(a.rect);
           cv::Vec4i vecClassId;
           // 记录得分前 4 的分类
           for (int i = 0; i < 4; ++i)
             vecClassId[i] = a.categories[i].classId + 1; // classId + 1 是为了用 0 标识未定义的类别
           vecNonPersonClassId->push_back(std::move(vecClassId));
        });
    }
}

void TargetDetectorImpl::DoNMS(const std::vector<TargetDetectorImpl::TargetCandidate> &input,
                         std::vector<TargetDetectorImpl::TargetCandidate> &output) {
    output.clear();

    std::vector<cv::Rect> vecRect;
    std::vector<float> vecConfidence;
    vecRect.reserve((input.size()));
    vecConfidence.reserve(input.size());

    std::for_each(input.begin(), input.end(), [&](const TargetCandidate& a){
        vecRect.push_back(a.rect);
        vecConfidence.push_back(a.confidence);
    });

    std::vector<int> indices;
    cv::dnn::NMSBoxes(vecRect, vecConfidence, m_fConfidenceThresh, m_fNMSThresh, indices);

    output.reserve(indices.size());
    for (size_t i = 0; i < indices.size(); ++i)
    {
        int idx = indices[i];
        output.push_back(input[idx]);
    }
}

bool TargetDetectorImpl::NeedDetect() {
    // 未启用跟踪或上一次检测失败或跟踪器未创建或尚未初始化
    //if (m_ullDetectFailCnt != 0 || m_pTracker == nullptr || !m_bTrackerInitialized)    
    if (!m_bEnableTrack || m_pTracker == nullptr || !m_bTrackerInitialized ||
        /*m_ullDetectFailCnt > 5 ||*/ (m_ullDetectFailCnt % 2 == 1)) // 连续检测失败 1、3、5 次或者超过 5 次（超过 5 次时跟踪器会被删），则不跟踪了（前几次为了跟踪和检测交替进行，避免暂时丢失）
        return true;

    // 达到检测周期
    if (CurTick() - m_ullLastDetectTime >= DETECT_INTERVAL)
        return true;

    return false;
}

void TargetDetectorImpl::DetectFailPostProcess()
{
    ++m_ullDetectFailCnt;
    // 连续检测失败超过 5 次，删除跟踪器
    if (m_ullDetectFailCnt >= 5)
    {
        if (m_pTracker != nullptr)
            m_pTracker.release();
    }
}

