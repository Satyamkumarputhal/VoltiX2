-- Seed baseline tenants and grid zones.
-- This data was previously inserted manually outside of Flyway at some point
-- in the project's history, which meant a full volume reset (docker compose
-- down -v && up -d) could never successfully replay migrations from scratch --
-- V3 (users) depends on tenant_id=1 existing, which nothing before this
-- migration actually created. Capturing it here makes the migration history
-- self-contained and reproducible.
INSERT INTO tenants (tenant_id, tenant_name, status)
VALUES
    (1, 'Test Tenant', 'ACTIVE'),
    (2, 'Tenant 2', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier)
VALUES
    (1, 1, 'Zone A', 1.00),
    (2, 2, 'Zone B', 1.00)
ON CONFLICT (zone_id) DO NOTHING;

-- Advance the BIGSERIAL sequences past the explicitly-inserted IDs above,
-- so future auto-generated inserts don't collide with these seeded rows.
SELECT setval('tenants_tenant_id_seq', (SELECT MAX(tenant_id) FROM tenants));
SELECT setval('grid_zones_zone_id_seq', (SELECT MAX(zone_id) FROM grid_zones));

