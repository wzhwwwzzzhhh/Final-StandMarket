import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const paymentApi = vi.hoisted(() => ({ verifyReturn: vi.fn(), payStatus: vi.fn() }))
vi.mock('@/api/payment', () => ({ paymentApi }))

import PayResult from './PayResult.vue'


function mountResult(query) {
  return shallowMount(PayResult, {
    global: {
      mocks: {
        $route: { query },
        $router: { push: vi.fn() }
      },
      stubs: { 'el-icon': true, 'el-button': true }
    }
  })
}


describe('PayResult', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    paymentApi.verifyReturn.mockReset()
    paymentApi.payStatus.mockReset()
  })

  it('uses the shared mapping for a pending verified return', async () => {
    paymentApi.verifyReturn.mockResolvedValue({ data: { code: 1, data: { payStatus: 0 } } })
    const wrapper = mountResult({ orderId: 'local', out_trade_no: 'trade', sign: 'signed' })
    await flushPromises()
    expect(paymentApi.verifyReturn).toHaveBeenCalledWith({ out_trade_no: 'trade', sign: 'signed' })
    expect(wrapper.vm.paymentState).toBe('pending')
  })

  it('does not verify a return containing duplicate signed keys', async () => {
    const wrapper = mountResult({ orderId: 'local', out_trade_no: 'trade', sign: ['a', 'b'] })
    await flushPromises()
    expect(paymentApi.verifyReturn).not.toHaveBeenCalled()
    expect(wrapper.vm.paymentState).toBe('invalid')
    expect(wrapper.vm.checked).toBe(true)
  })
})
