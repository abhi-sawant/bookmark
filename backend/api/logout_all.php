<?php
/**
 * POST /api/logout_all.php (auth required)
 * Revokes every non-revoked token belonging to the calling user.
 */

require_once __DIR__ . '/../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();

$nowDt = millis_to_datetime((int) round(microtime(true) * 1000));
$stmt = db()->prepare(
    'UPDATE auth_tokens SET revoked_at = :now WHERE user_id = :user_id AND revoked_at IS NULL'
);
$stmt->execute([':now' => $nowDt, ':user_id' => $auth['userId']]);

json_ok(['ok' => true, 'revoked_count' => $stmt->rowCount()]);
