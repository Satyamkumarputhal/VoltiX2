-- Seed a small set of smart meters for the demo simulation endpoint.
-- These are referenced by DemoSimulationController (SM-0 through SM-4).
INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status)
VALUES
    ('SM-0', 1, 1, 'SN-DEMO-000', 'ACTIVE'),
    ('SM-1', 1, 1, 'SN-DEMO-001', 'ACTIVE'),
    ('SM-2', 1, 1, 'SN-DEMO-002', 'ACTIVE'),
    ('SM-3', 1, 1, 'SN-DEMO-003', 'ACTIVE'),
    ('SM-4', 1, 1, 'SN-DEMO-004', 'ACTIVE')
ON CONFLICT (meter_id) DO NOTHING;
