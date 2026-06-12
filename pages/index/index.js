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

const FILTER_TYPES = {
  ALL: 'all',
  SOON: 'soon',
  EXPIRED: 'expired',
  CATEGORY: 'category'
}

const FILTER_TEXT = {
  all: '全部',
  soon: '即将过期',
  expired: '已过期',
  category: '分类'
}

const FILTER_OPTIONS = [
  { label: '全部', type: FILTER_TYPES.ALL },
  { label: '即将过期', type: FILTER_TYPES.SOON },
  { label: '已过期', type: FILTER_TYPES.EXPIRED }
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
    expiryDateText: food.expiryDate || STATUS_TEXT.unknown
  }
}

function buildDisplayFoods(foods) {
  return sortFoodsByExpiryDate(foods).map(formatFoodForDisplay)
}

function buildCategoryFilterOptions(rawFoods) {
  const seen = {}
  const options = []

  rawFoods.forEach((food) => {
    const category = typeof food.category === 'string' ? food.category.trim() : ''

    if (!category || seen[category]) {
      return
    }

    seen[category] = true
    options.push({
      label: getDisplayText(CATEGORY_TEXT, category),
      value: category
    })
  })

  return options
}

function getCategoryFilterIndex(categoryFilterOptions, activeCategory) {
  const index = categoryFilterOptions.findIndex((option) => option.value === activeCategory)
  return index >= 0 ? index : 0
}

function filterRawFoods(rawFoods, filterType, activeCategory) {
  if (filterType === FILTER_TYPES.SOON || filterType === FILTER_TYPES.EXPIRED) {
    return rawFoods.filter((food) => getFoodExpiryStatus(food) === filterType)
  }

  if (filterType === FILTER_TYPES.CATEGORY) {
    return rawFoods.filter((food) => food.category === activeCategory)
  }

  return rawFoods
}

function buildFoodStatistics(rawFoods) {
  return rawFoods.reduce((statistics, food) => {
    const status = getFoodExpiryStatus(food)

    if (status === FILTER_TYPES.SOON) {
      statistics.soonCount += 1
    }

    if (status === FILTER_TYPES.EXPIRED) {
      statistics.expiredCount += 1
    }

    return statistics
  }, {
    totalCount: rawFoods.length,
    soonCount: 0,
    expiredCount: 0
  })
}

function buildCurrentFilterText(filterType, activeCategory) {
  if (filterType === FILTER_TYPES.CATEGORY) {
    return `当前筛选：分类：${getDisplayText(CATEGORY_TEXT, activeCategory)}`
  }

  return `当前筛选：${FILTER_TEXT[filterType] || FILTER_TEXT.all}`
}

function buildFoodPageData(rawFoods, filterType, activeCategory) {
  const categoryFilterOptions = buildCategoryFilterOptions(rawFoods)
  let nextFilterType = FILTER_TEXT[filterType] ? filterType : FILTER_TYPES.ALL
  let nextActiveCategory = typeof activeCategory === 'string' ? activeCategory : ''

  if (nextFilterType === FILTER_TYPES.CATEGORY) {
    const hasCategory = categoryFilterOptions.some((option) => option.value === nextActiveCategory)

    if (!hasCategory) {
      nextFilterType = FILTER_TYPES.ALL
      nextActiveCategory = ''
    }
  } else {
    nextActiveCategory = ''
  }

  const filteredFoods = filterRawFoods(rawFoods, nextFilterType, nextActiveCategory)

  return {
    rawFoods,
    foods: buildDisplayFoods(filteredFoods),
    statistics: buildFoodStatistics(rawFoods),
    categoryFilterOptions,
    categoryFilterIndex: getCategoryFilterIndex(categoryFilterOptions, nextActiveCategory),
    activeFilterType: nextFilterType,
    activeCategory: nextActiveCategory,
    activeCategoryLabel: nextActiveCategory ? getDisplayText(CATEGORY_TEXT, nextActiveCategory) : '',
    currentFilterText: buildCurrentFilterText(nextFilterType, nextActiveCategory)
  }
}

function toFormText(value) {
  if (value === null || typeof value === 'undefined') {
    return ''
  }

  return String(value)
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
    statistics: { ...DEFAULT_STATISTICS },
    filterOptions: FILTER_OPTIONS,
    categoryFilterOptions: [],
    categoryFilterIndex: 0,
    activeFilterType: FILTER_TYPES.ALL,
    activeCategory: '',
    activeCategoryLabel: '',
    currentFilterText: '当前筛选：全部',
    showAddForm: false,
    addForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    shelfLifeUnitIndex: 0,
    shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    selectedFood: null,
    selectedFoodDetails: [],
    isEditing: false,
    editingFoodId: '',
    editForm: { ...DEFAULT_FORM },
    editShelfLifeUnitIndex: 0,
    editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    editFormError: '',
    formError: ''
  },

  onLoad() {
    this.setData(buildFoodPageData(getInitialRawFoods(), FILTER_TYPES.ALL, ''))
  },

  changeStatusFilter(event) {
    const filterType = event.currentTarget.dataset.filter

    this.setData({
      ...buildFoodPageData(this.data.rawFoods, filterType, ''),
      selectedFood: null,
      selectedFoodDetails: [],
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  changeCategoryFilter(event) {
    const optionIndex = Number(event.detail.value) || 0
    const categoryOption = this.data.categoryFilterOptions[optionIndex]

    if (!categoryOption) {
      return
    }

    this.setData({
      ...buildFoodPageData(this.data.rawFoods, FILTER_TYPES.CATEGORY, categoryOption.value),
      selectedFood: null,
      selectedFoodDetails: [],
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  toggleAddForm() {
    this.setData({
      showAddForm: !this.data.showAddForm,
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: '',
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
      selectedFoodDetails: buildFoodDetailRows(selectedFood),
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  closeFoodDetail() {
    this.setData({
      selectedFood: null,
      selectedFoodDetails: [],
      isEditing: false,
      editingFoodId: '',
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

  startEditSelectedFood() {
    const selectedFood = this.data.selectedFood

    if (!selectedFood) {
      return
    }

    const editForm = buildFoodForm(selectedFood)
    const editShelfLifeUnitIndex = getShelfLifeUnitIndex(editForm.shelfLifeUnit)
    const editShelfLifeUnitOption = SHELF_LIFE_UNIT_OPTIONS[editShelfLifeUnitIndex]

    this.setData({
      showAddForm: false,
      formError: '',
      isEditing: true,
      editingFoodId: selectedFood.id,
      editForm,
      editShelfLifeUnitIndex,
      editShelfLifeUnitLabel: editShelfLifeUnitOption.label,
      editFormError: ''
    })
  },

  cancelEditFood() {
    this.setData({
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
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
      ...buildFoodPageData(rawFoods, this.data.activeFilterType, this.data.activeCategory),
      addForm: { ...DEFAULT_FORM },
      shelfLifeUnitIndex: 0,
      shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      showAddForm: false,
      formError: ''
    })
  },

  submitEditForm() {
    const foodId = this.data.editingFoodId
    const form = this.data.editForm
    const originalFood = this.data.rawFoods.find((food) => food.id === foodId)

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

    const rawFoods = this.data.rawFoods.map((food) => (
      food.id === foodId ? updatedFood : food
    ))
    const saved = writeStoredFoods(rawFoods)
    const selectedFood = formatFoodForDisplay(updatedFood)

    if (!saved) {
      showError('本地保存失败，数据可能只在当前页面保留')
    }

    this.setData({
      ...buildFoodPageData(rawFoods, this.data.activeFilterType, this.data.activeCategory),
      selectedFood,
      selectedFoodDetails: selectedFood ? buildFoodDetailRows(selectedFood) : [],
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  deleteSelectedFood() {
    const selectedFood = this.data.selectedFood

    if (!selectedFood) {
      return
    }

    const rawFoods = this.data.rawFoods.filter((food) => food.id !== selectedFood.id)
    const saved = writeStoredFoods(rawFoods)

    if (!saved) {
      showError('本地保存失败，数据可能只在当前页面保留')
    }

    this.setData({
      ...buildFoodPageData(rawFoods, this.data.activeFilterType, this.data.activeCategory),
      selectedFood: null,
      selectedFoodDetails: [],
      isEditing: false,
      editingFoodId: '',
      editForm: { ...DEFAULT_FORM },
      editShelfLifeUnitIndex: 0,
      editShelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
      editFormError: ''
    })
  },

  confirmDeleteSelectedFood() {
    if (!this.data.selectedFood) {
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
            this.deleteSelectedFood()
          }
        }
      })
      return
    }

    this.deleteSelectedFood()
  }
})
