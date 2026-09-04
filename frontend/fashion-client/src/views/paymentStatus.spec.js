import { describe, expect, it } from 'vitest'

import { buildAlipayVerifyParams, interpretPayStatus } from './paymentStatus'


describe('payment return read-only state mapping', () => {
  it.each([
    [0, 'pending', true],
    [1, 'pending', true],
    [2, 'success', false],
    [3, 'failed', false],
    [-1, 'incomplete', false]
  ])('maps status %s to %s', (status, state, shouldPoll) => {
    expect(interpretPayStatus(status)).toEqual({ state, shouldPoll })
  })

  it('preserves every Alipay parameter exactly except the local orderId', () => {
    const params = buildAlipayVerifyParams({
      orderId: 'local-order',
      sign: '  signed value  ',
      out_trade_no: 'trade-1',
      charset: 'utf-8'
    })
    expect(params).toEqual({
      sign: '  signed value  ',
      out_trade_no: 'trade-1',
      charset: 'utf-8'
    })
  })

  it('fails closed when an Alipay query key is repeated', () => {
    expect(() => buildAlipayVerifyParams({
      orderId: 'local-order',
      sign: ['first', 'second'],
      out_trade_no: 'trade-1'
    })).toThrow('INVALID_ALIPAY_RETURN')

    expect(() => buildAlipayVerifyParams({
      orderId: ['local-1', 'local-2'],
      sign: 'signed',
      out_trade_no: 'trade-1'
    })).toThrow('INVALID_ALIPAY_RETURN')
  })
})
