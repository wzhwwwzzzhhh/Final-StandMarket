import { beforeEach, describe, expect, it, vi } from 'vitest'

const axiosHarness = vi.hoisted(() => {
  const requestUse = vi.fn()
  const responseUse = vi.fn()
  const api = {
    interceptors: {
      request: { use: requestUse },
      response: { use: responseUse }
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
  return { requestUse, responseUse, api, create: vi.fn(() => api) }
})

vi.mock('axios', () => ({ default: { create: axiosHarness.create } }))

import request from './request'
import { uploadApi } from '@/api/upload'
import { userApi } from '@/api/user'


describe('unified request contract', () => {
  beforeEach(() => {
    localStorage.clear()
    axiosHarness.api.get.mockClear()
    axiosHarness.api.post.mockClear()
    axiosHarness.api.put.mockClear()
    axiosHarness.api.delete.mockClear()
  })

  it('injects the bearer token even when skipAuthRedirect is enabled', () => {
    localStorage.setItem('token', 'user-token')
    const onRequest = axiosHarness.requestUse.mock.calls[0][0]
    const config = onRequest({ headers: {}, skipAuthRedirect: true })
    expect(config.headers.Authorization).toBe('Bearer user-token')
  })

  it('does not force a global content type so FormData keeps its boundary', () => {
    expect(axiosHarness.create).toHaveBeenCalledWith({
      baseURL: '/api',
      timeout: 10000
    })
  })

  it('preserves login state on opted-out 401 and still rejects the error', async () => {
    localStorage.setItem('token', 'user-token')
    localStorage.setItem('userInfo', '{"id":1}')
    const onRejected = axiosHarness.responseUse.mock.calls[0][1]
    const error = { response: { status: 401 }, config: { skipAuthRedirect: true } }

    await expect(onRejected(error)).rejects.toBe(error)
    expect(localStorage.getItem('token')).toBe('user-token')
    expect(localStorage.getItem('userInfo')).toBe('{"id":1}')
  })

  it('clears login state for a protected 401', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    localStorage.setItem('token', 'user-token')
    localStorage.setItem('userInfo', '{"id":1}')
    const onRejected = axiosHarness.responseUse.mock.calls[0][1]
    const error = { response: { status: 401 }, config: {} }

    await expect(onRejected(error)).rejects.toBe(error)
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    consoleError.mockRestore()
  })

  it('opts login, registration, and SMS requests out of redirect loops', () => {
    userApi.login({ phone: '1' })
    userApi.register({ phone: '1' })
    userApi.sendSmsCode('1')
    for (const call of request.post.mock.calls) {
      expect(call.at(-1)).toEqual({ skipAuthRedirect: true })
    }
  })

  it('keeps upload timeout and lets the browser choose the multipart boundary', () => {
    const file = new File(['content'], 'test.txt', { type: 'text/plain' })
    uploadApi.uploadFile(file)
    const form = request.post.mock.calls[0][1]
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('file')).toBe(file)
    expect(request.post).toHaveBeenCalledWith('/upload/oss', form, { timeout: 30000 })
    expect(request.post.mock.calls[0][2].headers).toBeUndefined()
  })
})
