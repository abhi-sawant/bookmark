<?php
/**
 * cron/prune_rate_limits.php
 *
 * Deletes rate_limit_events rows older than 7 days. Meant to be invoked by
 * a cPanel Cron Job directly, e.g.:
 *   php /home/youruser/backend/cron/prune_rate_limits.php
 *
 * Plain CLI script -- no HTTP auth needed/applicable.
 */

require_once __DIR__ . '/../db.php';
require_once __DIR__ . '/../response.php';

$cutoffMs = (int) round(microtime(true) * 1000) - (7 * 24 * 3600 * 1000);
$cutoffDt = millis_to_datetime($cutoffMs);

$stmt = db()->prepare('DELETE FROM rate_limit_events WHERE created_at < :cutoff');
$stmt->execute([':cutoff' => $cutoffDt]);

echo 'Deleted ' . $stmt->rowCount() . " rate_limit_events row(s) older than 7 days.\n";
