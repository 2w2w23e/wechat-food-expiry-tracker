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
  SOON: 'soon',
  EXPIRED: 'expired',
  NORMAL: 'normal'
}

const DEFAULT_STATUS_FILTERS = [STATUS_FILTERS.SOON]

const STATUS_FILTER_VALUES = [
  STATUS_FILTERS.SOON,
  STATUS_FILTERS.EXPIRED,
  STATUS_FILTERS.NORMAL
]

const STATUS_FILTER_TEXT = {
  soon: '即将过期',
  expired: '已过期',
  normal: '正常'
}

const CATEGORY_FILTERS = {
  ALL: 'all'
}

const PLACEHOLDER_TEXT = '未填写'

const STATUS_FILTER_OPTIONS = [
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
  const seen = {}
  const options = []

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

function normalizeFilterArray(value, fallback) {
  if (Array.isArray(value)) {
    return value
  }

  if (typeof value === 'string' && value) {
    return [value]
  }

  return fallback
}

function normalizeStatusFilters(statusFilters) {
  const selectedValues = normalizeFilterArray(statusFilters, DEFAULT_STATUS_FILTERS)
  const selectedMap = selectedValues.reduce((map, value) => {
    if (STATUS_FILTER_VALUES.includes(value)) {
      map[value] = true
    }

    return map
  }, {})

  return STATUS_FILTER_VALUES.filter((value) => selectedMap[value])
}

function buildStatusFilterOptions(statusFilters) {
  return STATUS_FILTER_OPTIONS.map((option) => ({
    ...option,
    selected: statusFilters.includes(option.value)
  }))
}

function buildSelectedStatusChips(statusFilters) {
  return statusFilters.map((value) => ({
    value,
    label: STATUS_FILTER_TEXT[value] || value
  }))
}

function getAvailableCategoryValues(categoryFilterOptions) {
  return categoryFilterOptions
    .map((option) => option.value)
    .filter((value) => value !== CATEGORY_FILTERS.ALL)
}

function normalizeExplicitCategoryFilters(categoryFilters, categoryFilterOptions) {
  const selectedValues = normalizeFilterArray(categoryFilters, [])
  const availableValues = getAvailableCategoryValues(categoryFilterOptions)
  const availableMap = availableValues.reduce((map, value) => {
    map[value] = true
    return map
  }, {})
  const selectedMap = selectedValues.reduce((map, value) => {
    if (availableMap[value]) {
      map[value] = true
    }

    return map
  }, {})

  return availableValues.filter((value) => selectedMap[value])
}

function collapseCategoryFilters(categoryFilters, categoryFilterOptions) {
  const normalizedValues = normalizeExplicitCategoryFilters(categoryFilters, categoryFilterOptions)
  const availableValues = getAvailableCategoryValues(categoryFilterOptions)

  if (availableValues.length && normalizedValues.length === availableValues.length) {
    return [CATEGORY_FILTERS.ALL]
  }

  return normalizedValues
}

function expandCategoryFilters(categoryFilters, categoryFilterOptions) {
  return isAllCategorySelected(categoryFilters)
    ? getAvailableCategoryValues(categoryFilterOptions)
    : normalizeExplicitCategoryFilters(categoryFilters, categoryFilterOptions)
}

function normalizeCategoryFilters(categoryFilters, categoryFilterOptions) {
  const selectedValues = normalizeFilterArray(categoryFilters, [CATEGORY_FILTERS.ALL])

  if (selectedValues.includes(CATEGORY_FILTERS.ALL)) {
    return [CATEGORY_FILTERS.ALL]
  }

  return collapseCategoryFilters(selectedValues, categoryFilterOptions)
}

function isAllCategorySelected(categoryFilters) {
  return categoryFilters.length === 1 && categoryFilters[0] === CATEGORY_FILTERS.ALL
}

function buildCategoryFilterLabel(categoryFilters, categoryFilterOptions) {
  if (isAllCategorySelected(categoryFilters)) {
    return '已全选'
  }

  if (!categoryFilters.length) {
    return '未选择'
  }

  if (categoryFilters.length === 1) {
    const option = categoryFilterOptions.find((item) => item.value === categoryFilters[0])
    return option ? option.label : '已全选'
  }

  return `已选 ${categoryFilters.length} 个分类`
}

function buildSelectedCategoryChips(categoryFilters, categoryFilterOptions) {
  if (isAllCategorySelected(categoryFilters)) {
    return [{
      value: CATEGORY_FILTERS.ALL,
      label: '已全选',
      removable: false
    }]
  }

  if (!categoryFilters.length) {
    return [{
      value: 'none',
      label: '未选择分类',
      removable: false
    }]
  }

  return categoryFilters.map((value) => {
    const option = categoryFilterOptions.find((item) => item.value === value)

    return {
      value,
      label: option ? option.label : value,
      removable: true
    }
  })
}

function buildCategoryPanelSelectedText(categoryFilters) {
  if (isAllCategorySelected(categoryFilters)) {
    return '已全选'
  }

  return categoryFilters.length
    ? `已选 ${categoryFilters.length} 个分类`
    : '未选择分类'
}

function applyCategoryOptionSelection(categoryFilterOptions, categoryFilters) {
  const allCategorySelected = isAllCategorySelected(categoryFilters)
  const selectedMap = categoryFilters.reduce((map, value) => {
    map[value] = true
    return map
  }, {})

  return categoryFilterOptions.map((option) => ({
    ...option,
    selected: allCategorySelected || Boolean(selectedMap[option.value])
  }))
}

function sortCategoryOptionsBySelection(categoryFilterOptions, categoryFilters) {
  return applyCategoryOptionSelection(categoryFilterOptions, categoryFilters)
    .map((option, index) => ({
      ...option,
      originalIndex: index
    }))
    .sort((left, right) => {
      if (left.selected !== right.selected) {
        return left.selected ? -1 : 1
      }

      return left.originalIndex - right.originalIndex
    })
    .map(({ originalIndex, ...option }) => option)
}

function filterCategorySearchResults(
  categoryFilterOptions,
  searchText,
  categoryFilters,
  prioritizeSelected = false
) {
  const matchedOptions = categoryFilterOptions.filter((option) => matchesCategorySearch(option, searchText))

  return prioritizeSelected
    ? sortCategoryOptionsBySelection(matchedOptions, categoryFilters)
    : applyCategoryOptionSelection(matchedOptions, categoryFilters)
}

function updateCategorySearchResultSelections(categorySearchResults, categoryFilters) {
  return applyCategoryOptionSelection(categorySearchResults, categoryFilters)
}

function toggleStatusFilterValue(statusFilters, statusFilter) {
  if (!STATUS_FILTER_VALUES.includes(statusFilter)) {
    return statusFilters
  }

  const nextStatusFilters = statusFilters.includes(statusFilter)
    ? statusFilters.filter((value) => value !== statusFilter)
    : [...statusFilters, statusFilter]

  return normalizeStatusFilters(nextStatusFilters)
}

function toggleCategoryFilterValue(categoryFilters, categoryFilter, categoryFilterOptions) {
  if (categoryFilter === CATEGORY_FILTERS.ALL) {
    return [CATEGORY_FILTERS.ALL]
  }

  const baseCategoryFilters = expandCategoryFilters(categoryFilters, categoryFilterOptions)
  const nextCategoryFilters = baseCategoryFilters.includes(categoryFilter)
    ? baseCategoryFilters.filter((value) => value !== categoryFilter)
    : [...baseCategoryFilters, categoryFilter]

  return collapseCategoryFilters(nextCategoryFilters, categoryFilterOptions)
}

function removeCategoryFilterValue(categoryFilters, categoryFilter, categoryFilterOptions) {
  if (categoryFilter === CATEGORY_FILTERS.ALL) {
    return [CATEGORY_FILTERS.ALL]
  }

  return collapseCategoryFilters(
    expandCategoryFilters(categoryFilters, categoryFilterOptions).filter((value) => value !== categoryFilter),
    categoryFilterOptions
  )
}

function getVisibleCategoryValues(categorySearchResults) {
  return getAvailableCategoryValues(categorySearchResults)
}

function selectVisibleCategoryFilters(categoryFilters, categorySearchResults, categoryFilterOptions) {
  const visibleValues = getVisibleCategoryValues(categorySearchResults)

  if (!visibleValues.length || isAllCategorySelected(categoryFilters)) {
    return categoryFilters
  }

  return collapseCategoryFilters(
    [...expandCategoryFilters(categoryFilters, categoryFilterOptions), ...visibleValues],
    categoryFilterOptions
  )
}

function resetVisibleCategoryFilters(categoryFilters, categorySearchResults, categoryFilterOptions) {
  const visibleValues = getVisibleCategoryValues(categorySearchResults)

  if (!visibleValues.length) {
    return categoryFilters
  }

  const visibleMap = visibleValues.reduce((map, value) => {
    map[value] = true
    return map
  }, {})

  return collapseCategoryFilters(
    expandCategoryFilters(categoryFilters, categoryFilterOptions).filter((value) => !visibleMap[value]),
    categoryFilterOptions
  )
}

function filterRawFoods(rawFoods, statusFilters, categoryFilters) {
  if (!statusFilters.length) {
    return []
  }

  return rawFoods.filter((food) => {
    const matchesStatus = statusFilters.includes(getFoodExpiryStatus(food))
    const matchesCategory = categoryFilters.length > 0 && (
      isAllCategorySelected(categoryFilters) ||
      categoryFilters.includes(food.category)
    )

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

function buildEmptyText(rawFoods, statusFilters, categoryFilters) {
  if (!rawFoods.length) {
    return '暂无食品数据'
  }

  if (!statusFilters.length) {
    return '未选择状态，列表不展示食品'
  }

  if (!categoryFilters.length) {
    return '未选择分类，列表不展示食品'
  }

  return '当前筛选下没有食品'
}

function buildActiveFilterSummary(statusFilters, categoryFilters, categoryFilterOptions) {
  const statusText = statusFilters.length
    ? buildSelectedStatusChips(statusFilters).map((item) => item.label).join('、')
    : '未选状态'
  const categoryText = buildCategoryFilterLabel(categoryFilters, categoryFilterOptions)

  return `状态：${statusText}；分类：${categoryText}`
}

function buildFoodPageData(rawFoods, statusFilters, categoryFilters, categorySearchText = '') {
  const categoryFilterOptions = buildCategoryFilterOptions(rawFoods)
  const nextStatusFilters = normalizeStatusFilters(statusFilters)
  const nextCategoryFilters = normalizeCategoryFilters(categoryFilters, categoryFilterOptions)
  const filteredFoods = filterRawFoods(rawFoods, nextStatusFilters, nextCategoryFilters)

  return {
    rawFoods,
    foods: buildDisplayFoods(filteredFoods),
    statistics: buildFoodStatistics(rawFoods),
    categoryFilterOptions,
    statusFilters: nextStatusFilters,
    categoryFilters: nextCategoryFilters,
    statusFilterOptions: buildStatusFilterOptions(nextStatusFilters),
    categoryFilterLabel: buildCategoryFilterLabel(nextCategoryFilters, categoryFilterOptions),
    categoryPanelSelectedText: buildCategoryPanelSelectedText(nextCategoryFilters),
    selectedStatusChips: buildSelectedStatusChips(nextStatusFilters),
    selectedCategoryChips: buildSelectedCategoryChips(nextCategoryFilters, categoryFilterOptions),
    categorySearchResults: filterCategorySearchResults(
      categoryFilterOptions,
      categorySearchText,
      nextCategoryFilters
    ),
    activeFilterSummary: buildActiveFilterSummary(
      nextStatusFilters,
      nextCategoryFilters,
      categoryFilterOptions
    ),
    emptyText: buildEmptyText(rawFoods, nextStatusFilters, nextCategoryFilters)
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
    statusFilterOptions: buildStatusFilterOptions(DEFAULT_STATUS_FILTERS),
    categoryFilterOptions: [],
    statusFilters: [...DEFAULT_STATUS_FILTERS],
    categoryFilters: [CATEGORY_FILTERS.ALL],
    categoryFilterLabel: '已全选',
    categoryPanelSelectedText: '已全选',
    selectedStatusChips: buildSelectedStatusChips(DEFAULT_STATUS_FILTERS),
    selectedCategoryChips: [{
      value: CATEGORY_FILTERS.ALL,
      label: '已全选',
      removable: false
    }],
    showCategoryPanel: false,
    categorySearchText: '',
    categorySearchResults: [],
    activeFilterSummary: '状态：即将过期；分类：已全选',
    activeFilterCollapsed: false,
    showFilterBarCollapse: false,
    showBackToTop: false,
    emptyText: '当前筛选下没有食品',
    showAddForm: false,
    addForm: { ...DEFAULT_FORM },
    shelfLifeUnitOptions: SHELF_LIFE_UNIT_OPTIONS,
    shelfLifeUnitIndex: 0,
    shelfLifeUnitLabel: SHELF_LIFE_UNIT_OPTIONS[0].label,
    formError: '',
    openingFoodDetailId: ''
  },

  onLoad() {
    this.setData(buildFoodPageData(getInitialRawFoods(), DEFAULT_STATUS_FILTERS, [CATEGORY_FILTERS.ALL]))
  },

  onShow() {
    this.setData(buildFoodPageData(
      getInitialRawFoods(),
      this.data.statusFilters,
      this.data.categoryFilters,
      this.data.categorySearchText
    ))
  },

  toggleStatusFilter(event) {
    const statusFilter = event.currentTarget.dataset.status || ''
    const statusFilters = toggleStatusFilterValue(this.data.statusFilters, statusFilter)

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      statusFilters,
      this.data.categoryFilters,
      this.data.categorySearchText
    ))
  },

  removeStatusFilter(event) {
    const statusFilter = event.currentTarget.dataset.status || ''
    const statusFilters = normalizeStatusFilters(
      this.data.statusFilters.filter((value) => value !== statusFilter)
    )

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      statusFilters,
      this.data.categoryFilters,
      this.data.categorySearchText
    ))
  },

  openCategoryPanel() {
    const categorySearchText = ''

    this.setData({
      showCategoryPanel: true,
      categorySearchText,
      categorySearchResults: filterCategorySearchResults(
        this.data.categoryFilterOptions,
        categorySearchText,
        this.data.categoryFilters,
        true
      )
    })
  },

  closeCategoryPanel() {
    this.setData({
      showCategoryPanel: false,
      categorySearchText: '',
      categorySearchResults: filterCategorySearchResults(
        this.data.categoryFilterOptions,
        '',
        this.data.categoryFilters,
        false
      )
    })
  },

  stopCategoryPanelTap() {},

  updateCategorySearch(event) {
    const categorySearchText = event.detail.value || ''

    this.setData({
      categorySearchText,
      categorySearchResults: filterCategorySearchResults(
        this.data.categoryFilterOptions,
        categorySearchText,
        this.data.categoryFilters,
        true
      )
    })
  },

  toggleCategoryFilter(event) {
    const categoryFilter = event.currentTarget.dataset.category || CATEGORY_FILTERS.ALL
    const categoryFilters = toggleCategoryFilterValue(
      this.data.categoryFilters,
      categoryFilter,
      this.data.categoryFilterOptions
    )
    const nextData = buildFoodPageData(
      this.data.rawFoods,
      this.data.statusFilters,
      categoryFilters,
      this.data.categorySearchText
    )

    this.setData({
      ...nextData,
      categorySearchResults: updateCategorySearchResultSelections(
        this.data.categorySearchResults,
        categoryFilters
      )
    })
  },

  selectAllCategoryFilters() {
    const categoryFilters = selectVisibleCategoryFilters(
      this.data.categoryFilters,
      this.data.categorySearchResults,
      this.data.categoryFilterOptions
    )

    this.setData({
      ...buildFoodPageData(
        this.data.rawFoods,
        this.data.statusFilters,
        categoryFilters,
        this.data.categorySearchText
      ),
      categorySearchResults: updateCategorySearchResultSelections(
        this.data.categorySearchResults,
        categoryFilters
      )
    })
  },

  resetCategoryFilters() {
    const categoryFilters = resetVisibleCategoryFilters(
      this.data.categoryFilters,
      this.data.categorySearchResults,
      this.data.categoryFilterOptions
    )

    this.setData({
      ...buildFoodPageData(
        this.data.rawFoods,
        this.data.statusFilters,
        categoryFilters,
        this.data.categorySearchText
      ),
      categorySearchResults: updateCategorySearchResultSelections(
        this.data.categorySearchResults,
        categoryFilters
      )
    })
  },

  removeCategoryFilter(event) {
    const categoryFilter = event.currentTarget.dataset.category || CATEGORY_FILTERS.ALL
    const categoryFilters = removeCategoryFilterValue(
      this.data.categoryFilters,
      categoryFilter,
      this.data.categoryFilterOptions
    )

    this.setData(buildFoodPageData(
      this.data.rawFoods,
      this.data.statusFilters,
      categoryFilters,
      this.data.categorySearchText
    ))
  },

  tapCategoryFilterChip(event) {
    const categoryFilter = event.currentTarget.dataset.category || CATEGORY_FILTERS.ALL

    if (categoryFilter === CATEGORY_FILTERS.ALL || categoryFilter === 'none') {
      this.openCategoryPanel()
      return
    }

    this.removeCategoryFilter(event)
  },

  scrollToTop() {
    if (typeof wx === 'undefined' || typeof wx.pageScrollTo !== 'function') {
      return
    }

    wx.pageScrollTo({
      scrollTop: 0,
      duration: 260
    })
  },

  toggleActiveFilterCollapse() {
    this.setData({
      activeFilterCollapsed: !this.data.activeFilterCollapsed
    })
  },

  onPageScroll(event) {
    const scrollTop = event && typeof event.scrollTop === 'number' ? event.scrollTop : 0
    const showBackToTop = scrollTop > 360
    const showFilterBarCollapse = scrollTop > 260
    const nextData = {}

    if (this.data.showBackToTop !== showBackToTop) {
      nextData.showBackToTop = showBackToTop
    }

    if (this.data.showFilterBarCollapse !== showFilterBarCollapse) {
      nextData.showFilterBarCollapse = showFilterBarCollapse
    }

    if (!showFilterBarCollapse && this.data.activeFilterCollapsed) {
      nextData.activeFilterCollapsed = false
    }

    if (Object.keys(nextData).length) {
      this.setData(nextData)
    }
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
      ...buildFoodPageData(rawFoods, this.data.statusFilters, this.data.categoryFilters),
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
