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
  EXPIRED: 'expired',
  NORMAL: 'normal'
}

const STATUS_FILTER_VALUES = [
  STATUS_FILTERS.ALL,
  STATUS_FILTERS.SOON,
  STATUS_FILTERS.EXPIRED,
  STATUS_FILTERS.NORMAL
]

const STATUS_FILTER_TEXT = {
  all: '全部状态',
  soon: '即将过期',
  expired: '已过期',
  normal: '正常'
}

const CATEGORY_FILTERS = {
  ALL: 'all'
}

const PLACEHOLDER_TEXT = '未填写'

const STATUS_FILTER_OPTIONS = [
  { label: '全部状态', value: STATUS_FILTERS.ALL },
  { label: '即将过期', value: STATUS_FILTERS.SOON },
  { label: '已过期', value: STATUS_FILTERS.EXPIRED },
  { label: '正常', value: STATUS_FILTERS.NORMAL }
]

const BASE_CATEGORY_FILTER_OPTIONS = [
  { label: '乳制品', value: 'dairy' },
  { label: '主食', value: 'staple' },
  { label: '冷冻食品', value: 'frozen' },
  { label: '饮品', value: 'beverage' },
  { label: '零食', value: 'snack' },
  { label: '调味品', value: 'condiment' },
  { label: '其他', value: 'other' }
]

const CATEGORY_SEARCH_ALIASES = {
  all: ['quanbufenlei', 'qbf', 'quanbu', 'qb'],
  dairy: ['ruzhipin', 'rzp'],
  staple: ['zhushi', 'zs'],
  frozen: ['lengdongshipin', 'ldsp'],
  beverage: ['yinpin', 'yp'],
  snack: ['lingshi', 'ls'],
  condiment: ['tiaoweipin', 'twp'],
  other: ['qita', 'qt']
}

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

function buildCategoryFilterOption(label, value) {
  return {
    label,
    value,
    searchAliases: CATEGORY_SEARCH_ALIASES[value] || []
  }
}

function buildCategoryFilterOptions(rawFoods) {
  const seen = {
    [CATEGORY_FILTERS.ALL]: true
  }
  const options = [
    buildCategoryFilterOption('全部分类', CATEGORY_FILTERS.ALL)
  ]

  BASE_CATEGORY_FILTER_OPTIONS.forEach((option) => {
    seen[option.value] = true
    options.push(buildCategoryFilterOption(option.label, option.value))
  })

  rawFoods.forEach((food) => {
    const category = typeof food.category === 'string' ? food.category.trim() : ''
    const label = getDisplayText(CATEGORY_TEXT, category)

    if (!category || !label || seen[category]) {
      return
    }

    seen[category] = true
    options.push(buildCategoryFilterOption(label, category))
  })

  return options
}

function normalizeSearchText(value) {
  return typeof value === 'string'
    ? value.trim().toLowerCase().replace(/\s+/g, '')
    : ''
}

function hasChineseText(value) {
  return /[\u4e00-\u9fff]/.test(value)
}

function matchesCategorySearch(option, searchText) {
  const query = normalizeSearchText(searchText)

  if (!query) {
    return true
  }

  const label = normalizeSearchText(option.label)

  if (hasChineseText(query) && label.includes(query)) {
    return true
  }

  return option.searchAliases.some((alias) => normalizeSearchText(alias).startsWith(query))
}

function filterCategorySearchResults(categoryFilterOptions, searchText) {
  return categoryFilterOptions.filter((option) => matchesCategorySearch(option, searchText))
}

function getStatusFilterLabel(statusFilter) {
  return STATUS_FILTER_TEXT[statusFilter] || STATUS_FILTER_TEXT.all
}

function getCategoryFilterLabel(categoryFilter, categoryFilterOptions) {
  const option = categoryFilterOptions.find((item) => item.value === categoryFilter)
  return option ? option.label : '全部分类'
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
  const statusText = getStatusFilterLabel(statusFilter)
  const categoryText = categoryFilter === CATEGORY_FILTERS.ALL
    ? '全部分类'
    : getDisplayText(CATEGORY_TEXT, categoryFilter)

  return `当前筛选：${statusText}；${categoryText}`
}

function buildFoodPageData(rawFoods, statusFilter, categoryFilter, categorySearchText = '') {
  const categoryFilterOptions = buildCategoryFilterOptions(rawFoods)
  const nextStatusFilter = normalizeStatusFilter(statusFilter)
  const nextCategoryFilter = normalizeCategoryFilter(categoryFilter, categoryFilterOptions)
  const filteredFoods = filterRawFoods(rawFoods, nextStatusFilter, nextCategoryFilter)

  return {
    rawFoods,
    foods: buildDisplayFoods(filteredFoods),
    statistics: buildFoodStatistics(rawFoods),
    categoryFilterOptions,
    statusFilter: nextStatusFilter,
    categoryFilter: nextCategoryFilter,
    statusFilterLabel: getStatusFilterLabel(nextStatusFilter),
    categoryFilterLabel: getCategoryFilterLabel(nextCategoryFilter, categoryFilterOptions),
    categorySearchResults: filterCategorySearchResults(categoryFilterOptions, categorySearchText),
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
    statusFilterLabel: STATUS_FILTER_OPTIONS[0].label,
    categoryFilterLabel: '全部分类',
    showCategoryPanel: false,
    categorySearchText: '',
    categorySearchResults: [],
    currentFilterText: '当前筛选：全部状态；全部分类',
    showAddForm: false,
    addForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    shelfLifeUnitIndex: 0,
    shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    formError: '',
    openingFoodDetailId: ''
  },

  onLoad() {
    this.setData(buildFoodPageData(getInitialRawFoods(), STATUS_FILTERS.ALL, CATEGORY_FILTERS.ALL))
  },

  onShow() {
    this.setData(buildFoodPageData(
      getInitialRawFoods(),
      this.data.statusFilter,
      this.data.categoryFilter,
      this.data.categorySearchText
    ))
  },

  changeStatusFilter(event) {
    const statusFilter = event.currentTarget.dataset.filter || STATUS_FILTERS.ALL

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      statusFilter,
      this.data.categoryFilter,
      this.data.categorySearchText
    ))
  },

  openCategoryPanel() {
    const categorySearchText = ''

    this.setData({
      showCategoryPanel: true,
      categorySearchText,
      categorySearchResults: filterCategorySearchResults(this.data.categoryFilterOptions, categorySearchText)
    })
  },

  closeCategoryPanel() {
    this.setData({
      showCategoryPanel: false,
      categorySearchText: '',
      categorySearchResults: filterCategorySearchResults(this.data.categoryFilterOptions, '')
    })
  },

  stopCategoryPanelTap() {},

  updateCategorySearch(event) {
    const categorySearchText = event.detail.value || ''

    this.setData({
      categorySearchText,
      categorySearchResults: filterCategorySearchResults(this.data.categoryFilterOptions, categorySearchText)
    })
  },

  selectCategoryFilter(event) {
    const categoryFilter = event.currentTarget.dataset.category || CATEGORY_FILTERS.ALL
    const nextData = buildFoodPageData(
      this.data.rawFoods,
      this.data.statusFilter,
      categoryFilter,
      this.data.categorySearchText
    )

    this.setData({
      ...nextData,
      showCategoryPanel: false,
      categorySearchText: '',
      categorySearchResults: filterCategorySearchResults(nextData.categoryFilterOptions, '')
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

    if (this.data.openingFoodDetailId) {
      return
    }

    this.setData({
      openingFoodDetailId: foodId
    })

    if (typeof wx.showLoading === 'function') {
      wx.showLoading({
        title: '正在打开详情',
        mask: true
      })
    }

    wx.navigateTo({
      url: `/pages/food-detail/food-detail?id=${encodeURIComponent(foodId)}`,
      success: () => {
        if (typeof wx.hideLoading === 'function') {
          wx.hideLoading()
        }

        this.setData({
          openingFoodDetailId: ''
        })
      },
      fail: () => {
        if (typeof wx.hideLoading === 'function') {
          wx.hideLoading()
        }

        this.setData({
          openingFoodDetailId: ''
        })
        showError('打开详情失败，请稍后再试')
      }
    })
  }
})
