<?php
/**
 * POST /api/metadata.php (auth required, JSON)
 * Body: {"url": "https://...", "bookmark_id": "optional"}
 *
 * Fetches a page on the caller's behalf (the web app cannot, because browsers
 * block cross-site reads) and returns its title, description, site name and
 * preview image. When bookmark_id is given and PHP's GD supports WebP, the
 * image is also downscaled and stored where thumbnails/upload.php keeps them,
 * and its URL and size are returned.
 *
 * All outbound requests go through safe_fetch.php (SSRF protections).
 */

require_once __DIR__ . '/../bootstrap.php';
require_once __DIR__ . '/../safe_fetch.php';

const METADATA_HTML_MAX_BYTES = 1048576;       // 1MB of HTML is plenty for <head>
const METADATA_IMAGE_MAX_BYTES = 3145728;      // 3MB, same cap as thumbnail uploads
const METADATA_IMAGE_MAX_PIXELS = 40000000;    // refuse decompression bombs
const METADATA_THUMBNAIL_WIDTH = 640;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();
$userId = $auth['userId'];

$limit = defined('METADATA_RATE_PER_HOUR') ? (int) METADATA_RATE_PER_HOUR : 60;
check_rate_limit('metadata', 'user:' . $userId, $limit, 3600);

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    json_error('VALIDATION_FAILED', 'Invalid JSON body.', 422);
}

$url = require_string($body['url'] ?? null, 'url', 1, 2048);
$bookmarkId = $body['bookmark_id'] ?? null;
if ($bookmarkId !== null && (!is_string($bookmarkId) || !preg_match('/^[A-Za-z0-9\-]{1,64}$/', $bookmarkId))) {
    json_error('VALIDATION_FAILED', 'bookmark_id must be a safe identifier.', 422);
}

try {
    $page = safe_fetch($url, 'GET', METADATA_HTML_MAX_BYTES);
} catch (SafeFetchException $e) {
    json_error('FETCH_FAILED', 'Could not fetch that page: ' . $e->getMessage(), 502);
}

if ($page['status'] >= 400 || $page['status'] === 0) {
    json_error('FETCH_FAILED', 'The page answered with HTTP ' . $page['status'] . '.', 502);
}

$meta = metadata_parse($page['body'], $page['headers']['content-type'] ?? '', $page['final_url']);

$result = [
    'title' => $meta['title'],
    'description' => $meta['description'],
    'site_name' => $meta['site_name'],
    'image_url' => $meta['image_url'],
    'final_url' => $page['final_url'],
    'thumbnail_url' => null,
    'thumbnail_width' => null,
    'thumbnail_height' => null,
];

if ($bookmarkId !== null && $meta['image_url'] !== null) {
    $thumb = metadata_store_thumbnail($meta['image_url'], (int) $userId, $bookmarkId);
    if ($thumb !== null) {
        $result['thumbnail_url'] = $thumb['url'];
        $result['thumbnail_width'] = $thumb['width'];
        $result['thumbnail_height'] = $thumb['height'];
    }
}

json_ok($result);

// ---------------------------------------------------------------------------

/** Truncates to $max characters (not bytes) and collapses whitespace; null when empty. */
function metadata_clean(?string $value, int $max): ?string
{
    if ($value === null) {
        return null;
    }
    $value = trim(preg_replace('/\s+/u', ' ', html_entity_decode($value, ENT_QUOTES | ENT_HTML5, 'UTF-8')) ?? '');
    if ($value === '') {
        return null;
    }
    return mb_substr($value, 0, $max, 'UTF-8');
}

/**
 * Pulls title, description, site name and image out of an HTML document:
 * Open Graph first, then Twitter cards, then the plain tags.
 *
 * @return array{title:?string, description:?string, site_name:?string, image_url:?string}
 */
function metadata_parse(string $html, string $contentType, string $baseUrl): array
{
    $empty = ['title' => null, 'description' => null, 'site_name' => null, 'image_url' => null];
    if (trim($html) === '') {
        return $empty;
    }

    // Convert to UTF-8: trust the HTTP charset, then a <meta charset>, else assume UTF-8.
    $charset = null;
    if (preg_match('/charset=([\w\-]+)/i', $contentType, $m)) {
        $charset = $m[1];
    } elseif (preg_match('/<meta[^>]+charset=["\']?([\w\-]+)/i', $html, $m)) {
        $charset = $m[1];
    }
    if ($charset !== null && strcasecmp($charset, 'utf-8') !== 0 && strcasecmp($charset, 'utf8') !== 0) {
        $converted = @mb_convert_encoding($html, 'UTF-8', $charset);
        if (is_string($converted)) {
            $html = $converted;
        }
    }

    $previous = libxml_use_internal_errors(true);
    $doc = new DOMDocument();
    $loaded = $doc->loadHTML('<?xml encoding="UTF-8">' . $html, LIBXML_NONET | LIBXML_NOWARNING | LIBXML_NOERROR);
    libxml_clear_errors();
    libxml_use_internal_errors($previous);
    if (!$loaded) {
        return $empty;
    }

    $props = [];
    foreach ($doc->getElementsByTagName('meta') as $tag) {
        $key = strtolower($tag->getAttribute('property') ?: $tag->getAttribute('name'));
        $content = $tag->getAttribute('content');
        if ($key !== '' && $content !== '' && !isset($props[$key])) {
            $props[$key] = $content;
        }
    }

    $docTitle = null;
    $titles = $doc->getElementsByTagName('title');
    if ($titles->length > 0) {
        $docTitle = $titles->item(0)->textContent;
    }

    $image = $props['og:image'] ?? $props['og:image:url'] ?? $props['twitter:image'] ?? $props['twitter:image:src'] ?? null;
    if ($image !== null) {
        $image = safe_fetch_absolute_url($baseUrl, trim($image));
        if (strlen($image) > 500 || !preg_match('#^https?://#i', $image)) {
            $image = null;
        }
    }

    return [
        'title' => metadata_clean($props['og:title'] ?? $props['twitter:title'] ?? $docTitle, 200),
        'description' => metadata_clean($props['og:description'] ?? $props['twitter:description'] ?? $props['description'] ?? null, 500),
        'site_name' => metadata_clean($props['og:site_name'] ?? null, 60),
        'image_url' => $image,
    ];
}

/**
 * Downloads $imageUrl, downscales it to a WebP and stores it at
 * uploads/thumbnails/{userId}/{bookmarkId}.webp. Returns null when GD/WebP is
 * unavailable or anything about the image is unusable -- the preview is
 * best-effort and the caller still gets the text fields.
 *
 * @return array{url:string, width:int, height:int}|null
 */
function metadata_store_thumbnail(string $imageUrl, int $userId, string $bookmarkId): ?array
{
    if (!extension_loaded('gd') || !function_exists('imagewebp')) {
        return null;
    }

    try {
        $image = safe_fetch($imageUrl, 'GET', METADATA_IMAGE_MAX_BYTES);
    } catch (SafeFetchException $e) {
        return null;
    }
    if ($image['status'] !== 200 || $image['body'] === '') {
        return null;
    }

    $info = @getimagesizefromstring($image['body']);
    if ($info === false || $info[0] < 1 || $info[1] < 1 || $info[0] * $info[1] > METADATA_IMAGE_MAX_PIXELS) {
        return null;
    }
    if (!in_array($info[2], [IMAGETYPE_JPEG, IMAGETYPE_PNG, IMAGETYPE_GIF, IMAGETYPE_WEBP], true)) {
        return null;
    }

    $source = @imagecreatefromstring($image['body']);
    if ($source === false) {
        return null;
    }

    $width = imagesx($source);
    $height = imagesy($source);
    if ($width > METADATA_THUMBNAIL_WIDTH) {
        $scaled = imagescale($source, METADATA_THUMBNAIL_WIDTH);
        if ($scaled !== false) {
            $source = $scaled;
            $width = imagesx($source);
            $height = imagesy($source);
        }
    }
    imagepalettetotruecolor($source);
    imagealphablending($source, true);
    imagesavealpha($source, true);

    $userDir = __DIR__ . '/../uploads/thumbnails/' . $userId;
    if (!is_dir($userDir) && !mkdir($userDir, 0755, true) && !is_dir($userDir)) {
        return null;
    }
    // $bookmarkId was validated against [A-Za-z0-9-]{1,64}, so it is safe in a path.
    $destPath = $userDir . '/' . $bookmarkId . '.webp';
    if (!imagewebp($source, $destPath, 80)) {
        return null;
    }
    chmod($destPath, 0644);

    return [
        'url' => rtrim(APP_BASE_URL, '/') . '/uploads/thumbnails/' . $userId . '/' . $bookmarkId . '.webp',
        'width' => $width,
        'height' => $height,
    ];
}
