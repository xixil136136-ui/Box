package com.github.tvbox.osc.player.danmu;

import android.os.AsyncTask;

import com.github.tvbox.osc.util.LOG;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * B站弹幕抓取器
 * 根据 cid 获取 Bilibili XML 弹幕，转换为 DanmakuFlameMaster 可用的格式
 */
public class DanmuFetcher {

    public interface DanmuCallback {
        void onSuccess(List<BiliDanmu> danmuList);
        void onError(String msg);
    }

    /**
     * B站弹幕数据模型
     */
    public static class BiliDanmu {
        public long time;      // 弹幕出现时间（毫秒）
        public int type;       // 1=滚动 4=底部 5=顶部
        public int color;      // 颜色
        public String text;    // 弹幕文本

        public BiliDanmu(long time, int type, int color, String text) {
            this.time = time;
            this.type = type;
            this.color = 0xFF000000 | color;
            this.text = text;
        }
    }

    /**
     * 通过 cid 获取 B站弹幕
     * @param cid B站视频 cid
     */
    public static void fetch(String cid, DanmuCallback callback) {
        new DanmuTask(cid, callback).execute();
    }

    /**
     * 通过 aid 和 cid 获取
     */
    public static void fetch(String aid, String cid, DanmuCallback callback) {
        fetch(cid, callback);
    }

    private static class DanmuTask extends AsyncTask<Void, Void, List<BiliDanmu>> {
        private final String cid;
        private final DanmuCallback callback;
        private String error;

        DanmuTask(String cid, DanmuCallback callback) {
            this.cid = cid;
            this.callback = callback;
        }

        @Override
        protected List<BiliDanmu> doInBackground(Void... params) {
            try {
                String url = "https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid;
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Referer", "https://www.bilibili.com");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);

                InputStream is = conn.getInputStream();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);
                is.close();

                NodeList dNodes = doc.getElementsByTagName("d");
                List<BiliDanmu> list = new ArrayList<>();

                for (int i = 0; i < dNodes.getLength(); i++) {
                    Element el = (Element) dNodes.item(i);
                    String p = el.getAttribute("p");
                    String text = el.getTextContent();

                    String[] parts = p.split(",");
                    if (parts.length >= 4) {
                        long timeMs = (long) (Float.parseFloat(parts[0]) * 1000);
                        int type = Integer.parseInt(parts[1]);
                        int color = Integer.parseInt(parts[3]);
                        list.add(new BiliDanmu(timeMs, type, color, text));
                    }
                }
                LOG.i("Danmu: fetched " + list.size() + " danmaku for cid=" + cid);
                return list;
            } catch (Exception e) {
                error = e.getMessage();
                LOG.e("Danmu fetch error: " + error);
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<BiliDanmu> list) {
            if (list != null) {
                callback.onSuccess(list);
            } else {
                callback.onError(error != null ? error : "弹幕获取失败");
            }
        }
    }
}
