<?php
/**
 * GET /api/devices.php (auth required)
 *   -> list non-revoked tokens/devices for the calling user.
 * POST /api/devices.php?action=revoke  Body: {device_id} (auth required)
 *   -> revoke that device's token if it belongs to the caller.
 */

require_once __DIR__ . '/../bootstrap.php';

$auth = require_auth();
$method = $_SERVER['REQUEST_METHOD'] ?? '';

if ($method === 'GET') {
    $stmt = db()->prepare(
        'SELECT id, device_name, created_at, last_used_at
         FROM auth_tokens
         WHERE user_id = :user_id AND revoked_at IS NULL
         ORDER BY created_at ASC'
    );
    $stmt->execute([':user_id' => $auth['userId']]);
    $rows = $stmt->fetchAll();

    $devices = array_map(function ($row) use ($auth) {
        return [
            'device_id' => (int) $row['id'],
            'device_name' => $row['device_name'],
            'created_at' => datetime_to_millis($row['created_at']),
            'last_used_at' => $row['last_used_at'] !== null ? datetime_to_millis($row['last_used_at']) : null,
            'is_current' => ((int) $row['id']) === $auth['deviceId'],
        ];
    }, $rows);

    json_ok($devices);
}

if ($method === 'POST') {
    $action = $_GET['action'] ?? '';
    if ($action !== 'revoke') {
        json_error('VALIDATION_FAILED', 'Unknown or missing action.', 422);
    }

    $body = json_decode(file_get_contents('php://input'), true);
    if (!is_array($body) || !isset($body['device_id'])) {
        json_error('VALIDATION_FAILED', 'device_id is required.', 422);
    }
    $deviceId = require_in_range($body['device_id'], 'device_id', 1, PHP_INT_MAX);

    $nowDt = millis_to_datetime((int) round(microtime(true) * 1000));
    $stmt = db()->prepare(
        'UPDATE auth_tokens SET revoked_at = :now
         WHERE id = :id AND user_id = :user_id AND revoked_at IS NULL'
    );
    $stmt->execute([':now' => $nowDt, ':id' => $deviceId, ':user_id' => $auth['userId']]);

    if ($stmt->rowCount() === 0) {
        json_error('NOT_FOUND', 'Device not found.', 404);
    }

    json_ok(['ok' => true]);
}

json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts GET or POST.', 405);
