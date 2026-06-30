package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.RadioChannel;
import com.github.tvbox.osc.bean.RadioGroup;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class RadioActivity extends BaseActivity {

    private TvRecyclerView rvGroup;
    private TvRecyclerView rvChannel;
    private TextView tvNowPlaying;
    private TextView tvChannelName;
    private TextView tvPlayStatus;
    private View llPlayerControl;

    private List<RadioGroup> radioGroups = new ArrayList<>();
    private int currentGroupIndex = 0;
    private ExoPlayer exoPlayer;

    private static final String RADIO_SOURCE_URL_KEY = "radio_source_url";
    private static final String DEFAULT_RADIO_FILE = "radio_sources.json";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_radio;
    }

    @Override
    protected void init() {
        initView();
        loadRadioSources();
    }

    private void initView() {
        rvGroup = findViewById(R.id.rvGroup);
        rvChannel = findViewById(R.id.rvChannel);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        tvChannelName = findViewById(R.id.tvChannelName);
        tvPlayStatus = findViewById(R.id.tvPlayStatus);
        llPlayerControl = findViewById(R.id.llPlayerControl);

        rvGroup.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));
        rvChannel.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));

        findViewById(R.id.btnHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void loadRadioSources() {
        // Try to load from assets first
        try {
            InputStream is = getAssets().open(DEFAULT_RADIO_FILE);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            Type type = new TypeToken<List<RadioGroup>>(){}.getType();
            radioGroups = new Gson().fromJson(json, type);
            if (radioGroups != null && !radioGroups.isEmpty()) {
                setupGroupList();
                setupChannelList(0);
                return;
            }
        } catch (Exception ignored) {}

        // Fallback: empty state
        Toast.makeText(mContext, "未找到电台配置", Toast.LENGTH_SHORT).show();
    }

    private void setupGroupList() {
        List<String> groupNames = new ArrayList<>();
        for (RadioGroup g : radioGroups) {
            groupNames.add(g.group);
        }
        com.owen.tvrecyclerview.widget.V7LinearLayoutManager layoutManager = new com.owen.tvrecyclerview.widget.V7LinearLayoutManager(mContext, com.owen.tvrecyclerview.widget.V7LinearLayoutManager.VERTICAL, false);
        rvGroup.setLayoutManager(layoutManager);
        
        com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder> groupAdapter = 
            new com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder>(R.layout.item_radio_group, groupNames) {
                @Override
                protected void convert(@NonNull com.chad.library.adapter.base.BaseViewHolder helper, String item) {
                    helper.setText(R.id.tvGroupName, item);
                    // Highlight selected group
                    int pos = helper.getLayoutPosition();
                    TextView tv = helper.getView(R.id.tvGroupName);
                    if (pos == currentGroupIndex) {
                        tv.setTextColor(Color.WHITE);
                    } else {
                        tv.setTextColor(Color.parseColor("#FFAAAAAA"));
                    }
                }
            };
        rvGroup.setAdapter(groupAdapter);
        
        rvGroup.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                currentGroupIndex = position;
                setupChannelList(position);
                groupAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupChannelList(int groupIndex) {
        if (groupIndex < 0 || groupIndex >= radioGroups.size()) return;
        RadioGroup group = radioGroups.get(groupIndex);
        List<RadioChannel> channels = group.channels;
        
        com.chad.library.adapter.base.BaseQuickAdapter<RadioChannel, com.chad.library.adapter.base.BaseViewHolder> channelAdapter = 
            new com.chad.library.adapter.base.BaseQuickAdapter<RadioChannel, com.chad.library.adapter.base.BaseViewHolder>(R.layout.item_radio_channel, channels) {
                @Override
                protected void convert(@NonNull com.chad.library.adapter.base.BaseViewHolder helper, RadioChannel item) {
                    helper.setText(R.id.tvName, item.name);
                }
            };
        rvChannel.setAdapter(channelAdapter);
        
        rvChannel.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                RadioChannel channel = channels.get(position);
                playChannel(channel);
            }
        });
    }

    private void playChannel(RadioChannel channel) {
        try {
            if (exoPlayer == null) {
                exoPlayer = new ExoPlayer.Builder(mContext).build();
            }
            exoPlayer.stop();
            exoPlayer.setMediaItem(MediaItem.fromUri(channel.url));
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            llPlayerControl.setVisibility(View.VISIBLE);
            tvChannelName.setText(channel.name);
            tvNowPlaying.setText("正在播放: " + channel.name);
            tvPlayStatus.setText("▶ 播放中");
        } catch (Exception e) {
            Toast.makeText(mContext, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
