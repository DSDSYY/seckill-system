/**
 * 抢购 Demo 页
 * 流程：进入自动登录(wx.login -> 后端换token) -> 输入活动ID
 *       -> 查库存 -> 抢购(POST /api/seckill/do) -> 展示结果
 */
const api = require('../../services/api');
const { BASE_URL } = require('../../utils/config');
const app = getApp();

Page({
  data: {
    baseUrl: BASE_URL,
    userId: '',
    activityId: '1',
    stockText: '',
    loading: false,
    result: null, // { code, message, data, debug }
    debugText: '',
  },

  onLoad() {
    this.autoLogin();
  },

  /** 登录：wx.login 拿 code -> 后端签发 token（后端默认 mock 模式） */
  autoLogin() {
    wx.showLoading({ title: '登录中...' });
    wx.login({
      success: (res) => {
        console.log('[登录] wx.login code =', res.code);
        if (!res.code) {
          wx.hideLoading();
          this.showError({ code: -1, message: 'wx.login 未返回 code，请检查 AppID/基础库' });
          return;
        }
        api.login(res.code)
          .then((body) => {
            console.log('[登录] 成功 token =', body.data.token);
            wx.setStorageSync('token', body.data.token);
            app.globalData.userId = body.data.userId;
            this.setData({ userId: body.data.userId });
            wx.showToast({ title: '登录成功', icon: 'success' });
          })
          .catch((e) => this.showError(e))
          .finally(() => wx.hideLoading());
      },
      fail: (err) => {
        console.log('[登录] wx.login fail =', err);
        wx.hideLoading();
        this.showError({ code: -1, message: 'wx.login 调用失败：' + (err.errMsg || JSON.stringify(err)) });
      },
    });
  },

  onActivityIdInput(e) {
    this.setData({ activityId: e.detail.value });
  },

  /** 查询 Redis 剩余库存 */
  queryStock() {
    const id = this.getActivityId();
    if (!id) return;
    this.setData({ loading: true, result: null, debugText: '' });
    api.getStock(id)
      .then((body) => {
        const s = body.data;
        this.setData({
          result: body,
          stockText: s === null ? '（尚未预热）' : '剩余库存：' + s,
        });
      })
      .catch((e) => this.showError(e))
      .finally(() => this.setData({ loading: false }));
  },

  /** 抢购 */
  onSeckill() {
    const id = this.getActivityId();
    if (!id) return;
    this.setData({ loading: true, result: null, debugText: '' });
    api.doSeckill(id)
      .then((body) => {
        this.setData({ result: body });
        wx.showToast({ title: '抢购成功，请尽快支付', icon: 'success' });
        // TODO: 后端提供 GET /api/order/{orderNo} 后，在这里轮询订单状态
      })
      .catch((e) => this.showError(e))
      .finally(() => this.setData({ loading: false }));
  },

  getActivityId() {
    const id = Number(this.data.activityId);
    if (!id) {
      wx.showToast({ title: '请输入活动ID', icon: 'none' });
      return null;
    }
    return id;
  },

  /** 统一错误提示（按后端 ResultCode 映射）+ 记录原始错误便于排查 */
  showError(e) {
    const map = {
      401: '请先登录',
      429: '操作太快啦，请稍后再试',
      1001: '秒杀活动不存在',
      1002: '秒杀未开始',
      1003: '秒杀已结束',
      1004: '手慢了，已售罄',
      1005: '人数过多，请重试',
      1006: '您已参与过本场秒杀',
    };
    const msg = map[e.code] || e.message || '操作失败';
    const debugText = JSON.stringify(e.debug || { code: e.code, message: msg });
    console.error('[接口错误]', e);
    this.setData({
      result: { code: e.code, message: msg, data: null },
      debugText,
    });
    wx.showToast({ title: msg, icon: 'none' });
  },

  /** 复制诊断信息，便于反馈给后端排查 */
  copyDebug() {
    if (!this.data.debugText) return;
    wx.setClipboardData({ data: this.data.debugText });
  },
});