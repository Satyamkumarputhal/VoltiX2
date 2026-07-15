INSERT INTO tenants (tenant_id, tenant_name, status) 
VALUES (1, 'Load Test Tenant', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO grid_zones (zone_id, tenant_id, zone_name, risk_multiplier) 
VALUES (1, 1, 'Load Test Zone 1', 1.0)
ON CONFLICT (zone_id) DO NOTHING;

-- Generate 1000 smart meters for load testing
INSERT INTO smart_meters (meter_id, tenant_id, zone_id, serial_number, status)
SELECT 
    'SM-' || i, 
    1, 
    1, 
    'SN-' || i, 
    'ACTIVE'
FROM generate_series(0, 999) as i
ON CONFLICT (meter_id) DO NOTHING;
