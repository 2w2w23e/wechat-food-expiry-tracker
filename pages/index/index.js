const { mockFoods } = require('../../mock/foods')
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

function getDisplayText(map, value) {
  return map[value] || value || '未填写'
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

Page({
  data: {
    foods: []
  },

  onLoad() {
    const foods = sortFoodsByExpiryDate(mockFoods).map(formatFoodForDisplay)

    this.setData({ foods })
  }
})
