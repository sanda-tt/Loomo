package com.xs.ai.loomodemo.segwayservice;

import android.content.Context;
import android.util.Log;

import com.xs.ai.loomodemo.R;

import java.io.InputStream;
import java.util.HashMap;

public class VoiceCommand {
    private static final String TAG = "VoiceCommand";
    private Context mContext;

    private VoiceCommand() {}

    // 使用单例模式
    private static class VoiceCommandLoader {
        private static final VoiceCommand INSTANCE = new VoiceCommand();
    }
    public static VoiceCommand getInstance() {
        return VoiceCommandLoader.INSTANCE;
    }

    public static void init(final Context context) {
        VoiceCommandLoader.INSTANCE.mContext = context;
        VoiceCommandLoader.INSTANCE.mActionVoice = new HashMap<String, ACTION>();

        // --- 保留原有的资源映射 ---
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_left), ACTION.TURN_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_right), ACTION.TURN_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_turn_to_me), ACTION.TURN_TO_ME);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_move_ahead), ACTION.MOVE_AHEAD);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_move_back), ACTION.MOVE_BACK);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_left), ACTION.LOOK_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_right), ACTION.LOOK_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_look_front), ACTION.LOOK_FRONT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_keep_moving), ACTION.KEEP_MOVING);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_speed_up), ACTION.SPEED_UP);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_slow_down), ACTION.SLOW_DOWN);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_stop_there), ACTION.STOP_THERE);
        VoiceCommandLoader.INSTANCE.mActionVoice.put(context.getString(R.string.voice_stop_speech_recog), ACTION.BYE);

        // --- 中文指令映射 ---
        // 1. 基础运动
        VoiceCommandLoader.INSTANCE.mActionVoice.put("前进", ACTION.MOVE_AHEAD);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向前", ACTION.MOVE_AHEAD);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("后退", ACTION.MOVE_BACK);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向后", ACTION.MOVE_BACK);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("左转", ACTION.TURN_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向左", ACTION.TURN_LEFT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("右转", ACTION.TURN_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向右", ACTION.TURN_RIGHT);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("停止", ACTION.STOP_THERE);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("停下", ACTION.STOP_THERE);

        // 2. 微调指令 (新增)
        VoiceCommandLoader.INSTANCE.mActionVoice.put("左微调", ACTION.TURN_LEFT_SMALL);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向左一点", ACTION.TURN_LEFT_SMALL);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("右微调", ACTION.TURN_RIGHT_SMALL);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("向右一点", ACTION.TURN_RIGHT_SMALL);

        // 3. 道路跟随指令
        VoiceCommandLoader.INSTANCE.mActionVoice.put("开始道路跟随", ACTION.START_ROAD_FOLLOW);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("进入道路跟随", ACTION.START_ROAD_FOLLOW);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("退出道路跟随", ACTION.STOP_ROAD_FOLLOW);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("停止道路跟随", ACTION.STOP_ROAD_FOLLOW);
        VoiceCommandLoader.INSTANCE.mActionVoice.put("关闭道路跟随", ACTION.STOP_ROAD_FOLLOW);
    }

    private HashMap<String, ACTION> mActionVoice;

    void loadCommands() {
        loadCommand(R.raw.voice_cmd_common);
        loadCommand(R.raw.voice_cmd_move);
    }

    boolean shouldStopRecog(String words) {
        return parseCommand(words) == ACTION.BYE;
    }

    private boolean loadCommand(int rawResourceId) {
        String grammarJson;
        try {
            InputStream in_s = mContext.getResources().openRawResource(rawResourceId);
            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            grammarJson = new String(b);
            SegwayService.speechRecognizer().addGrammarConstraint(SegwayService.speechRecognizer().createGrammarConstraint(grammarJson));
        } catch (Exception e) {
            Log.e(TAG, "loadCommand: addGrammarConstraint " + rawResourceId + " exception: " + e.getMessage());
            return false;
        }
        return true;
    }

    public enum ACTION {
        UNKNOWN,
        TURN_LEFT,
        TURN_RIGHT,
        TURN_TO_ME,
        MOVE_AHEAD,
        MOVE_BACK,
        LOOK_LEFT,
        LOOK_RIGHT,
        LOOK_FRONT,
        KEEP_MOVING,
        SPEED_UP,
        SLOW_DOWN,
        STOP_THERE,
        BYE,
        // --- 新增动作 ---
        START_ROAD_FOLLOW,
        STOP_ROAD_FOLLOW,
        TURN_LEFT_SMALL, // 左微调
        TURN_RIGHT_SMALL // 右微调
    }

    public ACTION parseCommand(String words) {
        if (mActionVoice.containsKey(words))
            return mActionVoice.get(words);

        // 模糊匹配逻辑
        if (words.contains("左微调") || words.contains("向左一点")) return ACTION.TURN_LEFT_SMALL;
        if (words.contains("右微调") || words.contains("向右一点")) return ACTION.TURN_RIGHT_SMALL;

        if (words.contains("前进") || words.contains("向前")) return ACTION.MOVE_AHEAD;
        if (words.contains("后退") || words.contains("向后")) return ACTION.MOVE_BACK;
        if (words.contains("左转")) return ACTION.TURN_LEFT;
        if (words.contains("右转")) return ACTION.TURN_RIGHT;
        if (words.contains("停")) return ACTION.STOP_THERE;
        if (words.contains("开始道路跟随") || words.contains("进入道路跟随")) return ACTION.START_ROAD_FOLLOW;
        if (words.contains("停止道路跟随") || words.contains("退出道路跟随")) return ACTION.STOP_ROAD_FOLLOW;

        Log.w(TAG, "parseCommand unknown words: " + words);
        return ACTION.UNKNOWN;
    }
}