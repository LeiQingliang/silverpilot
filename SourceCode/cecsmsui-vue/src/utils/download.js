const INVALID_FILENAME = /[<>:"/\\|?*]/g
const replaceControlCharacters = (value) => [...value]
  .map((character) => character.codePointAt(0) < 32 ? '_' : character)
  .join('')

export const sanitizeDownloadFilename = (value, fallback = 'download') => {
  const candidate = replaceControlCharacters(String(value || '').trim()).replace(INVALID_FILENAME, '_').replace(/[. ]+$/g, '')
  const safeFallback = replaceControlCharacters(String(fallback || 'download').trim()).replace(INVALID_FILENAME, '_') || 'download'
  return (candidate || safeFallback).slice(0, 180)
}

export const filenameFromContentDisposition = (header, fallback = 'download') => {
  if (typeof header !== 'string' || !header.trim()) return sanitizeDownloadFilename(fallback)
  const encoded = header.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try { return sanitizeDownloadFilename(decodeURIComponent(encoded), fallback) } catch { /* use plain filename */ }
  }
  const plain = header.match(/filename\s*=\s*(?:"([^"]+)"|([^;]+))/i)
  return sanitizeDownloadFilename(plain?.[1] || plain?.[2], fallback)
}

const responseHeader = (headers, name) => {
  if (!headers) return ''
  if (typeof headers.get === 'function') return headers.get(name) || ''
  return headers[name] || headers[name.toLowerCase()] || ''
}

export const saveBlobResponse = async (response, fallbackFilename, dependencies = {}) => {
  if (!response || response.status < 200 || response.status >= 300) {
    throw new Error('文件下载请求未成功')
  }
  const contentType = responseHeader(response.headers, 'content-type') || 'application/octet-stream'
  const blob = response.data instanceof Blob
    ? response.data
    : new Blob([response.data], { type: contentType })
  if (!blob.size) throw new Error('服务器返回了空文件')
  if (/application\/(?:problem\+)?json/i.test(contentType)) {
    const payload = await blob.text().then((value) => JSON.parse(value)).catch(() => ({}))
    throw new Error(payload.msg || payload.message || '服务器未返回可下载文件')
  }

  const filename = filenameFromContentDisposition(
    responseHeader(response.headers, 'content-disposition'),
    fallbackFilename
  )
  const documentRef = dependencies.documentRef || document
  const urlApi = dependencies.urlApi || URL
  const objectUrl = urlApi.createObjectURL(blob)
  const anchor = documentRef.createElement('a')
  anchor.href = objectUrl
  anchor.download = filename
  anchor.hidden = true
  documentRef.body.appendChild(anchor)
  try {
    anchor.click()
  } finally {
    anchor.remove()
    urlApi.revokeObjectURL(objectUrl)
  }
  return filename
}
