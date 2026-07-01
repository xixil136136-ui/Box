package com.github.tvbox.osc.dlna;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.avtransport.impl.AVTransportService;
import org.fourthline.cling.support.connectionmanager.impl.ConnectionManagerServiceImpl;
import org.fourthline.cling.support.renderingcontrol.impl.RenderingControlServiceImpl;

import java.util.UUID;

/**
 * DLNA 投屏接收端服务
 * 让本机作为 DLNA MediaRenderer，接收手机端APP投屏
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
            Log.i(TAG, "DLNA Renderer started");
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
        avtService.setManager(new DefaultServiceManager<>(avtService, AVTransportService.class));

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
