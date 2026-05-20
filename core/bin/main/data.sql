DELETE FROM settlement_expected;
DELETE FROM payment_transaction;
DELETE FROM fee_policy;

INSERT INTO fee_policy (
  pg_company,
  merchant_id,
  payment_method,
  fee_type,
  fee_value,
  settlement_cycle_days,
  start_date,
  end_date,
  created_at,
  updated_at
) VALUES
  ('PG1', 'merchant1', 'CARD', 'RATE', 0.020600, 2, DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
  ('PG1', 'merchant1', 'EASY_PAY_CARD', 'RATE', 0.015000, 2, DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
  ('PG1', 'merchant1', 'EASY_PAY_ACCOUNT', 'RATE', 0.013000, 1, DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
  ('PG1', 'merchant1', 'BANK_TRANSFER', 'FIXED', 250.000000, 1, DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
  ('PG1', 'merchant2', 'CARD', 'RATE', 0.020000, 2, DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

INSERT INTO payment_transaction (
  transaction_id,
  pg_company,
  merchant_id,
  payment_method,
  transaction_type,
  amount,
  transaction_date,
  original_transaction_id,
  created_at
) VALUES
  ('tx-100', 'PG1', 'merchant1', 'CARD', 'APPROVAL', 100000, DATE '2026-03-10', NULL, CURRENT_TIMESTAMP()),
  ('tx-101', 'PG1', 'merchant1', 'CARD', 'CANCEL', 30000, DATE '2026-03-10', 'tx-100', CURRENT_TIMESTAMP()),
  ('tx-102', 'PG1', 'merchant1', 'BANK_TRANSFER', 'APPROVAL', 100000, DATE '2026-03-10', NULL, CURRENT_TIMESTAMP()),
  ('tx-103', 'PG1', 'merchant1', 'BANK_TRANSFER', 'CANCEL', 40000, DATE '2026-03-10', 'tx-102', CURRENT_TIMESTAMP()),
  ('tx-104', 'PG1', 'merchant1', 'BANK_TRANSFER', 'CANCEL', 60000, DATE '2026-03-11', 'tx-102', CURRENT_TIMESTAMP()),
  ('tx-200', 'PG1', 'merchant2', 'CARD', 'APPROVAL', 50000, DATE '2026-03-10', NULL, CURRENT_TIMESTAMP());
