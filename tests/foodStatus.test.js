const assert = require('assert')

const {
  DEFAULT_SOON_DAYS,
  EXPIRY_STATUS,
  getExpiryStatusByDate,
  getFoodExpiryStatus
} = require('../utils/foodStatus')
const { sortFoodsByExpiryDate } = require('../utils/foodList')
const { mockFoods } = require('../mock/foods')

const TEST_TODAY = '2026-06-09'

function test(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

test('expiry status supports expired today soon normal and unknown', () => {
  assert.strictEqual(getExpiryStatusByDate('2026-06-08', { today: TEST_TODAY }), EXPIRY_STATUS.EXPIRED)
  assert.strictEqual(getExpiryStatusByDate('2026-06-09', { today: TEST_TODAY }), EXPIRY_STATUS.TODAY)
  assert.strictEqual(getExpiryStatusByDate('2026-06-10', { today: TEST_TODAY }), EXPIRY_STATUS.SOON)
  assert.strictEqual(getExpiryStatusByDate('2026-06-16', { today: TEST_TODAY }), EXPIRY_STATUS.SOON)
  assert.strictEqual(getExpiryStatusByDate('2026-06-17', { today: TEST_TODAY }), EXPIRY_STATUS.NORMAL)
})

test('invalid expiryDate values return unknown', () => {
  const invalidDates = [null, undefined, '', 'abc', '2026-2-9', '2026/06/09', '2026-02-30']

  invalidDates.forEach((expiryDate) => {
    assert.strictEqual(getExpiryStatusByDate(expiryDate, { today: TEST_TODAY }), EXPIRY_STATUS.UNKNOWN)
    assert.strictEqual(getFoodExpiryStatus({ expiryDate }, { today: TEST_TODAY }), EXPIRY_STATUS.UNKNOWN)
  })
})

test('soon threshold defaults to 7 days and can be overridden', () => {
  assert.strictEqual(DEFAULT_SOON_DAYS, 7)
  assert.strictEqual(getExpiryStatusByDate('2026-06-16', { today: TEST_TODAY }), EXPIRY_STATUS.SOON)
  assert.strictEqual(getExpiryStatusByDate('2026-06-16', { today: TEST_TODAY, soonDays: 3 }), EXPIRY_STATUS.NORMAL)
  assert.strictEqual(getExpiryStatusByDate('2026-06-12', { today: TEST_TODAY, soonDays: 3 }), EXPIRY_STATUS.SOON)
})

test('food status reads expiryDate from food item only', () => {
  const food = {
    name: '名称不参与状态判断',
    createdAt: '2020-01-01T00:00:00+08:00',
    updatedAt: '2020-01-02T00:00:00+08:00',
    expiryDate: '2026-06-08'
  }

  assert.strictEqual(getFoodExpiryStatus(food, { today: TEST_TODAY }), EXPIRY_STATUS.EXPIRED)
  assert.strictEqual(getFoodExpiryStatus(null, { today: TEST_TODAY }), EXPIRY_STATUS.UNKNOWN)
})

test('sortFoodsByExpiryDate sorts valid expiryDate ascending', () => {
  const foods = [
    { id: 'normal', expiryDate: '2026-06-17' },
    { id: 'today', expiryDate: '2026-06-09' },
    { id: 'expired', expiryDate: '2026-06-08' },
    { id: 'soon', expiryDate: '2026-06-10' }
  ]

  assert.deepStrictEqual(sortFoodsByExpiryDate(foods).map((food) => food.id), [
    'expired',
    'today',
    'soon',
    'normal'
  ])
})

test('invalid expiryDate items are kept stably at the bottom', () => {
  const foods = [
    { id: 'invalid_null', expiryDate: null },
    { id: 'valid_later', expiryDate: '2026-06-17' },
    { id: 'invalid_empty', expiryDate: '' },
    { id: 'valid_earlier', expiryDate: '2026-06-08' },
    { id: 'invalid_text', expiryDate: 'abc' }
  ]

  assert.deepStrictEqual(sortFoodsByExpiryDate(foods).map((food) => food.id), [
    'valid_earlier',
    'valid_later',
    'invalid_null',
    'invalid_empty',
    'invalid_text'
  ])
})

test('same expiryDate items keep original relative order', () => {
  const foods = [
    { id: 'same_1', expiryDate: '2026-06-10' },
    { id: 'earlier', expiryDate: '2026-06-08' },
    { id: 'same_2', expiryDate: '2026-06-10' },
    { id: 'same_3', expiryDate: '2026-06-10' }
  ]

  assert.deepStrictEqual(sortFoodsByExpiryDate(foods).map((food) => food.id), [
    'earlier',
    'same_1',
    'same_2',
    'same_3'
  ])
})

test('sortFoodsByExpiryDate does not mutate input array', () => {
  const foods = [
    { id: 'later', expiryDate: '2026-06-17' },
    { id: 'earlier', expiryDate: '2026-06-08' }
  ]
  const originalOrder = foods.map((food) => food.id)
  const sorted = sortFoodsByExpiryDate(foods)

  assert.notStrictEqual(sorted, foods)
  assert.deepStrictEqual(foods.map((food) => food.id), originalOrder)
})

test('mock foods can be recognized by status utility', () => {
  const statuses = mockFoods.map((food) => getFoodExpiryStatus(food, { today: TEST_TODAY }))

  assert.strictEqual(statuses.includes(EXPIRY_STATUS.EXPIRED), true)
  assert.strictEqual(statuses.includes(EXPIRY_STATUS.TODAY), true)
  assert.strictEqual(statuses.includes(EXPIRY_STATUS.SOON), true)
  assert.strictEqual(statuses.includes(EXPIRY_STATUS.NORMAL), true)
  assert.strictEqual(statuses.includes(EXPIRY_STATUS.UNKNOWN), true)
})
