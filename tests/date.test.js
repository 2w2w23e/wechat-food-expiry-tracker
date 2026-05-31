const assert = require('assert')
const { calculateExpiryDate, parseDate } = require('../utils/date')

function test(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

test('calculates expiryDate from days', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2026-05-01',
      shelfLifeValue: 30,
      shelfLifeUnit: 'day'
    }),
    {
      expiryDate: '2026-05-31',
      dateSource: 'calculated'
    }
  )
})

test('calculates expiryDate from months and clamps month-end dates', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2026-01-31',
      shelfLifeValue: 1,
      shelfLifeUnit: 'month'
    }),
    {
      expiryDate: '2026-02-28',
      dateSource: 'calculated'
    }
  )
})

test('calculates expiryDate from years and handles leap day', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2024-02-29',
      shelfLifeValue: 1,
      shelfLifeUnit: 'year'
    }),
    {
      expiryDate: '2025-02-28',
      dateSource: 'calculated'
    }
  )
})

test('supports plural shelf life units', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2026-12-31',
      shelfLifeValue: 1,
      shelfLifeUnit: 'days'
    }),
    {
      expiryDate: '2027-01-01',
      dateSource: 'calculated'
    }
  )
})

test('returns manual expiryDate without date calculation', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      mode: 'manual',
      expiryDate: '2026-06-15'
    }),
    {
      expiryDate: '2026-06-15',
      dateSource: 'manual'
    }
  )
})

test('rejects invalid dates', () => {
  assert.strictEqual(parseDate('2026-02-29'), null)
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2026-02-29',
      shelfLifeValue: 1,
      shelfLifeUnit: 'day'
    }),
    {
      expiryDate: '',
      dateSource: 'calculated'
    }
  )
})

test('rejects missing or invalid shelf life values', () => {
  assert.deepStrictEqual(
    calculateExpiryDate({
      productionDate: '2026-05-01',
      shelfLifeValue: -1,
      shelfLifeUnit: 'day'
    }),
    {
      expiryDate: '',
      dateSource: 'calculated'
    }
  )
})
