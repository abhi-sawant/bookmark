<?php
/**
 * POST /api/forgot_password.php
 * Body: {email}
 *
 * Always responds 200 {"ok":true, "message": "..."} regardless of whether
 * the account exists, to avoid leaking which emails are registered. If the
 * account exists, a reset link is emailed via SMTP (vendored PHPMailer).
 */

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../vendor/PHPMailer/autoload.php';

use PHPMailer\PHPMailer\PHPMailer;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    json_error('VALIDATION_FAILED', 'Invalid JSON body.', 422);
}

$email = require_email($body['email'] ?? null);

// Rate limit by both email and IP, but only AFTER validating shape so
// malformed requests don't burn the limit. Always applied before any
// existence check so brute-forcing existence via timing is not amplified.
check_rate_limit('forgot_password', $email, 3, 3600);
check_rate_limit('forgot_password', $ip, 10, 3600);

$pdo = db();
$stmt = $pdo->prepare('SELECT id FROM users WHERE email = :email LIMIT 1');
$stmt->execute([':email' => $email]);
$user = $stmt->fetch();

if ($user !== false) {
    try {
        $userId = (int) $user['id'];
        $rawToken = bin2hex(random_bytes(32));
        $tokenHash = hash('sha256', $rawToken);
        $nowMs = (int) round(microtime(true) * 1000);
        $expiresDt = millis_to_datetime($nowMs + (3600 * 1000));
        $createdDt = millis_to_datetime($nowMs);

        $insert = $pdo->prepare(
            'INSERT INTO password_resets (user_id, token_hash, created_at, expires_at)
             VALUES (:user_id, :token_hash, :created_at, :expires_at)'
        );
        $insert->execute([
            ':user_id' => $userId,
            ':token_hash' => $tokenHash,
            ':created_at' => $createdDt,
            ':expires_at' => $expiresDt,
        ]);

        $resetLink = rtrim(APP_BASE_URL, '/') . '/api/reset_password.php?token=' . urlencode($rawToken);

        $mail = new PHPMailer(true);
        $mail->isSMTP();
        $mail->Host = SMTP_HOST;
        $mail->Port = SMTP_PORT;
        // PHPMailer's own default is 300s. A wrong host/port/blocked outbound
        // port would otherwise hang this entire request (and the client) for
        // up to five minutes instead of failing fast into the catch below.
        $mail->Timeout = 10;
        $mail->SMTPAuth = true;
        $mail->Username = SMTP_USERNAME;
        $mail->Password = SMTP_PASSWORD;
        $mail->SMTPSecure = SMTP_SECURE;
        $mail->setFrom(SMTP_FROM_EMAIL, SMTP_FROM_NAME);
        $mail->addAddress($email);
        $mail->Subject = 'Reset your Bookmark password';
        $mail->isHTML(true);
        $safeLink = htmlspecialchars($resetLink, ENT_QUOTES);
        $mail->Body = '<p>We received a request to reset your Bookmark account password.</p>'
            . '<p><a href="' . $safeLink . '">Click here to choose a new password</a></p>'
            . '<p>This link expires in 1 hour. If you did not request this, you can ignore this email.</p>';
        $mail->AltBody = "We received a request to reset your Bookmark account password.\n"
            . "Open this link to choose a new password (expires in 1 hour):\n" . $resetLink;

        $mail->send();
    } catch (Throwable $e) {
        // Never let a mail failure change the response shape or leak
        // whether sending succeeded -- just log server-side.
        error_log('[bookmark-api] forgot_password mail error: ' . $e->getMessage());
    }
}

json_ok(['ok' => true, 'message' => "If that email exists, we've sent a reset link."]);
