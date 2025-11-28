#include <jni.h>
#include <android/log.h>
#include <string>
#include <opencv2/opencv.hpp>
#include "TargetDetector.h"
#include "util/system/system_util.h"

#define TAG "NATIVE-LIB"

// USE_TINY_YOLO USE_SSD_MOBILENET
#define USE_SSD_MOBILENET

inline void vector_Rect_to_Mat(std::vector<cv::Rect>& v_rect, cv::Mat& mat)
{
    mat = cv::Mat(v_rect, true);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeCreateObject(JNIEnv *env, jclass type) {

    auto rootDir = getenv("EXTERNAL_STORAGE");
    std::string workpath = std::string(rootDir) + "/model";

#ifdef USE_TINY_YOLO
    std::string weights = workpath + "/yolov3-tiny.weights";
    std::string cfg = workpath + "/yolov3-tiny.cfg";
    TargetDetector* detector = new TargetDetectorImpl(TargetDetector::DETECTOR_TINY_YOLO_V3, cfg, weights, false);
#elif defined(USE_SSD_MOBILENET)
    std::string weights = workpath + "/ssd_mobilenet_v1_ppn_coco.pb";
    std::string cfg = workpath + "/ssd_mobilenet_v1_ppn_coco.pbtxt";
    TargetDetector* detector = new TargetDetectorImpl(TargetDetector::DETECTOR_SSD_V1_PPN, cfg, weights, false);
#endif

    if (!detector->IsGood()) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Create TargetDetectorImpl fail!");
        delete(detector);
        detector = nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "TargetDetectorImpl created");
    return (jlong)detector;

}

extern "C"
JNIEXPORT void JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeDestroyObject(JNIEnv *env, jclass type, jlong thiz) {

    if (thiz != 0)
        delete (TargetDetector*)thiz;
    __android_log_print(ANDROID_LOG_INFO, TAG, "TargetDetectorImpl destroied");

}

extern "C"
JNIEXPORT void JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeDetect(JNIEnv *env, jclass type, jlong thiz,
                                                 jlong inputImage, jlong person, jlong nonPerson,
                                                 jlong nonPersonClassId) {
    if (thiz == 0 || inputImage == 0 || person == 0)
        return;

    auto detector = (TargetDetector*)thiz;
    cv::Mat bgr;
    cv::cvtColor(*((cv::Mat*)inputImage), bgr, cv::COLOR_RGBA2BGR);

    std::vector<cv::Rect> vecPerson, vecNonPerson;
    std::vector<cv::Vec4i> vecNonPersonClassId;
    vecPerson = detector->Detect(bgr, nonPerson == 0 ? nullptr : &vecNonPerson,
                                 nonPersonClassId == 0 ? nullptr : &vecNonPersonClassId);
    *((cv::Mat*)person) = cv::Mat(vecPerson, true);
    if (nonPerson != 0) {
        *((cv::Mat *) nonPerson) = cv::Mat(vecNonPerson, true);
        if (nonPersonClassId != 0)
            *((cv::Mat *) nonPersonClassId) = cv::Mat(vecNonPersonClassId, true);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeEnableTrack(JNIEnv *env, jclass type, jlong thiz,
                                                      jboolean enabled) {
    if (thiz == 0)
        return;
    auto detector = (TargetDetector*)thiz;
    detector->SetTrackEnabled(enabled);
}

namespace _ml_ {
    cv::Ptr<cv::ml::StatModel> model;
    const std::string reviseListFilename = std::string(getenv("EXTERNAL_STORAGE")) + "/LoomoDemo/record/revise_list.csv";

    std::vector<std::string> vecClassName;

    std::vector<cv::Mat> vecImage;
    std::vector<int> vecLabel;

    // 准备训练用的数据，返回数据条数
    int prepareTrainData() {
        vecClassName.clear();
        vecImage.clear();
        vecLabel.clear();

        std::map<std::string, int> mapLabel;

        int id = 0;

        std::ifstream ifs(reviseListFilename);
        if (!ifs.is_open())
            return 0;

        std::string line;
        while (std::getline(ifs, line)) {
            auto pos = line.rfind(',');
            if (pos == line.npos)
                continue;
            std::string imageFilename = line.substr(0, pos);
            auto image = cv::imread(imageFilename, cv::IMREAD_GRAYSCALE);
            if (image.empty())
                continue;
            if (image.cols != 64 || image.rows != 64)
                cv::resize(image, image, cv::Size(64, 64)); // 缩放到固定大小
            // 调整到训练要求的格式
            image.convertTo(image, CV_32FC1);
            image = image.reshape(0, 1);
            vecImage.push_back(std::move(image));
            std::string className = line.substr(pos + 1);
            if (mapLabel.count(className) == 0) {
                mapLabel[className] = id;
                vecClassName.push_back(className);
                ++id;
            }
            vecLabel.push_back(mapLabel.at(className));
        }

        return vecLabel.size();
    }

    bool CheckTrainPrecondition() {
        return !(vecLabel.empty() || vecImage.empty() || vecLabel.size() != vecImage.size());
    }

    // KNN
    bool KnnTrain() {
        if (!CheckTrainPrecondition()) return false;

        auto knn = cv::ml::KNearest::create();
        model = knn;
        knn->setDefaultK(vecLabel.size());
        knn->setIsClassifier(true);
        knn->setAlgorithmType(cv::ml::KNearest::BRUTE_FORCE);

        cv::Mat train_labels(vecLabel.size(), 1, CV_32FC1);
        float* p = (float*)train_labels.data;
        for (auto label : vecLabel) {
            *p = label;
            ++p;
        }

        cv::Mat train_data(vecImage.size(), 64 * 64, CV_32FC1);
        for (int j = 0; j < vecImage.size(); ++j) {
            auto tmp = train_data.row(j);
            vecImage[j].copyTo(tmp);
        }

        return knn->train(train_data, cv::ml::ROW_SAMPLE, train_labels);
    }

    // 决策树
    bool DTreesTrain() {
        if (!CheckTrainPrecondition()) return false;

        auto dtrees = cv::ml::DTrees::create();
        model = dtrees;
        //树的最大可能深度
        dtrees->setMaxDepth(8);
        //节点最小样本数量
        dtrees->setMinSampleCount(2);
        //是否建立替代分裂点
        dtrees->setUseSurrogates(false);
        //交叉验证次数
        dtrees->setCVFolds(0);
        //是否严格修剪
        dtrees->setUse1SERule(false);
        //分支是否完全移除
        dtrees->setTruncatePrunedTree(false);

        cv::Mat train_labels(vecLabel.size(), 1, CV_32FC1);
        float* p = (float*)train_labels.data;
        for (auto label : vecLabel) {
            *p = label;
            ++p;
        }

        cv::Mat train_data(vecImage.size(), 64 * 64, CV_32FC1);
        for (int j = 0; j < vecImage.size(); ++j) {
            auto tmp = train_data.row(j);
            vecImage[j].copyTo(tmp);
        }

        return dtrees->train(train_data, cv::ml::ROW_SAMPLE, train_labels);
    }

    // SVM
    bool SVMTrain() {
        if (!CheckTrainPrecondition()) return false;

        auto svm = cv::ml::SVM::create();
        model = svm;

        // SVM 的 label 要求为整型
        cv::Mat train_labels(vecLabel.size(), 1, CV_32S);
        int* p = (int*)train_labels.data;
        for (auto label : vecLabel) {
            *p = label;
            ++p;
        }

        cv::Mat train_data(vecImage.size(), 64 * 64, CV_32FC1);
        for (int j = 0; j < vecImage.size(); ++j) {
            auto tmp = train_data.row(j);
            vecImage[j].copyTo(tmp);
        }

        return svm->train(train_data, cv::ml::ROW_SAMPLE, train_labels);
    }

    // image 要求为 RGBA
    std::string Predict(cv::Mat image) {
        if (vecLabel.empty())
            return std::string();

        cv::Mat gray;
        cv::cvtColor(image, gray, cv::COLOR_RGBA2GRAY);
        cv::resize(gray, gray, cv::Size(64, 64)); // 缩放到固定大小
        cv::equalizeHist(gray, gray); // 直方图均衡
        gray.convertTo(gray, CV_32FC1);
        gray = gray.reshape(0, 1);

        cv::Mat result;
        auto response = (int)model->predict(gray, result);
        return vecClassName[*(float*)result.data];
    }

    // image 要求为 RGBA
    void AddRevisiedResult(cv::Mat image, std::string className) {
        if (image.empty() || className.empty())
            return;

        cv::Mat gray;
        cv::cvtColor(image, gray, cv::COLOR_RGBA2GRAY);
        cv::resize(gray, gray, cv::Size(64, 64)); // 缩放到固定大小
        cv::equalizeHist(gray, gray); // 直方图均衡

        auto rootDir = getenv("EXTERNAL_STORAGE");
        std::string reviseImageDir = std::string(rootDir) + "/LoomoDemo/record/revised";

        char uuid[UUID4_LEN] = { 0 };
        uuid4_generate(uuid);

        // 保存图片
        std::string reviseImageFilename = reviseImageDir + "/" + uuid + ".png";
        cv::imwrite(reviseImageFilename, gray);

        // 写记录文件
        std::ofstream ofs(reviseListFilename, std::ios::app);
        ofs << reviseImageFilename << "," << className << std::endl;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeCheckRevisiedResult(JNIEnv *env, jclass type,
                                                              jlong inputImage) {
    if (!_ml_::model) return env->NewStringUTF("");
    auto str = _ml_::Predict(*((cv::Mat*)inputImage));
    return env->NewStringUTF(str.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeTrainRevisiedResult(JNIEnv *env, jclass type) {
    if (_ml_::prepareTrainData() > 30) // 如果没有 30 条数据，就不进行训练
        _ml_::SVMTrain();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xs_ai_loomodemo_NativeAlgo_nativeAddRevisiedResult(JNIEnv *env, jclass type,
                                                            jlong inputImage,
                                                            jstring revisiedClassName_) {
    const char *revisiedClassName = env->GetStringUTFChars(revisiedClassName_, 0);
    _ml_::AddRevisiedResult(*((cv::Mat*)inputImage), revisiedClassName);
    env->ReleaseStringUTFChars(revisiedClassName_, revisiedClassName);
}