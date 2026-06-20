const VALID_UNITS = ['day', 'month', 'year']

const UNIT_ALIASES = {
  day: 'day',
  days: 'day',
  month: 'month',
  months: 'month',
  year: 'year',
  years: 'year',
  '天': 'day',
  '日': 'day',
  '月': 'month',
  '年': 'year'
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

function isLeapYear(year) {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function getDaysInMonth(year, month) {
  return [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}

function parseDateParts(dateString) {
  if (typeof dateString !== 'string') {
    return null
  }

  // Strict date-only parsing avoids browser/runtime timezone conversion drift.
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateString)
  if (!match) {
    return null
  }

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])

  if (month < 1 || month > 12) {
    return null
  }

  if (day < 1 || day > getDaysInMonth(year, month)) {
    return null
  }

  return { year, month, day }
}

function isValidDateString(dateString) {
  return parseDateParts(dateString) !== null
}

function formatDate(date) {
  if (date instanceof Date) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
  }

  if (!date || !Number.isInteger(date.year) || !Number.isInteger(date.month) || !Number.isInteger(date.day)) {
    return null
  }

  const dateParts = parseDateParts(`${date.year}-${pad2(date.month)}-${pad2(date.day)}`)
  return dateParts ? `${dateParts.year}-${pad2(dateParts.month)}-${pad2(dateParts.day)}` : null
}

function addDays(date, amount) {
  const utc = Date.UTC(date.year, date.month - 1, date.day)
  const next = new Date(utc + amount * 24 * 60 * 60 * 1000)

  return {
    year: next.getUTCFullYear(),
    month: next.getUTCMonth() + 1,
    day: next.getUTCDate()
  }
}

function addMonths(date, amount) {
  const totalMonths = date.year * 12 + (date.month - 1) + amount
  const year = Math.floor(totalMonths / 12)
  const month = totalMonths % 12 + 1
  // Clamp month-end dates when the original day does not exist in target month.
  const day = Math.min(date.day, getDaysInMonth(year, month))

  return { year, month, day }
}

function addYears(date, amount) {
  const year = date.year + amount
  // Feb 29 becomes Feb 28 when the target year is not a leap year.
  const day = Math.min(date.day, getDaysInMonth(year, date.month))

  return { year, month: date.month, day }
}

function normalizeShelfLifeUnit(unit) {
  if (typeof unit !== 'string') {
    return null
  }

  return UNIT_ALIASES[unit] || null
}

function parseShelfLifeValue(value) {
  if (typeof value === 'number') {
    return Number.isInteger(value) && value > 0 ? value : null
  }

  if (typeof value === 'string' && /^\d+$/.test(value)) {
    const parsed = Number(value)
    return parsed > 0 ? parsed : null
  }

  return null
}

function addShelfLife(productionDate, shelfLifeValue, shelfLifeUnit) {
  const date = parseDateParts(productionDate)
  const value = parseShelfLifeValue(shelfLifeValue)
  const unit = normalizeShelfLifeUnit(shelfLifeUnit)

  if (!date || !value || !VALID_UNITS.includes(unit)) {
    return null
  }

  if (unit === 'day') {
    return formatDate(addDays(date, value))
  }

  if (unit === 'month') {
    return formatDate(addMonths(date, value))
  }

  return formatDate(addYears(date, value))
}

function calculateExpiryDate(input) {
  if (!input || typeof input !== 'object') {
    return null
  }

  if (input.mode === 'manual') {
    return isValidDateString(input.expiryDate) ? input.expiryDate : null
  }

  // expiryDate is the canonical date-only string used by future sorting/reminders.
  const expiryDate = addShelfLife(input.productionDate, input.shelfLifeValue, input.shelfLifeUnit)
  return expiryDate || null
}

module.exports = {
  addShelfLife,
  calculateExpiryDate,
  formatDate,
  getDaysInMonth,
  isLeapYear,
  isValidDateString,
  parseDateParts
}
