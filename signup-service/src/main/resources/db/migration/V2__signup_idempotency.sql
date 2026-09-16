CREATE TABLE signup_idempotency (
  idempotency_key VARCHAR(128) PRIMARY KEY,
  request_hash VARCHAR(64) NOT NULL,
  user_id UUID NOT NULL,
  email VARCHAR(320) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
