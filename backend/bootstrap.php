<?php
/**
 * bootstrap.php - required at the top of every endpoint file.
 *
 * - Enforces HTTPS.
 * - Sets Content-Type: application/json (unless the endpoint defines
 *   BOOTSTRAP_HTML_MODE = true before including this file, e.g. the HTML
 *   branches of reset_password.php).
 * - Requires config/db/response/auth/validate.
 * - Installs a top-level exception/error handler that never leaks internals
 *   to the client.
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/response.php';
require_once __DIR__ . '/validate.php';
require_once __DIR__ . '/auth.php';

// -----------------------------------------------------------------------
// Enforce HTTPS
// -----------------------------------------------------------------------
$isHttps = (
    (!empty($_SERVER['HTTPS']) && strtolower($_SERVER['HTTPS']) !== 'off')
    || (!empty($_SERVER['HTTP_X_FORWARDED_PROTO']) && strtolower($_SERVER['HTTP_X_FORWARDED_PROTO']) === 'https')
);

$host = $_SERVER['HTTP_HOST'] ?? '';
$isLocalhost = (bool) preg_match('/^(localhost|127\.0\.0\.1)(:\d+)?$/i', $host);

if (!$isHttps) {
    if ($isLocalhost) {
        // Plain HTTP is fine for local development.
    } elseif (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'GET') {
        // Safe to redirect GET requests to HTTPS.
        $redirectUrl = 'https://' . $host . ($_SERVER['REQUEST_URI'] ?? '/');
        header('Location: ' . $redirectUrl, true, 301);
        exit;
    } else {
        // Do not silently redirect non-GET requests (would drop the body).
        http_response_code(400);
        if (defined('BOOTSTRAP_HTML_MODE') && BOOTSTRAP_HTML_MODE === true) {
            header('Content-Type: text/html; charset=utf-8');
            echo '<!DOCTYPE html><html><head><meta charset="utf-8"><title>HTTPS required</title></head>'
                . '<body><h1>HTTPS is required</h1><p>Please retry this request over HTTPS.</p></body></html>';
        } else {
            header('Content-Type: application/json');
            echo json_encode(['error' => ['code' => 'HTTPS_REQUIRED', 'message' => 'HTTPS is required.']]);
        }
        exit;
    }
}

// -----------------------------------------------------------------------
// Default response content type
// -----------------------------------------------------------------------
if (!defined('BOOTSTRAP_HTML_MODE') || BOOTSTRAP_HTML_MODE !== true) {
    header('Content-Type: application/json');
}

// -----------------------------------------------------------------------
// Global error handling - never leak internals to the client.
// -----------------------------------------------------------------------
set_exception_handler(function (Throwable $e) {
    error_log('[bookmark-api] Uncaught exception: ' . $e->getMessage() . "\n" . $e->getTraceAsString());

    if (defined('BOOTSTRAP_HTML_MODE') && BOOTSTRAP_HTML_MODE === true) {
        http_response_code(500);
        header('Content-Type: text/html; charset=utf-8');
        echo '<!DOCTYPE html><html><head><meta charset="utf-8"><title>Error</title></head>'
            . '<body><h1>Something went wrong</h1><p>Please try again later.</p></body></html>';
        exit;
    }

    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode(['error' => ['code' => 'INTERNAL_ERROR', 'message' => 'An internal error occurred.']]);
    exit;
});

set_error_handler(function ($severity, $message, $file, $line) {
    // Convert PHP warnings/notices into exceptions so they hit the handler
    // above instead of leaking into the response body.
    if (!(error_reporting() & $severity)) {
        return false;
    }
    throw new ErrorException($message, 0, $severity, $file, $line);
});
