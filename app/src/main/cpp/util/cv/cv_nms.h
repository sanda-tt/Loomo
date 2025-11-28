#pragma once

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <map>

template <typename T>
static inline float rectOverlap(const T& a, const T& b)
{
    return 1.f - static_cast<float>(jaccardDistance(a, b));
}

static inline float rotatedRectIOU(const cv::RotatedRect& a, const cv::RotatedRect& b)
{
    std::vector<cv::Point2f> inter;
    int res = cv::rotatedRectangleIntersection(a, b, inter);
    if (inter.empty() || res == cv::INTERSECT_NONE)
        return 0.0f;
    if (res == cv::INTERSECT_FULL)
        return 1.0f;
    float interArea = static_cast<float>(cv::contourArea(inter));
    return interArea / (a.size.area() + b.size.area() - interArea);
}

namespace
{
template <typename T>
static inline bool SortScorePairDescend(const std::pair<float, T>& pair1,
    const std::pair<float, T>& pair2)
{
    return pair1.first > pair2.first;
}

} // namespace

// Get max scores with corresponding indices.
//    scores: a set of scores.
//    threshold: only consider scores higher than the threshold.
//    top_k: if -1, keep all; otherwise, keep at most top_k.
//    score_index_vec: store the sorted (score, index) pair.
inline void GetMaxScoreIndex(const std::vector<float>& scores, const float threshold, const int top_k,
    std::vector<std::pair<float, int> >& score_index_vec)
{
    CV_DbgAssert(score_index_vec.empty());
    // Generate index score pairs.
    for (size_t i = 0; i < scores.size(); ++i)
    {
        if (scores[i] > threshold)
        {
            score_index_vec.push_back(std::make_pair(scores[i], static_cast<int>(i)));
        }
    }

    // Sort the score pair according to the scores in descending order
    std::stable_sort(score_index_vec.begin(), score_index_vec.end(),
        SortScorePairDescend<int>);

    // Keep top_k scores if needed.
    if (top_k > 0 && top_k < (int)score_index_vec.size())
    {
        score_index_vec.resize(top_k);
    }
}

// Do non maximum suppression given bboxes and scores.
// Inspired by Piotr Dollar's NMS implementation in EdgeBox.
// https://goo.gl/jV3JYS
//    bboxes: a set of bounding boxes.
//    scores: a set of corresponding confidences.
//    score_threshold: a threshold used to filter detection results.
//    nms_threshold: a threshold used in non maximum suppression.
//    top_k: if not > 0, keep at most top_k picked indices.
//    indices: the kept indices of bboxes after nms.
template <typename BoxType>
inline void NMSFast_(const std::vector<BoxType>& bboxes,
    const std::vector<float>& scores, const float score_threshold,
    const float nms_threshold, const float eta, const int top_k,
    std::vector<int>& indices, float(*computeOverlap)(const BoxType&, const BoxType&))
{
    CV_Assert(bboxes.size() == scores.size());

    // Get top_k scores (with corresponding indices).
    std::vector<std::pair<float, int> > score_index_vec;
    GetMaxScoreIndex(scores, score_threshold, top_k, score_index_vec);

    // Do nms.
    float adaptive_threshold = nms_threshold;
    indices.clear();
    for (size_t i = 0; i < score_index_vec.size(); ++i) {
        const int idx = score_index_vec[i].second;
        bool keep = true;
        for (int k = 0; k < (int)indices.size() && keep; ++k) {
            const int kept_idx = indices[k];
            float overlap = computeOverlap(bboxes[idx], bboxes[kept_idx]);
            keep = overlap <= adaptive_threshold;
        }
        if (keep)
            indices.push_back(idx);
        if (keep && eta < 1 && adaptive_threshold > 0.5) {
            adaptive_threshold *= eta;
        }
    }
}

//=============================================================================
/** @brief Performs non maximum suppression given boxes and corresponding scores.
 * @param bboxes a set of bounding boxes to apply NMS.
 * @param scores a set of corresponding confidences.
 * @param score_threshold a threshold used to filter boxes by score.
 * @param nms_threshold a threshold used in non maximum suppression.
 * @param indices the kept indices of bboxes after NMS.
 * @param eta a coefficient in adaptive threshold formula: \f$nms\_threshold_{i+1}=eta\cdot nms\_threshold_i\f$.
 * @param top_k if `>0`, keep at most @p top_k picked indices.
 */
static inline void NMSBoxes(const std::vector<cv::Rect>& bboxes, const std::vector<float>& scores,
    const float score_threshold, const float nms_threshold,
    std::vector<int>& indices, const float eta = 1.f, const int top_k = 0)
{
    //CV_Assert_N(bboxes.size() == scores.size(), score_threshold >= 0,
    //    nms_threshold >= 0, eta > 0);
    NMSFast_(bboxes, scores, score_threshold, nms_threshold, eta, top_k, indices, rectOverlap);
}

static inline void NMSBoxes(const std::vector<cv::Rect2d>& bboxes, const std::vector<float>& scores,
    const float score_threshold, const float nms_threshold,
    std::vector<int>& indices, const float eta = 1.f, const int top_k = 0)
{
    //CV_Assert_N(bboxes.size() == scores.size(), score_threshold >= 0,
    //    nms_threshold >= 0, eta > 0);
    NMSFast_(bboxes, scores, score_threshold, nms_threshold, eta, top_k, indices, rectOverlap);
}

static inline void NMSBoxes(const std::vector<cv::RotatedRect>& bboxes, const std::vector<float>& scores,
    const float score_threshold, const float nms_threshold,
    std::vector<int>& indices,
    const float eta = 1.f, const int top_k = 0)
{
    //CV_Assert_N(bboxes.size() == scores.size(), score_threshold >= 0,
    //    nms_threshold >= 0, eta > 0);
    NMSFast_(bboxes, scores, score_threshold, nms_threshold, eta, top_k, indices, rotatedRectIOU);
}
//=============================================================================