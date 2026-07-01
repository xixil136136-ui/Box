package com.github.tvbox.osc.cast;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * 游戏画面局域网投屏（MediaProjection + MediaCodec H.264 + UDP）
 *
 * 原理：
 * 1. MediaProjection 截取屏幕
 * 2. MediaCodec 编码为 H.264
 * 3. UDP 发送到电视端（电视端用 ExoPlayer 或其他播放器接收）
 *
 * 用法：
 *   ScreenCastManager caster = new ScreenCastManager(activity);
 *   caster.setTargetIp("192.168.1.100");
 *   caster.setTargetPort(12345);
 *   // 在 onActivityResult 中：
 *   caster.start(resultCode, data);
 *
 *   // 停止：
 *   caster.stop();
 */
public class ScreenCastManager {

    private static final String TAG = "ScreenCast";
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int BITRATE = 2_000_000; // 2Mbps
    private static final int FPS = 30;

    private final Activity activity;
    private MediaProjectionManager mpManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec encoder;
    private Surface inputSurface;
    private DatagramSocket udpSocket;
    private InetAddress targetAddress;
    private int targetPort = 12345;
    private volatile boolean isRunning = false;
    private HandlerThread encodeThread;

    public ScreenCastManager(Activity activity) {
        this.activity = activity;
        this.mpManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    /** 设置电视端 IP */
    public void setTargetIp(String ip) {
        try {
            targetAddress = InetAddress.getByName(ip);
        } catch (Exception e) {
            Log.e(TAG, "Bad IP: " + ip, e);
        }
    }

    public void setTargetPort(int port) {
        this.targetPort = port;
    }

    /** 创建屏幕捕获 Intent（在 Activity 中调用 startActivityForResult） */
    public Intent createScreenCaptureIntent() {
        return mpManager.createScreenCaptureIntent();
    }

    /** 请求屏幕录制权限的请求码 */
    public static final int REQUEST_CODE = 10001;

    /** 启动投屏（在 onActivityResult 中调用） */
    public void start(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "用户拒绝了屏幕录制权限");
            return;
        }

        isRunning = true;

        try {
            udpSocket = new DatagramSocket();
        } catch (Exception e) {
            Log.e(TAG, "UDP Socket 创建失败", e);
            return;
        }

        mediaProjection = mpManager.getMediaProjection(resultCode, data);
        initEncoder();
        createVirtualDisplay();
        startEncodingLoop();
    }

    private void initEncoder() {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();
            Log.i(TAG, "Encoder initialized");
        } catch (IOException e) {
            Log.e(TAG, "Encoder init failed", e);
        }
    }

    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "GameScreenCast", WIDTH, HEIGHT, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null);
        Log.i(TAG, "VirtualDisplay created");
    }

    private void startEncodingLoop() {
        encodeThread = new HandlerThread("CastEncode");
        encodeThread.start();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = encoder.getOutputBuffers();

        while (isRunning) {
            int outputIndex = encoder.dequeueOutputBuffer(info, 10000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = encoder.getOutputBuffers();
            } else if (outputIndex >= 0) {
                ByteBuffer buffer = outputBuffers[outputIndex];
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                sendH264Data(buffer, info.size);
                encoder.releaseOutputBuffer(outputIndex, false);
            }
            try {
                Thread.sleep(1000 / FPS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void sendH264Data(ByteBuffer buffer, int size) {
        if (udpSocket == null || targetAddress == null) return;
        try {
            byte[] data = new byte[size];
            buffer.get(data);
            DatagramPacket packet = new DatagramPacket(data, data.length, targetAddress, targetPort);
            udpSocket.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "UDP send error", e);
        }
    }

    /** 停止投屏 */
    public void stop() {
        isRunning = false;
        if (encoder != null) {
            try { encoder.stop(); } catch (Exception ignored) {}
            encoder.release();
            encoder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            udpSocket = null;
        }
        if (encodeThread != null) {
            encodeThread.quitSafely();
            encodeThread = null;
        }
        Log.i(TAG, "Screen cast stopped");
    }
}
