import request from './request'

export const userApi = {
  getProfile: () => request.get('/user/profile'),
  updateProfile: (data) => request.put('/user/profile', data),
  verifyRealName: (data) => request.post('/user/verify/real-name', data),
  getMembership: () => request.get('/user/membership'),
  upgradeMembership: (data) => request.post('/user/membership/upgrade', data),
  getApiKeys: () => request.get('/user/api-keys'),
  createApiKey: (data) => request.post('/user/api-keys', data),
  deleteApiKey: (id) => request.delete(`/user/api-keys/${id}`)
}
