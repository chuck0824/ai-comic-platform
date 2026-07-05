/**
 * Pure state helpers for the trade module.
 * No side effects, no imports of Vue or the API module.
 * All money is integer cents from the API; display is derived here.
 */

/** Format cents to CNY display string. */
export function formatCents(cents, currency = 'CNY') {
  if (cents == null || isNaN(cents)) return '—'
  const n = Number(cents)
  if (currency === 'CNY') return `¥${(n / 100).toFixed(2)}`
  return `${(n / 100).toFixed(2)} ${currency}`
}

/** Map order status to a UI label. */
export function orderStatusLabel(status) {
  const map = {
    PENDING_APPROVAL: '待审批',
    REJECTED: '已驳回',
    PENDING_PAYMENT: '待支付',
    PAYING: '支付中',
    PAYMENT_FAILED: '支付失败',
    PAYMENT_UNKNOWN: '支付结果确认中',
    PAID_PENDING_DELIVERY: '交付中',
    COMPENSATING: '补偿处理中',
    FULFILLED: '已完成',
    REFUND_REQUESTED: '退款申请中',
    REFUND_PROCESSING: '退款处理中',
    REFUND_REJECTED: '退款已驳回',
    REFUNDED: '已退款',
    CANCELLED: '已取消',
    EXPIRED: '已过期'
  }
  return map[status] || status
}

/** Severity for Element Plus tag/badge. */
export function orderStatusSeverity(status) {
  if (!status) return 'info'
  if (status === 'FULFILLED') return 'success'
  if (status === 'REFUNDED' || status === 'CANCELLED' || status === 'EXPIRED') return 'info'
  if (status.startsWith('REFUND_')) return 'warning'
  if (status.startsWith('PAYMENT_') && status !== 'PAYMENT_FAILED') return 'warning'
  if (status === 'PAYMENT_FAILED' || status === 'COMPENSATING') return 'danger'
  return 'info'
}

/** Whether the user can retry payment from this state. */
export function canRetryPayment(status) {
  return status === 'PAYMENT_FAILED' || status === 'PENDING_PAYMENT'
}

/** Whether the payment is still in-flight and the UI should poll. */
export function isPaymentPending(status) {
  return status === 'PAYING' || status === 'PAYMENT_UNKNOWN'
      || status === 'PAID_PENDING_DELIVERY' || status === 'COMPENSATING'
}

/** Route to navigate to for an order detail/result. */
export function orderRoute(orderNo, status) {
  if (isPaymentPending(status)) return `/trade/orders/${orderNo}/result`
  return `/trade/orders/${orderNo}`
}

/** Map license type to Chinese label. */
export function licenseLabel(type) {
  const map = {
    FREE: '免费领取',
    NORMAL: '普通授权',
    EXCLUSIVE: '独家授权',
    BUYOUT: '买断授权'
  }
  return map[type] || type
}

/** Listing status labels. */
export function listingStatusLabel(status) {
  const map = {
    DRAFT: '草稿',
    UNDER_REVIEW: '审核中',
    REJECTED: '已驳回',
    LISTED: '在售',
    EXCLUSIVE_RESERVED: '已保留',
    EXCLUSIVE_SOLD: '已售出',
    UNLISTED: '已下架'
  }
  return map[status] || status
}

/** Return URL-safe top-up path with validated return_to. */
export function topUpReturnPath(orderNo) {
  const ret = `/trade/checkout/${orderNo}`
  return `/wallet/topup?return_to=${encodeURIComponent(ret)}`
}

/**
 * Validate that a return_to URL is safe.
 * Only allows relative paths starting with /trade, /market, /profile.
 */
export function safeReturnPath(path) {
  if (!path) return '/market'
  if (!/^\/(trade|market|profile)(\/|\?|$)/.test(path)) return '/market'
  return path
}
