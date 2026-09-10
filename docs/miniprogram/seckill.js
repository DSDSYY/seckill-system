/**
 * seckill.js —— 微信小程序端调用秒杀后端核心示例
 *
 * 使用前提：
 *   1. 小程序后台配置 request 合法域名（或本地开发勾选"不校验合法域名"）；
 *   2. 后端开启 mock 登录（seckill.wechat.mock-enabled=true）便于本地联调。
 *
 * 完整流程：
 *   wx.login() 拿 code
 *     -> POST /api/auth/login          换取 { userId, token }，本地缓存 token
 *     -> POST /api/seckill/do          抢购（Header 带 Bearer token）
 *     -> 轮询订单状态（后续阶段提供 GET /api/order/{orderNo}）
 */

const BASE_URL = 'https://your-domain.com'; // TODO 改成你的后端域名

/** 统一请求封装：自动带 token、统一解析 Result { code, message, data } */
function request(path, method, data, withToken) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' };
    if (withToken) {
      const token = wx.getStorageSync('token');
      if (!token) { reject({ code: 401, message: '未登录' }); return; }
      header.Authorization = 'Bearer ' + token;
    }
    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header,
      success(res) {
        const body = res.data || {};
        if (body.code === 200) {
          resolve(body);          // 成功：body.data 为业务数据
        } else {
          reject(body);           // 失败：body.code / body.message 见 ResultCode
        }
      },
      fail(err) { reject({ code: -1, message: '网络异常', raw: err }); },
    });
  });
}

/** 包装 wx.login 为 Promise（拿临时 code） */
function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({ success: (res) => resolve(res.code), fail: reject });
  });
}

/** 1. 登录：code -> token */
async function login() {
  const code = await wxLogin();
  const res = await request('/api/auth/login', 'POST', { code });
  wx.setStorageSync('token', res.data.token);
  wx.setStorageSync('userId', res.data.userId);
  return res.data;
}

/** 2. 抢购：activityId -> 临时订单号 */
async function doSeckill(activityId) {
  const res = await request('/api/seckill/do', 'POST', { activityId }, true);
  // 抢购成功，拿到临时订单号，开始轮询订单状态（15 分钟内支付）
  startPollingOrder(res.data);
  return res.data;
}

/** 3. 页面点击抢购入口（含错误码提示） */
async function onTapSeckill(activityId) {
  try {
    const orderNo = await doSeckill(activityId);
    wx.showToast({ title: '抢购成功，请尽快支付', icon: 'success' });
    wx.setStorageSync('orderNo', orderNo);
  } catch (e) {
    // 错误码对照：401 未登录 / 429 限流 / 1001 活动不存在
    //            1002 未开始 / 1003 已结束 / 1004 售罄 / 1005 繁忙 / 1006 重复下单
    const tips = {
      401: '请先登录', 429: '操作太快啦，请稍后再试',
      1004: '手慢了，已售罄', 1006: '您已参与过本场秒杀',
    };
    wx.showToast({ title: tips[e.code] || e.message || '抢购失败', icon: 'none' });
    if (e.code === 401) { await login(); }          // token 失效则重新登录
  }
}

/** 轮询订单状态（简化示意；正式实现见后续"订单查询接口"） */
function startPollingOrder(orderNo) {
  let times = 0;
  const timer = setInterval(async () => {
    times += 1;
    if (times > 60) { clearInterval(timer); return; }   // 最多轮询 60 次
    try {
      // TODO: 后端提供 GET /api/order/{orderNo} 后替换
      const res = await request('/api/order/' + orderNo, 'GET', null, true);
      if (res.data.status === 0) return;                // 0=待支付，继续等
      clearInterval(timer);
      // 1=已支付 -> 跳支付成功页；2=已取消 -> 提示重新抢购
    } catch (e) { clearInterval(timer); }
  }, 1000);
}

module.exports = { login, doSeckill, onTapSeckill };