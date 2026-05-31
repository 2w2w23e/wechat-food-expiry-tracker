const VALID_UNITS = ['day', 'month', 'year']

function pad2(value) {
  return String(value).padStart(2, '0')
}

function isLeapYear(year) {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function getDaysInMonth(year, month) {
  return [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
}

function parseDate(value) {
  if (typeof value !== 'string') {
    return null
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
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

function formatDate(date) {
  return `${date.year}-${pad2(date.month)}-${pad2(date.day)}`
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
  const day = Math.min(date.day, getDaysInMonth(year, month))

  return { year, month, day }
}

function addYears(date, amount) {
  const year = date.year + amount
  const day = Math.min(date.day, getDaysInMonth(year, date.month))

  return { year, month: date.month, day }
}

function normalizeShelfLifeUnit(unit) {
  if (unit === 'days') {
    return 'day'
  }

  if (unit === 'months') {
    return 'month'
  }

  if (unit === 'years') {
    return 'year'
  }

  return unit
}

function createResult(expiryDate, dateSource) {
  return {
    expiryDate,
    dateSource
  }
}

function calculateExpiryDate(options) {
  const input = options || {}

  if (input.mode === 'manual') {
    const manualDate = parseDate(input.expiryDate)
    return createResult(manualDate ? formatDate(manualDate) : '', 'manual')
  }

  const productionDate = parseDate(input.productionDate)
  const shelfLifeValue = Number(input.shelfLifeValue)
  const shelfLifeUnit = normalizeShelfLifeUnit(input.shelfLifeUnit)

  if (!productionDate || !Number.isInteger(shelfLifeValue) || shelfLifeValue < 0 || !VALID_UNITS.includes(shelfLifeUnit)) {
    return createResult('', 'calculated')
  }

  if (shelfLifeUnit === 'day') {
    return createResult(formatDate(addDays(productionDate, shelfLifeValue)), 'calculated')
  }

  if (shelfLifeUnit === 'month') {
    return createResult(formatDate(addMonths(productionDate, shelfLifeValue)), 'calculated')
  }

  return createResult(formatDate(addYears(productionDate, shelfLifeValue)), 'calculated')
}

module.exports = {
  calculateExpiryDate,
  formatDate,
  getDaysInMonth,
  isLeapYear,
  parseDate
}
