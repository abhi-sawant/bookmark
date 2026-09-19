<?php
/**
 * POST /api/login.php
 * Body: {email, password, device_name}
 */

require_once __DIR__ . '/../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    json_error('VALIDATION_FAILED', 'Invalid JSON body.', 422);
}

$email = require_email($body['email'] ?? null);
$password = require_string($body['password'] ?? null, 'password', 1, 255);
$deviceName = optional_string($body['device_name'] ?? null, 'device_name', 100) ?? 'Unknown device';

// Rate limit by ip+email combo and by IP alone.
check_rate_limit('login', $ip . '|' . $email, 10, 900);
check_rate_limit('login', $ip, 30, 3600);

$pdo = db();

$stmt = $pdo->prepare('SELECT id, password_hash FROM users WHERE email = :email LIMIT 1');
$stmt->execute([':email' => $email]);
$user = $stmt->fetch();

if ($user === false || !password_verify($password, $user['password_hash'])) {
    json_error('INVALID_CREDENTIALS', 'Invalid email or password.', 401);
}

$userId = (int) $user['id'];
$nowDt = millis_to_datetime((int) round(microtime(true) * 1000));

$pdo->beginTransaction();
try {
    $tokenInfo = issue_token($userId, $deviceName);

    $upsertUnsorted = $pdo->prepare(
        "INSERT INTO categories (user_id, id, name, color_hex, icon_key, is_default, created_at, updated_at)
         VALUES (:user_id, 'unsorted', 'Unsorted', '#9E9E9E', NULL, 0, :created_at, :updated_at)
         ON DUPLICATE KEY UPDATE id = id"
    );
    $upsertUnsorted->execute([':user_id' => $userId, ':created_at' => $nowDt, ':updated_at' => $nowDt]);

    $pdo->commit();
} catch (Throwable $e) {
    $pdo->rollBack();
    throw $e;
}

json_ok([
    'user_id' => $userId,
    'device_id' => $tokenInfo['deviceId'],
    'token' => $tokenInfo['token'],
], 200);
