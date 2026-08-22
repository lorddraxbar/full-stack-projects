-- V11: Service categories become first-class records (admin-managed) and
-- services gain a deactivated_at timestamp for the 7-day hard-delete window.

CREATE TABLE service_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    icon VARCHAR(255) NOT NULL DEFAULT 'fa-solid fa-briefcase',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE services ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP;
ALTER TABLE services ADD COLUMN IF NOT EXISTS category_id BIGINT;

-- Existing category values become records, in display order.
INSERT INTO service_categories (name, icon, sort_order)
SELECT v.name, v.icon, v.ord
FROM (
    VALUES
        ('ECC', 'fa-solid fa-leaf', 1),
        ('CNC', 'fa-solid fa-circle-check', 2),
        ('Other Services', 'fa-solid fa-toolbox', 3)
) AS v(name, icon, ord)
WHERE NOT EXISTS (SELECT 1 FROM service_categories sc WHERE sc.name = v.name);

-- Backfill the relationship (null only if a service has no category).
UPDATE services s
SET category_id = sc.id
FROM service_categories sc
WHERE s.category_id IS NULL
  AND sc.name = s.category;

ALTER TABLE services
    ADD CONSTRAINT fk_services_category FOREIGN KEY (category_id) REFERENCES service_categories (id);
