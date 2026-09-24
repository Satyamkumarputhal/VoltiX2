-- V9: Make the 2-hour load forecast target timestamp explicit.
--
-- BACKGROUND / PROBLEM
-- --------------------
-- zone_hourly_aggregates is keyed by (tenant_id, zone_id, aggregated_hour),
-- where aggregated_hour is the SOURCE hour whose telemetry was aggregated into
-- total_kw_consumed / avg_voltage / avg_current_amp.
--
-- The 2-hour forecast (forecast_kw_2h) predicts load for aggregated_hour + 2h,
-- but that "+2h" offset lived only in scheduler Java code -- the schema stored
-- the predicted value on the SOURCE-hour row with no column stating which
-- future hour it is for. A consumer reading the row could not distinguish:
--     total_kw_consumed  = ACTUAL load at aggregated_hour
--     forecast_kw_2h     = PREDICTED load at aggregated_hour + 2h
-- i.e. the forecast target hour was implicit and ambiguous.
--
-- FIX
-- ---
-- Add an explicit forecast_target_hour column. Semantics become unambiguous:
--     forecast_kw_2h        = predicted load FOR forecast_target_hour
--     forecast_target_hour  = aggregated_hour + 2h  (the hour being predicted)
--     forecast_generated_at = wall-clock time the forecast row was written
--     aggregated_hour       = source hour of the aggregated actuals
--
-- This is additive only. No table is introduced, no existing column changes
-- meaning, and Model B / exact-lag / fallback behavior are untouched.

ALTER TABLE zone_hourly_aggregates
    ADD COLUMN forecast_target_hour TIMESTAMP WITH TIME ZONE;

-- Backfill existing rows that already carry a forecast: their target hour is,
-- by the established +2h contract, aggregated_hour + 2 hours.
UPDATE zone_hourly_aggregates
   SET forecast_target_hour = aggregated_hour + INTERVAL '2 hours'
 WHERE forecast_kw_2h IS NOT NULL
   AND forecast_target_hour IS NULL;
