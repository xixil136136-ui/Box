package com.github.tvbox.osc.dlna;

import android.content.Context;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;

import java.util.ArrayList;
import java.util.List;

/**
 * DLNA 投屏发送端控制器
 * 搜索局域网内的DLNA接收器并投屏
 */
public class DLNAController {

    private UpnpService upnpService;
    private List<Device> deviceList = new ArrayList<>();
    private DiscoveryCallback discoveryCallback;

    public interface DiscoveryCallback {
        void onDevicesFound(List<Device> devices);
    }

    public interface CastCallback {
        void onSuccess();
        void onError(String msg);
    }

    /** 1. 开始搜索 DLNA 设备 */
    public void startDiscovery(DiscoveryCallback callback) {
        this.discoveryCallback = callback;
        deviceList.clear();

        upnpService = new UpnpServiceImpl();
        upnpService.getRegistry().addListener(new DefaultRegistryListener() {
            @Override
            public void deviceAdded(Registry registry, Device device) {
                Service avtService = device.findService(new UDAServiceType("AVTransport"));
                if (avtService != null && !deviceList.contains(device)) {
                    deviceList.add(device);
                    if (discoveryCallback != null)
                        discoveryCallback.onDevicesFound(deviceList);
                }
            }
        });
        upnpService.getControlPoint().search();
    }

    /** 2. 投屏到指定设备 */
    public void cast(String url, String title, Device target, CastCallback callback) {
        if (target == null) {
            if (callback != null) callback.onError("未选择设备");
            return;
        }
        Service avtService = target.findService(new UDAServiceType("AVTransport"));
        if (avtService == null) {
            if (callback != null) callback.onError("设备不支持投屏");
            return;
        }

        upnpService.getControlPoint().execute(new SetAVTransportURI(avtService, url, title) {
            @Override
            public void success(ActionInvocation invocation) {
                upnpService.getControlPoint().execute(new Play(avtService) {
                    @Override
                    public void success(ActionInvocation invocation) {
                        if (callback != null) callback.onSuccess();
                    }
                    @Override
                    public void failure(ActionInvocation inv, UpnpResponse op, String msg) {
                        if (callback != null) callback.onError("播放失败: " + msg);
                    }
                });
            }
            @Override
            public void failure(ActionInvocation inv, UpnpResponse op, String msg) {
                if (callback != null) callback.onError("设置URI失败: " + msg);
            }
        });
    }

    /** 释放资源 */
    public void destroy() {
        if (upnpService != null) {
            upnpService.shutdown();
            upnpService = null;
        }
    }
}
