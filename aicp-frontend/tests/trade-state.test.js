import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatCents, orderStatusLabel, canRetryPayment,
  isPaymentPending, licenseLabel, listingStatusLabel,
  safeReturnPath, topUpReturnPath
} from '../src/views/trade/tradeState.js'

test('formatCents converts integer cents to CNY display', () => {
  assert.equal(formatCents(2990), '¥29.90')
  assert.equal(formatCents(0), '¥0.00')
  assert.equal(formatCents(100), '¥1.00')
  assert.equal(formatCents(null), '—')
  assert.equal(formatCents(undefined), '—')
})

test('orderStatusLabel maps all known states', () => {
  assert.equal(orderStatusLabel('FULFILLED'), '已完成')
  assert.equal(orderStatusLabel('PENDING_PAYMENT'), '待支付')
  assert.equal(orderStatusLabel('PAYMENT_UNKNOWN'), '支付结果确认中')
  assert.equal(orderStatusLabel('REFUNDED'), '已退款')
  assert.equal(orderStatusLabel('UNKNOWN_STATUS'), 'UNKNOWN_STATUS')
})

test('canRetryPayment allows retry only from safe states', () => {
  assert.equal(canRetryPayment('PAYMENT_FAILED'), true)
  assert.equal(canRetryPayment('PENDING_PAYMENT'), true)
  assert.equal(canRetryPayment('PAYMENT_UNKNOWN'), false)
  assert.equal(canRetryPayment('PAYING'), false)
  assert.equal(canRetryPayment('FULFILLED'), false)
})

test('isPaymentPending identifies in-flight states', () => {
  assert.equal(isPaymentPending('PAYING'), true)
  assert.equal(isPaymentPending('PAYMENT_UNKNOWN'), true)
  assert.equal(isPaymentPending('PAID_PENDING_DELIVERY'), true)
  assert.equal(isPaymentPending('COMPENSATING'), true)
  assert.equal(isPaymentPending('FULFILLED'), false)
  assert.equal(isPaymentPending('PENDING_PAYMENT'), false)
})

test('licenseLabel maps license types', () => {
  assert.equal(licenseLabel('FREE'), '免费领取')
  assert.equal(licenseLabel('NORMAL'), '普通授权')
  assert.equal(licenseLabel('EXCLUSIVE'), '独家授权')
  assert.equal(licenseLabel('BUYOUT'), '买断授权')
})

test('listingStatusLabel maps listing states', () => {
  assert.equal(listingStatusLabel('DRAFT'), '草稿')
  assert.equal(listingStatusLabel('LISTED'), '在售')
  assert.equal(listingStatusLabel('EXCLUSIVE_SOLD'), '已售出')
})

test('topUpReturnPath encodes order checkout path', () => {
  const path = topUpReturnPath('ORD-1')
  assert.ok(path.startsWith('/wallet/topup?return_to='))
  assert.ok(path.includes('ORD-1'))
})

test('safeReturnPath only allows whitelisted prefixes', () => {
  assert.equal(safeReturnPath('/trade/checkout/ORD-1'), '/trade/checkout/ORD-1')
  assert.equal(safeReturnPath('/market/1'), '/market/1')
  assert.equal(safeReturnPath('/profile'), '/profile')
  assert.equal(safeReturnPath('/evil/path'), '/market')
  assert.equal(safeReturnPath(null), '/market')
  assert.equal(safeReturnPath(''), '/market')
})
