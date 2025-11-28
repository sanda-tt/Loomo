/**
 * 作者：地狱丧钟/叁大（GitHub：@sanda-tt）
 * 邮箱：sandatt517@outlook.com
 * 创建日期：2025-10-20
 * 版权声明：本代码基于 MIT 协议开源，可自由使用、修改、分发，需保留原作者声明
 * 项目地址：https://github.com/sanda-tt/Loomo
 * 功能：道路自动驾驶，任务追随，漫游
 */

package com.xs.ai.loomodemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.sagether.facesdk.FaceActivity;
import com.sagether.facesdk.FaceIdName;
import com.sagether.facesdk.FaceManagerActivity;
//import com.xs.ai.loomodemo.depthcam.DepthCam;
import com.xs.ai.loomodemo.followme.CamViewListener;
import com.xs.ai.loomodemo.followme.FollowMe;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.wander.CamViewListener2;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends Activity {

    public static final int FACE_VALIDATE_REQUEST_CODE = 1;
    public static final int FACE_REGISTER_REQUEST_CODE = 2;
    public static final int FACE_MANAGE_REQUEST_CODE = 3;
    private static final String TAG = "LoomoDemo_MainActivity";

    private static final boolean ENABLE_FACE_VERIFY = true; // shut down face reg by SC

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private CameraBridgeViewBase mCameraView;
    private boolean mIsFollowingMe = false;
    private boolean mIsWandering = false;
    private FollowMe mFollowMe;
    private CamViewListener mCamViewListener;
    private CamViewListener2 mCamViewListener2;


    private boolean mIsRoadFollowing = false;
    private RoadFollower mRoadFollower;






    private CameraBridgeViewBase.CvCameraViewListener2 mDefaultViewListener = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width, int height) {}
        @Override
        public void onCameraViewStopped() {}
        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            mRgba = inputFrame.rgba();
            Core.flip(mRgba, mRgba, 1);
            return mRgba;
        }
        private Mat mRgba;
    };

    private void showFullScreen(boolean fullScreen) {
        if (fullScreen) {
            Objects.requireNonNull(getActionBar()).hide();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            Objects.requireNonNull(getActionBar()).show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    protected void setFollowMe(boolean isWork) {
        mIsFollowingMe = isWork;
        if (mCamViewListener != null) {
            if (isWork)
                mCamViewListener.startFollowMe();
            else
                mCamViewListener.stopFollowMe();
        }
        if (mFollowMe != null) {
            if (isWork)
                mFollowMe.start();
            else
                mFollowMe.stop();
        }
    }

    protected void startFollowMe() {
        SegwayService.base().setLinearVelocity(0.0f);
        SegwayService.base().setAngularVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.4f);

        if (ENABLE_FACE_VERIFY) {
            SegwayService.speak(getString(R.string.face_verify_start));
            // shun down face reg by SC -2
            FaceActivity.startValidateActivityForResult(
                this, FACE_VALIDATE_REQUEST_CODE, 10 * 1000);
        } else {
            setFollowMe(true);
            ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_stop));
            SegwayService.speak(getString(R.string.face_verify_succ));
            findViewById(R.id.btn_follow_me).setEnabled(true);
        }
    }

    protected void stopFollowMe() {
        mCameraView.setCvCameraViewListener(mDefaultViewListener);
        SegwayService.base().setLinearVelocity(0.0f);
        SegwayService.base().setAngularVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.4f);
        setFollowMe(false);

        ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_start));

        if (ENABLE_FACE_VERIFY) {
            findViewById(R.id.btn_register_face).setEnabled(true);
            findViewById(R.id.btn_face_manage).setEnabled(true);
        }
    }

    protected void startWandering() {
        mCameraView.setCvCameraViewListener(mCamViewListener2);
        mIsWandering = true;
        SegwayService.base().setLinearVelocity(0.0f);
        SegwayService.base().setAngularVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.0f);

        ((Button) findViewById(R.id.btn_wander)).setText(getString(R.string.btn_wander_stop));
        SegwayService.speak("Let's walking around");
    }

    protected void stopWandering() {
        mCameraView.setCvCameraViewListener(mDefaultViewListener);
        SegwayService.base().setLinearVelocity(0.0f);
        SegwayService.base().setAngularVelocity(0.0f);
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);

        ((Button) findViewById(R.id.btn_wander)).setText(getString(R.string.btn_wander_start));
        SegwayService.speak("see you bye bye");
        mIsWandering = false;
    }










    // 添加道路跟随控制方法
    protected void startRoadFollowing() {
        if (mRoadFollower == null) {
            mRoadFollower = new RoadFollower(getApplicationContext());
        }

        /*

        setHeadJointYaw(0.0f)：设置头部左右转动角度

                -1.0f ~ 1.0f 范围，0.0f 为正前方

        setWorldPitch(0.0f)：设置头部上下俯仰角度

                -1.0f ~ 1.0f 范围，0.0f 为水平直视

        正值向下看，负值向上看
          */

        // 设置头部角度为90°直视前方
        SegwayService.head().setHeadJointYaw(0.0f);      // 设置头部偏航角为0（正前方）
        SegwayService.head().setWorldPitch(0.0f);        // 设置俯仰角为0（水平直视）



        mCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {}

            @Override
            public void onCameraViewStopped() {}

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat rgba = inputFrame.rgba();
                Core.flip(rgba, rgba, 1);

                // 调用道路跟随处理
                return mRoadFollower.processFrame(rgba);
            }
        });

        mRoadFollower.start();
        mIsRoadFollowing = true;

        // 停止其他模式
        if (mIsFollowingMe) stopFollowMe();
        if (mIsWandering) stopWandering();


        Button btnRoadFollow = findViewById(R.id.btn_road_follow);
        if (btnRoadFollow != null) {
            btnRoadFollow.setText("停止道路跟随");
        }

        SegwayService.speak("开始道路跟随");
    }

    protected void stopRoadFollowing() {
        if (mRoadFollower != null) {
            mRoadFollower.stop();
        }



        // 恢复默认头部角度
        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);  // 恢复默认俯仰角



        mCameraView.setCvCameraViewListener(mDefaultViewListener);
        mIsRoadFollowing = false;

        Button btnRoadFollow = findViewById(R.id.btn_road_follow);
        if (btnRoadFollow != null) {
            btnRoadFollow.setText("道路跟随");
        }

        SegwayService.speak("停止道路跟随");
    }











    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
     //   setFollowMe(true);
     //   mCameraView.setCvCameraViewListener(mCamViewListener);
     //   ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_stop));
     //   SegwayService.speak(getString(R.string.face_verify_succ));
        //  shutdown face reg by SC
        if (requestCode == FACE_VALIDATE_REQUEST_CODE) { // 人脸验证结果
            if (resultCode == RESULT_OK) {
                if (ENABLE_FACE_VERIFY) {
                    findViewById(R.id.btn_register_face).setEnabled(false);
                    findViewById(R.id.btn_face_manage).setEnabled(false);
                }
                setFollowMe(true);
                mCameraView.setCvCameraViewListener(mCamViewListener);
                ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_stop));
                SegwayService.speak(getString(R.string.face_verify_succ));
            } else {
                //setFollowMe(false);
                //((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_start));
                //SegwayService.speak(getString(R.string.face_verify_fail));
                setFollowMe(true);
                mCameraView.setCvCameraViewListener(mCamViewListener);
                ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_stop));
                SegwayService.speak(getString(R.string.face_verify_succ));
            }
            findViewById(R.id.btn_follow_me).setEnabled(true);
        } else if (requestCode == FACE_REGISTER_REQUEST_CODE) { // 人脸注册结果
            if (resultCode == RESULT_OK) {
                String faceId = null;
                if (data != null) {
                    faceId = data.getStringExtra(FaceActivity.FACE_ID);
                }
                FaceIdName.getInstance().createNameDialog(faceId).show(); // 让用户输入名字
            }
        } else if (requestCode == FACE_MANAGE_REQUEST_CODE) { // 人脸管理（删除）结果
            String[] ids = new String[0];
            if (data != null) {
                ids = data.getStringArrayExtra(FaceManagerActivity.FACE_ID);
            }
            FaceIdName.getInstance().updateFaceIdNameFile(ids == null ? null : Arrays.asList(ids)); // 更新，移除已经被删除的名字
        }

    }

//    private DepthCam mDepthCam = new DepthCam();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showFullScreen(true); // 切换全屏显示

        mCameraView = findViewById(R.id.camera_surface_view);
//        mCameraView.setOnClickListener(new View.OnClickListener() { // 点击视频界面切换全屏
//            @Override
//            public void onClick(View view) {
//                mFullScreen = !mFullScreen;
//                showFullScreen(mFullScreen);
//            }
//        });
        mCamViewListener = new CamViewListener();
        mCamViewListener2 = new CamViewListener2(getApplicationContext());

        // 添加道路跟随监听器初始化（放在这里↓↓↓）
        mRoadFollower = new RoadFollower(getApplicationContext());

//        mCameraView.setCvCameraViewListener(mCamViewListener);
        mCameraView.setCvCameraViewListener(mDefaultViewListener);
        mCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);

        if (!ENABLE_FACE_VERIFY) {
            findViewById(R.id.btn_register_face).setVisibility(View.INVISIBLE);
            findViewById(R.id.btn_face_manage).setVisibility(View.INVISIBLE);
        } else {
            findViewById(R.id.btn_register_face).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // shut down by SC -1
                    FaceActivity.startRegisterActivityForResult(MainActivity.this, null, FACE_REGISTER_REQUEST_CODE);
                }
            });
            // shut down by SC -1
            FaceIdName.getInstance().setContext(MainActivity.this);
            findViewById(R.id.btn_face_manage).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // shut down by SC -1
                    FaceManagerActivity.startActivityForResult(MainActivity.this, FaceIdName.getInstance().readFaceIdNameFile(), FACE_MANAGE_REQUEST_CODE);
                }
            });
        }

        findViewById(R.id.btn_follow_me).setEnabled(false);
        findViewById(R.id.btn_follow_me).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                if (mIsWandering)
                    stopWandering();

                view.setEnabled(false);
                if (mIsFollowingMe)
                    stopFollowMe();
                else
                    startFollowMe();
            }
        });

        findViewById(R.id.btn_wander).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                if (mIsFollowingMe)
                    stopFollowMe();

                view.setEnabled(false);
                if (mIsWandering)
                    stopWandering();
                else
                    startWandering();

                // 延时启用，避免被连续快速按到两次
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setEnabled(true);
                    }
                }, 500);
            }
        });







        // 在现有按钮代码后面添加道路跟随按钮
        findViewById(R.id.btn_road_follow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);

                // 停止其他模式
                if (mIsFollowingMe) stopFollowMe();
                if (mIsWandering) stopWandering();

                if (mIsRoadFollowing) {
                    stopRoadFollowing();
                } else {
                    startRoadFollowing();
                }

                // 延时启用，避免被连续快速按到两次
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setEnabled(true);
                    }
                }, 500);
            }
        });














        SegwayService.bindService(getApplicationContext(),
                new SegwayService.BindStateListener() {
                    @Override
                    public void onBindDone() { // 所有服务绑定完成
                        SegwayService.speak(getString(R.string.on_speaker_binded));
                        mFollowMe = new FollowMe(getApplicationContext());
                        mCamViewListener.setFollowMe(mFollowMe);
                        SegwayService.base().enableBodyLight(false);

                        SegwayService.head().setHeadJointYaw(0.0f);
                        SegwayService.head().setWorldPitch(0.8f);

//                        mDepthCam.start();
                    }
        });
    }












    @Override
    public void onPause() {
        super.onPause();
        if (mCameraView != null)
            mCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) { // 加载 OpenCV
            mCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
            mCameraView.enableView();
            findViewById(R.id.btn_follow_me).setEnabled(true);
        } else {
            mCameraView.disableView();
            findViewById(R.id.btn_follow_me).setEnabled(false);

            Toast.makeText(this, "FATAL ERROR", Toast.LENGTH_LONG).show();
            Log.e(TAG, "onResume: Cannot load opencv");
        }

        if (mIsWandering) {
            SegwayService.head().setWorldPitch(0.0f);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopFollowMe();
        mCameraView.disableView();
    }
}
