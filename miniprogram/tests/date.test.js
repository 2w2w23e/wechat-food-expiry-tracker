const assert = require('assert')

const {
  addShelfLife,
  calculateExpiryDate,
  formatDate,
  isValidDateString
} = require('../utils/date')

function test(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

test('valid day calculations', () => {
  assert.strictEqual(addShelfLife('2026-05-01', 30, 'day'), '2026-05-31')
  assert.strictEqual(addShelfLife('2026-12-31', 1, 'day'), '2027-01-01')
})

test('valid month calculations clamp month-end dates', () => {
  assert.strictEqual(addShelfLife('2026-01-31', 1, 'month'), '2026-02-28')
  assert.strictEqual(addShelfLife('2024-01-31', 1, 'month'), '2024-02-29')
  assert.strictEqual(addShelfLife('2026-03-31', 1, 'month'), '2026-04-30')
  assert.strictEqual(addShelfLife('2026-11-30', 3, 'month'), '2027-02-28')
})

test('valid year calculations clamp leap day when needed', () => {
  assert.strictEqual(addShelfLife('2026-05-31', 1, 'year'), '2027-05-31')
  assert.strictEqual(addShelfLife('2024-02-29', 1, 'year'), '2025-02-28')
})

test('leap year day calculations', () => {
  assert.strictEqual(addShelfLife('2024-02-28', 1, 'day'), '2024-02-29')
  assert.strictEqual(addShelfLife('2024-02-29', 1, 'day'), '2024-03-01')
})

test('calculateExpiryDate returns expiryDate string for calculated mode', () => {
  const expiryDate = calculateExpiryDate({
    productionDate: '2026-05-01',
    shelfLifeValue: 30,
    shelfLifeUnit: 'day'
  })

  assert.strictEqual(expiryDate, '2026-05-31')
})

test('calculateExpiryDate supports manual expiryDate mode', () => {
  assert.strictEqual(calculateExpiryDate({ mode: 'manual', expiryDate: '2026-06-09' }), '2026-06-09')
  assert.strictEqual(calculateExpiryDate({ mode: 'manual', expiryDate: '2026-02-30' }), null)
})

test('invalid date strings are rejected', () => {
  const invalidDates = [
    '2026-02-30',
    '2026-13-01',
    '2026-00-10',
    '2026-2-9',
    '2026/02/09',
    'abc',
    '',
    null,
    undefined
  ]

  invalidDates.forEach((date) => {
    assert.strictEqual(isValidDateString(date), false)
    assert.strictEqual(addShelfLife(date, 1, 'day'), null)
  })
})

test('invalid shelf life values return null', () => {
  const invalidValues = [0, -1, 1.5, 'abc', '', null, undefined]

  invalidValues.forEach((value) => {
    assert.strictEqual(addShelfLife('2026-05-01', value, 'day'), null)
  })
})

test('invalid shelf life units return null', () => {
  const invalidUnits = ['week', 'hour', 'unknown', '', null, undefined]

  invalidUnits.forEach((unit) => {
    assert.strictEqual(addShelfLife('2026-05-01', 1, unit), null)
  })
})

test('unit aliases map to internal day month year units', () => {
  assert.strictEqual(addShelfLife('2026-05-01', 1, 'days'), '2026-05-02')
  assert.strictEqual(addShelfLife('2026-05-01', 1, 'months'), '2026-06-01')
  assert.strictEqual(addShelfLife('2026-05-01', 1, 'years'), '2027-05-01')
  assert.strictEqual(addShelfLife('2026-05-01', 1, '天'), '2026-05-02')
  assert.strictEqual(addShelfLife('2026-05-01', 1, '月'), '2026-06-01')
  assert.strictEqual(addShelfLife('2026-05-01', 1, '年'), '2027-05-01')
})

test('date-only outputs do not drift across timezones', () => {
  assert.strictEqual(addShelfLife('2026-06-09', 1, 'day'), '2026-06-10')
  assert.strictEqual(formatDate({ year: 2026, month: 6, day: 9 }), '2026-06-09')
})
