/**
 * 统一请求封装
 *  - 自动附带 Authorization: Bearer {token}
 *  - 统一解析后端 Result { code, message, data }
 *  - code !== 200 时 reject({ code, message, debug })，业务侧按错误码提示
 */
const { BASE_URL, TIMEOUT } = require('./config');

function request(path, method = 'GET', data = {}, needAuth = true) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' };

    if (needAuth) {
      const token = wx.getStorageSync('token');
      if (!token) {
        reject({ code: 401, message: '未登录，请先登录' });
        return;
      }
      header.Authorization = 'Bearer ' + token;
    }

    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header,
      timeout: TIMEOUT,
      success(res) {
        const body = res.data;
        // 后端正常返回 Result 结构
        if (body && typeof body === 'object' && typeof body.code === 'number') {
          if (body.code === 200) {
            resolve(body);              // 成功：body.data 为业务数据
          } else {
            reject({ code: body.code, message: body.message, debug: body });
          }
          return;
        }
        // 非预期响应（404/500 HTML、网关错误等）——把 HTTP 状态码暴露出来便于排查
        reject({
          code: res.statusCode || -1,
          message: 'HTTP ' + (res.statusCode || '?') + '，响应格式异常（请确认后端已启动且路径正确）',
          debug: typeof body === 'string' ? body.substring(0, 200) : body,
        });
      },
      fail(err) {
        // 网络层失败：域名校验 / 地址不通 / 未勾选"不校验合法域名" 等都会走到这里
        reject({ code: -1, message: '网络异常：' + (err.errMsg || JSON.stringify(err)), debug: err });
      },
    });
  });
}

module.exports = { request };