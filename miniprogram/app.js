/**
 * 秒杀小程序 Demo —— 全局入口
 * 真实项目可在这里做启动静默登录；本 Demo 在首页点击/进入时登录。
 */
App({
  globalData: {
    userId: null, // 登录后由后端返回的系统用户ID
  },

  onLaunch() {
    // 预留：可在此 wx.login 静默登录（见 pages/index/index.js autoLogin）
  },
});