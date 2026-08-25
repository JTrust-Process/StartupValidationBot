UPDATE radar_companies
SET website_url = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE domain IS NULL
  AND website_url LIKE 'https://www.producthunt.com/r/%';
