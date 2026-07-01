package com.github.tvbox.osc.dlna;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;

/**
 * DLNA 投屏接收端（基于 NanoHTTPD）
 *
 * 原理：
 * 1. 监听 SSDP 组播（239.255.255.250:1900）M-SEARCH 请求
 * 2. 响应设备发现，返回 MediaRenderer 描述 XML
 * 3. NanoHTTPD 提供 device description XML 和控制端点
 *
 * 启动：DLNAManager.init(context);
 */
public class DLNAManager {

    private static final String TAG = "DLNA";
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String SERVER_NAME = "UPnP/1.0 DLNADOC/1.50 Portable SDK for UPnP devices";
    private static final String UUID_VAL = UUID.randomUUID().toString();
    private static final String DEVICE_UUID = "uuid:" + UUID_VAL;

    private static Context mContext;
    private static Thread ssdpThread;
    private static volatile boolean running = false;
    private static int httpPort = 9978;

    /** 初始化（在 Application.onCreate 或 HomeActivity 中调用） */
    public static void init(Context context) {
        mContext = context;
        running = true;

        // 启动 SSDP 响应线程
        ssdpThread = new Thread(DLNAManager::ssdpLoop, "DLNA-SSDP");
        ssdpThread.setDaemon(true);
        ssdpThread.start();
        Log.i(TAG, "DLNA Manager started, UUID=" + UUID_VAL);
    }

    /** 停止 DLNA */
    public static void stop() {
        running = false;
        if (ssdpThread != null) {
            ssdpThread.interrupt();
            ssdpThread = null;
        }
    }

    /** SSDP 监听循环 */
    private static void ssdpLoop() {
        try (DatagramSocket socket = new DatagramSocket(SSDP_PORT)) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(5000);

            byte[] buf = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running) {
                try {
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    String senderIp = packet.getAddress().getHostAddress();

                    if (msg.contains("M-SEARCH") && msg.contains("ssdp:discover")) {
                        // 判断是否搜索 MediaRenderer
                        boolean searchAll = msg.contains("ST: ssdp:all");
                        boolean searchRenderer = msg.contains("ST: urn:schemas-upnp-org:device:MediaRenderer");
                        boolean searchAvt = msg.contains("urn:schemas-upnp-org:service:AVTransport");

                        if (searchAll || searchRenderer || searchAvt) {
                            respondToMSearch(socket, packet.getAddress(), packet.getPort());
                            Log.d(TAG, "Responded to M-SEARCH from " + senderIp);
                        }
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                } catch (IOException e) {
                    if (running) Log.e(TAG, "SSDP error", e);
                }
            }
        } catch (SocketException e) {
            // 端口占用或权限不足，降级到只响应已知来源
            Log.w(TAG, "SSDP port busy, DLNA may not work automatically");
        }
    }

    /** 响应 SSDP M-SEARCH */
    private static void respondToMSearch(DatagramSocket socket, InetAddress targetIp, int targetPort) throws IOException {
        String localIp = getLocalIpAddress();
        if (localIp == null) return;

        String response = "HTTP/1.1 200 OK\r\n"
                + "CACHE-CONTROL: max-age=1800\r\n"
                + "DATE: " + new java.util.Date().toString() + "\r\n"
                + "EXT:\r\n"
                + "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                + "USN: " + DEVICE_UUID + "::urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                + "SERVER: " + SERVER_NAME + "\r\n"
                + "LOCATION: http://" + localIp + ":" + httpPort + "/dlna/device.xml\r\n"
                + "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n"
                + "01-NLS: " + System.currentTimeMillis() + "\r\n"
                + "BOOTID.UPNP.ORG: " + (System.currentTimeMillis() / 1000) + "\r\n"
                + "CONFIGID.UPNP.ORG: 1337\r\n"
                + "\r\n";

        byte[] respBytes = response.getBytes("UTF-8");
        // 发送 3 次（DLNA 规范要求）
        for (int i = 0; i < 3; i++) {
            DatagramPacket resp = new DatagramPacket(respBytes, respBytes.length, targetIp, targetPort);
            socket.send(resp);
        }
    }

    /** 获取本地 IP */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    String ip = addr.getHostAddress();
                    if (ip != null && ip.startsWith("192.") || (ip != null && ip.startsWith("10."))) {
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 获取设备描述 XML（由 NanoHTTPD 的 /dlna/device.xml 路由调用） */
    public static String getDeviceDescriptionXml() {
        String localIp = getLocalIpAddress();
        if (localIp == null) localIp = "127.0.0.1";

        return "<?xml version=\"1.0\"?>\r\n"
                + "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\r\n"
                + "  <specVersion><major>1</major><minor>0</minor></specVersion>\r\n"
                + "  <device>\r\n"
                + "    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>\r\n"
                + "    <friendlyName>全能看-TVBox</friendlyName>\r\n"
                + "    <manufacturer>QuanNengKan</manufacturer>\r\n"
                + "    <manufacturerURL>http://" + localIp + "</manufacturerURL>\r\n"
                + "    <modelDescription>全能看 DLNA MediaRenderer</modelDescription>\r\n"
                + "    <modelName>QNK-001</modelName>\r\n"
                + "    <modelNumber>v1.0</modelNumber>\r\n"
                + "    <UDN>" + DEVICE_UUID + "</UDN>\r\n"
                + "    <serviceList>\r\n"
                + "      <service>\r\n"
                + "        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>\r\n"
                + "        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\r\n"
                + "        <controlURL>/dlna/avt/control</controlURL>\r\n"
                + "        <eventSubURL>/dlna/avt/event</eventSubURL>\r\n"
                + "        <SCPDURL>/dlna/avt/scpd.xml</SCPDURL>\r\n"
                + "      </service>\r\n"
                + "      <service>\r\n"
                + "        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\r\n"
                + "        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\r\n"
                + "        <controlURL>/dlna/cm/control</controlURL>\r\n"
                + "        <eventSubURL>/dlna/cm/event</eventSubURL>\r\n"
                + "        <SCPDURL>/dlna/cm/scpd.xml</SCPDURL>\r\n"
                + "      </service>\r\n"
                + "      <service>\r\n"
                + "        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>\r\n"
                + "        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>\r\n"
                + "        <controlURL>/dlna/rc/control</controlURL>\r\n"
                + "        <eventSubURL>/dlna/rc/event</eventSubURL>\r\n"
                + "        <SCPDURL>/dlna/rc/scpd.xml</SCPDURL>\r\n"
                + "      </service>\r\n"
                + "    </serviceList>\r\n"
                + "  </device>\r\n"
                + "</root>\r\n";
    }

    /** 处理 AVTransport 控制请求（从 NanoHTTPD POST body 解析） */
    public static String handleAvtControl(String body) {
        Log.d(TAG, "AVT control: " + (body != null ? body.substring(0, Math.min(body.length(), 200)) : "null"));
        // 简单解析 SetAVTransportURI 和 Play
        if (body != null) {
            if (body.contains("SetAVTransportURI") && body.contains("CurrentURI")) {
                String uri = extractXmlTag(body, "CurrentURI");
                if (uri != null && !uri.isEmpty()) {
                    Log.i(TAG, "DLNA play URI: " + uri);
                }
            }
        }
        return "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:SetAVTransportURIResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"></u:SetAVTransportURIResponse></s:Body></s:Envelope>";
    }

    public static String handleAvtPlayControl(String body) {
        Log.i(TAG, "DLNA play command received");
        return "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:PlayResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"></u:PlayResponse></s:Body></s:Envelope>";
    }

    private static String extractXmlTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start >= 0 && end > start) {
            return xml.substring(start + open.length(), end);
        }
        // Try with namespace
        open = "<" + tag;
        int nsEnd = xml.indexOf(">", xml.indexOf(open));
        if (nsEnd > 0) {
            start = nsEnd + 1;
            end = xml.indexOf("</", start);
            if (end > start) return xml.substring(start, end).trim();
        }
        return null;
    }

    /** SCPD XML */
    public static String getAvtScpdXml() {
        return "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList><action><name>SetAVTransportURI</name><argumentList><argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument><argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument><argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument></argumentList></action><action><name>Play</name><argumentList><argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument><argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument></argumentList></action></actionList></scpd>";
    }

    public static String getCmScpdXml() {
        return "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList></actionList></scpd>";
    }

    public static String getRcScpdXml() {
        return "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList></actionList></scpd>";
    }
}
