package com.github.tvbox.osc.dlna;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.fourthline.clingg.UpnpService;
import org.fourthline.clingg.UpnpServiceImpl;
import org.fourthline.clingg.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.clingg.model.DefaultServiceManager;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.clingg.model.meta.DeviceIdentity;
import org.fourthline.clingg.model.meta.LocalDevice;
import org.fourthline.clingg.model.meta.LocalService;
import org.fourthline.clingg.model.meta.ManufacturerDetails;
import org.fourthline.clingg.model.meta.ModelDetails;
import org.fourthline.clingg.model.types.DeviceType;
import org.fourthline.clingg.model.types.UDADeviceType;
import org.fourthline.clingg.model.types.UDN;
import org.fourthline.clingg.support.avtransport.impl.AVTransportService;
import org.fourthline.clingg.support.connectionmanager.impl.ConnectionManagerServiceImpl;
import org.fourthline.clingg.support.renderingcontrol.impl.RenderingControlServiceImpl;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * DLNA 投屏接收端服务
 * 让本机作为 DLNA MediaRenderer，接收手机端腾讯/爱奇艺/B站等APP投屏
 *
 * 在 Application.onCreate() 或 HomeActivity 中启动：
 *   startService(new Intent(context, DLNARenderService.class));
 */
public class DLNARenderService extends Service {

    private static final String TAG = "DLNA_Render";
    private UpnpService upnpService;

    @Override
    public void onCreate() {
        super.onCreate();
        startUpnp();
    }

    private void startUpnp() {
        try {
            upnpService = new UpnpServiceImpl();
            LocalDevice device = createDevice();
            upnpService.getRegistry().addDevice(device);
            Log.i(TAG, "DLNA Renderer started: 全能看-TVBox");
        } catch (Exception e) {
            Log.e(TAG, "DLNA start failed", e);
        }
    }

    private LocalDevice createDevice() throws Exception {
        DeviceIdentity identity = new DeviceIdentity(new UDN(UUID.randomUUID().toString()));
        DeviceType type = new UDADeviceType("MediaRenderer", 1);
        DeviceDetails details = new DeviceDetails(
                "全能看-TVBox",
                new ManufacturerDetails("QuanNengKan", ""),
                new ModelDetails("QNK-001", "DLNA MediaRenderer", "v1.0"));

        LocalService<AVTransportService> avtService =
                new AnnotationLocalServiceBinder().read(AVTransportService.class);
        avtService.setManager(new DefaultServiceManager<>(
                avtService, AVTransportService.class));

        LocalService<ConnectionManagerServiceImpl> cmService =
                new AnnotationLocalServiceBinder().read(ConnectionManagerServiceImpl.class);

        LocalService<RenderingControlServiceImpl> rcService =
                new AnnotationLocalServiceBinder().read(RenderingControlServiceImpl.class);

        return new LocalDevice(identity, type, details,
                new LocalService[]{avtService, cmService, rcService});
    }

    @Override
    public void onDestroy() {
        if (upnpService != null) upnpService.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
