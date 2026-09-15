ALTER TABLE refresh_tokens
  ADD COLUMN email VARCHAR(320) NOT NULL DEFAULT 'unknown@invalid.local';

ALTER TABLE refresh_tokens
  ALTER COLUMN email DROP DEFAULT;
