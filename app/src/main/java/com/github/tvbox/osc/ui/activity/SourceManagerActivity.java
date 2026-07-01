package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义源管理界面
 * 支持: 视频点播源 / IPTV直播源 / 游戏模拟源
 */
public class SourceManagerActivity extends BaseActivity {

    private static final String KEY_SOURCES = "custom_sources_list";

    private RecyclerView recyclerView;
    private SourceAdapter adapter;
    private List<SourceItem> sourceList;
    private int currentFilter = 0; // 0=全部 1=视频 2=直播 3=游戏

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_source_manager;
    }

    @Override
    protected void init() {
        setTitle("📦 自定义源管理");

        recyclerView = findViewById(R.id.sourceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 加载源数据
        sourceList = Hawk.get(KEY_SOURCES, new ArrayList<>());
        adapter = new SourceAdapter(sourceList);
        recyclerView.setAdapter(adapter);

        // 分类过滤按钮
        findViewById(R.id.btnAll).setOnClickListener(v -> filterSources(0));
        findViewById(R.id.btnVideo).setOnClickListener(v -> filterSources(1));
        findViewById(R.id.btnLive).setOnClickListener(v -> filterSources(2));
        findViewById(R.id.btnGame).setOnClickListener(v -> filterSources(3));

        // 添加源
        findViewById(R.id.btnAddSource).setOnClickListener(v -> showAddDialog());
    }

    private void filterSources(int type) {
        currentFilter = type;
        List<SourceItem> filtered;
        if (type == 0) {
            filtered = sourceList;
        } else {
            filtered = new ArrayList<>();
            for (SourceItem item : sourceList) {
                if (item.type == type) filtered.add(item);
            }
        }
        adapter.setData(filtered);
        ((TextView)findViewById(R.id.tvFilterTitle)).setText(
                type == 0 ? "全部源" : type == 1 ? "🎬 视频点播源" : type == 2 ? "📺 IPTV直播源" : "🎮 游戏模拟源");
    }

    private void showAddDialog() {
        String[] types = {"🎬 视频点播源", "📺 IPTV直播源", "🎮 游戏模拟源"};
        new AlertDialog.Builder(this)
                .setTitle("选择源类型")
                .setItems(types, (dialog, which) -> showEditDialog(null, which + 1))
                .show();
    }

    private void showEditDialog(SourceItem existing, int type) {
        boolean isEdit = existing != null;
        int sourceType = isEdit ? existing.type : type;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        EditText etName = new EditText(this);
        etName.setHint("源名称（如：饭太硬）");
        etName.setText(isEdit ? existing.name : "");
        etName.setPadding(20, 12, 20, 12);
        layout.addView(etName);

        EditText etUrl = new EditText(this);
        etUrl.setHint(isEdit ? existing.url : getUrlHint(sourceType));
        etUrl.setText(isEdit ? existing.url : "");
        etUrl.setPadding(20, 12, 20, 12);
        etUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(etUrl);

        // 仅视频源显示API字段
        EditText etApi = null;
        if (sourceType == 1) {
            etApi = new EditText(this);
            etApi.setHint("API地址（可选，如: https://example.com/api.php/provide/vod）");
            etApi.setText(isEdit ? existing.api : "");
            etApi.setPadding(20, 12, 20, 12);
            layout.addView(etApi);
        }

        String title = isEdit ? "编辑" : "添加";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + getTypeName(sourceType))
                .setView(layout)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            String api = etApi != null ? etApi.getText().toString().trim() : "";

            if (name.isEmpty()) { Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show(); return; }
            if (url.isEmpty()) { Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show(); return; }

            if (isEdit) {
                existing.name = name;
                existing.url = url;
                if (existing.type == 1) existing.api = api;
            } else {
                sourceList.add(new SourceItem(name, url, api, sourceType));
            }
            saveAndRefresh();
            dialog.dismiss();
        });
    }

    private String getUrlHint(int type) {
        switch (type) {
            case 1: return "JSON接口地址（如: http://饭太硬.com/tv）";
            case 2: return "M3U直播地址（如: https://iptv-org.github.io/iptv/index.m3u）";
            case 3: return "ROM下载地址或游戏源接口";
            default: return "源地址";
        }
    }

    private String getTypeName(int type) {
        switch (type) {
            case 1: return "🎬 视频点播源";
            case 2: return "📺 IPTV直播源";
            case 3: return "🎮 游戏模拟源";
            default: return "源";
        }
    }

    private void saveAndRefresh() {
        Hawk.put(KEY_SOURCES, sourceList);
        filterSources(currentFilter);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    // ═══ 数据模型 ═══

    public static class SourceItem {
        public String name;
        public String url;
        public String api;     // 仅视频源使用
        public int type;       // 1=视频 2=直播 3=游戏
        public boolean enabled;

        public SourceItem(String name, String url, String api, int type) {
            this.name = name;
            this.url = url;
            this.api = api;
            this.type = type;
            this.enabled = true;
        }
    }

    // ═══ 适配器 ═══

    private class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {
        private List<SourceItem> data;

        SourceAdapter(List<SourceItem> data) { this.data = new ArrayList<>(data); }

        void setData(List<SourceItem> data) { this.data = new ArrayList<>(data); notifyDataSetChanged(); }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(SourceManagerActivity.this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(30, 20, 30, 20);
            item.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout info = new LinearLayout(SourceManagerActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(SourceManagerActivity.this);
            tvName.setTextSize(16);
            tvName.setTextColor(0xFFE0E0EE);
            info.addView(tvName);

            TextView tvUrl = new TextView(SourceManagerActivity.this);
            tvUrl.setTextSize(12);
            tvUrl.setTextColor(0xFF6B6B80);
            tvUrl.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvUrl.setMaxLines(1);
            info.addView(tvUrl);

            item.addView(info);

            LinearLayout actions = new LinearLayout(SourceManagerActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            TextView btnDel = new TextView(SourceManagerActivity.this);
            btnDel.setText("删除");
            btnDel.setTextSize(14);
            btnDel.setTextColor(0xFFE94560);
            btnDel.setPadding(20, 8, 20, 8);
            actions.addView(btnDel);

            item.addView(actions);
            return new ViewHolder(item, tvName, tvUrl, btnDel);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            SourceItem item = data.get(pos);
            String prefix = item.type == 1 ? "🎬 " : item.type == 2 ? "📺 " : "🎮 ";
            h.name.setText(prefix + item.name);
            h.url.setText(item.url);
            h.itemView.setOnClickListener(v -> showEditDialog(item, 0));
            h.btnDel.setOnClickListener(v -> {
                sourceList.remove(item);
                saveAndRefresh();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, url, btnDel;
            ViewHolder(View v, TextView n, TextView u, TextView d) {
                super(v); name = n; url = u; btnDel = d;
            }
        }
    }
}
