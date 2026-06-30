package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.orhanobut.hawk.Hawk;

import java.util.Random;

/**
 * 内容安全拦截器
 * 功能：
 * 1. 少儿模式 — 随机算术题 + 定时器
 * 2. 成人/4K 分区锁 — 需管理员密码
 */
public class ContentGuardInterceptor {

    // ═══════════════════════════════════════════════════════
    //  少儿模式
    // ═══════════════════════════════════════════════════════

    /**
     * 进入少儿模式（算术题验证）
     */
    public static void enterKidsMode(Context context, Runnable onPass) {
        showMathDialog(context, "🧒 家长验证", "请计算以下算式以开启少儿模式", () -> {
            // 开启少儿模式
            Hawk.put(HawkConfig.KIDS_MODE_ENABLED, true);
            int minutes = Hawk.get(HawkConfig.KIDS_MODE_TIMER_MINUTES, 45);
            Hawk.put(HawkConfig.KIDS_MODE_REMAINING_SECONDS, minutes * 60);
            Toast.makeText(context, "✅ 已进入少儿模式（" + minutes + "分钟）", Toast.LENGTH_LONG).show();
            if (onPass != null) onPass.run();
        });
    }

    /**
     * 退出少儿模式（算术题验证）
     */
    public static void exitKidsMode(Context context, Runnable onPass) {
        showMathDialog(context, "🧒 退出少儿模式", "请输入计算结果以退出", () -> {
            Hawk.put(HawkConfig.KIDS_MODE_ENABLED, false);
            Hawk.put(HawkConfig.KIDS_MODE_REMAINING_SECONDS, 0);
            Toast.makeText(context, "✅ 已退出少儿模式", Toast.LENGTH_LONG).show();
            if (onPass != null) onPass.run();
        });
    }

    /**
     * 随机算术题弹窗
     */
    private static void showMathDialog(Context context, String title, String message, Runnable onCorrect) {
        Random rand = new Random();
        int a = rand.nextInt(20) + 1;
        int b = rand.nextInt(20) + 1;
        int op = rand.nextInt(2);
        String opStr = op == 0 ? "+" : "-";
        int answer;
        if (op == 0) {
            answer = a + b;
        } else {
            if (a < b) { int t = a; a = b; b = t; }
            answer = a - b;
        }

        final int correctAnswer = answer;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message + "\n\n" + a + " " + opStr + " " + b + " = ?");

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("输入答案");
        builder.setView(input, 40, 0, 40, 0);

        builder.setPositiveButton("确定", (dialog, which) -> {
            try {
                int userAns = Integer.parseInt(input.getText().toString().trim());
                if (userAns == correctAnswer) {
                    if (onCorrect != null) onCorrect.run();
                } else {
                    Toast.makeText(context, "❌ 答案错误，请重试", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(context, "请输入数字", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 检查少儿模式是否开启且未超时
     */
    public static boolean isKidsModeActive() {
        if (!Hawk.get(HawkConfig.KIDS_MODE_ENABLED, false)) return false;
        int remaining = Hawk.get(HawkConfig.KIDS_MODE_REMAINING_SECONDS, 0);
        return remaining > 0;
    }

    /**
     * 减少剩余时间（每秒调用）
     */
    public static void tickKidsMode() {
        if (!Hawk.get(HawkConfig.KIDS_MODE_ENABLED, false)) return;
        int remaining = Hawk.get(HawkConfig.KIDS_MODE_REMAINING_SECONDS, 0);
        if (remaining > 0) {
            remaining--;
            Hawk.put(HawkConfig.KIDS_MODE_REMAINING_SECONDS, remaining);
            if (remaining <= 0) {
                Hawk.put(HawkConfig.KIDS_MODE_ENABLED, false);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  成人内容锁
    // ═══════════════════════════════════════════════════════

    public static boolean isAdultCategory(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("成人") || n.contains("伦理") || n.contains("午夜")
            || n.contains("限制") || n.contains("18禁") || n.contains("se");
    }
}
