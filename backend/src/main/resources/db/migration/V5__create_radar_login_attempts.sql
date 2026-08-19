-- Durable login throttling.
--
-- The previous throttle lived in a ConcurrentHashMap, so it was erased by every deploy and by every
-- Fly machine auto-stop (min_machines_running = 0). Persisting attempts makes lockout survive
-- restarts, and keying per client instead of per proxy address prevents one attacker from locking
-- the single legitimate user out of the whole application.
--
-- Only a client key, counters and timestamps are stored. Passwords are never written here.

CREATE TABLE IF NOT EXISTS radar_login_attempts (
    client_key        VARCHAR(200) PRIMARY KEY,
    failures          INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP NOT NULL,
    blocked_until     TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_radar_login_attempts_updated
    ON radar_login_attempts(updated_at);
