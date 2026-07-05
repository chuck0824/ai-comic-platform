import request from './request'

/** Trade/market API — all paths relative to /api/v1. */
export const tradeApi = {
  // Public market
  searchListings: (params) => request.get('/trade/market/listings', { params }),
  getListing: (id) => request.get(`/trade/market/listings/${id}`),
  getPreview: (id) => request.get(`/trade/market/listings/${id}/preview`),

  // Orders (requires X-Workspace-Id)
  createOrder: (data) => request.post('/trade/orders', data),
  getOrder: (orderNo) => request.get(`/trade/orders/${orderNo}`),
  getOrders: () => request.get('/trade/orders'),
  cancelOrder: (orderNo) => request.post(`/trade/orders/${orderNo}/cancel`),
  payOrder: (orderNo, data) => request.post(`/trade/orders/${orderNo}/pay`, data),

  // Entitlements
  getEntitlements: () => request.get('/trade/entitlements'),

  // Seller
  getSellerOverview: () => request.get('/trade/seller/overview'),
  getSellerOrders: () => request.get('/trade/seller/orders'),

  // Listings (seller)
  createListing: (data) => request.post('/trade/listings', data),
  updateListing: (id, data) => request.put(`/trade/listings/${id}`, data),
  submitListing: (id) => request.post(`/trade/listings/${id}/submit`),
  withdrawListing: (id) => request.post(`/trade/listings/${id}/withdraw`),
  unlistListing: (id) => request.post(`/trade/listings/${id}/unlist`),
  getMyListings: () => request.get('/trade/listings'),

  // Enterprise
  submitPurchaseRequest: (data) => request.post('/trade/purchase-requests', data),
  approvePurchaseRequest: (id, data) => request.post(`/trade/purchase-requests/${id}/approve`, data),
  rejectPurchaseRequest: (id, data) => request.post(`/trade/purchase-requests/${id}/reject`, data),

  // Refunds
  requestRefund: (orderNo, data) => request.post(`/trade/orders/${orderNo}/refund-requests`, data),

  // Wallet (8080 facade)
  getWalletBalance: () => request.get('/trade/wallet/balance'),
  getTopUpInfo: () => request.get('/trade/wallet/topup-info'),
  createTopUp: (data) => request.post('/trade/wallet/topups', data)
}
