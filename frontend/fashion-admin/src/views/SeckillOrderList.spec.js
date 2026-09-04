import { beforeEach, describe, expect, it, vi } from 'vitest'

const seckillApi = vi.hoisted(() => ({ deleteSeckillOrder: vi.fn() }))
vi.mock('@/api/seckillCoupon', () => ({ seckillApi }))

import SeckillOrderList from './SeckillOrderList.vue'


function context() {
  return {
    $confirm: vi.fn().mockResolvedValue(),
    $message: { success: vi.fn(), error: vi.fn() },
    loadSeckillOrders: vi.fn(),
    loadStatistics: vi.fn()
  }
}


describe('SeckillOrderList delete result contract', () => {
  beforeEach(() => seckillApi.deleteSeckillOrder.mockReset())

  it('treats only code 1 as success', async () => {
    const vm = context()
    seckillApi.deleteSeckillOrder.mockResolvedValue({ data: { code: 1 } })
    await SeckillOrderList.methods.handleDelete.call(vm, { id: 7 })
    expect(vm.$message.success).toHaveBeenCalledWith('删除订单成功')
    expect(vm.loadSeckillOrders).toHaveBeenCalled()
  })

  it('does not refresh or show success for code 0', async () => {
    const vm = context()
    seckillApi.deleteSeckillOrder.mockResolvedValue({ data: { code: 0, msg: 'failed' } })
    await SeckillOrderList.methods.handleDelete.call(vm, { id: 7 })
    expect(vm.$message.success).not.toHaveBeenCalled()
    expect(vm.$message.error).toHaveBeenCalledWith('failed')
    expect(vm.loadSeckillOrders).not.toHaveBeenCalled()
  })
})
