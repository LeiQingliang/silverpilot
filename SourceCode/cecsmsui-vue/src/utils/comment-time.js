// Comment DATETIME values are Beijing wall-clock times. Parse them explicitly
// so a browser's locale/time zone cannot change their meaning or their order.
const BUSINESS_OFFSET_MINUTES = 8 * 60

export function parseCommentTime(value) {
  if (typeof value !== 'string') return null
  const match = value.trim().match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})?$/i)
  if (!match) return null
  const [, year, month, day, hour, minute, second, fraction = '', zone] = match
  const parts = [year, month, day, hour, minute, second].map(Number)
  const [y, m, d, h, min, s] = parts
  const date = new Date(0)
  date.setUTCFullYear(y, m - 1, d)
  date.setUTCHours(h, min, s, Number(fraction.padEnd(3, '0').slice(0, 3)))
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d
    || date.getUTCHours() !== h || date.getUTCMinutes() !== min || date.getUTCSeconds() !== s) return null
  let offset = BUSINESS_OFFSET_MINUTES
  if (zone?.toUpperCase() === 'Z') offset = 0
  else if (zone) {
    const offsetHour = Number(zone.slice(1, 3))
    const offsetMinute = Number(zone.slice(4, 6))
    if (offsetHour > 23 || offsetMinute > 59) return null
    offset = (zone[0] === '-' ? -1 : 1) * (offsetHour * 60 + offsetMinute)
  }
  return date.getTime() - offset * 60_000
}

export function formatCommentDateTime(value) {
  const timestamp = parseCommentTime(value)
  if (timestamp === null) return '时间未知'
  return new Date(timestamp + BUSINESS_OFFSET_MINUTES * 60_000).toISOString().slice(0, 19).replace('T', ' ')
}

export function formatCommentTime(value, now = Date.now()) {
  const timestamp = parseCommentTime(value)
  const text = formatCommentDateTime(value)
  return timestamp !== null && timestamp > Number(now) ? `${text}（未来时间）` : text
}

export function commentDateTime(value) {
  const timestamp = parseCommentTime(value)
  return timestamp === null ? undefined : new Date(timestamp).toISOString()
}

export function compareCommentTimes(a, b, oldestFirst = false) {
  const first = parseCommentTime(a?.createTime)
  const second = parseCommentTime(b?.createTime)
  // Missing/invalid times are always last; equal times use the persistent ID.
  if (first === null && second !== null) return 1
  if (second === null && first !== null) return -1
  const direction = oldestFirst ? 1 : -1
  return (first !== null && second !== null && first !== second)
    ? direction * (first - second)
    : direction * ((Number(a?.id) || 0) - (Number(b?.id) || 0))
}

export function sortComments(comments, order = 'newest') {
  const replyCount = comment => Array.isArray(comment?.replies)
    ? comment.replies.length : Math.max(0, Number(comment?.replyCount) || 0)
  return [...comments].sort((a, b) => {
    if (order === 'most_replies') {
      const countDifference = replyCount(b) - replyCount(a)
      if (countDifference) return countDifference
    }
    return compareCommentTimes(a, b, order === 'oldest')
  })
}
