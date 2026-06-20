const CATEGORY_OPTIONS = [
  { value: 'dairy', label: '乳制品', pinyin: 'ruzhipin', initials: 'rzp' },
  { value: 'staple', label: '主食', pinyin: 'zhushi', initials: 'zs' },
  { value: 'frozen', label: '冷冻食品', pinyin: 'lengdongshipin', initials: 'ldsp' },
  { value: 'beverage', label: '饮品', pinyin: 'yinpin', initials: 'yp' },
  { value: 'snack', label: '零食', pinyin: 'lingshi', initials: 'ls' },
  { value: 'condiment', label: '调味品', pinyin: 'tiaoweipin', initials: 'twp' },
  { value: 'produce', label: '蔬果', pinyin: 'shuguo', initials: 'sg' },
  { value: 'meat_egg_seafood', label: '肉蛋水产', pinyin: 'roudanshuichan', initials: 'rdsc' },
  { value: 'cooked', label: '熟食/剩菜', pinyin: 'shushishengcai', initials: 'sssc' },
  { value: 'other', label: '其他', pinyin: 'qita', initials: 'qt' }
]

const PLACEHOLDER_TEXT = '未填写'

const CATEGORY_BY_VALUE = CATEGORY_OPTIONS.reduce((map, category) => {
  map[category.value] = category
  return map
}, {})

const CATEGORY_BY_LABEL = CATEGORY_OPTIONS.reduce((map, category) => {
  map[category.label] = category
  return map
}, {})

const CATEGORY_TEXT = CATEGORY_OPTIONS.reduce((map, category) => {
  map[category.value] = category.label
  return map
}, {})

function normalizeText(value) {
  if (typeof value !== 'string') {
    return ''
  }

  const text = value.trim()
  return text === PLACEHOLDER_TEXT ? '' : text
}

function normalizeSearchText(value) {
  return normalizeText(value).toLowerCase().replace(/\s+/g, '')
}

function hasChineseText(value) {
  return /[\u4e00-\u9fff]/.test(value)
}

function normalizeCategoryValue(value) {
  const text = normalizeText(value)

  if (!text) {
    return ''
  }

  if (CATEGORY_BY_VALUE[text]) {
    return text
  }

  if (CATEGORY_BY_LABEL[text]) {
    return CATEGORY_BY_LABEL[text].value
  }

  return text
}

function getCategoryLabel(value) {
  const normalizedValue = normalizeCategoryValue(value)

  if (!normalizedValue) {
    return ''
  }

  return CATEGORY_TEXT[normalizedValue] || normalizedValue
}

function getCategoryOption(value) {
  const normalizedValue = normalizeCategoryValue(value)

  if (!normalizedValue) {
    return null
  }

  if (CATEGORY_BY_VALUE[normalizedValue]) {
    return CATEGORY_BY_VALUE[normalizedValue]
  }

  return {
    value: normalizedValue,
    label: normalizedValue,
    pinyin: '',
    initials: ''
  }
}

function matchesCategorySearch(category, searchText) {
  const query = normalizeSearchText(searchText)

  if (!query) {
    return true
  }

  if (!category || typeof category !== 'object') {
    return false
  }

  const label = normalizeSearchText(category.label)

  if (hasChineseText(query)) {
    return label.includes(query)
  }

  const pinyin = normalizeSearchText(category.pinyin)
  const initials = normalizeSearchText(category.initials)

  return pinyin.startsWith(query) || initials.startsWith(query)
}

function searchCategories(searchText, categories = CATEGORY_OPTIONS) {
  return categories.filter((category) => matchesCategorySearch(category, searchText))
}

module.exports = {
  CATEGORY_OPTIONS,
  CATEGORY_TEXT,
  getCategoryLabel,
  getCategoryOption,
  matchesCategorySearch,
  normalizeCategoryValue,
  searchCategories
}
