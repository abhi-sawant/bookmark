<?php
/**
 * auth.php - token issuance, request authentication, and rate limiting.
 */

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/response.php';

/**
 * Create a new auth token row for a user/device.
 *
 * @return array{deviceId:int, token:string}
 */
function issue_token(int $userId, string $deviceName): array
{
    $token = bin2hex(random_bytes(32));
    $tokenHash = hash('sha256', $token);

    $stmt = db()->prepare(
        'INSERT INTO auth_tokens (user_id, device_name, token_hash, created_at)
         VALUES (:user_id, :device_name, :token_hash, :created_at)'
    );
    $stmt->execute([
        ':user_id' => $userId,
        ':device_name' => $deviceName,
        ':token_hash' => $tokenHash,
        ':created_at' => millis_to_datetime((int) round(microtime(true) * 1000)),
    ]);

    $deviceId = (int) db()->lastInsertId();

    return ['deviceId' => $deviceId, 'token' => $token];
}

/**
 * Extract the bearer token from the Authorization header, falling back to
 * X-Auth-Token in case a host strips the Authorization header.
 */
function extract_bearer_token(): ?string
{
    $authHeader = null;

    if (isset($_SERVER['HTTP_AUTHORIZATION'])) {
        $authHeader = $_SERVER['HTTP_AUTHORIZATION'];
    } elseif (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        // Common shared-hosting/CGI gotcha: Apache/PHP-CGI sometimes only
        // exposes the Authorization header under this redirected name.
        $authHeader = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    } elseif (function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        foreach ($headers as $name => $value) {
            if (strcasecmp($name, 'Authorization') === 0) {
                $authHeader = $value;
                break;
            }
        }
    }

    if ($authHeader !== null && preg_match('/^Bearer\s+(.+)$/i', trim($authHeader), $matches)) {
        return trim($matches[1]);
    }

    if (!empty($_SERVER['HTTP_X_AUTH_TOKEN'])) {
        return trim($_SERVER['HTTP_X_AUTH_TOKEN']);
    }

    return null;
}

/**
 * Require a valid, non-revoked auth token on the current request. On
 * success returns the associated user/device ids and touches
 * last_used_at. On failure, sends a 401 JSON error and terminates.
 *
 * @return array{userId:int, deviceId:int}
 */
function require_auth(): array
{
    $token = extract_bearer_token();

    if ($token === null || $token === '') {
        json_error('UNAUTHORIZED', 'Missing authentication token.', 401);
    }

    $tokenHash = hash('sha256', $token);

    $stmt = db()->prepare(
        'SELECT id, user_id, token_hash FROM auth_tokens
         WHERE token_hash = :token_hash AND revoked_at IS NULL
         LIMIT 1'
    );
    $stmt->execute([':token_hash' => $tokenHash]);
    $row = $stmt->fetch();

    if ($row === false || !hash_equals($row['token_hash'], $tokenHash)) {
        json_error('UNAUTHORIZED', 'Invalid or expired authentication token.', 401);
    }

    $nowDt = millis_to_datetime((int) round(microtime(true) * 1000));
    $update = db()->prepare('UPDATE auth_tokens SET last_used_at = :now WHERE id = :id');
    $update->execute([':now' => $nowDt, ':id' => $row['id']]);

    return ['userId' => (int) $row['user_id'], 'deviceId' => (int) $row['id']];
}

/**
 * Count rate_limit_events for $action/$identifier within the last
 * $windowSeconds, record this attempt, and 429 if the count (including this
 * attempt) exceeds $maxCount.
 */
function check_rate_limit(string $action, string $identifier, int $maxCount, int $windowSeconds): void
{
    // Guard against the identifier column's 255-char limit (e.g. an
    // ip+email composite key) so an unusually long value never causes a
    // DB error instead of a clean rate-limit check.
    if (strlen($identifier) > 255) {
        $identifier = hash('sha256', $identifier);
    }

    $pdo = db();
    $nowMs = (int) round(microtime(true) * 1000);
    $windowStart = millis_to_datetime($nowMs - ($windowSeconds * 1000));

    $stmt = $pdo->prepare(
        'SELECT COUNT(*) AS cnt FROM rate_limit_events
         WHERE action = :action AND identifier = :identifier AND created_at >= :window_start'
    );
    $stmt->execute([
        ':action' => $action,
        ':identifier' => $identifier,
        ':window_start' => $windowStart,
    ]);
    $count = (int) $stmt->fetch()['cnt'];

    // Record this attempt regardless of outcome so the window fills up
    // correctly even when callers hammer the endpoint.
    $insert = $pdo->prepare(
        'INSERT INTO rate_limit_events (action, identifier, created_at) VALUES (:action, :identifier, :now)'
    );
    $insert->execute([
        ':action' => $action,
        ':identifier' => $identifier,
        ':now' => millis_to_datetime($nowMs),
    ]);

    if ($count + 1 > $maxCount) {
        json_error('RATE_LIMITED', 'Too many requests. Please try again later.', 429);
    }
}
