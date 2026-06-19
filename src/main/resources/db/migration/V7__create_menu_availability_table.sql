-- V7__create_menu_availability_table.sql
CREATE TABLE MENU_AVAILABILITY (
    menu_item_id UUID PRIMARY KEY REFERENCES MENU_ITEM(id),
    makeable_quantity BIGINT NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    override_state BOOLEAN NOT NULL DEFAULT FALSE
);
