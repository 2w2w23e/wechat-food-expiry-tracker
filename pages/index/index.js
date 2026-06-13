const { mockFoods } = require('../../mock/foods')
const { calculateExpiryDate } = require('../../utils/date')
const { createMockFoodItem } = require('../../utils/food')
const { sortFoodsByExpiryDate } = require('../../utils/foodList')
const { getFoodExpiryStatus } = require('../../utils/foodStatus')

const STORAGE_KEY = 'food_expiry_tracker_foods_v0'

const STATUS_TEXT = {
  expired: '已过期',
  today: '今日到期',
  soon: '即将到期',
  normal: '正常',
  unknown: '暂无到期日'
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

const STATUS_FILTERS = {
  ALL: 'all',
  SOON: 'soon',
  EXPIRED: 'expired'
}

const STATUS_FILTER_VALUES = [
  STATUS_FILTERS.ALL,
  STATUS_FILTERS.SOON,
  STATUS_FILTERS.EXPIRED
]

const STATUS_FILTER_TEXT = {
  all: '全部状态',
  soon: '即将过期',
  expired: '已过期'
}

const CATEGORY_FILTERS = {
  ALL: 'all'
}

const PLACEHOLDER_TEXT = '未填写'

const STATUS_FILTER_OPTIONS = [
  { label: '全部状态', value: STATUS_FILTERS.ALL },
  { label: '即将过期', value: STATUS_FILTERS.SOON },
  { label: '已过期', value: STATUS_FILTERS.EXPIRED }
]

const DEFAULT_STATISTICS = {
  totalCount: 0,
  soonCount: 0,
  expiredCount: 0
}

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

function normalizeDisplayValue(value) {
  if (typeof value !== 'string') {
    return value || ''
  }

  const text = value.trim()
  return text === PLACEHOLDER_TEXT ? '' : text
}

function getDisplayText(map, value) {
  const displayValue = normalizeDisplayValue(value)
  return map[displayValue] || displayValue || ''
}

function sanitizeFoodInput(item) {
  const sanitizedItem = { ...item }
  const textFields = [
    'name',
    'category',
    'productionDate',
    'shelfLifeValue',
    'shelfLifeUnit',
    'expiryDate',
    'dateSource',
    'unit',
    'storageMethod',
    'notes'
  ]

  textFields.forEach((field) => {
    if (Object.prototype.hasOwnProperty.call(sanitizedItem, field)) {
      sanitizedItem[field] = normalizeDisplayValue(sanitizedItem[field])
    }
  })

  return sanitizedItem
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

    const food = createMockFoodItem(sanitizeFoodInput(item))

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
  const categoryText = getDisplayText(CATEGORY_TEXT, food.category)
  const storageMethodText = getDisplayText(STORAGE_TEXT, food.storageMethod)

  return {
    ...food,
    status,
    statusText: STATUS_TEXT[status] || STATUS_TEXT.unknown,
    statusClass: `status-${status}`,
    categoryText,
    storageMethodText,
    hasCategoryText: Boolean(categoryText),
    hasStorageMethodText: Boolean(storageMethodText),
    expiryDateText: food.expiryDate || STATUS_TEXT.unknown
  }
}

function buildDisplayFoods(foods) {
  return sortFoodsByExpiryDate(foods).map(formatFoodForDisplay)
}

function buildCategoryFilterOptions(rawFoods) {
  const seen = {
    [CATEGORY_FILTERS.ALL]: true
  }
  const options = [
    { label: '全部分类', value: CATEGORY_FILTERS.ALL }
  ]

  rawFoods.forEach((food) => {
    const category = typeof food.category === 'string' ? food.category.trim() : ''
    const label = getDisplayText(CATEGORY_TEXT, category)

    if (!category || !label || seen[category]) {
      return
    }

    seen[category] = true
    options.push({
      label,
      value: category
    })
  })

  return options
}

function getOptionIndex(options, value) {
  const optionIndex = options.findIndex((option) => option.value === value)
  return optionIndex >= 0 ? optionIndex : 0
}

function normalizeStatusFilter(statusFilter) {
  return STATUS_FILTER_VALUES.includes(statusFilter)
    ? statusFilter
    : STATUS_FILTERS.ALL
}

function normalizeCategoryFilter(categoryFilter, categoryFilterOptions) {
  if (typeof categoryFilter !== 'string' || categoryFilter === '') {
    return CATEGORY_FILTERS.ALL
  }

  const hasCategory = categoryFilterOptions.some((option) => option.value === categoryFilter)
  return hasCategory ? categoryFilter : CATEGORY_FILTERS.ALL
}

function filterRawFoods(rawFoods, statusFilter, categoryFilter) {
  return rawFoods.filter((food) => {
    const matchesStatus = statusFilter === STATUS_FILTERS.ALL ||
      getFoodExpiryStatus(food) === statusFilter
    const matchesCategory = categoryFilter === CATEGORY_FILTERS.ALL ||
      food.category === categoryFilter

    return matchesStatus && matchesCategory
  })
}

function buildFoodStatistics(rawFoods) {
  return rawFoods.reduce((statistics, food) => {
    const status = getFoodExpiryStatus(food)

    if (status === STATUS_FILTERS.SOON) {
      statistics.soonCount += 1
    }

    if (status === STATUS_FILTERS.EXPIRED) {
      statistics.expiredCount += 1
    }

    return statistics
  }, {
    totalCount: rawFoods.length,
    soonCount: 0,
    expiredCount: 0
  })
}

function buildCurrentFilterText(statusFilter, categoryFilter) {
  const statusText = STATUS_FILTER_TEXT[statusFilter] || STATUS_FILTER_TEXT.all
  const categoryText = categoryFilter === CATEGORY_FILTERS.ALL
    ? '全部分类'
    : getDisplayText(CATEGORY_TEXT, categoryFilter)

  return `当前筛选：${statusText}；${categoryText}`
}

function buildFoodPageData(rawFoods, statusFilter, categoryFilter) {
  const categoryFilterOptions = buildCategoryFilterOptions(rawFoods)
  const nextStatusFilter = normalizeStatusFilter(statusFilter)
  const nextCategoryFilter = normalizeCategoryFilter(categoryFilter, categoryFilterOptions)
  const filteredFoods = filterRawFoods(rawFoods, nextStatusFilter, nextCategoryFilter)
  const statusFilterIndex = getOptionIndex(STATUS_FILTER_OPTIONS, nextStatusFilter)
  const categoryFilterIndex = getOptionIndex(categoryFilterOptions, nextCategoryFilter)

  return {
    rawFoods,
    foods: buildDisplayFoods(filteredFoods),
    statistics: buildFoodStatistics(rawFoods),
    categoryFilterOptions,
    statusFilter: nextStatusFilter,
    categoryFilter: nextCategoryFilter,
    statusFilterIndex,
    statusFilterLabel: STATUS_FILTER_OPTIONS[statusFilterIndex].label,
    categoryFilterIndex,
    categoryFilterLabel: categoryFilterOptions[categoryFilterIndex].label,
    currentFilterText: buildCurrentFilterText(nextStatusFilter, nextCategoryFilter)
  }
}

function trimFormValue(value) {
  const normalizedValue = normalizeDisplayValue(value)
  return typeof normalizedValue === 'string' ? normalizedValue : ''
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
    statistics: { ...DEFAULT_STATISTICS },
    statusFilterOptions: STATUS_FILTER_OPTIONS,
    categoryFilterOptions: [],
    statusFilter: STATUS_FILTERS.ALL,
    categoryFilter: CATEGORY_FILTERS.ALL,
    statusFilterIndex: 0,
    statusFilterLabel: STATUS_FILTER_OPTIONS[0].label,
    categoryFilterIndex: 0,
    categoryFilterLabel: '全部分类',
    currentFilterText: '当前筛选：全部状态；全部分类',
    showAddForm: false,
    addForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    shelfLifeUnitIndex: 0,
    shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    formError: ''
  },

  onLoad() {
    this.setData(buildFoodPageData(getInitialRawFoods(), STATUS_FILTERS.ALL, CATEGORY_FILTERS.ALL))
  },

  onShow() {
    this.setData(buildFoodPageData(
      getInitialRawFoods(),
      this.data.statusFilter,
      this.data.categoryFilter
    ))
  },

  changeStatusFilter(event) {
    const optionIndex = Number(event.detail.value) || 0
    const option = this.data.statusFilterOptions[optionIndex] || this.data.statusFilterOptions[0]

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      option.value,
      this.data.categoryFilter
    ))
  },

  changeCategoryFilter(event) {
    const optionIndex = Number(event.detail.value) || 0
    const option = this.data.categoryFilterOptions[optionIndex] || this.data.categoryFilterOptions[0]

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      this.data.statusFilter,
      option.value
    ))
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

    const validation = this.validateFoodForm(form, resolvedDate.expiryDate)

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
    const saved = writeStoredFoods(rawFoods)

    if (!saved) {
      showError('本地保存失败，数据可能只在当前页面保留')
    }

    this.setData({
      ...buildFoodPageData(rawFoods, this.data.statusFilter, this.data.categoryFilter),
      addForm: { ...DEFAULT_FORM },
      shelfLifeUnitIndex: 0,
      shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      showAddForm: false,
      formError: ''
    })
  },

  viewFoodDetail(event) {
    const foodId = event.currentTarget.dataset.id

    if (!foodId || typeof wx === 'undefined' || typeof wx.navigateTo !== 'function') {
      return
    }

    wx.navigateTo({
      url: `/pages/food-detail/food-detail?id=${encodeURIComponent(foodId)}`
    })
  }
})
