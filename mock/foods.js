const { createMockFoodItem } = require('../utils/food')

// Fixed reference date for status coverage: 2026-06-09.
const MOCK_REFERENCE_DATE = '2026-06-09'

const mockFoods = [
  createMockFoodItem({
    id: 'food_001',
    name: '纯牛奶',
    category: 'dairy',
    productionDate: '2026-06-01',
    shelfLifeValue: 30,
    shelfLifeUnit: 'day',
    expiryDate: '2026-07-01',
    dateSource: 'calculated',
    quantity: 6,
    remainingQuantity: 4,
    unit: 'box',
    storageMethod: 'refrigerated',
    notes: '正常未来到期示例',
    createdAt: '2026-06-01T09:00:00+08:00',
    updatedAt: '2026-06-01T09:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_002',
    name: '全麦面包',
    category: 'staple',
    productionDate: '2026-06-05',
    shelfLifeValue: 7,
    shelfLifeUnit: 'day',
    expiryDate: '2026-06-12',
    dateSource: 'calculated',
    quantity: 1,
    remainingQuantity: 1,
    unit: 'bag',
    storageMethod: 'room_temp',
    notes: '临期示例',
    createdAt: '2026-06-05T10:00:00+08:00',
    updatedAt: '2026-06-06T10:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_003',
    name: '酸奶',
    category: 'dairy',
    productionDate: '2026-05-25',
    shelfLifeValue: 15,
    shelfLifeUnit: 'day',
    expiryDate: MOCK_REFERENCE_DATE,
    dateSource: 'calculated',
    quantity: 3,
    remainingQuantity: 1,
    unit: 'bottle',
    storageMethod: 'refrigerated',
    notes: '今日到期示例',
    createdAt: '2026-05-25T08:30:00+08:00',
    updatedAt: '2026-06-08T20:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_004',
    name: '冷冻水饺',
    category: 'frozen',
    productionDate: '2025-12-01',
    shelfLifeValue: 6,
    shelfLifeUnit: 'month',
    expiryDate: '2026-06-01',
    dateSource: 'calculated',
    quantity: 1000,
    remainingQuantity: 250,
    unit: 'g',
    storageMethod: 'frozen',
    notes: '已过期示例',
    createdAt: '2026-01-10T18:00:00+08:00',
    updatedAt: '2026-06-02T18:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_005',
    name: '果汁',
    category: 'beverage',
    productionDate: null,
    shelfLifeValue: null,
    shelfLifeUnit: null,
    expiryDate: '2026-06-20',
    dateSource: 'manual',
    quantity: 1,
    remainingQuantity: 1,
    unit: 'L',
    storageMethod: 'avoid_light',
    notes: '手动填写最终可食用日期',
    createdAt: '2026-06-07T14:00:00+08:00',
    updatedAt: '2026-06-07T14:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_006',
    name: '散装坚果',
    category: 'snack',
    productionDate: null,
    shelfLifeValue: null,
    shelfLifeUnit: null,
    expiryDate: null,
    dateSource: 'unknown',
    quantity: 500,
    remainingQuantity: 300,
    unit: 'g',
    storageMethod: 'cool_dry',
    notes: '无可靠到期日期示例',
    createdAt: '2026-06-08T16:00:00+08:00',
    updatedAt: '2026-06-08T16:00:00+08:00'
  }),
  createMockFoodItem({
    id: 'food_007',
    name: '酱油',
    category: 'condiment',
    productionDate: '2025-06-01',
    shelfLifeValue: 1,
    shelfLifeUnit: 'year',
    expiryDate: '2026-06-01',
    dateSource: 'calculated',
    quantity: 1,
    remainingQuantity: 0.5,
    unit: 'bottle',
    storageMethod: 'cool_dry',
    notes: '年份保质期示例',
    createdAt: '2026-05-01T12:00:00+08:00',
    updatedAt: '2026-06-01T12:00:00+08:00'
  })
]

module.exports = {
  MOCK_REFERENCE_DATE,
  mockFoods
}
