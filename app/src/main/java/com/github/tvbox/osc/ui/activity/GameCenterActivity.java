package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.GameGroup;
import com.github.tvbox.osc.bean.GameItem;
import com.github.tvbox.osc.ui.activity.SourceManagerActivity.SourceItem;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GameCenterActivity extends BaseActivity {

    private TvRecyclerView rvGroup;
    private TvRecyclerView rvGames;
    private List<GameGroup> gameGroups = new ArrayList<>();
    private int currentGroupIndex = 0;

    private static final String DEFAULT_GAME_FILE = "game_sources.json";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_game_center;
    }

    @Override
    protected void init() {
        initView();
        loadGameSources();
    }

    private void initView() {
        rvGroup = findViewById(R.id.rvGroup);
        rvGames = findViewById(R.id.rvGames);
        rvGroup.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));
        rvGames.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));

        findViewById(R.id.btnHome).setOnClickListener(v -> onBackPressed());
    }

    private void loadGameSources() {
        // 1. Try assets/game_sources.json
        try {
            InputStream is = getAssets().open(DEFAULT_GAME_FILE);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            Type type = new TypeToken<List<GameGroup>>(){}.getType();
            List<GameGroup> assetGames = new Gson().fromJson(json, type);
            if (assetGames != null && !assetGames.isEmpty()) {
                gameGroups.clear();
                gameGroups.addAll(assetGames);
            }
        } catch (Exception ignored) {}

        // 2. Load type=3 custom sources from custom_sources_list and merge
        try {
            String KEY_SOURCES = "custom_sources_list";
            List<SourceItem> sources = Hawk.get(KEY_SOURCES, new ArrayList<>());
            for (SourceItem si : sources) {
                if (si.type == 3 && !TextUtils.isEmpty(si.url)) {
                    List<GameItem> games = new ArrayList<>();
                    games.add(new GameItem(si.name, si.url, "h5"));
                    gameGroups.add(new GameGroup("🎮 " + si.name, games));
                }
            }
        } catch (Exception ignored) {}

        if (gameGroups != null && !gameGroups.isEmpty()) {
            setupGroupList();
            setupGameList(0);
            return;
        }
        Toast.makeText(mContext, "未找到游戏配置\n请先在「源管理」中添加游戏源", Toast.LENGTH_LONG).show();
    }

    private void setupGroupList() {
        List<String> groupNames = new ArrayList<>();
        for (GameGroup g : gameGroups) groupNames.add(g.group);

        com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder> groupAdapter =
            new com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder>(R.layout.item_radio_group, groupNames) {
                @Override
                protected void convert(@NonNull com.chad.library.adapter.base.BaseViewHolder helper, String item) {
                    helper.setText(R.id.tvGroupName, item);
                    int pos = helper.getLayoutPosition();
                    TextView tv = helper.getView(R.id.tvGroupName);
                    tv.setTextColor(pos == currentGroupIndex ? Color.WHITE : Color.parseColor("#FFAAAAAA"));
                }
            };
        rvGroup.setAdapter(groupAdapter);
        rvGroup.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                currentGroupIndex = position;
                setupGameList(position);
                groupAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupGameList(int groupIndex) {
        if (groupIndex < 0 || groupIndex >= gameGroups.size()) return;
        List<GameItem> games = gameGroups.get(groupIndex).games;

        com.chad.library.adapter.base.BaseQuickAdapter<GameItem, com.chad.library.adapter.base.BaseViewHolder> gameAdapter =
            new com.chad.library.adapter.base.BaseQuickAdapter<GameItem, com.chad.library.adapter.base.BaseViewHolder>(R.layout.item_radio_channel, games) {
                @Override
                protected void convert(@NonNull com.chad.library.adapter.base.BaseViewHolder helper, GameItem item) {
                    helper.setText(R.id.tvName, "🎮 " + item.name);
                }
            };
        rvGames.setAdapter(gameAdapter);
        rvGames.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                GameItem game = games.get(position);
                Intent intent = new Intent(GameCenterActivity.this, GameEmulatorActivity.class);
                intent.putExtra("game_name", game.name);
                intent.putExtra("game_url", game.url);
                startActivity(intent);
            }
        });
    }
}
