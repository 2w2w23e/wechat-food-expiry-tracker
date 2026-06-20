const { isValidDateString } = require('./date')
const { EXPIRY_STATUS, getFoodExpiryStatus } = require('./foodStatus')

const ALL_CATEGORY_FILTER = 'all'
const ALL_STATUS_FILTER_VALUES = [
  EXPIRY_STATUS.EXPIRED,
  EXPIRY_STATUS.TODAY,
  EXPIRY_STATUS.SOON,
  EXPIRY_STATUS.NORMAL,
  EXPIRY_STATUS.UNKNOWN
]

function sortFoodsByExpiryDate(foods) {
  if (!Array.isArray(foods)) {
    return []
  }

  return foods
    .map((food, index) => ({
      food,
      index,
      expiryDate: food && typeof food === 'object' ? food.expiryDate : null
    }))
    .sort((left, right) => {
      const leftValid = isValidDateString(left.expiryDate)
      const rightValid = isValidDateString(right.expiryDate)

      if (leftValid && rightValid) {
        if (left.expiryDate === right.expiryDate) {
          return left.index - right.index
        }

        return left.expiryDate < right.expiryDate ? -1 : 1
      }

      if (leftValid) {
        return -1
      }

      if (rightValid) {
        return 1
      }

      return left.index - right.index
    })
    .map((entry) => entry.food)
}

function normalizeFilterArray(value) {
  if (Array.isArray(value)) {
    return value
  }

  if (typeof value === 'string' && value) {
    return [value]
  }

  return []
}

function normalizeStatusFilterValues(statusFilters) {
  const selectedMap = normalizeFilterArray(statusFilters).reduce((map, value) => {
    if (ALL_STATUS_FILTER_VALUES.includes(value)) {
      map[value] = true
    }

    return map
  }, {})
  const normalizedValues = ALL_STATUS_FILTER_VALUES.filter((value) => selectedMap[value])

  return normalizedValues.length ? normalizedValues : [...ALL_STATUS_FILTER_VALUES]
}

function normalizeCategoryFilterValues(categoryFilters) {
  const selectedValues = normalizeFilterArray(categoryFilters)

  if (!selectedValues.length || selectedValues.includes(ALL_CATEGORY_FILTER)) {
    return [ALL_CATEGORY_FILTER]
  }

  return selectedValues.filter((value, index) => (
    typeof value === 'string' &&
    value &&
    value !== ALL_CATEGORY_FILTER &&
    selectedValues.indexOf(value) === index
  ))
}

function isAllCategoryFilterSelected(categoryFilters) {
  return !categoryFilters.length || categoryFilters.includes(ALL_CATEGORY_FILTER)
}

function filterFoodsByStatusAndCategory(foods, options = {}) {
  if (!Array.isArray(foods)) {
    return []
  }

  const statusFilters = normalizeStatusFilterValues(options.statusFilters)
  const categoryFilters = normalizeCategoryFilterValues(options.categoryFilters)
  const allCategoriesSelected = isAllCategoryFilterSelected(categoryFilters)

  return foods.filter((food) => {
    if (!food || typeof food !== 'object') {
      return false
    }

    const matchesStatus = statusFilters.includes(getFoodExpiryStatus(food, options.statusOptions))
    const matchesCategory = allCategoriesSelected || categoryFilters.includes(food.category)

    return matchesStatus && matchesCategory
  })
}

function resolveInitialFoods(storedFoods) {
  return Array.isArray(storedFoods) ? storedFoods : []
}

function canLoadSampleFoods(rawFoods) {
  return Array.isArray(rawFoods) && rawFoods.length === 0
}

module.exports = {
  ALL_CATEGORY_FILTER,
  ALL_STATUS_FILTER_VALUES,
  canLoadSampleFoods,
  filterFoodsByStatusAndCategory,
  normalizeCategoryFilterValues,
  normalizeStatusFilterValues,
  resolveInitialFoods,
  sortFoodsByExpiryDate
}
