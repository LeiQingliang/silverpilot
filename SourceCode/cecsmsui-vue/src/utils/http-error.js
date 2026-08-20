export const isCanceledRequest = (error) => (
  error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError'
)

export const getBackendErrorMessage = (error) => {
  const data = error?.response?.data
  if (!data || typeof data !== 'object') return ''
  for (const field of ['error', 'message', 'msg']) {
    if (typeof data[field] === 'string' && data[field].trim()) return data[field].trim()
  }
  return ''
}

export const resolveGlobalHttpFeedback = (error) => {
  if (isCanceledRequest(error)) return null
  const status = Number(error?.response?.status) || 0
  const backendMessage = getBackendErrorMessage(error)

  if (status === 403) return backendMessage || '没有该操作权限'
  if (status === 404) return backendMessage || '请求的资源不存在'
  if (status === 429) return backendMessage || '操作过于频繁，请稍后重试'
  if (status >= 500) return backendMessage || '服务器暂时不可用'
  if (!error?.response) {
    return error?.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接服务器'
  }
  return null
}
