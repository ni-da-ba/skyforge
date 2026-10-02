console.info("[SKYFORGE-S05] KUBEJS_FIXTURE_PARSED");

ServerEvents.recipes(event => {
  if (event.recipes.create == null) {
    throw new Error("[SKYFORGE-S05] KubeJS Create recipe bridge missing");
  }
  console.info("[SKYFORGE-S05] KUBEJS_CREATE_BRIDGE_PRESENT");
});
