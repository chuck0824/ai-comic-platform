import request from './request'

export const authApi = {
  sendCode: (target, type, scene) =>
    request.post('/auth/send-code', { target, type, scene }),
  register: (data) =>
    request.post('/auth/register', data),
  login: (data) =>
    request.post('/auth/login', data),
  loginBySms: (phone, verifyCode) =>
    request.post('/auth/login/sms', { phone, verify_code: verifyCode }),
  loginByWechat: (code, state) =>
    request.post('/auth/login/wechat', { code, state }),
  refreshToken: (token) =>
    request.post('/auth/refresh-token', { refresh_token: token }),
  logout: (refreshToken) =>
    request.post('/auth/logout', { refresh_token: refreshToken }),
  createSsoTicket: () =>
    request.post('/auth/sso/ticket'),
  loginBySso: (ticket) =>
    request.post('/auth/login/sso', { ticket })
}
