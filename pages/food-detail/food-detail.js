const { mockFoods } = require('../../mock/foods')
const { calculateExpiryDate } = require('../../utils/date')
const { createMockFoodItem } = require('../../utils/food')
const { getFoodExpiryStatus } = require('../../utils/foodStatus')

const STORAGE_KEY = 'food_expiry_tracker_foods_v0'

const STATUS_TEXT = {
  expired: '已过期',
  today: '今日到期',
  soon: '即将到期',
  normal: '正常',
  unknown: '未填写到期日'
}

const CATEGORY_TEXT = {
  dairy: '乳制品',
  staple: '主食',
  frozen: '冷冻食品',
  beverage: '饮品',
  snack: '零食',
  condiment: '调味品',
  other: '其他'
}

const STORAGE_TEXT = {
  refrigerated: '冷藏',
  room_temp: '常温',
  frozen: '冷冻',
  avoid_light: '避光',
  cool_dry: '阴凉干燥'
}

const DATE_SOURCE_TEXT = {
  calculated: '自动计算',
  manual: '手动填写',
  unknown: '未填写'
}

const SHELF_LIFE_UNIT_TEXT = {
  day: '日',
  month: '月',
  year: '年'
}

const EMPTY_TEXT = '未填写'

const DEFAULT_FORM = {
  name: '',
  category: '',
  quantity: '',
  remainingQuantity: '',
  unit: '',
  storageMethod: '',
  productionDate: '',
  shelfLifeValue: '',
  shelfLifeUnit: 'day',
  expiryDate: '',
  notes: ''
}

const SHELF_LIFE_UNIT_OPTIONS = [
  { label: '日', value: 'day' },
  { label: '月', value: 'month' },
  { label: '年', value: 'year' }
]

function getDisplayText(map, value) {
  return map[value] || value || EMPTY_TEXT
}

function isObjectRecord(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

function normalizeFoodList(value) {
  if (!Array.isArray(value)) {
    return null
  }

  const normalizedFoods = []

  for (const item of value) {
    if (!isObjectRecord(item)) {
      return null
    }

    const food = createMockFoodItem(item)

    if (!food.id || !food.name) {
      return null
    }

    normalizedFoods.push(food)
  }

  return normalizedFoods
}

function getFallbackFoods() {
  return normalizeFoodList(mockFoods) || []
}

function readStoredFoods() {
  if (typeof wx === 'undefined' || typeof wx.getStorageSync !== 'function') {
    return null
  }

  try {
    const storedFoods = wx.getStorageSync(STORAGE_KEY)

    if (storedFoods === '' || storedFoods === null || typeof storedFoods === 'undefined') {
      return null
    }

    return normalizeFoodList(storedFoods)
  } catch (error) {
    return null
  }
}

function writeStoredFoods(rawFoods) {
  if (typeof wx === 'undefined' || typeof wx.setStorageSync !== 'function') {
    return true
  }

  try {
    wx.setStorageSync(STORAGE_KEY, rawFoods)
    return true
  } catch (error) {
    return false
  }
}

function getInitialRawFoods() {
  const storedFoods = readStoredFoods()

  if (storedFoods !== null) {
    return storedFoods
  }

  return getFallbackFoods()
}

function formatFoodForDisplay(food) {
  const status = getFoodExpiryStatus(food)

  return {
    ...food,
    status,
    statusText: STATUS_TEXT[status] || STATUS_TEXT.unknown,
    statusClass: `status-${status}`,
    categoryText: getDisplayText(CATEGORY_TEXT, food.category),
    storageMethodText: getDisplayText(STORAGE_TEXT, food.storageMethod),
    dateSourceText: getDisplayText(DATE_SOURCE_TEXT, food.dateSource),
    shelfLifeUnitText: getDisplayText(SHELF_LIFE_UNIT_TEXT, food.shelfLifeUnit)
  }
}

function trimFormValue(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function toFormText(value) {
  if (value === null || typeof value === 'undefined') {
    return ''
  }

  return String(value)
}

function formatDetailValue(value) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? String(value) : EMPTY_TEXT
  }

  const text = trimFormValue(value)
  return text || EMPTY_TEXT
}

function getShelfLifeUnitIndex(value) {
  const index = SHELF_LIFE_UNIT_OPTIONS.findIndex((option) => option.value === value)
  return index >= 0 ? index : 0
}

function buildFoodForm(food) {
  return {
    name: toFormText(food.name),
    category: toFormText(food.category),
    quantity: toFormText(food.quantity),
    remainingQuantity: toFormText(food.remainingQuantity),
    unit: toFormText(food.unit),
    storageMethod: toFormText(food.storageMethod),
    productionDate: toFormText(food.productionDate),
    shelfLifeValue: toFormText(food.shelfLifeValue),
    shelfLifeUnit: food.shelfLifeUnit || DEFAULT_FORM.shelfLifeUnit,
    expiryDate: toFormText(food.expiryDate),
    notes: toFormText(food.notes)
  }
}

function buildFoodDetailRows(food) {
  return [
    { label: '食品名称', value: formatDetailValue(food.name) },
    { label: '分类', value: food.categoryText || formatDetailValue(food.category) },
    { label: '数量', value: formatDetailValue(food.quantity) },
    { label: '剩余数量', value: formatDetailValue(food.remainingQuantity) },
    { label: '单位', value: formatDetailValue(food.unit) },
    { label: '保存方式', value: food.storageMethodText || formatDetailValue(food.storageMethod) },
    { label: '生产日期', value: formatDetailValue(food.productionDate) },
    { label: '保质期', value: formatDetailValue(food.shelfLifeValue) },
    { label: '保质期单位', value: food.shelfLifeUnitText || formatDetailValue(food.shelfLifeUnit) },
    { label: '最终可食用日期', value: formatDetailValue(food.expiryDate) },
    { label: '日期来源', value: food.dateSourceText || formatDetailValue(food.dateSource) },
    { label: '到期状态', value: formatDetailValue(food.statusText) },
    { label: '备注', value: formatDetailValue(food.notes) }
  ]
}

function parseFormQuantity(value, emptyFallback) {
  const text = trimFormValue(value)

  if (text === '') {
    return emptyFallback
  }

  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : null
}

function showError(message) {
  if (typeof wx !== 'undefined' && wx.showToast) {
    wx.showToast({
      title: message,
      icon: 'none'
    })
  }
}

function returnToIndex() {
  if (typeof wx === 'undefined') {
    return
  }

  if (typeof wx.navigateBack === 'function') {
    wx.navigateBack({
      delta: 1,
      fail: () => {
        if (typeof wx.redirectTo === 'function') {
          wx.redirectTo({ url: '/pages/index/index' })
        }
      }
    })
    return
  }

  if (typeof wx.redirectTo === 'function') {
    wx.redirectTo({ url: '/pages/index/index' })
  }
}

Page({
  data: {
    foodId: '',
    rawFoods: [],
    food: null,
    foodDetails: [],
    notFound: false,
    isEditing: false,
    editForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    editShelfLifeUnitIndex: 0,
    editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    editFormError: ''
  },

  onLoad(options) {
    const foodId = options && typeof options.id === 'string'
      ? decodeURIComponent(options.id)
      : ''

    this.setData({ foodId })
    this.loadFood(foodId)
  },

  loadFood(foodId) {
    const rawFoods = getInitialRawFoods()
    const food = rawFoods.find((item) => item.id === foodId)

    if (!food) {
      this.setData({
        rawFoods,
        food: null,
        foodDetails: [],
        notFound: true,
        isEditing: false,
        editForm: { ...DEFAULT_FORM },
        editFormError: ''
      })
      return
    }

    const displayFood = formatFoodForDisplay(food)

    this.setData({
      rawFoods,
      food: displayFood,
      foodDetails: buildFoodDetailRows(displayFood),
      notFound: false,
      isEditing: false,
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  startEditFood() {
    const food = this.data.food

    if (!food) {
      return
    }

    const editForm = buildFoodForm(food)
    const editShelfLifeUnitIndex = getShelfLifeUnitIndex(editForm.shelfLifeUnit)
    const editShelfLifeUnitOption = SHELF_LIFE_UNIT_OPTIONS[editShelfLifeUnitIndex]

    this.setData({
      isEditing: true,
      editForm,
      editShelfLifeUnitIndex,
      editShelfLifeUnitLabel: editShelfLifeUnitOption.label,
      editFormError: ''
    })
  },

  cancelEditFood() {
    this.setData({
      isEditing: false,
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  updateEditForm(event) {
    const field = event.currentTarget.dataset.field

    if (!field) {
      return
    }

    const optionIndex = Number(event.detail.value) || 0
    const shelfLifeUnitOption = SHELF_LIFE_UNIT_OPTIONS[optionIndex] || SHELF_LIFE_UNIT_OPTIONS[0]
    const value = field === 'shelfLifeUnit'
      ? shelfLifeUnitOption.value
      : event.detail.value
    const nextData = {
      [`editForm.${field}`]: value,
      editFormError: ''
    }

    if (field === 'shelfLifeUnit') {
      nextData.editShelfLifeUnitIndex = SHELF_LIFE_UNIT_OPTIONS[optionIndex] ? optionIndex : 0
      nextData.editShelfLifeUnitLabel = shelfLifeUnitOption.label
    }

    this.setData(nextData)
  },

  resolveExpiryDate(form, originalFood) {
    const manualExpiryDateText = trimFormValue(form.expiryDate)
    const shouldUseCalculatedDate = originalFood &&
      originalFood.dateSource === 'calculated' &&
      manualExpiryDateText !== '' &&
      manualExpiryDateText === originalFood.expiryDate

    if (manualExpiryDateText !== '' && !shouldUseCalculatedDate) {
      const manualExpiryDate = calculateExpiryDate({
        mode: 'manual',
        expiryDate: manualExpiryDateText
      })

      if (!manualExpiryDate) {
        return {
          message: '最终可食用日期格式不正确，请使用 YYYY-MM-DD',
          expiryDate: null,
          dateSource: 'unknown'
        }
      }

      return {
        expiryDate: manualExpiryDate,
        dateSource: 'manual'
      }
    }

    const calculatedExpiryDate = calculateExpiryDate({
      productionDate: trimFormValue(form.productionDate),
      shelfLifeValue: trimFormValue(form.shelfLifeValue),
      shelfLifeUnit: form.shelfLifeUnit
    })

    return {
      expiryDate: calculatedExpiryDate,
      dateSource: calculatedExpiryDate ? 'calculated' : 'unknown'
    }
  },

  validateFoodForm(form, expiryDate) {
    const name = trimFormValue(form.name)
    const quantity = parseFormQuantity(form.quantity, 1)
    const remainingQuantity = parseFormQuantity(form.remainingQuantity, quantity)

    if (!name) {
      return { message: '请填写食品名称' }
    }

    if (quantity === null || quantity < 0) {
      return { message: '数量不能小于 0' }
    }

    if (remainingQuantity === null || remainingQuantity < 0) {
      return { message: '剩余数量不能小于 0' }
    }

    if (remainingQuantity > quantity) {
      return { message: '剩余数量不能大于总数量' }
    }

    if (!expiryDate) {
      return { message: '请填写最终可食用日期，或填写生产日期和保质期' }
    }

    return {
      name,
      quantity,
      remainingQuantity
    }
  },

  submitEditForm() {
    const foodId = this.data.foodId
    const form = this.data.editForm
    const rawFoods = getInitialRawFoods()
    const originalFood = rawFoods.find((food) => food.id === foodId)

    if (!originalFood) {
      const message = '未找到要编辑的食品'
      this.setData({ editFormError: message })
      showError(message)
      return
    }

    const resolvedDate = this.resolveExpiryDate(form, originalFood)

    if (resolvedDate.message) {
      this.setData({
        editFormError: resolvedDate.message,
        isEditing: true
      })
      showError(resolvedDate.message)
      return
    }

    const validation = this.validateFoodForm(form, resolvedDate.expiryDate)

    if (validation.message) {
      this.setData({
        editFormError: validation.message,
        isEditing: true
      })
      showError(validation.message)
      return
    }

    const updatedFood = createMockFoodItem({
      ...originalFood,
      name: validation.name,
      category: trimFormValue(form.category) || 'other',
      quantity: validation.quantity,
      remainingQuantity: validation.remainingQuantity,
      unit: trimFormValue(form.unit) || 'piece',
      storageMethod: trimFormValue(form.storageMethod) || 'room_temp',
      productionDate: trimFormValue(form.productionDate) || null,
      shelfLifeValue: trimFormValue(form.shelfLifeValue) || null,
      shelfLifeUnit: form.shelfLifeUnit || null,
      expiryDate: resolvedDate.expiryDate,
      dateSource: resolvedDate.dateSource,
      notes: trimFormValue(form.notes),
      updatedAt: new Date().toISOString()
    })

    const nextRawFoods = rawFoods.map((food) => (
      food.id === foodId ? updatedFood : food
    ))
    const saved = writeStoredFoods(nextRawFoods)
    const displayFood = formatFoodForDisplay(updatedFood)

    if (!saved) {
      showError('本地保存失败，数据可能只在当前页面保留')
    }

    this.setData({
      rawFoods: nextRawFoods,
      food: displayFood,
      foodDetails: buildFoodDetailRows(displayFood),
      notFound: false,
      isEditing: false,
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  deleteFood() {
    const foodId = this.data.foodId
    const rawFoods = getInitialRawFoods()
    const exists = rawFoods.some((food) => food.id === foodId)

    if (!exists) {
      showError('没有找到这个食品')
      this.setData({
        rawFoods,
        food: null,
        foodDetails: [],
        notFound: true,
        isEditing: false
      })
      return
    }

    const nextRawFoods = rawFoods.filter((food) => food.id !== foodId)
    const saved = writeStoredFoods(nextRawFoods)

    if (!saved) {
      showError('本地保存失败，请稍后再试')
      return
    }

    returnToIndex()
  },

  confirmDeleteFood() {
    if (!this.data.food) {
      return
    }

    if (typeof wx !== 'undefined' && wx.showModal) {
      wx.showModal({
        title: '删除食品',
        content: '确定删除这个食品吗？',
        cancelText: '取消',
        confirmText: '删除',
        confirmColor: '#9f352e',
        success: (result) => {
          if (result.confirm) {
            this.deleteFood()
          }
        }
      })
      return
    }

    this.deleteFood()
  },

  goBack() {
    returnToIndex()
  }
})
