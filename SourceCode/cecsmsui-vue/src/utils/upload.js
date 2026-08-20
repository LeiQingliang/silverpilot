const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif'])
const DEFAULT_MAX_IMAGE_BYTES = 2 * 1024 * 1024
const DEFAULT_COMPRESSION_THRESHOLD = 600 * 1024

export const validateImageUpload = (file, maxBytes = DEFAULT_MAX_IMAGE_BYTES) => {
  if (!file || !IMAGE_TYPES.has(file.type)) {
    return '仅支持 JPG、PNG 或 GIF 图片'
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    return '图片文件不能为空'
  }
  if (file.size > maxBytes) {
    return `图片大小不能超过 ${Math.floor(maxBytes / 1024 / 1024)}MB`
  }
  return ''
}

const loadBitmap = async (file) => {
  if (typeof createImageBitmap === 'function') return createImageBitmap(file)
  const objectUrl = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.decoding = 'async'
    image.src = objectUrl
    await image.decode()
    return image
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
  }
}

const canvasBlob = (canvas, type, quality) => new Promise((resolve, reject) => {
  canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('图片压缩失败')), type, quality)
})

/**
 * Downscales large photo uploads before Element Plus sends them. GIF files are
 * intentionally left untouched so animation is not destroyed.
 */
export const prepareImageUpload = async (file, options = {}) => {
  const maxBytes = options.maxBytes ?? DEFAULT_MAX_IMAGE_BYTES
  const typeError = validateImageUpload(file, Number.MAX_SAFE_INTEGER)
  if (typeError) throw new Error(typeError)
  if (file.type === 'image/gif' || file.size <= (options.threshold ?? DEFAULT_COMPRESSION_THRESHOLD)) {
    const error = validateImageUpload(file, maxBytes)
    if (error) throw new Error(error)
    return file
  }

  const bitmap = await loadBitmap(file)
  const maxDimension = options.maxDimension ?? 1600
  const ratio = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height))
  const width = Math.max(1, Math.round(bitmap.width * ratio))
  const height = Math.max(1, Math.round(bitmap.height * ratio))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d', { alpha: file.type === 'image/png' })
  if (!context) throw new Error('浏览器无法处理该图片')
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close?.()
  if (bitmap instanceof HTMLImageElement) URL.revokeObjectURL(bitmap.src)

  const outputType = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  const blob = await canvasBlob(canvas, outputType, options.quality ?? 0.82)
  const optimized = new File([blob], file.name, { type: outputType, lastModified: Date.now() })
  const error = validateImageUpload(optimized, maxBytes)
  if (error) throw new Error(error)
  return optimized.size < file.size ? optimized : file
}
