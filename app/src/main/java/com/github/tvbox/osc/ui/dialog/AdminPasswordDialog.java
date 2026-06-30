package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

public class AdminPasswordDialog extends androidx.appcompat.app.AlertDialog {

    private OnPasswordVerifyListener listener;
    private String title;
    private boolean isSettingPassword;

    public AdminPasswordDialog(@NonNull Context context, String title, boolean isSettingPassword) {
        super(context);
        this.title = title;
        this.isSettingPassword = isSettingPassword;
        setCancelable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_admin_password);

        TextView tvTitle = findViewById(R.id.tvDialogTitle);
        EditText etPassword = findViewById(R.id.etPassword);
        TextView tvConfirm = findViewById(R.id.tvConfirm);
        TextView tvCancel = findViewById(R.id.tvCancel);

        tvTitle.setText(title);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        tvConfirm.setOnClickListener(v -> {
            String pwd = etPassword.getText().toString().trim();
            if (isSettingPassword) {
                if (pwd.length() < 4) {
                    Toast.makeText(getContext(), "密码至少4位", Toast.LENGTH_SHORT).show();
                    return;
                }
                Hawk.put(HawkConfig.ADMIN_PASSWORD, pwd);
                Toast.makeText(getContext(), "管理员密码已设置", Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onSuccess();
                dismiss();
            } else {
                String savedPwd = Hawk.get(HawkConfig.ADMIN_PASSWORD, "");
                if (savedPwd.isEmpty()) {
                    if (pwd.length() < 4) {
                        Toast.makeText(getContext(), "请先设置管理员密码（至少4位）", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Hawk.put(HawkConfig.ADMIN_PASSWORD, pwd);
                    Toast.makeText(getContext(), "管理员密码已设置", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onSuccess();
                    dismiss();
                } else if (pwd.equals(savedPwd)) {
                    if (listener != null) listener.onSuccess();
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }
        });

        tvCancel.setOnClickListener(v -> dismiss());
    }

    public void setOnPasswordVerifyListener(OnPasswordVerifyListener listener) {
        this.listener = listener;
    }

    public interface OnPasswordVerifyListener {
        void onSuccess();
    }
}
