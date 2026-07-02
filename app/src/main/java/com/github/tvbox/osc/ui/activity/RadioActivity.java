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
import com.github.tvbox.osc.ui.activity.SourceManagerActivity.SourceItem;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.media3.common.MediaItem;
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
        // 从 Hawk 获取自定义源列表 (SourceManager 存储)
        List<SourceItem> allSources = Hawk.get("custom_sources_list", new ArrayList<>());
        List<SourceItem> radioSources = new ArrayList<>();
        for (SourceItem item : allSources) {
            if (item.type == 4 && item.enabled) {
                radioSources.add(item);
            }
        }

        if (radioSources.isEmpty()) {
            Toast.makeText(mContext, "未找到电台源，请先在「自定义源管理」中添加音乐电台源", Toast.LENGTH_LONG).show();
            return;
        }

        // 逐个加载电台源 (M3U)
        radioGroups.clear();
        loadM3uSources(radioSources);
    }

    private void loadM3uSources(final List<SourceItem> radioSources) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<RadioGroup> result = new ArrayList<>();
                for (SourceItem source : radioSources) {
                    try {
                        RadioGroup group = parseM3u(source.name, source.url);
                        if (group != null && group.channels != null && !group.channels.isEmpty()) {
                            result.add(group);
                        }
                    } catch (Exception ignored) {}
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.isEmpty()) {
                            Toast.makeText(mContext, "电台源加载失败，请检查网络或源地址", Toast.LENGTH_LONG).show();
                            return;
                        }
                        radioGroups = result;
                        setupGroupList();
                        setupChannelList(0);
                    }
                });
            }
        }).start();
    }

    /**
     * 解析 M3U 文件，提取频道列表
     */
    private RadioGroup parseM3u(String groupName, String m3uUrl) throws Exception {
        RadioGroup group = new RadioGroup();
        group.group = groupName;
        group.channels = new ArrayList<>();

        URL url = new URL(m3uUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setInstanceFollowRedirects(true);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        String line;
        String currentName = null;
        String currentLogo = null;
        Pattern extinfPattern = Pattern.compile("#EXTINF:(-?\\d+),(.+?)$");
        Pattern logoPattern = Pattern.compile("tvg-logo=\"([^\"]+)\"");
        Pattern groupPattern = Pattern.compile("group-title=\"([^\"]+)\"");

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                // Parse metadata
                Matcher logoMatcher = logoPattern.matcher(line);
                currentLogo = logoMatcher.find() ? logoMatcher.group(1) : null;

                Matcher groupMatcher = groupPattern.matcher(line);
                if (groupMatcher.find()) {
                    group.group = groupMatcher.group(1);
                }

                Matcher nameMatcher = extinfPattern.matcher(line);
                currentName = nameMatcher.find() ? nameMatcher.group(2).trim() : "未知频道";
            } else if (!line.startsWith("#") && !line.isEmpty()) {
                // This is the URL following #EXTINF
                if (currentName != null) {
                    RadioChannel channel = new RadioChannel();
                    channel.name = currentName;
                    channel.url = line;
                    channel.logo = currentLogo;
                    group.channels.add(channel);
                    currentName = null;
                    currentLogo = null;
                }
            }
        }
        reader.close();
        conn.disconnect();
        return group;
    }

    private void setupGroupList() {
        List<String> groupNames = new ArrayList<>();
        for (RadioGroup g : radioGroups) {
            groupNames.add(g.group);
        }

        com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder> groupAdapter =
            new com.chad.library.adapter.base.BaseQuickAdapter<String, com.chad.library.adapter.base.BaseViewHolder>(R.layout.item_radio_group, groupNames) {
                @Override
                protected void convert(@NonNull com.chad.library.adapter.base.BaseViewHolder helper, String item) {
                    helper.setText(R.id.tvGroupName, item);
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
