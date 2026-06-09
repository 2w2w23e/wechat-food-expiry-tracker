const { isValidDateString } = require('./date')

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

module.exports = {
  sortFoodsByExpiryDate
}
