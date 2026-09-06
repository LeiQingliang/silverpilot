export function isActivityDateDisabled(date, editing, now = new Date()) {
  if (editing) return false
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return date.getTime() < today.getTime()
}

export function getActivityDateError(value, editing, now = new Date()) {
  if (!value) return '请选择活动日期'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return '活动日期格式应为 YYYY-MM-DD'
  const [, year, month, day] = match.map(Number)
  const date = new Date(0)
  date.setFullYear(year, month - 1, day)
  date.setHours(0, 0, 0, 0)
  if (year < 1 || date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return '请输入有效的活动日期'
  }
  return isActivityDateDisabled(date, editing, now) ? '新增活动日期不能早于今天' : ''
}

export function activityDateShortcut(days, now = new Date()) {
  const date = new Date(now)
  date.setDate(date.getDate() + days)
  date.setHours(0, 0, 0, 0)
  return date
}
