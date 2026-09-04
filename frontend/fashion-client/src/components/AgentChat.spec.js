import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chatWithAgent = vi.hoisted(() => vi.fn())
vi.mock('@/api/agent', () => ({ chatWithAgent }))

import AgentChat from './AgentChat.vue'


const SERVER_SESSION = 'abcdefghijklmnopqrstuv'

function mountChat() {
  return mount(AgentChat, {
    global: {
      mocks: { $router: { push: vi.fn() } },
      stubs: { transition: false }
    }
  })
}


describe('AgentChat trusted session contract', () => {
  beforeEach(() => {
    localStorage.clear()
    chatWithAgent.mockReset()
  })

  it('does not call the API for an unauthenticated user', async () => {
    const wrapper = mountChat()
    await wrapper.vm.sendMessage('hello')
    expect(chatWithAgent).not.toHaveBeenCalled()
    expect(wrapper.vm.pendingLogin).toBe(true)
  })

  it('drops legacy sessions and sends only message on the first request', async () => {
    localStorage.setItem('token', 'user-token')
    localStorage.setItem('userInfo', '{"id":999}')
    localStorage.setItem('agent_session', '0123456789abcdef')
    chatWithAgent.mockResolvedValue({
      data: { code: 1, data: { reply: 'ok', sessionId: SERVER_SESSION, products: [], degraded: false, degradationReasons: [] } }
    })

    const wrapper = mountChat()
    await wrapper.vm.sendMessage('hello')

    expect(chatWithAgent).toHaveBeenCalledWith({ message: 'hello' })
    expect(chatWithAgent.mock.calls[0][0]).not.toHaveProperty('userId')
    expect(chatWithAgent.mock.calls[0][0]).not.toHaveProperty('token')
    expect(localStorage.getItem('agent_session')).toBe(SERVER_SESSION)
  })

  it('retries INVALID_SESSION_ID once without a session', async () => {
    localStorage.setItem('token', 'user-token')
    localStorage.setItem('agent_session', SERVER_SESSION)
    chatWithAgent
      .mockRejectedValueOnce({ response: { status: 422, data: { msg: 'INVALID_SESSION_ID' } } })
      .mockResolvedValueOnce({
        data: { code: 1, data: { reply: 'recovered', sessionId: SERVER_SESSION, products: [], degraded: true, degradationReasons: ['REDIS_UNAVAILABLE'] } }
      })

    const wrapper = mountChat()
    await wrapper.vm.sendMessage('hello')

    expect(chatWithAgent).toHaveBeenNthCalledWith(1, { message: 'hello', sessionId: SERVER_SESSION })
    expect(chatWithAgent).toHaveBeenNthCalledWith(2, { message: 'hello' })
    expect(chatWithAgent).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.messages.at(-1).content).toBe('recovered')
  })

  it('does not retry other validation failures and separates expired login', async () => {
    localStorage.setItem('token', 'user-token')
    chatWithAgent.mockRejectedValueOnce({ response: { status: 422, data: { msg: 'INVALID_MESSAGE' } } })
    const wrapper = mountChat()
    await wrapper.vm.sendMessage('hello')
    expect(chatWithAgent).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.messages.at(-1).content).toContain('消息内容无效')

    chatWithAgent.mockRejectedValueOnce({ response: { status: 401, data: {} } })
    await wrapper.vm.sendMessage('again')
    expect(wrapper.vm.pendingLogin).toBe(true)
    expect(wrapper.vm.messages.at(-1).content).toContain('登录状态已失效')
  })

  it.each([
    { reply: '', products: [], degraded: false, degradationReasons: [] },
    { reply: 'bad products', products: {}, degraded: false, degradationReasons: [] },
    { reply: 'bad invariant', products: [], degraded: false, degradationReasons: ['REDIS_UNAVAILABLE'] },
    { reply: 'bad reason', products: [], degraded: true, degradationReasons: ['UNKNOWN_REASON'] }
  ])('does not persist a session from an invalid success schema', async invalidData => {
    localStorage.setItem('token', 'user-token')
    chatWithAgent.mockResolvedValue({
      data: { code: 1, data: { sessionId: SERVER_SESSION, ...invalidData } }
    })

    const wrapper = mountChat()
    await wrapper.vm.sendMessage('hello')

    expect(localStorage.getItem('agent_session')).toBeNull()
    expect(wrapper.vm.sessionId).toBe('')
    expect(wrapper.vm.messages.at(-1).content).toBe('服务暂时不可用')
    expect(wrapper.findAll('.product-card')).toHaveLength(0)
  })

  it('renders valid products even when the response is explicitly degraded', async () => {
    localStorage.setItem('token', 'user-token')
    chatWithAgent.mockResolvedValue({
      data: {
        code: 1,
        data: {
          reply: '缓存暂不可用，但仍有结果',
          sessionId: SERVER_SESSION,
          products: [{ id: 7, name: 'T恤', price: 99, image: 'ok.jpg' }],
          degraded: true,
          degradationReasons: ['REDIS_UNAVAILABLE']
        }
      }
    })

    const wrapper = mountChat()
    wrapper.vm.openChat()
    await wrapper.vm.$nextTick()
    await wrapper.vm.sendMessage('hello')
    await wrapper.vm.$nextTick()

    expect(localStorage.getItem('agent_session')).toBe(SERVER_SESSION)
    expect(wrapper.vm.messages.at(-1).content).toContain('缓存暂不可用')
    expect(wrapper.findAll('.product-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('T恤')
  })
})
