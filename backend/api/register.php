<?php
/**
 * POST /api/register.php
 * Body: {email, password, device_name}
 */

require_once __DIR__ . '/../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
check_rate_limit('register', $ip, 5, 3600);

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    json_error('VALIDATION_FAILED', 'Invalid JSON body.', 422);
}

$email = require_email($body['email'] ?? null);
$password = require_string($body['password'] ?? null, 'password', 8, 255);
$deviceName = optional_string($body['device_name'] ?? null, 'device_name', 100) ?? 'Unknown device';

$pdo = db();

$check = $pdo->prepare('SELECT id FROM users WHERE email = :email LIMIT 1');
$check->execute([':email' => $email]);
if ($check->fetch() !== false) {
    json_error('EMAIL_TAKEN', 'An account with this email already exists.', 409);
}

$passwordHash = password_hash($password, PASSWORD_BCRYPT);
$nowMs = (int) round(microtime(true) * 1000);
$nowDt = millis_to_datetime($nowMs);

$pdo->beginTransaction();
try {
    $insertUser = $pdo->prepare(
        'INSERT INTO users (email, password_hash, created_at) VALUES (:email, :password_hash, :created_at)'
    );
    try {
        $insertUser->execute([
            ':email' => $email,
            ':password_hash' => $passwordHash,
            ':created_at' => $nowDt,
        ]);
    } catch (PDOException $e) {
        // Handles the narrow race where two requests for the same email
        // pass the earlier existence check concurrently.
        if ($e->getCode() === '23000') {
            $pdo->rollBack();
            json_error('EMAIL_TAKEN', 'An account with this email already exists.', 409);
        }
        throw $e;
    }
    $userId = (int) $pdo->lastInsertId();

    $tokenInfo = issue_token($userId, $deviceName);

    // Idempotently ensure the 'unsorted' category exists for this user.
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
], 201);
