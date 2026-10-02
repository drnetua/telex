ALTER TABLE user_entities
    ALTER COLUMN display_name TYPE VARCHAR(200) USING left(display_name, 200);
