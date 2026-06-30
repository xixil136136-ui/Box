package com.github.tvbox.osc.player;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.base.App;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView implements Object.Callback {
    private View danmuView;

    public MyVideoView(@NonNull Context context) {
        super(context, null);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AbstractPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

    public int[] getVideoSize(){
        return mVideoSize;
    }

    @Override
    public void seekTo(long pos) {
        super.seekTo(pos);
        if (haveDanmu()) danmuView.seekTo(pos);
    }

    @Override
    public void resume() {
        super.resume();
        if (haveDanmu()) danmuView.resume();
    }

    @Override
    public void start() {
        super.start();
        if (haveDanmu()) danmuView.resume();
    }

    @Override
    public void pause() {
        super.pause();
        if (haveDanmu()) danmuView.pause();
    }

    @Override
    public void stopPlay() {
        super.stopPlay();
        if (haveDanmu()) danmuView.stop();
    }

    @Override
    public void release() {
        super.release();
        if (haveDanmu()) danmuView.release();
    }

    private boolean haveDanmu() {
        return danmuView != null && danmuView.isPrepared();
    }

    public void setDanmuView(View view) {
        view.setCallback(this);
        danmuView = view;
    }
    public View getDanmuView() {
        return danmuView;
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (danmuView == null) return;
            if (isPlaying() && danmuView.isPrepared()) danmuView.start(getCurrentPosition());
        });
    }

    @Override
    public void updateTimer(Object timer) {

    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {

    }

    @Override
    public void drawingFinished() {

    }
}
