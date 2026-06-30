package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.Random;

public class KidsModeLockActivity extends BaseActivity {

    private TextView tvTimer;
    private TextView tvQuestion;
    private TextView tvAnswer;
    private TextView tvResult;

    private int questionAnswer;  // 正确答案
    private StringBuilder currentInput = new StringBuilder();
    private CountDownTimer countDownTimer;
    private long remainingSeconds;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_kids_mode_lock;
    }

    @Override
    protected void init() {
        initView();
        generateQuestion();
        startTimer();
    }

    private void initView() {
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvAnswer = findViewById(R.id.tvAnswer);
        tvResult = findViewById(R.id.tvResult);

        // 数字键盘
        int[] btnIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        for (int i = 0; i < btnIds.length; i++) {
            final int digit = i;
            findViewById(btnIds[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDigitClick(digit);
                }
            });
        }
        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentInput.setLength(0);
                tvAnswer.setText("");
                tvResult.setText("");
            }
        });
        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAnswer();
            }
        });
    }

    private void generateQuestion() {
        int level = Hawk.get(HawkConfig.KIDS_MODE_MATH_LEVEL, 1);
        Random rand = new Random();
        int a, b;

        switch (level) {
            case 2: // 中等: 两位数加减
                a = rand.nextInt(50) + 10;
                b = rand.nextInt(50) + 10;
                if (rand.nextBoolean()) {
                    questionAnswer = a + b;
                    tvQuestion.setText(a + " + " + b + " = ?");
                } else {
                    questionAnswer = a;
                    a = a + b;
                    tvQuestion.setText(a + " - " + b + " = ?");
                }
                break;
            case 3: // 困难: 三位数加减或乘法
                if (rand.nextBoolean()) {
                    a = rand.nextInt(20) + 2;
                    b = rand.nextInt(20) + 2;
                    questionAnswer = a * b;
                    tvQuestion.setText(a + " × " + b + " = ?");
                } else {
                    a = rand.nextInt(200) + 50;
                    b = rand.nextInt(100) + 10;
                    questionAnswer = a + b;
                    tvQuestion.setText(a + " + " + b + " = ?");
                }
                break;
            default: // 简单: 个位数加减
                a = rand.nextInt(9) + 1;
                b = rand.nextInt(9) + 1;
                if (rand.nextBoolean()) {
                    questionAnswer = a + b;
                    tvQuestion.setText(a + " + " + b + " = ?");
                } else {
                    if (a < b) { int t = a; a = b; b = t; }
                    questionAnswer = a - b;
                    tvQuestion.setText(a + " - " + b + " = ?");
                }
                break;
        }
    }

    private void onDigitClick(int digit) {
        if (currentInput.length() < 6) {
            currentInput.append(digit);
            tvAnswer.setText(currentInput.toString());
        }
    }

    private void checkAnswer() {
        if (currentInput.length() == 0) return;
        try {
            int userAnswer = Integer.parseInt(currentInput.toString());
            if (userAnswer == questionAnswer) {
                tvResult.setText("✓ 回答正确!");
                tvResult.setTextColor(0xFF4CAF50);
                // 正确后退出少儿模式
                Hawk.put(HawkConfig.KIDS_MODE_ENABLED, false);
                if (countDownTimer != null) countDownTimer.cancel();
                Toast.makeText(this, "少儿模式已关闭", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                tvResult.setText("✗ 答案不对, 再试试");
                tvResult.setTextColor(0xFFFF4444);
                currentInput.setLength(0);
                tvAnswer.setText("");
            }
        } catch (NumberFormatException e) {
            tvResult.setText("输入无效");
            tvResult.setTextColor(0xFFFF4444);
        }
    }

    private void startTimer() {
        int minutes = Hawk.get(HawkConfig.KIDS_MODE_TIMER_MINUTES, 30);
        remainingSeconds = minutes * 60L;

        countDownTimer = new CountDownTimer(remainingSeconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingSeconds = millisUntilFinished / 1000;
                long mins = remainingSeconds / 60;
                long secs = remainingSeconds % 60;
                tvTimer.setText(String.format("剩余使用时间: %02d:%02d", mins, secs));
                Hawk.put(HawkConfig.KIDS_MODE_REMAINING_SECONDS, (int) remainingSeconds);
            }

            @Override
            public void onFinish() {
                // 时间到，自动锁定返回Home
                tvTimer.setText("使用时间已到");
                Hawk.put(HawkConfig.KIDS_MODE_ENABLED, true);
                Toast.makeText(KidsModeLockActivity.this, "使用时间已到", Toast.LENGTH_LONG).show();
                finishAffinity();
            }
        }.start();
    }

    @Override
    public void onBackPressed() {
        // 阻止返回键 - 必须回答问题或时间到
        Toast.makeText(this, "请先回答数学题退出少儿模式", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
