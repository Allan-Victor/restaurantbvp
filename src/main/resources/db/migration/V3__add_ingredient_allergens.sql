CREATE TABLE INGREDIENT_ALLERGEN (
    ingredient_id UUID NOT NULL REFERENCES INGREDIENT(id),
    allergen TEXT NOT NULL,
    PRIMARY KEY (ingredient_id, allergen)
);
