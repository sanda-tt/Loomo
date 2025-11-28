package com.xs.ai.loomodemo.wander;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.xs.ai.loomodemo.NativeAlgo;
import com.xs.ai.loomodemo.R;
import com.xs.ai.loomodemo.coco.CocoClassName;
import com.xs.ai.loomodemo.segwayservice.SegwayService;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class DetectResultActivity extends Activity {
    public static final String EXTRA_IMAGE = "EXTRA_IMAGE";
    public static final String EXTRA_RECT = "EXTRA_RECT";
    public static final String EXTRA_IDS = "EXTRA_IDS";

    static {
        NativeAlgo.trainRevisiedResult(); // 先训练一下
    }

    static class VoiceControl implements AutoCloseable {

        interface VoiceListener {
            void onYes();
            void onNo();
        }

        VoiceControl(@NonNull VoiceListener listener) {
            mListener = listener;
            try {
                SegwayService.speechRecognizer().addGrammarConstraint(SegwayService.speechRecognizer().createGrammarConstraint(grammar));
            } catch (VoiceException e) {
                e.printStackTrace();
            }
        }

        void start() {
            try {
                SegwayService.speechRecognizer().startRecognitionMode(new RecognitionListener() {
                    @Override
                    public void onRecognitionStart() {

                    }

                    @Override
                    public boolean onRecognitionResult(RecognitionResult recognitionResult) {
                        int RECOG_THRESH = 50;
                        if (recognitionResult.getConfidence() < RECOG_THRESH)
                            return true; // true for recognition
                        if (recognitionResult.getRecognitionResult().equalsIgnoreCase("yes")) {
                            mListener.onYes();
                            return false;
                        } else if (recognitionResult.getRecognitionResult().equalsIgnoreCase("no")) {
                            mListener.onNo();
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public boolean onRecognitionError(String error) {
                        return true;
                    }
                });
            } catch (VoiceException e) {
                e.printStackTrace();
            }
        }

        void stop() {
            try {
                SegwayService.speechRecognizer().stopRecognition();
            } catch (VoiceException e) {
                e.printStackTrace();
            }
        }

        private static final String grammar = "{\n" +
                "  \"name\": \"result_checker\",\n" +
                "  \"slotList\": [\n" +
                "    {\n" +
                "      \"name\": \"yes_or_no\",\n" +
                "      \"isOptional\": false,\n" +
                "      \"word\": [\n" +
                "        \"yes\",\n" +
                "        \"no\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        private VoiceListener mListener;

        @Override
        public void close() throws Exception {
            stop();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_result);

        setFullScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SegwayService.head().setWorldPitch(0.8f);

        byte[] byteArray = getIntent().getByteArrayExtra(EXTRA_IMAGE);
        mBmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        Utils.bitmapToMat(mBmp, mImage);

        int[] rect_ = getIntent().getIntArrayExtra(EXTRA_RECT);
        mRect = new Rect(rect_[0], rect_[1], rect_[2], rect_[3]);

        int[] ids = getIntent().getIntArrayExtra(EXTRA_IDS);
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; ++i) {
            names[i] = ((ids[i] <= 0) ? "UNKNOWN" : CocoClassName.name(ids[i] - 1));
        }

        mDetectResult = names[0];
        mDetectResults = names;

        // 启动语音识别，侦听 yes or no
        mVoiceRecog = new VoiceControl(new VoiceControl.VoiceListener() {
            @Override
            public void onYes() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.btn_yes).performClick();
                    }
                });
            }
            @Override
            public void onNo() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.btn_no).performClick();
                    }
                });
            }
        });
        mVoiceRecog.start();

        if (mDetectResult != null && !mDetectResult.isEmpty()) {
            // 根据历史记录自动修正
            if (mImage != null && !mImage.empty() && mRect != null && mRect.area() > 100) {
                String strAutoRevisied = NativeAlgo.checkRevisiedResult(mImage.submat(mRect));
                if (!strAutoRevisied.isEmpty()) {
                    // 如果有修正结果，把修正结果放到第一位，原结果依次后移一位
//                    mDetectResults[3] = mDetectResults[2];
//                    mDetectResults[2] = mDetectResults[1];
//                    mDetectResults[1] = mDetectResults[0];
//                    mDetectResults[0] = strAutoRevisied;
//                    mDetectResult = mDetectResults[0];
                    // 仅修改第一个候选项（因为目前自动修正效果不行，而且都没有阈值）
                    mDetectResults[3] = mDetectResults[2];
                    mDetectResults[2] = mDetectResults[1];
                    mDetectResults[1] = strAutoRevisied;
                }
            }
            //SegwayService.speak("Is this " + mDetectResult + "?");
            SegwayService.speak("I see a " + mDetectResult + ", am I right " + "?");
        }

        initView();

        findViewById(R.id.btn_yes).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVoiceRecog.stop();
                if (mCountDownTimer != null) mCountDownTimer.cancel();
                ((Button)findViewById(R.id.btn_yes)).setText("YES");
                SegwayService.speak("Ok, it's " + mDetectResult);

                // 识别正确的结果也保存下来
                mResultSaver.addRecord(mImage, mRect, mDetectResults, "");

                finish();
            }
        });

        findViewById(R.id.btn_no).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVoiceRecog.stop();
                if (mCountDownTimer != null) mCountDownTimer.cancel();
                ((Button)findViewById(R.id.btn_yes)).setText("YES");
                ((TextView)findViewById(R.id.tips)).setText("Then what's this?");
                SegwayService.speak("Then what's this?");

                findViewById(R.id.btn_yes).setEnabled(false);
                findViewById(R.id.btn_no).setEnabled(false);
                findViewById(R.id.btn_candidate_1).setVisibility(View.VISIBLE);
                findViewById(R.id.btn_candidate_2).setVisibility(View.VISIBLE);
                findViewById(R.id.btn_candidate_3).setVisibility(View.VISIBLE);
                findViewById(R.id.btn_custom).setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.btn_candidate_1).setOnClickListener(mOnClickListener);
        findViewById(R.id.btn_candidate_2).setOnClickListener(mOnClickListener);
        findViewById(R.id.btn_candidate_3).setOnClickListener(mOnClickListener);
        findViewById(R.id.btn_custom).setOnClickListener(mOnClickListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mVoiceRecog != null)
            mVoiceRecog = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private static final int REQUEST_CODE_MANUAL_REVISE = 2001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MANUAL_REVISE) {
            if (resultCode == RESULT_OK) {
                String name = data.getStringExtra("name");
                SegwayService.speak("Got it! It's " + name);
                mResultSaver.addRecord(mImage, mRect, mDetectResults, name);
                finish();
            }
        }
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_candidate_1:
                case R.id.btn_candidate_2:
                case R.id.btn_candidate_3: {
                    String name = ((Button)view).getText().toString();
                    SegwayService.speak("OK, it's " + name);
                    mResultSaver.addRecord(mImage, mRect, mDetectResults, name);
                    finish();
                }
                break;
                case R.id.btn_custom: {
                    SegwayService.speak("Please select or input the type");
                    // 显示一个弹窗，以列表显示所有可选项，额外增加一个输入框，让用户手动输入
                    Intent intent = new Intent(DetectResultActivity.this, ManualReviseDetectResult.class);
                    startActivityForResult(intent, REQUEST_CODE_MANUAL_REVISE);
                }
                break;
                default:
                    break;
            }
        }
    };

    private void setFullScreen() {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void initView() {
        // 显示图像
        Mat img = mImage.clone();
        Imgproc.rectangle(img, mRect, new Scalar(255, 0, 0, 255), 2);

        Utils.matToBitmap(img, mBmp);
        ((ImageView)findViewById(R.id.imageView)).setImageBitmap(mBmp);

        // 显示检测结果和候选结果
        ((TextView)findViewById(R.id.detect_result)).setText(mDetectResults[0].toUpperCase() + "?");
        ((TextView)findViewById(R.id.btn_candidate_1)).setText(mDetectResults[1].toUpperCase());
        ((TextView)findViewById(R.id.btn_candidate_2)).setText(mDetectResults[2].toUpperCase());
        ((TextView)findViewById(R.id.btn_candidate_3)).setText(mDetectResults[3].toUpperCase());

        // 默认不显示候选项，只有用户选择了“NO”才显示出来
        findViewById(R.id.btn_candidate_1).setVisibility(View.GONE);
        findViewById(R.id.btn_candidate_2).setVisibility(View.GONE);
        findViewById(R.id.btn_candidate_3).setVisibility(View.GONE);
        findViewById(R.id.btn_custom).setVisibility(View.GONE);

        mCountDownTimer = new CountDownTimer(20*1000, 500) {
            @Override
            public void onTick(long l) {
                ((Button)findViewById(R.id.btn_yes)).setText("YES(" + l / 1000 + ")");
            }

            @Override
            public void onFinish() {
                // 倒计时结束没有任何动作则直接认为 YES
                findViewById(R.id.btn_yes).performClick();
            }
        };
         mCountDownTimer.start();
    }

    private CountDownTimer mCountDownTimer;
    private VoiceControl mVoiceRecog;

    private ResultSaver mResultSaver = new ResultSaver();

    private String mDetectResult;
    private String[] mDetectResults;
    private Mat mImage = new Mat();
    private Bitmap mBmp;
    private Rect mRect;
}
