const { isValidDateString } = require('./date')

const EXPIRY_STATUS = {
  EXPIRED: 'expired',
  TODAY: 'today',
  SOON: 'soon',
  NORMAL: 'normal',
  UNKNOWN: 'unknown'
}

const DEFAULT_SOON_DAYS = 7
const DAY_MS = 24 * 60 * 60 * 1000

function toUtcDay(dateString) {
  if (!isValidDateString(dateString)) {
    return null
  }

  const [year, month, day] = dateString.split('-').map(Number)
  return Date.UTC(year, month - 1, day)
}

function getTodayString() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function normalizeSoonDays(value) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    return DEFAULT_SOON_DAYS
  }

  return Math.floor(value)
}

function getExpiryStatusByDate(expiryDate, options = {}) {
  const today = isValidDateString(options.today) ? options.today : getTodayString()
  const expiryUtc = toUtcDay(expiryDate)
  const todayUtc = toUtcDay(today)

  if (expiryUtc === null || todayUtc === null) {
    return EXPIRY_STATUS.UNKNOWN
  }

  const diffDays = Math.round((expiryUtc - todayUtc) / DAY_MS)

  if (diffDays < 0) {
    return EXPIRY_STATUS.EXPIRED
  }

  if (diffDays === 0) {
    return EXPIRY_STATUS.TODAY
  }

  // V0 temporary default: 7 days. Later reminder settings or user preferences can override this.
  if (diffDays <= normalizeSoonDays(options.soonDays)) {
    return EXPIRY_STATUS.SOON
  }

  return EXPIRY_STATUS.NORMAL
}

function getFoodExpiryStatus(food, options = {}) {
  if (!food || typeof food !== 'object') {
    return EXPIRY_STATUS.UNKNOWN
  }

  return getExpiryStatusByDate(food.expiryDate, options)
}

module.exports = {
  DEFAULT_SOON_DAYS,
  EXPIRY_STATUS,
  getExpiryStatusByDate,
  getFoodExpiryStatus
}
