const { mockFoods } = require('../../mock/foods')
const { calculateExpiryDate } = require('../../utils/date')
const { createMockFoodItem } = require('../../utils/food')
const { sortFoodsByExpiryDate } = require('../../utils/foodList')
const { getFoodExpiryStatus } = require('../../utils/foodStatus')

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

function formatFoodForDisplay(food) {
  const status = getFoodExpiryStatus(food)

  return {
    ...food,
    status,
    statusText: STATUS_TEXT[status] || STATUS_TEXT.unknown,
    statusClass: `status-${status}`,
    categoryText: getDisplayText(CATEGORY_TEXT, food.category),
    storageMethodText: getDisplayText(STORAGE_TEXT, food.storageMethod),
    expiryDateText: food.expiryDate || STATUS_TEXT.unknown
  }
}

function buildDisplayFoods(foods) {
  return sortFoodsByExpiryDate(foods).map(formatFoodForDisplay)
}

function formatDetailValue(value) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? String(value) : EMPTY_TEXT
  }

  const text = trimFormValue(value)
  return text || EMPTY_TEXT
}

function formatShelfLifeText(food) {
  const value = formatDetailValue(food.shelfLifeValue)

  if (value === EMPTY_TEXT) {
    return EMPTY_TEXT
  }

  return `${value} ${getDisplayText(SHELF_LIFE_UNIT_TEXT, food.shelfLifeUnit)}`
}

function buildFoodDetailRows(food) {
  return [
    { label: '食品名称', value: formatDetailValue(food.name) },
    { label: '分类', value: getDisplayText(CATEGORY_TEXT, food.category) },
    { label: '数量', value: formatDetailValue(food.quantity) },
    { label: '剩余数量', value: formatDetailValue(food.remainingQuantity) },
    { label: '单位', value: formatDetailValue(food.unit) },
    { label: '保存方式', value: getDisplayText(STORAGE_TEXT, food.storageMethod) },
    { label: '生产日期', value: formatDetailValue(food.productionDate) },
    { label: '保质期', value: formatShelfLifeText(food) },
    { label: '最终可食用日期', value: formatDetailValue(food.expiryDate) },
    { label: '日期来源', value: getDisplayText(DATE_SOURCE_TEXT, food.dateSource) },
    { label: '到期状态', value: formatDetailValue(food.statusText) },
    { label: '备注', value: formatDetailValue(food.notes) }
  ]
}

function trimFormValue(value) {
  return typeof value === 'string' ? value.trim() : ''
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

Page({
  data: {
    foods: [],
    rawFoods: [],
    showAddForm: false,
    addForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    shelfLifeUnitIndex: 0,
    shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    selectedFood: null,
    selectedFoodDetails: [],
    formError: ''
  },

  onLoad() {
    this.setData({
      foods: buildDisplayFoods(mockFoods),
      rawFoods: mockFoods
    })
  },

  toggleAddForm() {
    this.setData({
      showAddForm: !this.data.showAddForm,
      formError: ''
    })
  },

  updateAddForm(event) {
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
      [`addForm.${field}`]: value,
      formError: ''
    }

    if (field === 'shelfLifeUnit') {
      nextData.shelfLifeUnitIndex = SHELF_LIFE_UNIT_OPTIONS[optionIndex] ? optionIndex : 0
      nextData.shelfLifeUnitLabel = shelfLifeUnitOption.label
    }

    this.setData(nextData)
  },

  showFoodDetail(event) {
    const foodId = event.currentTarget.dataset.id
    const selectedFood = this.data.foods.find((food) => food.id === foodId)

    if (!selectedFood) {
      return
    }

    this.setData({
      selectedFood,
      selectedFoodDetails: buildFoodDetailRows(selectedFood)
    })
  },

  closeFoodDetail() {
    this.setData({
      selectedFood: null,
      selectedFoodDetails: []
    })
  },

  resolveExpiryDate(form) {
    const manualExpiryDateText = trimFormValue(form.expiryDate)

    if (manualExpiryDateText !== '') {
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

  validateAddForm(form, expiryDate) {
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

  submitAddForm() {
    const form = this.data.addForm
    const resolvedDate = this.resolveExpiryDate(form)

    if (resolvedDate.message) {
      this.setData({
        formError: resolvedDate.message,
        showAddForm: true
      })
      showError(resolvedDate.message)
      return
    }

    const validation = this.validateAddForm(form, resolvedDate.expiryDate)

    if (validation.message) {
      this.setData({
        formError: validation.message,
        showAddForm: true
      })
      showError(validation.message)
      return
    }

    const now = new Date().toISOString()
    const food = createMockFoodItem({
      id: `food_${Date.now()}`,
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
      createdAt: now,
      updatedAt: now
    })

    const rawFoods = [food, ...this.data.rawFoods]

    this.setData({
      rawFoods,
      foods: buildDisplayFoods(rawFoods),
      addForm: { ...DEFAULT_FORM },
      shelfLifeUnitIndex: 0,
      shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      showAddForm: false,
      formError: ''
    })
  }
})
