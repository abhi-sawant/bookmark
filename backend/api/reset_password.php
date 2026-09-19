<?php
/**
 * GET  /api/reset_password.php?token=...
 *   -> renders a minimal HTML form (or an error page if the token is
 *      missing/invalid/expired/used).
 * POST /api/reset_password.php
 *   Form fields: token, new_password, confirm_password
 *   -> validates and updates the password, revoking all existing sessions.
 *
 * This endpoint renders HTML, not JSON.
 */

define('BOOTSTRAP_HTML_MODE', true);
require_once __DIR__ . '/../bootstrap.php';

const RESET_PASSWORD_MIN_LEN = 8;

function html_escape(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

function render_page(string $title, string $bodyHtml, ?int $status = null): void
{
    if ($status !== null) {
        http_response_code($status);
    } elseif (http_response_code() === false) {
        http_response_code(200);
    }
    echo '<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>' . html_escape($title) . '</title>
<style>
  :root { color-scheme: light dark; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    background: #f4f5f7;
    color: #1a1a1a;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    margin: 0;
    padding: 16px;
  }
  .card {
    background: #ffffff;
    border-radius: 12px;
    box-shadow: 0 2px 16px rgba(0,0,0,0.08);
    padding: 32px 28px;
    max-width: 380px;
    width: 100%;
  }
  h1 { font-size: 20px; margin: 0 0 16px; }
  p { font-size: 14px; line-height: 1.5; color: #444; }
  label { display: block; font-size: 13px; font-weight: 600; margin: 16px 0 6px; }
  input[type="password"] {
    width: 100%;
    box-sizing: border-box;
    padding: 10px 12px;
    font-size: 14px;
    border: 1px solid #ccc;
    border-radius: 8px;
  }
  button {
    margin-top: 20px;
    width: 100%;
    padding: 11px 12px;
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    background: #2f6feb;
    border: none;
    border-radius: 8px;
    cursor: pointer;
  }
  button:hover { background: #255cc4; }
  .error {
    background: #fdecea;
    color: #9c2b1f;
    border-radius: 8px;
    padding: 10px 12px;
    font-size: 13px;
    margin-bottom: 8px;
  }
</style>
</head>
<body>
<div class="card">
' . $bodyHtml . '
</div>
</body>
</html>';
    exit;
}

function render_form(string $token, ?string $error = null): void
{
    $errorHtml = $error !== null
        ? '<div class="error">' . html_escape($error) . '</div>'
        : '';

    render_page('Reset your password', '
<h1>Choose a new password</h1>
' . $errorHtml . '
<form method="post" action="">
  <input type="hidden" name="token" value="' . html_escape($token) . '">
  <label for="new_password">New password</label>
  <input type="password" id="new_password" name="new_password" minlength="8" required autofocus>
  <label for="confirm_password">Confirm password</label>
  <input type="password" id="confirm_password" name="confirm_password" minlength="8" required>
  <button type="submit">Reset password</button>
</form>
');
}

function render_error_page(string $message, int $status = 400): void
{
    render_page('Link no longer valid', '
<h1>This link is no longer valid</h1>
<p>' . html_escape($message) . '</p>
<p>Please request a new password reset from the app.</p>
', $status);
}

function render_success_page(): void
{
    render_page('Password updated', '
<h1>Password updated</h1>
<p>Your password has been changed. You have been signed out of all devices for security -- please sign in again with your new password.</p>
');
}

/**
 * Look up a live, valid password_resets row by raw token.
 * Returns the row (with user_id) or null.
 */
function lookup_valid_reset(string $rawToken): ?array
{
    if ($rawToken === '') {
        return null;
    }
    $tokenHash = hash('sha256', $rawToken);
    $pdo = db();
    $stmt = $pdo->prepare(
        'SELECT pr.id, pr.user_id, pr.token_hash, pr.expires_at, pr.used_at, u.id AS uid
         FROM password_resets pr
         JOIN users u ON u.id = pr.user_id
         WHERE pr.token_hash = :token_hash
         LIMIT 1'
    );
    $stmt->execute([':token_hash' => $tokenHash]);
    $row = $stmt->fetch();

    if ($row === false || !hash_equals($row['token_hash'], $tokenHash)) {
        return null;
    }
    if ($row['used_at'] !== null) {
        return null;
    }
    $nowMs = (int) round(microtime(true) * 1000);
    if (datetime_to_millis($row['expires_at']) < $nowMs) {
        return null;
    }

    return $row;
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method === 'GET') {
    $token = isset($_GET['token']) ? (string) $_GET['token'] : '';
    $reset = lookup_valid_reset($token);
    if ($reset === null) {
        render_error_page('This password reset link is missing, invalid, expired, or has already been used.');
    }
    render_form($token);
}

if ($method === 'POST') {
    $token = isset($_POST['token']) ? (string) $_POST['token'] : '';
    $newPassword = isset($_POST['new_password']) ? (string) $_POST['new_password'] : '';
    $confirmPassword = isset($_POST['confirm_password']) ? (string) $_POST['confirm_password'] : '';

    $reset = lookup_valid_reset($token);
    if ($reset === null) {
        render_error_page('This password reset link is missing, invalid, expired, or has already been used.');
    }

    if (strlen($newPassword) < RESET_PASSWORD_MIN_LEN) {
        render_form($token, 'Password must be at least ' . RESET_PASSWORD_MIN_LEN . ' characters.');
    }
    if ($newPassword !== $confirmPassword) {
        render_form($token, 'Passwords do not match.');
    }

    $pdo = db();
    $nowDt = millis_to_datetime((int) round(microtime(true) * 1000));

    $pdo->beginTransaction();
    try {
        $passwordHash = password_hash($newPassword, PASSWORD_BCRYPT);

        $updateUser = $pdo->prepare('UPDATE users SET password_hash = :hash WHERE id = :id');
        $updateUser->execute([':hash' => $passwordHash, ':id' => $reset['user_id']]);

        $markUsed = $pdo->prepare('UPDATE password_resets SET used_at = :now WHERE id = :id');
        $markUsed->execute([':now' => $nowDt, ':id' => $reset['id']]);

        $revokeTokens = $pdo->prepare(
            'UPDATE auth_tokens SET revoked_at = :now WHERE user_id = :user_id AND revoked_at IS NULL'
        );
        $revokeTokens->execute([':now' => $nowDt, ':user_id' => $reset['user_id']]);

        $pdo->commit();
    } catch (Throwable $e) {
        $pdo->rollBack();
        throw $e;
    }

    render_success_page();
}

render_error_page('Unsupported request method.', 405);
