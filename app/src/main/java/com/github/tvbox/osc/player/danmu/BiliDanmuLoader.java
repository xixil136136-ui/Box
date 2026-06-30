package com.github.tvbox.osc.player.danmu;

import com.lzy.okgo.OkGo;

import org.json.JSONObject;

/**
 * B站弹幕加载器
 * 通过BV号或avid获取B站视频的弹幕XML
 */
public class BiliDanmuLoader {

    private static final String API_VIEW = "https://api.bilibili.com/x/web-interface/view?";
    private static final String API_DANMU = "https://api.bilibili.com/x/v1/dm/list.so?oid=";

    /**
     * 通过BV号/avid获取弹幕XML
     * @param videoId BV号(如 BV1xx...) 或 avid(如 av12345)
     * @return 弹幕XML字符串，失败返回null
     */
    public static String fetchDanmuXml(String videoId) {
        try {
            // 1. 获取cid
            String viewUrl;
            if (videoId.startsWith("BV") || videoId.startsWith("bv")) {
                viewUrl = API_VIEW + "bvid=" + videoId;
            } else {
                String avid = videoId;
                if (!avid.startsWith("av")) avid = "av" + avid;
                viewUrl = API_VIEW + "aid=" + avid.substring(2);
            }

            String viewResp = OkGo.<String>get(viewUrl)
                    .headers("User-Agent", "Mozilla/5.0")
                    .execute()
                    .body()
                    .string();

            JSONObject viewJson = new JSONObject(viewResp);
            if (viewJson.getInt("code") != 0) return null;

            int cid = viewJson.getJSONObject("data").getInt("cid");

            // 2. 获取弹幕XML
            String danmuXml = OkGo.<String>get(API_DANMU + cid)
                    .headers("User-Agent", "Mozilla/5.0")
                    .execute()
                    .body()
                    .string();

            return danmuXml;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
