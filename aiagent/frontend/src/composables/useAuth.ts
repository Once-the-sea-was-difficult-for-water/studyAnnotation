import { ref } from 'vue'

/**
 * 认证 composable — 管理 JWT Token，提供给 useWebSocket 使用。
 *
 * 调用 login(userId) 从后端 /api/auth/token 获取 JWT，
 * 存储在内存 + localStorage 中，供 WebSocket 握手传递。
 */
export function useAuth() {
  const token = ref<string>(localStorage.getItem('jwt_token') ?? '')
  const userId = ref<string>(localStorage.getItem('user_id') ?? '')
  const isAuthenticated = ref(!!token.value)

  /**
   * 登录：调用后端获取 JWT Token。
   * @param uid 用户 ID
   */
  async function login(uid: string): Promise<string> {
    const response = await fetch('/api/auth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: uid }),
    })

    if (!response.ok) {
      throw new Error(`Authentication failed: ${response.status}`)
    }

    const data = (await response.json()) as { token: string; userId: string }
    token.value = data.token
    userId.value = data.userId
    isAuthenticated.value = true

    localStorage.setItem('jwt_token', data.token)
    localStorage.setItem('user_id', data.userId)

    return data.token
  }

  /** 登出：清除 Token */
  function logout() {
    token.value = ''
    userId.value = ''
    isAuthenticated.value = false
    localStorage.removeItem('jwt_token')
    localStorage.removeItem('user_id')
  }

  /**
   * 检查响应头中的刷新 Token 并更新本地存储。
   * 在 HTTP 请求拦截器中调用。
   */
  function handleTokenRefresh(response: Response) {
    const refreshed = response.headers.get('X-Refreshed-Token')
    if (refreshed) {
      token.value = refreshed
      localStorage.setItem('jwt_token', refreshed)
    }
  }

  return {
    token,
    userId,
    isAuthenticated,
    login,
    logout,
    handleTokenRefresh,
  }
}
