#include <iostream>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <time.h>

#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>

#include <vector>

#include <windows.h>
//#include <dirent.h>

#include "TargetDetector.h"


using namespace cv;

std::vector <cv::Mat> imgVec;

int main(int argc, char* argv[]){

	if (argc > 5) return -1;
       
	// DSSTTracker tracker;

	cv::Mat frame;
	//char name[7];
	//std::string imgName;
	//std::string imgPath = "./person19/";

	////get init target box params from information file
	//std::ifstream initInfoFile;
	//std::string fileName = imgPath + "initInfo.txt";
	//initInfoFile.open(fileName);
	//std::string firstLine;
	//std::getline(initInfoFile, firstLine);
	//float initX, initY, initWidth, initHegiht;
	//char ch;
	//std::istringstream ss(firstLine);
	//ss >> initX, ss >> ch;
	//ss >> initY, ss >> ch;
	//ss >> initWidth, ss >> ch;
	//ss >> initHegiht, ss >> ch;

	//cv::Rect initRect = cv::Rect(initX, initY, initWidth, initHegiht);

    //cv::VideoCapture v(R"(F:\Dataset\Invasion\ch29_20181212161936-half.mp4)");
    cv::VideoCapture v(0);

    auto detector = std::make_shared<TargetDetectorImpl>(R"(D:\Loomo\data\yolov3-tiny.cfg)", R"(D:\Loomo\data\yolov3-tiny.weights)");

	double duration = 0;
	for (;;)
	{
		auto t_start = clock();
		//sprintf_s(name, "%06d", count);
		//std::string imgFinalPath = imgPath + std::string(name) + ".jpg";
		//processImg = cv::imread(imgFinalPath, IMREAD_GRAYSCALE);

		//processImg = cv::imread(imgFinalPath, CV_LOAD_IMAGE_COLOR);

		//if (processImg.empty())
		//{
		//	break;
		//}

        if (!v.read(frame))
            break;

        auto vec = detector->Detect(frame);

        for (auto& showRect : vec)
		    cv::rectangle(frame, showRect, cv::Scalar(0, 0, 255), 2);
		cv::imshow("windows", frame);
		cv::waitKey(1);
	}

	system("pause");
	return 0;

}
