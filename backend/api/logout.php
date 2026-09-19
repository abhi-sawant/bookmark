<?php
/**
 * POST /api/logout.php (auth required)
 * Revokes only the calling token's row.
 */

require_once __DIR__ . '/../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();

$nowDt = millis_to_datetime((int) round(microtime(true) * 1000));
$stmt = db()->prepare('UPDATE auth_tokens SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL');
$stmt->execute([':now' => $nowDt, ':id' => $auth['deviceId']]);

json_ok(['ok' => true]);
