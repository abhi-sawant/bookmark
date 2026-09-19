<?php
/**
 * cron/prune_password_resets.php
 *
 * Deletes password_resets rows that are used or expired AND older than 7
 * days. Meant to be invoked by a cPanel Cron Job directly, e.g.:
 *   php /home/youruser/backend/cron/prune_password_resets.php
 *
 * Plain CLI script -- no HTTP auth needed/applicable.
 */

require_once __DIR__ . '/../db.php';
require_once __DIR__ . '/../response.php';

$nowDt = millis_to_datetime((int) round(microtime(true) * 1000));
$cutoffMs = (int) round(microtime(true) * 1000) - (7 * 24 * 3600 * 1000);
$cutoffDt = millis_to_datetime($cutoffMs);

$delete = db()->prepare(
    'DELETE FROM password_resets
     WHERE (used_at IS NOT NULL OR expires_at < :now) AND created_at < :cutoff'
);
$delete->execute([':now' => $nowDt, ':cutoff' => $cutoffDt]);

echo 'Deleted ' . $delete->rowCount() . " password_resets row(s).\n";
