/**
 * 全局配置
 *
 * ⚠️ 联调三步：
 *  1) 开发者工具调试：BASE_URL 填 http://127.0.0.1:8081 即可（并勾选"不校验合法域名"）；
 *  2) 真机预览：BASE_URL 改为电脑局域网 IP，如 http://192.168.1.100:8081，
 *     且必须勾选"不校验合法域名"（开发版）；
 *  3) 上线发布：必须使用已备案的 HTTPS 域名（微信强制 https），并在
 *     小程序后台「开发管理-开发设置-服务器域名」配置 request 合法域名。
 */
module.exports = {
  BASE_URL: 'http://127.0.0.1:8081',
  TIMEOUT: 10000,
  // 与后端 seckill.order.pay-timeout-minutes 保持一致（秒）
  PAY_TIMEOUT_SEC: 15 * 60,
};