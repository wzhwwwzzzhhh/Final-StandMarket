<template>
  <div class="agent-chat-widget">
    <!-- 悬浮按钮 -->
    <div v-if="!isOpen" class="chat-fab" @click="openChat">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
    </div>

    <!-- 聊天浮窗 -->
    <transition name="slide-up">
      <div v-if="isOpen" class="chat-panel">
        <!-- 标题栏 -->
        <div class="chat-header">
          <span class="chat-title">AI 智能导购</span>
          <button class="chat-close" @click="closeChat">✕</button>
        </div>

        <!-- 消息列表 -->
        <div class="chat-messages" ref="msgRef">
          <div v-for="(msg, i) in messages" :key="i"
               :class="['message-row', msg.role === 'user' ? 'message-user' : 'message-assistant']">
            <div class="message-bubble">
              <div class="message-content">{{ msg.content }}</div>
              <div v-if="msg.products && msg.products.length" class="product-strip">
                <div v-for="(p, j) in msg.products" :key="j" class="product-card"
                     @click="goProduct(p.id)">
                  <img :src="p.image" :alt="p.name" class="product-img" />
                  <div class="product-name">{{ p.name }}</div>
                  <div class="product-price">¥{{ p.price }}</div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="loading" class="message-row message-assistant">
            <div class="message-bubble thinking">思考中...</div>
          </div>
        </div>

        <!-- 快捷提问 -->
        <div class="quick-chips">
          <button v-for="(chip, i) in quickChips" :key="i"
                  class="chip" @click="sendMessage(chip)">{{ chip }}</button>
        </div>

        <!-- 登录引导 -->
        <div v-if="pendingLogin" class="login-hint">
          <span>登录后可查询订单、获取精准推荐</span>
          <button class="login-btn" @click="goLogin">去登录</button>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <input v-model="inputText" class="chat-input"
                 placeholder="输入消息..." @keyup.enter="sendMessage(inputText)" />
          <button class="send-btn" :disabled="!inputText.trim() || loading"
                  @click="sendMessage(inputText)">发送</button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { chatWithAgent } from '@/api/agent'

function genId() {
  if (crypto.randomUUID) return crypto.randomUUID().slice(0, 16)
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  }).slice(0, 16)
}

export default {
  name: 'AgentChat',
  data() {
    return {
      isOpen: false,
      loading: false,
      inputText: '',
      pendingLogin: false,
      sessionId: localStorage.getItem('agent_session') || '',
      messages: []
    }
  },
  computed: {
    quickChips() {
      return ['有什么推荐？', '我的订单到哪了？', '帮我搭配一套', '身高170体重65选什么尺码？']
    },
    userId() {
      try {
        const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
        return info.id || 0
      } catch { return 0 }
    }
  },
  methods: {
    openChat() {
      this.isOpen = true
      if (!this.messages.length) {
        this.messages.push({
          role: 'assistant',
          content: '你好！我是 AI 导购，有什么可以帮你的？',
          products: []
        })
      }
      this.$nextTick(() => this.scrollBottom())
    },
    closeChat() {
      this.isOpen = false
    },
    async sendMessage(text) {
      const msg = (text || '').trim()
      if (!msg || this.loading) return

      this.messages.push({ role: 'user', content: msg, products: [] })
      this.inputText = ''
      this.loading = true
      this.scrollBottom()

      if (!this.sessionId) {
        this.sessionId = genId()
        localStorage.setItem('agent_session', this.sessionId)
      }

      try {
        const res = await chatWithAgent({
          userId: this.userId || -1,
          sessionId: this.sessionId,
          message: msg
        })
        const data = res.data
        if (data.code === 1 && data.data) {
          this.messages.push({
            role: 'assistant',
            content: data.data.reply || '抱歉，暂时无法回复',
            products: data.data.products || []
          })
        } else if (data.msg && data.msg.includes('登录')) {
          // 后端要求登录态，未登录时引导跳转登录页
          this.messages.push({
            role: 'assistant',
            content: '需要先登录才能使用订单查询等服务，去登录一下吧～',
            products: []
          })
          this.pendingLogin = true
        } else {
          this.messages.push({
            role: 'assistant',
            content: data.msg || '服务暂时不可用',
            products: []
          })
        }
      } catch {
        this.messages.push({
          role: 'assistant',
          content: '网络连接失败，请稍后再试',
          products: []
        })
      }
      this.loading = false
      this.scrollBottom()
    },
    goProduct(id) {
      this.$router.push(`/product/detail/${id}`)
    },
    goLogin() {
      this.$router.push('/login')
    },
    scrollBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgRef
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }
}
</script>

<style scoped>
.agent-chat-widget {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* 悬浮按钮 */
.chat-fab {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #d100ff, #7c3aed);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(209, 0, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}
.chat-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(209, 0, 255, 0.55);
}

/* 聊天面板 */
.chat-panel {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 380px;
  height: 520px;
  background: #1a1a2e;
  border: 1px solid rgba(209, 0, 255, 0.3);
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 标题栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #1a1a2e, #2d1b69);
  border-bottom: 1px solid rgba(209, 0, 255, 0.2);
}
.chat-title {
  font-weight: 700;
  font-size: 15px;
  color: #e0e0e0;
  letter-spacing: 0.05em;
}
.chat-close {
  background: none;
  border: none;
  color: #888;
  font-size: 16px;
  cursor: pointer;
  padding: 2px 6px;
}
.chat-close:hover {
  color: #fff;
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-messages::-webkit-scrollbar {
  width: 4px;
}
.chat-messages::-webkit-scrollbar-thumb {
  background: rgba(209, 0, 255, 0.3);
  border-radius: 2px;
}

.message-row {
  display: flex;
}
.message-user {
  justify-content: flex-end;
}
.message-assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}
.message-user .message-bubble {
  background: linear-gradient(135deg, #7c3aed, #d100ff);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.message-assistant .message-bubble {
  background: #252540;
  color: #d0d0e0;
  border-bottom-left-radius: 4px;
}
.thinking {
  color: #888;
  font-style: italic;
}

/* 商品卡片 */
.product-strip {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.product-card {
  min-width: 100px;
  max-width: 120px;
  background: #1e1e3a;
  border: 1px solid rgba(209, 0, 255, 0.15);
  border-radius: 8px;
  padding: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.product-card:hover {
  border-color: rgba(209, 0, 255, 0.5);
}
.product-img {
  width: 100%;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  background: #333;
}
.product-name {
  font-size: 11px;
  color: #aaa;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-price {
  font-size: 12px;
  color: #d100ff;
  font-weight: 700;
  margin-top: 2px;
}

/* 快捷提问 */
.quick-chips {
  display: flex;
  gap: 6px;
  padding: 8px 14px;
  overflow-x: auto;
  border-top: 1px solid rgba(255,255,255,0.05);
}
.chip {
  flex-shrink: 0;
  background: #252540;
  border: 1px solid rgba(209, 0, 255, 0.2);
  color: #aaa;
  font-size: 11px;
  padding: 5px 12px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.chip:hover {
  background: rgba(209, 0, 255, 0.15);
  color: #d100ff;
  border-color: rgba(209, 0, 255, 0.4);
}

/* 登录引导 */
.login-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: rgba(209, 0, 255, 0.08);
  border-top: 1px solid rgba(209, 0, 255, 0.15);
  font-size: 12px;
  color: #aaa;
}
.login-btn {
  background: linear-gradient(135deg, #7c3aed, #d100ff);
  border: none;
  border-radius: 12px;
  padding: 4px 14px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;
}
.login-btn:hover {
  opacity: 0.85;
}

/* 输入区 */
.chat-input-area {
  display: flex;
  padding: 10px 14px;
  gap: 8px;
  border-top: 1px solid rgba(255,255,255,0.05);
  background: #16162a;
}
.chat-input {
  flex: 1;
  background: #252540;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  padding: 8px 12px;
  color: #e0e0e0;
  font-size: 13px;
  outline: none;
}
.chat-input::placeholder {
  color: #666;
}
.chat-input:focus {
  border-color: rgba(209, 0, 255, 0.4);
}
.send-btn {
  background: linear-gradient(135deg, #7c3aed, #d100ff);
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.send-btn:not(:disabled):hover {
  opacity: 0.85;
}

/* 动画 */
.slide-up-enter-active {
  animation: slideUp 0.25s ease-out;
}
.slide-up-leave-active {
  animation: slideUp 0.2s ease-in reverse;
}
@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
