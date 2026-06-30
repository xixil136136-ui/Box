// ═══════════════════════════════════════════════════════════
//  TVBox 卡密鉴权系统 v2.0
//  新增：VIP等级 / 投屏时间限制 / 游戏帧率锁
//  部署: Cloudflare Workers + KV (USER_DB)
// ═══════════════════════════════════════════════════════════

const TVBOX_SOURCE = {
  "sites": [
    {"key": "csp_DYTT", "name": "电影天堂", "type": 3, "api": "https://你的域名/csp/DYTT", "searchable": 1, "quickSearch": 0, "filterable": 1},
    {"key": "csp_DXAL", "name": "低端影视", "type": 3, "api": "https://你的域名/csp/DXAL", "searchable": 1, "quickSearch": 0, "filterable": 1}
  ],
  "lives": [
    {"group": "央视", "channels": [{"name": "CCTV-1", "urls": ["https://你的直播源地址.m3u8"]}]}
  ],
  "parses": [], "ijk": [], "ads": [], "wallpaper": ""
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Content-Type": "application/json; charset=utf-8"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders });

    try {
      switch (path) {
        case "/auth":              return handleAuth(request, env, corsHeaders);
        case "/api/get_config":    return handleGetConfig(request, env, corsHeaders);
        case "/api/check_cast":    return handleCheckCast(request, env, corsHeaders);
        case "/api/report_cast_end": return handleReportCastEnd(request, env, corsHeaders);
        case "/api/get_game_config": return handleGetGameConfig(request, env, corsHeaders);
        case "/admin/gen":         return handleGenCard(request, env, corsHeaders);
        case "/admin/query":       return handleQueryCard(request, env, corsHeaders);
        case "/admin/stats":       return handleStats(request, env, corsHeaders);
        case "/admin/delete":      return handleDeleteCard(request, env, corsHeaders);
        case "/admin/unbind":      return handleUnbindDevice(request, env, corsHeaders);
        default:                   return handleIndex(corsHeaders);
      }
    } catch (e) {
      return json({ msg: "服务器错误: " + e.message }, corsHeaders, 500);
    }
  }
};

// ─── 鉴权中间件 ───────────────────────────────────────
async function authMiddleware(request, env) {
  const authHeader = request.headers.get("Authorization") || "";
  const ADMIN_TOKEN = await env.USER_DB.get("_admin_token");
  if (!ADMIN_TOKEN) return "NO_TOKEN";
  if (authHeader !== `Bearer ${ADMIN_TOKEN}`) return "FAIL";
  return "OK";
}

// ═══════════════════════════════════════════════════════════
//  ① 卡密鉴权（原 /auth，兼容旧版）
// ═══════════════════════════════════════════════════════════
async function handleAuth(request, env, corsHeaders) {
  const url = new URL(request.url);
  const device_id = (url.searchParams.get("device_id") || "").trim();
  const card_key = (url.searchParams.get("card_key") || "").trim();
  if (!card_key) return json({ msg: "请提供卡密 (card_key)" }, corsHeaders);
  if (!device_id) return json({ msg: "请提供设备码 (device_id)" }, corsHeaders);

  const cardData = await env.USER_DB.get(card_key, { type: "json" });
  if (!cardData) return json({ msg: "卡密不存在，请联系管理员" }, corsHeaders);

  const now = Date.now();
  if (cardData.expire_at && cardData.expire_at < now)
    return json({ msg: "您的订阅已到期" }, corsHeaders);

  // ─ 设备绑定逻辑 ─
  if (cardData.device_id && cardData.device_id !== "") {
    if (cardData.device_id !== device_id)
      return json({ msg: "该卡密已绑定其他设备" }, corsHeaders);
  } else {
    cardData.device_id = device_id;
    cardData.bind_time = Date.now();
    cardData.bind_ip = request.headers.get("CF-Connecting-IP") || "";
    await env.USER_DB.put(card_key, JSON.stringify(cardData));
    await env.USER_DB.put(`dev:${device_id}`, card_key);
  }

  // ─ vip_level 默认值 ─
  const vipLevel = cardData.vip_level !== undefined ? cardData.vip_level : 0;

  const gameFps = vipLevel >= 1 ? 60 : 30;
  const allowFullscreenGame = vipLevel >= 1;
  const castUnlimited = vipLevel >= 1;

  const encoded = btoa(JSON.stringify(TVBOX_SOURCE));
  return json({
    code: 1,
    msg: "验证成功",
    device_id,
    expire_at: cardData.expire_at,
    remain_days: Math.max(0, Math.floor((cardData.expire_at - now) / 86400000)),
    // ── 权限字段 ──
    vip_level: vipLevel,
    game_fps: gameFps,
    allow_fullscreen_game: allowFullscreenGame,
    cast_unlimited: castUnlimited,
    // ── 影视源 ──
    source: encoded,
    source_raw: TVBOX_SOURCE
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ② 影视源接口（新版 /api/get_config）
//  客户端调用此接口获取完整的配置 + 权限标记
// ═══════════════════════════════════════════════════════════
async function handleGetConfig(request, env, corsHeaders) {
  const url = new URL(request.url);
  const device_id = (url.searchParams.get("device_id") || "").trim();
  const card_key = (url.searchParams.get("card_key") || "").trim();
  if (!card_key || !device_id)
    return json({ msg: "需要 card_key 和 device_id" }, corsHeaders);

  const cardData = await env.USER_DB.get(card_key, { type: "json" });
  if (!cardData) return json({ msg: "卡密不存在" }, corsHeaders);

  if (cardData.expire_at && cardData.expire_at < Date.now())
    return json({ msg: "您的订阅已到期" }, corsHeaders);

  if (cardData.device_id && cardData.device_id !== device_id)
    return json({ msg: "卡密已绑定其他设备" }, corsHeaders);

  const vipLevel = cardData.vip_level !== undefined ? cardData.vip_level : 0;
  const gameFps = vipLevel >= 1 ? 60 : 30;

  // ─ 返回加密源 + 权限字段 ─
  const configPayload = {
    vip_level: vipLevel,
    game_fps: gameFps,
    allow_fullscreen_game: vipLevel >= 1,
    cast_unlimited: vipLevel >= 1,
    source: TVBOX_SOURCE
  };

  // 使用 Base64 模拟加密
  const encoded = btoa(JSON.stringify(configPayload));

  return json({
    code: 1,
    msg: "success",
    expire_at: cardData.expire_at,
    remain_days: Math.max(0, Math.floor((cardData.expire_at - Date.now()) / 86400000)),
    config_encoded: encoded,
    // 同时明文返回权限字段，方便客户端直接解析
    vip_level: vipLevel,
    game_fps: gameFps,
    allow_fullscreen_game: vipLevel >= 1,
    cast_unlimited: vipLevel >= 1
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ③ 投屏时间校验（/api/check_cast）
// ═══════════════════════════════════════════════════════════
async function handleCheckCast(request, env, corsHeaders) {
  const url = new URL(request.url);
  const device_id = (url.searchParams.get("device_id") || "").trim();
  const card_key = (url.searchParams.get("card_key") || "").trim();

  if (!card_key || !device_id)
    return json({ msg: "需要 card_key 和 device_id" }, corsHeaders);

  const cardData = await env.USER_DB.get(card_key, { type: "json" });
  if (!cardData) return json({ msg: "卡密不存在" }, corsHeaders);

  // ─ 检查卡密有效期 ─
  if (cardData.expire_at && cardData.expire_at < Date.now())
    return json({ msg: "您的订阅已到期" }, corsHeaders);

  // ─ 设备绑定校验 ─
  if (cardData.device_id && cardData.device_id !== device_id)
    return json({ msg: "卡密已绑定其他设备" }, corsHeaders);

  const vipLevel = cardData.vip_level !== undefined ? cardData.vip_level : 0;

  // ─ VIP 不限投屏 ─
  if (vipLevel >= 1) {
    // 记录当前投屏会话（用于统计，不限制）
    await env.USER_DB.put(`cast:${device_id}:session`, JSON.stringify({
      start_time: Date.now(),
      device_id,
      card_key,
      vip_level: vipLevel
    }), { expirationTtl: 86400 }); // 1天后自动过期

    return json({
      status: "success",
      allow: true,
      msg: "VIP 用户无限投屏",
      vip_level: vipLevel,
      cast_remain_seconds: -1  // -1 表示无限
    }, corsHeaders);
  }

  // ─ 普通用户：检查今日累计投屏时间 ─
  const today = getDateKey();
  const castKey = `cast:${device_id}:${today}`;
  const castData = await env.USER_DB.get(castKey, { type: "json" });

  const DAILY_LIMIT_SECONDS = 600; // 10分钟
  const usedSeconds = castData ? castData.total_seconds : 0;

  if (usedSeconds >= DAILY_LIMIT_SECONDS) {
    return json({
      status: "limit",
      allow: false,
      msg: "普通用户每日投屏限额10分钟已用完，请升级VIP",
      vip_level: 0,
      cast_used_seconds: usedSeconds,
      cast_limit_seconds: DAILY_LIMIT_SECONDS
    }, corsHeaders);
  }

  // ─ 记录本次投屏会话开始 ─
  await env.USER_DB.put(`cast:${device_id}:session`, JSON.stringify({
    start_time: Date.now(),
    device_id,
    card_key,
    vip_level: 0
  }), { expirationTtl: 86400 });

  return json({
    status: "success",
    allow: true,
    msg: `今日剩余投屏 ${DAILY_LIMIT_SECONDS - usedSeconds} 秒`,
    vip_level: 0,
    cast_used_seconds: usedSeconds,
    cast_remain_seconds: DAILY_LIMIT_SECONDS - usedSeconds,
    cast_limit_seconds: DAILY_LIMIT_SECONDS
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ④ 投屏结束上报（/api/report_cast_end）
//  客户端在投屏结束时调用，用于累加普通用户的投屏时长
// ═══════════════════════════════════════════════════════════
async function handleReportCastEnd(request, env, corsHeaders) {
  const url = new URL(request.url);
  const device_id = (url.searchParams.get("device_id") || "").trim();

  if (!device_id) return json({ msg: "需要 device_id" }, corsHeaders);

  // 获取本次投屏会话
  const sessionKey = `cast:${device_id}:session`;
  const session = await env.USER_DB.get(sessionKey, { type: "json" });

  if (!session) {
    return json({ msg: "没有活跃的投屏会话" }, corsHeaders);
  }

  // VIP 用户不记录时长
  if (session.vip_level >= 1) {
    await env.USER_DB.delete(sessionKey);
    return json({ msg: "VIP投屏结束，不计时" }, corsHeaders);
  }

  // 计算本次投屏时长
  const durationMs = Date.now() - (session.start_time || Date.now());
  const durationSec = Math.ceil(durationMs / 1000);

  // 累加到今日总数
  const today = getDateKey();
  const castKey = `cast:${device_id}:${today}`;
  const castData = await env.USER_DB.get(castKey, { type: "json" });

  const newTotal = (castData ? castData.total_seconds : 0) + durationSec;

  await env.USER_DB.put(castKey, JSON.stringify({
    total_seconds: newTotal,
    sessions: (castData ? castData.sessions : 0) + 1,
    last_cast_end: Date.now(),
    device_id
  }), { expirationTtl: 86400 * 2 }); // 保留2天

  // 清理会话
  await env.USER_DB.delete(sessionKey);

  return json({
    msg: "投屏时长已记录",
    duration_seconds: durationSec,
    total_today_seconds: newTotal
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑤ 游戏配置接口（/api/get_game_config）
//  客户端在启动游戏前调用，获取帧率/全屏权限
// ═══════════════════════════════════════════════════════════
async function handleGetGameConfig(request, env, corsHeaders) {
  const url = new URL(request.url);
  const card_key = (url.searchParams.get("card_key") || "").trim();

  if (!card_key) return json({ msg: "需要 card_key" }, corsHeaders);

  const cardData = await env.USER_DB.get(card_key, { type: "json" });
  if (!cardData) return json({ msg: "卡密不存在" }, corsHeaders);

  if (cardData.expire_at && cardData.expire_at < Date.now())
    return json({ msg: "订阅已到期" }, corsHeaders);

  const vipLevel = cardData.vip_level !== undefined ? cardData.vip_level : 0;
  const gameFps = vipLevel >= 1 ? 60 : 30;

  return json({
    code: 1,
    vip_level: vipLevel,
    game_fps: gameFps,
    allow_fullscreen_game: vipLevel >= 1,
    allow_60fps: vipLevel >= 1,
    max_game_session_minutes: vipLevel >= 1 ? 120 : 30 // VIP单次2小时，普通30分钟
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑥ 生成卡密（管理员）
// ═══════════════════════════════════════════════════════════
async function handleGenCard(request, env, corsHeaders) {
  if (request.method !== "POST") return json({ msg: "请使用 POST" }, corsHeaders, 405);
  const auth = await authMiddleware(request, env);
  if (auth === "NO_TOKEN") {
    const t = generateToken(32);
    await env.USER_DB.put("_admin_token", t);
    return json({ msg: "首次使用，已生成管理Token", admin_token: t, warning: "请保存！" }, corsHeaders);
  }
  if (auth === "FAIL") return json({ msg: "鉴权失败" }, corsHeaders, 401);

  let body;
  try { body = await request.json(); } catch (e) { return json({ msg: "需要 JSON" }, corsHeaders, 400); }

  const days = parseInt(body.days) || 30;
  const count = Math.min(parseInt(body.count) || 1, 200);
  const remark = body.remark || "";
  // ⭐ 新增：指定 VIP 等级
  const vipLevel = parseInt(body.vip_level) || 0;

  const results = [];
  for (let i = 0; i < count; i++) {
    const cardKey = generateCardKey();
    const cardData = {
      card_key: cardKey,
      create_at: Date.now(),
      expire_at: Date.now() + days * 86400000,
      device_id: "",
      bind_time: null,
      bind_ip: "",
      remark: remark + (vipLevel >= 1 ? " [VIP]" : ""),
      status: "active",
      vip_level: vipLevel       // ⭐ 存储 VIP 等级
    };
    await env.USER_DB.put(cardKey, JSON.stringify(cardData));
    results.push({ card_key: cardKey, expire_at: cardData.expire_at, remark: cardData.remark, vip_level: vipLevel });
  }

  return json({
    msg: `成功生成 ${results.length} 张卡密`,
    days,
    cards: results
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑦ 查询卡密
// ═══════════════════════════════════════════════════════════
async function handleQueryCard(request, env, corsHeaders) {
  const url = new URL(request.url);
  const card_key = url.searchParams.get("card") || "";

  if (card_key) {
    const data = await env.USER_DB.get(card_key, { type: "json" });
    if (!data) return json({ msg: "卡密不存在" }, corsHeaders);
    return json({ card: data }, corsHeaders);
  }

  const list = await env.USER_DB.list({ limit: 1000 });
  const cards = [];
  for (const key of list.keys) {
    if (key.name.startsWith("_") || key.name.startsWith("dev:") || key.name.startsWith("cast:")) continue;
    const data = await env.USER_DB.get(key.name, { type: "json" });
    if (data) cards.push(data);
  }
  cards.sort((a, b) => (b.create_at || 0) - (a.create_at || 0));
  return json({ total: cards.length, cards }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑧ 统计（含VIP分类统计）
// ═══════════════════════════════════════════════════════════
async function handleStats(request, env, corsHeaders) {
  const list = await env.USER_DB.list({ limit: 1000 });
  let total = 0, active = 0, expired = 0, bound = 0;
  let vip0 = 0, vip1 = 0;

  for (const key of list.keys) {
    if (key.name.startsWith("_") || key.name.startsWith("dev:") || key.name.startsWith("cast:")) continue;
    total++;
    const data = await env.USER_DB.get(key.name, { type: "json" });
    if (!data) continue;

    if (data.vip_level >= 1) vip1++; else vip0++;

    if (data.expire_at && data.expire_at < Date.now()) expired++; else active++;
    if (data.device_id) bound++;
  }

  return json({
    total_cards: total,
    active, expired, bound_devices: bound,
    vip_normal: vip0,
    vip_premium: vip1,
    now: Date.now()
  }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑨ 删除卡密
// ═══════════════════════════════════════════════════════════
async function handleDeleteCard(request, env, corsHeaders) {
  const auth = await authMiddleware(request, env);
  if (auth !== "OK") return json({ msg: "鉴权失败" }, corsHeaders, 401);

  const url = new URL(request.url);
  const card_key = url.searchParams.get("card") || "";
  if (!card_key) return json({ msg: "需要 card 参数" }, corsHeaders);

  const data = await env.USER_DB.get(card_key, { type: "json" });
  if (!data) return json({ msg: "卡密不存在" }, corsHeaders);

  if (data.device_id) await env.USER_DB.delete(`dev:${data.device_id}`);
  await env.USER_DB.delete(card_key);
  return json({ msg: "已删除", card_key }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  ⑩ 解绑设备
// ═══════════════════════════════════════════════════════════
async function handleUnbindDevice(request, env, corsHeaders) {
  const auth = await authMiddleware(request, env);
  if (auth !== "OK") return json({ msg: "鉴权失败" }, corsHeaders, 401);
  if (request.method !== "POST") return json({ msg: "请使用 POST" }, corsHeaders, 405);

  let body;
  try { body = await request.json(); } catch (e) { return json({ msg: "需要 JSON" }, corsHeaders, 400); }
  const card_key = body.card_key || "";
  if (!card_key) return json({ msg: "需要 card_key" }, corsHeaders);

  const data = await env.USER_DB.get(card_key, { type: "json" });
  if (!data) return json({ msg: "卡密不存在" }, corsHeaders);

  if (data.device_id) await env.USER_DB.delete(`dev:${data.device_id}`);
  data.device_id = "";
  data.bind_time = null;
  data.bind_ip = "";
  await env.USER_DB.put(card_key, JSON.stringify(data));
  return json({ msg: "已解绑", card_key }, corsHeaders);
}

// ═══════════════════════════════════════════════════════════
//  首页
// ═══════════════════════════════════════════════════════════
function handleIndex(corsHeaders) {
  return new Response(`<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>TVBox 卡密系统 v2</title><style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,sans-serif;background:#0f0f1a;color:#e8e8f0;display:flex;justify-content:center;align-items:center;min-height:100vh}.card{background:#1a1a2e;padding:40px;border-radius:16px;max-width:640px;width:90%}h1{font-size:24px;background:linear-gradient(135deg,#e94560,#4a9eff);-webkit-background-clip:text;-webkit-text-fill-color:transparent}p{color:#a0a0b8;line-height:1.8;margin:12px 0;font-size:14px}code{background:#0f0f1a;padding:2px 8px;border-radius:4px;color:#4a9eff;word-break:break-all}hr{border:none;border-top:1px solid #2a2a40;margin:16px 0}.ep{background:#0f0f1a;padding:12px;border-radius:8px;margin:8px 0}.ep strong{color:#e94560}.tag{display:inline-block;padding:2px 10px;border-radius:10px;font-size:11px;background:rgba(233,69,96,.15);color:#e94560;margin-left:6px}</style></head><body><div class="card"><h1>TVBox 卡密鉴权系统 v2.0</h1><p>VIP等级 · 投屏控制 · 游戏帧率锁</p><hr><div class="ep"><strong>GET</strong> <code>/auth?device_id=xxx&card_key=xxx</code> <span class="tag">旧版兼容</span></div><div class="ep"><strong>GET</strong> <code>/api/get_config?device_id=xxx&card_key=xxx</code> <span class="tag">新版推荐</span><p>返回加密源 + vip_level / game_fps / cast_unlimited</p></div><div class="ep"><strong>GET</strong> <code>/api/check_cast?device_id=xxx&card_key=xxx</code><p>投屏前校验，VIP 无限，普通用户每日10分钟</p></div><div class="ep"><strong>GET</strong> <code>/api/report_cast_end?device_id=xxx</code><p>投屏结束时上报时长</p></div><div class="ep"><strong>GET</strong> <code>/api/get_game_config?card_key=xxx</code><p>获取游戏帧率/全屏权限</p></div><div class="ep"><strong>POST</strong> <code>/admin/gen</code><p>生成卡密（支持 <code>vip_level</code> 参数）</p></div><hr><p style="color:#6b6b80">vip_level: 0=普通 / 1=高级VIP</p></div></body></html>`, {
    headers: { ...corsHeaders, "Content-Type": "text/html; charset=utf-8" }
  });
}

// ═══════════════════════════════════════════════════════════
//  工具函数
// ═══════════════════════════════════════════════════════════

function generateCardKey() {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let key = "";
  for (let i = 0; i < 16; i++) {
    if (i > 0 && i % 4 === 0) key += "-";
    key += chars[Math.floor(Math.random() * chars.length)];
  }
  return key;
}

function generateToken(len = 32) {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  let t = "";
  for (let i = 0; i < len; i++) t += chars[Math.floor(Math.random() * chars.length)];
  return t;
}

function getDateKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

function json(data, headers, status = 200) {
  return new Response(JSON.stringify(data, null, 2), { status, headers });
}
