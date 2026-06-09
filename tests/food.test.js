const assert = require('assert')

const {
  REQUIRED_FOOD_FIELDS,
  VALID_DATE_SOURCES,
  isValidFoodItem,
  normalizeFoodItem
} = require('../utils/food')
const { MOCK_REFERENCE_DATE, mockFoods } = require('../mock/foods')

function test(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

test('mock foods include every required V0 field', () => {
  mockFoods.forEach((food) => {
    REQUIRED_FOOD_FIELDS.forEach((field) => {
      assert.strictEqual(Object.prototype.hasOwnProperty.call(food, field), true, `${food.id} missing ${field}`)
    })
    assert.strictEqual(isValidFoodItem(food), true, `${food.id} should be valid`)
  })
})

test('mock foods use notes field and do not keep legacy note field', () => {
  mockFoods.forEach((food) => {
    assert.strictEqual(Object.prototype.hasOwnProperty.call(food, 'notes'), true, `${food.id} missing notes`)
    assert.strictEqual(Object.prototype.hasOwnProperty.call(food, 'note'), false, `${food.id} has legacy note`)
  })
})

test('mock foods do not have remaining quantity greater than quantity', () => {
  mockFoods.forEach((food) => {
    assert.strictEqual(food.remainingQuantity <= food.quantity, true, `${food.id} has invalid remainingQuantity`)
  })
})

test('mock foods use expiryDate as the canonical date field', () => {
  const competingFields = ['finalDate', 'expireDate', 'endDate', 'bestBeforeDate']

  mockFoods.forEach((food) => {
    assert.strictEqual(Object.prototype.hasOwnProperty.call(food, 'expiryDate'), true)
    competingFields.forEach((field) => {
      assert.strictEqual(Object.prototype.hasOwnProperty.call(food, field), false, `${food.id} has ${field}`)
    })
  })
})

test('dateSource values are limited to calculated manual or unknown', () => {
  mockFoods.forEach((food) => {
    assert.strictEqual(VALID_DATE_SOURCES.includes(food.dateSource), true, `${food.id} has invalid dateSource`)
  })
})

test('mock foods cover normal soon today expired manual and unknown expiry scenarios', () => {
  assert.strictEqual(mockFoods.some((food) => food.expiryDate > '2026-06-16'), true)
  assert.strictEqual(mockFoods.some((food) => food.expiryDate > MOCK_REFERENCE_DATE && food.expiryDate <= '2026-06-16'), true)
  assert.strictEqual(mockFoods.some((food) => food.expiryDate === MOCK_REFERENCE_DATE), true)
  assert.strictEqual(mockFoods.some((food) => food.expiryDate && food.expiryDate < MOCK_REFERENCE_DATE), true)
  assert.strictEqual(mockFoods.some((food) => food.dateSource === 'manual'), true)
  assert.strictEqual(mockFoods.some((food) => food.expiryDate === null && food.dateSource === 'unknown'), true)
})

test('normalizeFoodItem does not mutate input and preserves expiryDate/dateSource', () => {
  const input = {
    id: 'food_test',
    name: '测试食品',
    expiryDate: '2026-06-20',
    dateSource: 'manual'
  }
  const before = Object.assign({}, input)
  const normalized = normalizeFoodItem(input)

  assert.deepStrictEqual(input, before)
  assert.strictEqual(normalized.expiryDate, '2026-06-20')
  assert.strictEqual(normalized.dateSource, 'manual')
  assert.strictEqual(normalized.category, 'other')
  assert.strictEqual(normalized.quantity, 1)
})

test('normalizeFoodItem clamps remainingQuantity down to quantity', () => {
  const normalized = normalizeFoodItem({
    id: 'food_quantity_normalize',
    name: '测试数量',
    expiryDate: '2026-06-20',
    dateSource: 'manual',
    quantity: 3,
    remainingQuantity: 5
  })

  assert.strictEqual(normalized.quantity, 3)
  assert.strictEqual(normalized.remainingQuantity, 3)
})

test('isValidFoodItem rejects remainingQuantity greater than quantity', () => {
  const item = normalizeFoodItem({
    id: 'food_quantity_invalid',
    name: '测试数量',
    expiryDate: '2026-06-20',
    dateSource: 'manual',
    quantity: 3,
    remainingQuantity: 3
  })

  assert.strictEqual(isValidFoodItem({
    ...item,
    remainingQuantity: 5
  }), false)
})
