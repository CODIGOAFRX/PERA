ALTER TABLE customer_profiles
    ADD CONSTRAINT uk_customer_profile_company_id UNIQUE (company_id, id);

ALTER TABLE products
    ADD CONSTRAINT uk_product_company_id UNIQUE (company_id, id);

ALTER TABLE price_lists
    ADD COLUMN valid_from DATE,
    ADD COLUMN valid_until DATE,
    ADD COLUMN priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN scope VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN customer_id UUID,
    ADD COLUMN product_nature_id UUID,
    ADD COLUMN product_supertype_id UUID,
    ADD COLUMN product_type_id UUID,
    ADD COLUMN product_group_id UUID,
    ADD COLUMN product_id UUID,
    ADD COLUMN parent_price_list_id UUID,
    ADD COLUMN general_surcharge_percentage NUMERIC(9,4),
    ADD COLUMN energy_surcharge_percentage NUMERIC(9,4),
    ADD COLUMN minimum_billing_amount NUMERIC(19,4),
    ADD COLUMN unit_multiple NUMERIC(19,6),
    ADD COLUMN minimum_per_piece NUMERIC(19,4);

UPDATE price_lists SET valid_from = DATE '1970-01-01' WHERE valid_from IS NULL;

ALTER TABLE price_lists
    ALTER COLUMN valid_from SET NOT NULL,
    ADD CONSTRAINT uk_price_list_company_id UNIQUE (company_id, id),
    ADD CONSTRAINT fk_price_list_customer_company
        FOREIGN KEY (company_id, customer_id) REFERENCES customer_profiles(company_id, id),
    ADD CONSTRAINT fk_price_list_nature_company
        FOREIGN KEY (company_id, product_nature_id) REFERENCES product_natures(company_id, id),
    ADD CONSTRAINT fk_price_list_supertype_company
        FOREIGN KEY (company_id, product_supertype_id) REFERENCES product_supertypes(company_id, id),
    ADD CONSTRAINT fk_price_list_type_company
        FOREIGN KEY (company_id, product_type_id) REFERENCES product_types(company_id, id),
    ADD CONSTRAINT fk_price_list_group_company
        FOREIGN KEY (company_id, product_group_id) REFERENCES product_groups(company_id, id),
    ADD CONSTRAINT fk_price_list_product_company
        FOREIGN KEY (company_id, product_id) REFERENCES products(company_id, id),
    ADD CONSTRAINT fk_price_list_parent_company
        FOREIGN KEY (company_id, parent_price_list_id) REFERENCES price_lists(company_id, id),
    ADD CONSTRAINT ck_price_list_currency CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_price_list_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    ADD CONSTRAINT ck_price_list_priority CHECK (priority >= 0),
    ADD CONSTRAINT ck_price_list_general_surcharge CHECK (
        general_surcharge_percentage IS NULL OR
        (general_surcharge_percentage >= 0 AND general_surcharge_percentage <= 100)),
    ADD CONSTRAINT ck_price_list_energy_surcharge CHECK (
        energy_surcharge_percentage IS NULL OR
        (energy_surcharge_percentage >= 0 AND energy_surcharge_percentage <= 100)),
    ADD CONSTRAINT ck_price_list_minimum_billing CHECK (
        minimum_billing_amount IS NULL OR minimum_billing_amount >= 0),
    ADD CONSTRAINT ck_price_list_unit_multiple CHECK (unit_multiple IS NULL OR unit_multiple > 0),
    ADD CONSTRAINT ck_price_list_minimum_piece CHECK (minimum_per_piece IS NULL OR minimum_per_piece >= 0),
    ADD CONSTRAINT ck_price_list_not_self_parent CHECK (parent_price_list_id IS NULL OR parent_price_list_id <> id),
    ADD CONSTRAINT ck_price_list_scope_target CHECK (
        (scope = 'GENERAL' AND customer_id IS NULL AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (scope = 'CUSTOMER' AND customer_id IS NOT NULL AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (scope = 'PRODUCT_NATURE' AND product_nature_id IS NOT NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (scope = 'PRODUCT_SUPERTYPE' AND product_nature_id IS NULL AND
            product_supertype_id IS NOT NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (scope = 'PRODUCT_TYPE' AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NOT NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (scope = 'PRODUCT_GROUP' AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NOT NULL AND
            product_id IS NULL)
        OR (scope = 'PRODUCT' AND product_nature_id IS NULL AND product_supertype_id IS NULL AND
            product_type_id IS NULL AND product_group_id IS NULL AND product_id IS NOT NULL)
    );

ALTER TABLE customer_profiles
    ADD CONSTRAINT fk_customer_price_list_company
        FOREIGN KEY (company_id, price_list_id) REFERENCES price_lists(company_id, id);

ALTER TABLE price_list_items
    DROP CONSTRAINT uk_price_list_product_date,
    ADD COLUMN customer_id UUID,
    ADD COLUMN discount_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    ADD COLUMN surcharge_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    ADD COLUMN priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT fk_price_list_item_list_company
        FOREIGN KEY (company_id, price_list_id) REFERENCES price_lists(company_id, id),
    ADD CONSTRAINT fk_price_list_item_product_company
        FOREIGN KEY (company_id, product_id) REFERENCES products(company_id, id),
    ADD CONSTRAINT fk_price_list_item_customer_company
        FOREIGN KEY (company_id, customer_id) REFERENCES customer_profiles(company_id, id),
    ADD CONSTRAINT ck_price_list_item_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    ADD CONSTRAINT ck_price_list_item_price CHECK (price >= 0),
    ADD CONSTRAINT ck_price_list_item_discount CHECK (
        discount_percentage >= 0 AND discount_percentage <= 100),
    ADD CONSTRAINT ck_price_list_item_surcharge CHECK (
        surcharge_percentage >= 0 AND surcharge_percentage <= 100),
    ADD CONSTRAINT ck_price_list_item_priority CHECK (priority >= 0);

CREATE UNIQUE INDEX uk_price_list_product_date_general
    ON price_list_items(company_id, price_list_id, product_id, priority, valid_from)
    WHERE customer_id IS NULL AND active;
CREATE UNIQUE INDEX uk_price_list_product_date_customer
    ON price_list_items(company_id, price_list_id, product_id, customer_id, priority, valid_from)
    WHERE customer_id IS NOT NULL AND active;

CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    price_list_id UUID NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    product_nature_id UUID,
    product_supertype_id UUID,
    product_type_id UUID,
    product_group_id UUID,
    product_id UUID,
    customer_id UUID,
    fixed_price NUMERIC(19,4),
    discount_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    surcharge_percentage NUMERIC(9,4) NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    valid_from DATE NOT NULL,
    valid_until DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pricing_rule_list_company
        FOREIGN KEY (company_id, price_list_id) REFERENCES price_lists(company_id, id),
    CONSTRAINT fk_pricing_rule_customer_company
        FOREIGN KEY (company_id, customer_id) REFERENCES customer_profiles(company_id, id),
    CONSTRAINT fk_pricing_rule_nature_company
        FOREIGN KEY (company_id, product_nature_id) REFERENCES product_natures(company_id, id),
    CONSTRAINT fk_pricing_rule_supertype_company
        FOREIGN KEY (company_id, product_supertype_id) REFERENCES product_supertypes(company_id, id),
    CONSTRAINT fk_pricing_rule_type_company
        FOREIGN KEY (company_id, product_type_id) REFERENCES product_types(company_id, id),
    CONSTRAINT fk_pricing_rule_group_company
        FOREIGN KEY (company_id, product_group_id) REFERENCES product_groups(company_id, id),
    CONSTRAINT fk_pricing_rule_product_company
        FOREIGN KEY (company_id, product_id) REFERENCES products(company_id, id),
    CONSTRAINT ck_pricing_rule_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_pricing_rule_price CHECK (fixed_price IS NULL OR fixed_price >= 0),
    CONSTRAINT ck_pricing_rule_discount CHECK (
        discount_percentage >= 0 AND discount_percentage <= 100),
    CONSTRAINT ck_pricing_rule_surcharge CHECK (
        surcharge_percentage >= 0 AND surcharge_percentage <= 100),
    CONSTRAINT ck_pricing_rule_priority CHECK (priority >= 0),
    CONSTRAINT ck_pricing_rule_effect CHECK (
        fixed_price IS NOT NULL OR discount_percentage > 0 OR surcharge_percentage > 0),
    CONSTRAINT ck_pricing_rule_target CHECK (
        (target_type = 'PRODUCT_NATURE' AND product_nature_id IS NOT NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (target_type = 'PRODUCT_SUPERTYPE' AND product_nature_id IS NULL AND
            product_supertype_id IS NOT NULL AND product_type_id IS NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (target_type = 'PRODUCT_TYPE' AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NOT NULL AND product_group_id IS NULL AND
            product_id IS NULL)
        OR (target_type = 'PRODUCT_GROUP' AND product_nature_id IS NULL AND
            product_supertype_id IS NULL AND product_type_id IS NULL AND product_group_id IS NOT NULL AND
            product_id IS NULL)
        OR (target_type = 'PRODUCT' AND product_nature_id IS NULL AND product_supertype_id IS NULL AND
            product_type_id IS NULL AND product_group_id IS NULL AND product_id IS NOT NULL)
    )
);

CREATE INDEX idx_price_lists_resolution
    ON price_lists(company_id, currency, active, valid_from, valid_until, priority DESC);
CREATE INDEX idx_price_lists_filters
    ON price_lists(company_id, scope, customer_id, product_nature_id, product_supertype_id, product_type_id);
CREATE INDEX idx_price_list_items_resolution
    ON price_list_items(company_id, price_list_id, product_id, customer_id, active, valid_from, valid_until);
CREATE INDEX idx_pricing_rules_resolution
    ON pricing_rules(company_id, price_list_id, target_type, customer_id, active, valid_from, valid_until, priority DESC);
