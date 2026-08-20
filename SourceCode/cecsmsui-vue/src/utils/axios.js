import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveGlobalHttpFeedback } from './http-error'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
export const apiUrl = (path) => `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`

const instance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json; charset=utf-8'
  },
  timeout: 70000,
  withCredentials: false,
  responseType: 'json'
})

let loginDialogOpen = false
let lastFeedback = { message: '', at: 0 }

const showHttpFeedback = (message) => {
  if (!message) return
  const now = Date.now()
  if (lastFeedback.message === message && now - lastFeedback.at < 1800) return
  lastFeedback = { message, at: now }
  ElMessage({
    type: 'error',
    message,
    duration: 3600,
    grouping: true,
    showClose: true
  })
}

const clearLoginState = () => {
  for (const storage of [sessionStorage, localStorage]) {
    for (const key of ['token', 'user', 'id', 'roleId', 'birthday', 'isNotified']) {
      storage.removeItem(key)
    }
  }
}

const redirectToLogin = () => {
  if (loginDialogOpen || window.location.pathname === '/login') return
  loginDialogOpen = true
  ElMessageBox.alert('登录已过期，请重新登录', '登录过期', {
    confirmButtonText: '重新登录',
    closeOnClickModal: false,
    closeOnPressEscape: false
  }).finally(() => {
    clearLoginState()
    window.location.assign('/login')
  })
}

instance.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token') || localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    if (response.data?.code === 401) {
      redirectToLogin()
      return Promise.reject(new Error('登录已过期'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      redirectToLogin()
    } else if (!error.config?.suppressErrorToast) {
      const feedback = resolveGlobalHttpFeedback(error)
      showHttpFeedback(feedback)
    }
    return Promise.reject(error)
  }
)

export default instance
