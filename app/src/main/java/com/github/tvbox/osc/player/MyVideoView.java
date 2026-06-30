package com.github.tvbox.osc.player;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.base.App;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView implements DrawHandler.Callback {
    /* danmuView removed */

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
    }

    @Override
    public void resume() {
        super.resume();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void pause() {
        super.pause();
    }

    @Override
    public void stopPlay() {
        super.stopPlay();
    }

    @Override
    public void release() {
        super.release();
    }

    }

        view.setCallback(this);
        /* danmuView removed */ null = view;
    }
        return /* danmuView removed */ null;
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (/* danmuView removed */ null == null) return;
        });
    }

    @Override

    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {

    }

    @Override
    public void drawingFinished() {

    }
}
