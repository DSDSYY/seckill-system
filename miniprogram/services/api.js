/**
 * 后端接口定义（与 seckill-backend 一一对应）
 *
 * 当前已对接：
 *  - POST /api/auth/login            登录换 token（mock 模式）
 *  - POST /api/seckill/do            抢购（单用户 5 次/秒限流）
 *  - GET  /api/seckill/stock/{id}    查 Redis 剩余库存
 *
 * 待后端补充后即可接入：
 *  - GET  /api/activity/list         活动列表（首页展示）
 *  - GET  /api/order/{orderNo}       订单状态轮询
 *  - POST /api/order/pay             支付（模拟/微信支付）
 */
const { request } = require('../utils/request');

/** 登录：wx.login 的 code -> { userId, token }（needAuth=false） */
const login = (code) => request('/api/auth/login', 'POST', { code }, false);

/** 抢购：Header 自动带 token */
const doSeckill = (activityId) => request('/api/seckill/do', 'POST', { activityId });

/** 查询剩余库存 */
const getStock = (activityId) => request('/api/seckill/stock/' + activityId, 'GET');

module.exports = { login, doSeckill, getStock };