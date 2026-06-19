CREATE TABLE MENU_ITEM (
    id UUID PRIMARY KEY,
    venue_id TEXT NOT NULL,
    name TEXT NOT NULL,
    price_minor BIGINT NOT NULL,
    service_period TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE RECIPE (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL UNIQUE REFERENCES MENU_ITEM(id),
    yield_qty INT NOT NULL,
    rolled_up_cost_minor BIGINT
);

CREATE TABLE INGREDIENT (
    id UUID PRIMARY KEY,
    venue_id TEXT NOT NULL,
    name TEXT NOT NULL,
    base_unit TEXT NOT NULL,
    unit_cost_minor BIGINT NOT NULL
);

CREATE TABLE RECIPE_COMPONENT (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES RECIPE(id),
    ingredient_id UUID REFERENCES INGREDIENT(id),
    sub_recipe_id UUID REFERENCES RECIPE(id),
    required_qty_minor BIGINT NOT NULL,
    unit TEXT NOT NULL,
    CONSTRAINT chk_component_source CHECK (
        (ingredient_id IS NOT NULL AND sub_recipe_id IS NULL) OR
        (ingredient_id IS NULL AND sub_recipe_id IS NOT NULL)
    )
);

CREATE TABLE STOCK_ITEM (
    id UUID PRIMARY KEY,
    venue_id TEXT NOT NULL,
    ingredient_id UUID NOT NULL UNIQUE REFERENCES INGREDIENT(id),
    on_hand_minor BIGINT NOT NULL DEFAULT 0,
    par_level_minor BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE STOCK_MOVEMENT (
    id UUID PRIMARY KEY,
    venue_id TEXT NOT NULL,
    stock_item_id UUID NOT NULL REFERENCES STOCK_ITEM(id),
    movement_type TEXT NOT NULL,
    qty_minor BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    source_event UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_movement_idempotency UNIQUE (venue_id, stock_item_id, idempotency_key)
);

CREATE TABLE RECIPE_ALLERGEN (
    recipe_id UUID NOT NULL REFERENCES RECIPE(id),
    allergen TEXT NOT NULL,
    PRIMARY KEY (recipe_id, allergen)
);
