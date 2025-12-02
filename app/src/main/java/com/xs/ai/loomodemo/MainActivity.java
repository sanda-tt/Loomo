/**
 * 作者：地狱丧钟/叁大（GitHub：@sanda-tt）
 * 邮箱：sandatt517@outlook.com
 * 创建日期：2025-10-20
 * 版权声明：本代码基于 MIT 协议开源，可自由使用、修改、分发，需保留原作者声明
 * 项目地址：https://github.com/sanda-tt/Loomo
 * 功能：道路自动驾驶，任务追随，漫游，语音控制
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
import com.xs.ai.loomodemo.followme.CamViewListener;
import com.xs.ai.loomodemo.followme.FollowMe;
import com.xs.ai.loomodemo.segwayservice.SegwayService;
import com.xs.ai.loomodemo.segwayservice.VoiceCommand;
import com.xs.ai.loomodemo.segwayservice.VoiceControl;
import com.xs.ai.loomodemo.wander.CamViewListener2;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends Activity implements VoiceControl.VoiceControlListener {

    public static final int FACE_VALIDATE_REQUEST_CODE = 1;
    public static final int FACE_REGISTER_REQUEST_CODE = 2;
    public static final int FACE_MANAGE_REQUEST_CODE = 3;
    private static final String TAG = "LoomoDemo_MainActivity";

    private static final boolean ENABLE_FACE_VERIFY = true;

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

    // --- 新增变量：语音接管控制 ---
    private boolean mIsVoiceOverriding = false; // 是否正在执行语音指令（覆盖自动模式）
    private Handler mVoiceHandler = new Handler(); // 用于处理语音动作的延时复位
    // ---------------------------

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

    protected void startRoadFollowing() {
        if (mRoadFollower == null) {
            mRoadFollower = new RoadFollower(getApplicationContext());
        }

        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.0f);

        mCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {}

            @Override
            public void onCameraViewStopped() {}

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat rgba = inputFrame.rgba();
                Core.flip(rgba, rgba, 1);

                // --- 修改点：语音接管逻辑 ---
                if (mIsVoiceOverriding) {
                    // 如果语音正在控制，直接返回图像用于预览，但不让 RoadFollower 计算和控制电机
                    return rgba;
                }
                // -------------------------

                return mRoadFollower.processFrame(rgba);
            }
        });

        mRoadFollower.start();
        mIsRoadFollowing = true;

        if (mIsFollowingMe) stopFollowMe();
        if (mIsWandering) stopWandering();

        Button btnRoadFollow = findViewById(R.id.btn_road_follow);
        if (btnRoadFollow != null) {
            btnRoadFollow.setText("停止道路跟随");
        }

        SegwayService.speak("大小姐驾到统统闪开");
    }

    protected void stopRoadFollowing() {
        if (mRoadFollower != null) {
            mRoadFollower.stop();
        }

        mIsVoiceOverriding = false; // 确保重置状态

        SegwayService.head().setHeadJointYaw(0.0f);
        SegwayService.head().setWorldPitch(0.8f);

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
        if (requestCode == FACE_VALIDATE_REQUEST_CODE) {
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
                setFollowMe(true);
                mCameraView.setCvCameraViewListener(mCamViewListener);
                ((Button) findViewById(R.id.btn_follow_me)).setText(getString(R.string.btn_follow_me_stop));
                SegwayService.speak(getString(R.string.face_verify_succ));
            }
            findViewById(R.id.btn_follow_me).setEnabled(true);
        } else if (requestCode == FACE_REGISTER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String faceId = null;
                if (data != null) {
                    faceId = data.getStringExtra(FaceActivity.FACE_ID);
                }
                FaceIdName.getInstance().createNameDialog(faceId).show();
            }
        } else if (requestCode == FACE_MANAGE_REQUEST_CODE) {
            String[] ids = new String[0];
            if (data != null) {
                ids = data.getStringArrayExtra(FaceManagerActivity.FACE_ID);
            }
            FaceIdName.getInstance().updateFaceIdNameFile(ids == null ? null : Arrays.asList(ids));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showFullScreen(true);

        mCameraView = findViewById(R.id.camera_surface_view);
        mCamViewListener = new CamViewListener();
        mCamViewListener2 = new CamViewListener2(getApplicationContext());
        mRoadFollower = new RoadFollower(getApplicationContext());

        mCameraView.setCvCameraViewListener(mDefaultViewListener);
        mCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);

        if (!ENABLE_FACE_VERIFY) {
            findViewById(R.id.btn_register_face).setVisibility(View.INVISIBLE);
            findViewById(R.id.btn_face_manage).setVisibility(View.INVISIBLE);
        } else {
            findViewById(R.id.btn_register_face).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    FaceActivity.startRegisterActivityForResult(MainActivity.this, null, FACE_REGISTER_REQUEST_CODE);
                }
            });
            FaceIdName.getInstance().setContext(MainActivity.this);
            findViewById(R.id.btn_face_manage).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    FaceManagerActivity.startActivityForResult(MainActivity.this, FaceIdName.getInstance().readFaceIdNameFile(), FACE_MANAGE_REQUEST_CODE);
                }
            });
        }

        findViewById(R.id.btn_follow_me).setEnabled(false);
        findViewById(R.id.btn_follow_me).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                if (mIsWandering) stopWandering();

                view.setEnabled(false);
                if (mIsFollowingMe) stopFollowMe();
                else startFollowMe();
            }
        });

        findViewById(R.id.btn_wander).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                if (mIsFollowingMe) stopFollowMe();

                view.setEnabled(false);
                if (mIsWandering) stopWandering();
                else startWandering();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setEnabled(true);
                    }
                }, 500);
            }
        });

        findViewById(R.id.btn_road_follow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                view.setEnabled(false);
                if (mIsFollowingMe) stopFollowMe();
                if (mIsWandering) stopWandering();

                if (mIsRoadFollowing) {
                    stopRoadFollowing();
                } else {
                    startRoadFollowing();
                }

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setEnabled(true);
                    }
                }, 500);
            }
        });

        VoiceControl.init(this);

        SegwayService.bindService(getApplicationContext(),
                new SegwayService.BindStateListener() {
                    @Override
                    public void onBindDone() {
                        SegwayService.speak(getString(R.string.on_speaker_binded));
                        mFollowMe = new FollowMe(getApplicationContext());
                        mCamViewListener.setFollowMe(mFollowMe);
                        SegwayService.base().enableBodyLight(false);

                        SegwayService.head().setHeadJointYaw(0.0f);
                        SegwayService.head().setWorldPitch(0.8f);

                        VoiceControl.getInstance().setVoiceControlListener(MainActivity.this);
                        VoiceControl.getInstance().start();
                    }
                });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCameraView != null)
            mCameraView.disableView();
        VoiceControl.getInstance().stop();
        mVoiceHandler.removeCallbacksAndMessages(null); // 清理回调
    }

    @Override
    public void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
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
        VoiceControl.getInstance().start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopFollowMe();
        mCameraView.disableView();
        VoiceControl.getInstance().close();
        mVoiceHandler.removeCallbacksAndMessages(null);
    }

    // -----------------------------------------------------
    // 语音控制回调
    // -----------------------------------------------------
    @Override
    public void onVoiceControl(VoiceCommand.ACTION action) {
        Log.d(TAG, "Received voice command: " + action);

        // 收到新指令时，先移除之前的延时复位任务，防止冲突
        mVoiceHandler.removeCallbacksAndMessages(null);

        // 如果是控制指令，且当前正在道路跟随，开启接管模式
        if (action != VoiceCommand.ACTION.UNKNOWN && action != VoiceCommand.ACTION.BYE) {
            if (mIsRoadFollowing) {
                mIsVoiceOverriding = true; // 开启接管，暂停自动逻辑
            }
        }

        switch (action) {
            case MOVE_AHEAD:
                SegwayService.base().setLinearVelocity(0.5f);
                SegwayService.base().setAngularVelocity(0.0f);
                SegwayService.speak("好的，前进");
                break;
            case MOVE_BACK:
                SegwayService.base().setLinearVelocity(-0.5f);
                SegwayService.base().setAngularVelocity(0.0f);
                SegwayService.speak("注意，后退");
                break;
            case TURN_LEFT:
                SegwayService.base().setLinearVelocity(0.0f);
                SegwayService.base().setAngularVelocity(1.5f);
                SegwayService.speak("左转");
                break;
            case TURN_RIGHT:
                SegwayService.base().setLinearVelocity(0.0f);
                SegwayService.base().setAngularVelocity(-1.5f);
                SegwayService.speak("右转");
                break;
            case TURN_LEFT_SMALL:
                // 左微调
                SegwayService.base().setLinearVelocity(0.0f);
                SegwayService.base().setAngularVelocity(0.3f);
                SegwayService.speak("微调");
                mVoiceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        SegwayService.base().setAngularVelocity(0.0f); // 停电机
                        mIsVoiceOverriding = false; // 动作完成，交还控制权给 RoadFollower
                    }
                }, 600);
                break;
            case TURN_RIGHT_SMALL:
                // 右微调
                SegwayService.base().setLinearVelocity(0.0f);
                SegwayService.base().setAngularVelocity(-0.3f);
                SegwayService.speak("微调");
                mVoiceHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        SegwayService.base().setAngularVelocity(0.0f); // 停电机
                        mIsVoiceOverriding = false; // 动作完成，交还控制权给 RoadFollower
                    }
                }, 600);
                break;
            case STOP_THERE:
                SegwayService.base().setLinearVelocity(0.0f);
                SegwayService.base().setAngularVelocity(0.0f);

                // 这里有歧义：是“停止语音动作”还是“停止道路跟随”？
                // 根据需求“不退出当前模式”，这里“停止”应当只结束语音的覆盖，让车停下。
                // 然后下一帧 RoadFollower 会根据画面决定是否继续走。

                if (mIsVoiceOverriding) {
                    mIsVoiceOverriding = false; // 交还控制权
                    SegwayService.speak("已停止");
                } else {
                    // 如果本来就没在接管（比如纯自动跑的时候喊停），那应该就是想彻底停下
                    if (mIsFollowingMe) stopFollowMe();
                    if (mIsWandering) stopWandering();
                    if (mIsRoadFollowing) stopRoadFollowing();
                    SegwayService.speak("全部停止");
                }
                break;
            case START_ROAD_FOLLOW:
                if (!mIsRoadFollowing) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsFollowingMe) stopFollowMe();
                            if (mIsWandering) stopWandering();
                            startRoadFollowing();
                        }
                    });
                } else {
                    SegwayService.speak("已经在道路跟随模式了");
                }
                break;
            case STOP_ROAD_FOLLOW:
                if (mIsRoadFollowing) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopRoadFollowing();
                        }
                    });
                } else {
                    SegwayService.speak("当前没有进行道路跟随");
                }
                break;
            case BYE:
                break;
            default:
                break;
        }
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Voice recognition timeout");
    }
}
