const { calculateExpiryDate, isValidDateString } = require('./date')

const VALID_DATE_SOURCES = ['calculated', 'manual', 'unknown']
const VALID_SHELF_LIFE_UNITS = ['day', 'month', 'year']

const DEFAULT_FOOD_ITEM = {
  id: '',
  name: '',
  category: 'other',
  productionDate: null,
  shelfLifeValue: null,
  shelfLifeUnit: null,
  expiryDate: null,
  dateSource: 'unknown',
  quantity: 1,
  remainingQuantity: 1,
  unit: 'piece',
  storageMethod: 'room_temp',
  notes: '',
  createdAt: '',
  updatedAt: ''
}

const REQUIRED_FOOD_FIELDS = Object.keys(DEFAULT_FOOD_ITEM)

function normalizePositiveInteger(value) {
  if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
    return value
  }

  if (typeof value === 'string' && /^\d+$/.test(value)) {
    const parsed = Number(value)
    return parsed > 0 ? parsed : null
  }

  return null
}

function normalizeQuantity(value, fallback) {
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0) {
    return value
  }

  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
  }

  return fallback
}

function normalizeDateString(value) {
  return isValidDateString(value) ? value : null
}

function normalizeShelfLifeUnit(value) {
  return VALID_SHELF_LIFE_UNITS.includes(value) ? value : null
}

function normalizeDateSource(value, expiryDate) {
  if (VALID_DATE_SOURCES.includes(value)) {
    return value
  }

  return expiryDate ? 'manual' : 'unknown'
}

function normalizeFoodItem(input) {
  const source = input && typeof input === 'object' ? input : {}
  const productionDate = normalizeDateString(source.productionDate)
  const shelfLifeValue = normalizePositiveInteger(source.shelfLifeValue)
  const shelfLifeUnit = normalizeShelfLifeUnit(source.shelfLifeUnit)
  const providedExpiryDate = normalizeDateString(source.expiryDate)
  const dateSource = normalizeDateSource(source.dateSource, providedExpiryDate)
  const calculatedExpiryDate = dateSource === 'calculated'
    ? calculateExpiryDate({ productionDate, shelfLifeValue, shelfLifeUnit })
    : null
  const expiryDate = dateSource === 'unknown' ? null : (providedExpiryDate || calculatedExpiryDate)
  const quantity = normalizeQuantity(source.quantity, DEFAULT_FOOD_ITEM.quantity)
  const remainingQuantity = Math.min(normalizeQuantity(source.remainingQuantity, quantity), quantity)

  return {
    id: typeof source.id === 'string' ? source.id : DEFAULT_FOOD_ITEM.id,
    name: typeof source.name === 'string' ? source.name : DEFAULT_FOOD_ITEM.name,
    category: typeof source.category === 'string' && source.category ? source.category : DEFAULT_FOOD_ITEM.category,
    productionDate,
    shelfLifeValue,
    shelfLifeUnit,
    expiryDate,
    dateSource: expiryDate ? dateSource : 'unknown',
    quantity,
    remainingQuantity,
    unit: typeof source.unit === 'string' && source.unit ? source.unit : DEFAULT_FOOD_ITEM.unit,
    storageMethod: typeof source.storageMethod === 'string' && source.storageMethod
      ? source.storageMethod
      : DEFAULT_FOOD_ITEM.storageMethod,
    notes: typeof source.notes === 'string' ? source.notes : DEFAULT_FOOD_ITEM.notes,
    createdAt: typeof source.createdAt === 'string' ? source.createdAt : DEFAULT_FOOD_ITEM.createdAt,
    updatedAt: typeof source.updatedAt === 'string' ? source.updatedAt : DEFAULT_FOOD_ITEM.updatedAt
  }
}

function createMockFoodItem(input) {
  return normalizeFoodItem(input)
}

function isValidFoodItem(input) {
  if (!input || typeof input !== 'object') {
    return false
  }

  const quantity = normalizeQuantity(input.quantity, null)
  const remainingQuantity = normalizeQuantity(input.remainingQuantity, null)
  const item = normalizeFoodItem(input)

  return REQUIRED_FOOD_FIELDS.every((field) => Object.prototype.hasOwnProperty.call(input, field)) &&
    item.id !== '' &&
    item.name !== '' &&
    quantity !== null &&
    remainingQuantity !== null &&
    remainingQuantity <= quantity &&
    VALID_DATE_SOURCES.includes(item.dateSource) &&
    (item.expiryDate === null || isValidDateString(item.expiryDate)) &&
    (item.dateSource !== 'unknown' || item.expiryDate === null)
}

module.exports = {
  REQUIRED_FOOD_FIELDS,
  VALID_DATE_SOURCES,
  createMockFoodItem,
  isValidFoodItem,
  normalizeFoodItem
}
