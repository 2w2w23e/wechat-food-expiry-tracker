const assert = require('assert')

const {
  CATEGORY_OPTIONS,
  getCategoryLabel,
  normalizeCategoryValue,
  searchCategories
} = require('../utils/category')

function test(name, fn) {
  try {
    fn()
    console.log(`ok - ${name}`)
  } catch (error) {
    console.error(`not ok - ${name}`)
    throw error
  }
}

function labelsFor(searchText) {
  return searchCategories(searchText).map((category) => category.label)
}

test('category options include the V0 standard categories', () => {
  assert.strictEqual(CATEGORY_OPTIONS.length, 10)
  assert.deepStrictEqual(CATEGORY_OPTIONS.map((category) => category.value), [
    'dairy',
    'staple',
    'frozen',
    'beverage',
    'snack',
    'condiment',
    'produce',
    'meat_egg_seafood',
    'cooked',
    'other'
  ])
})

test('Chinese category search matches labels', () => {
  assert.strictEqual(labelsFor('乳').includes('乳制品'), true)
  assert.strictEqual(labelsFor('调').includes('调味品'), true)
})

test('pinyin search matches dairy without using internal value', () => {
  ;['r', 'ru', 'ruzhipin', 'rzp'].forEach((query) => {
    assert.strictEqual(labelsFor(query).includes('乳制品'), true, query)
  })

  assert.strictEqual(labelsFor('dairy').includes('乳制品'), false)
})

test('pinyin search matches beverage and does not confuse y with dairy', () => {
  ;['y', 'yi', 'yinpin', 'yp'].forEach((query) => {
    assert.strictEqual(labelsFor(query).includes('饮品'), true, query)
  })

  assert.strictEqual(labelsFor('y').includes('乳制品'), false)
})

test('pinyin initials match frozen and condiment', () => {
  assert.strictEqual(labelsFor('ldsp').includes('冷冻食品'), true)
  assert.strictEqual(labelsFor('twp').includes('调味品'), true)
})

test('internal category values are not searchable aliases', () => {
  assert.strictEqual(labelsFor('staple').includes('主食'), false)
  assert.strictEqual(labelsFor('frozen').includes('冷冻食品'), false)
})

test('historical Chinese category labels normalize to stable values', () => {
  assert.strictEqual(normalizeCategoryValue('乳制品'), 'dairy')
  assert.strictEqual(normalizeCategoryValue('主食'), 'staple')
  assert.strictEqual(normalizeCategoryValue('冷冻食品'), 'frozen')
  assert.strictEqual(getCategoryLabel('dairy'), '乳制品')
})

test('unknown historical categories are preserved for display', () => {
  assert.strictEqual(normalizeCategoryValue('自定义分类'), '自定义分类')
  assert.strictEqual(getCategoryLabel('自定义分类'), '自定义分类')
})
