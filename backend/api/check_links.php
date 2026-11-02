<?php
/**
 * POST /api/check_links.php (auth required, JSON)
 * Body: {"urls": ["https://...", ...]}   (at most 10 per request)
 *
 * Asks each site whether the page still exists, on the caller's behalf (the web
 * app cannot, because browsers block cross-site reads). Returns one result per
 * URL, in the same order:
 *   {"url": "...", "status": "ok" | "broken" | "error", "http_status": 404 | null}
 *
 *   ok      the page answered 2xx/3xx
 *   broken  the page answered 404 or 410, or the host name does not resolve
 *   error   anything inconclusive (timeouts, 403/429/5xx, blocked address, ...):
 *           never reported as broken
 *
 * All outbound requests go through safe_fetch.php (SSRF protections).
 */

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../safe_fetch.php';

const CHECK_LINKS_MAX_URLS = 10;
// Shared hosting usually kills scripts after 30s; stop starting new checks before that.
const CHECK_LINKS_TIME_BUDGET_SECONDS = 22;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();
$userId = $auth['userId'];

// A request checks up to 10 links, so the allowance is lower than for previews.
$limit = defined('LINK_CHECK_REQUESTS_PER_HOUR') ? (int) LINK_CHECK_REQUESTS_PER_HOUR : 30;
check_rate_limit('metadata', 'user:' . $userId . ':links', $limit, 3600);

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body) || !isset($body['urls']) || !is_array($body['urls'])) {
    json_error('VALIDATION_FAILED', "Field 'urls' must be an array.", 422);
}
$urls = array_values($body['urls']);
if (count($urls) === 0 || count($urls) > CHECK_LINKS_MAX_URLS) {
    json_error('VALIDATION_FAILED', 'Send between 1 and ' . CHECK_LINKS_MAX_URLS . ' urls.', 422);
}
foreach ($urls as $url) {
    if (!is_string($url) || $url === '' || strlen($url) > 2048) {
        json_error('VALIDATION_FAILED', 'Each url must be a non-empty string up to 2048 characters.', 422);
    }
}

$started = microtime(true);
$results = [];
foreach ($urls as $url) {
    if (microtime(true) - $started > CHECK_LINKS_TIME_BUDGET_SECONDS) {
        $results[] = ['url' => $url, 'status' => 'error', 'http_status' => null];
        continue;
    }
    $results[] = check_links_one($url);
}

json_ok(['results' => $results]);

// ---------------------------------------------------------------------------

/** Only 404 and 410 mean "gone"; any other non-success is inconclusive. */
function check_links_status_for_code(int $code): string
{
    if ($code >= 200 && $code < 400) {
        return 'ok';
    }
    return ($code === 404 || $code === 410) ? 'broken' : 'error';
}

/** HEAD is refused by plenty of healthy servers, so those answers are re-checked with GET. */
function check_links_head_needs_get(int $code): bool
{
    return ($code < 200 || $code >= 400) && $code !== 404 && $code !== 410;
}

function check_links_one(string $url): array
{
    try {
        $res = safe_fetch($url, 'HEAD', 1024);
        if (check_links_head_needs_get($res['status'])) {
            $res = safe_fetch($url, 'GET', 1024);
        }
    } catch (SafeFetchException $e) {
        if ($e->getCode() === SAFE_FETCH_ERR_DNS) {
            return ['url' => $url, 'status' => 'broken', 'http_status' => null];
        }
        // The HEAD attempt failed outright (some servers drop it): try GET once.
        try {
            $res = safe_fetch($url, 'GET', 1024);
        } catch (SafeFetchException $e2) {
            $dead = $e2->getCode() === SAFE_FETCH_ERR_DNS;
            return ['url' => $url, 'status' => $dead ? 'broken' : 'error', 'http_status' => null];
        }
    }
    return [
        'url' => $url,
        'status' => check_links_status_for_code($res['status']),
        'http_status' => $res['status'] > 0 ? $res['status'] : null,
    ];
}
