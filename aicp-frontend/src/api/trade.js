import request from './request'

export const tradeApi = {
  searchMarket: (params) => request.get('/trade/market/search', { params }),
  getScriptDetail: (id) => request.get(`/trade/market/scripts/${id}`),
  getPreview: (id) => request.get(`/trade/market/scripts/${id}/preview`),
  createOrder: (data) => request.post('/trade/orders', data),
  getOrder: (id) => request.get(`/trade/orders/${id}`),
  getOrders: (params) => request.get('/trade/orders', { params }),
  payOrder: (id, data) => request.post(`/trade/orders/${id}/pay`, data),
  submitPurchaseRequest: (data) => request.post('/trade/enterprise/purchase-request', data),
  approvePurchaseRequest: (id, data) => request.put(`/trade/enterprise/purchase-request/${id}/approve`, data),
  getSales: () => request.get('/trade/sales'),
  getEarnings: () => request.get('/trade/earnings'),
  withdraw: (data) => request.post('/trade/earnings/withdraw', data)
}
