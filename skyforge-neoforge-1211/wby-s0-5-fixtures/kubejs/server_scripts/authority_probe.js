// WBY S0.5A no-op pack-authority fixture.
// Proves KubeJS, KubeJS Create, and LootJS event surfaces load without changing gameplay data.
ServerEvents.loaded(event => {
  console.info('WBY S0.5A KUBEJS SERVER FIXTURE PASS')
})

ServerEvents.recipes(event => {
  if (event.recipes.create == null) {
    throw new Error('WBY S0.5A KUBEJS CREATE FIXTURE FAIL: Create recipe namespace unavailable')
  }
  console.info('WBY S0.5A KUBEJS CREATE FIXTURE PASS')
})

LootJS.modifiers(event => {
  console.info('WBY S0.5A LOOTJS FIXTURE PASS')
})
