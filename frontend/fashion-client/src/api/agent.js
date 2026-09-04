import api from '@/utils/request'

export function chatWithAgent(data) {
  return api.post('/user/agent/chat', data)
}
