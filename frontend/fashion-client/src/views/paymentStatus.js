const PAYMENT_STATES = new Map([
  [0, { state: 'pending', shouldPoll: true }],
  [1, { state: 'pending', shouldPoll: true }],
  [2, { state: 'success', shouldPoll: false }],
  [3, { state: 'failed', shouldPoll: false }],
  [-1, { state: 'incomplete', shouldPoll: false }]
])


export function interpretPayStatus(status) {
  return PAYMENT_STATES.get(status) || { state: 'invalid', shouldPoll: false }
}


export function buildAlipayVerifyParams(query) {
  const params = {}
  for (const [key, value] of Object.entries(query || {})) {
    if (Array.isArray(value)) throw new Error('INVALID_ALIPAY_RETURN')
    if (key === 'orderId') continue
    params[key] = value
  }
  return params
}
