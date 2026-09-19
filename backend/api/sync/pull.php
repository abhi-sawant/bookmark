<?php
/**
 * GET /api/sync/pull.php?since=<ms>&limit=<n>  (auth required)
 */

require_once __DIR__ . '/../../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts GET.', 405);
}

$auth = require_auth();
$userId = $auth['userId'];

$since = isset($_GET['since']) ? (int) $_GET['since'] : 0;
if ($since < 0) {
    $since = 0;
}

$limit = isset($_GET['limit']) ? (int) $_GET['limit'] : 500;
if ($limit <= 0) {
    $limit = 500;
}
if ($limit > 1000) {
    $limit = 1000;
}

$sinceDt = millis_to_datetime($since);
$pdo = db();

function fetch_page(PDO $pdo, string $table, int $userId, string $sinceDt, int $limit): array
{
    $stmt = $pdo->prepare(
        "SELECT * FROM `$table` WHERE user_id = :user_id AND updated_at >= :since
         ORDER BY updated_at ASC, id ASC
         LIMIT $limit"
    );
    $stmt->execute([':user_id' => $userId, ':since' => $sinceDt]);
    return $stmt->fetchAll();
}

$categoryRows = fetch_page($pdo, 'categories', $userId, $sinceDt, $limit);
$bookmarkRows = fetch_page($pdo, 'bookmarks', $userId, $sinceDt, $limit);

$categoryUpserts = [];
$categoryDeletes = [];
foreach ($categoryRows as $row) {
    if ($row['deleted_at'] !== null) {
        $categoryDeletes[] = [
            'id' => $row['id'],
            'deleted_at' => datetime_to_millis($row['deleted_at']),
        ];
    } else {
        $categoryUpserts[] = [
            'id' => $row['id'],
            'name' => $row['name'],
            'color_hex' => $row['color_hex'],
            'icon_key' => $row['icon_key'],
            // Cast to a real PHP bool so json_encode emits a JSON boolean
            // literal (true/false) -- the Android client's kotlinx.serialization
            // decoder rejects a numeric 0/1 for a Boolean-typed field.
            'is_default' => (bool) $row['is_default'],
            'created_at' => datetime_to_millis($row['created_at']),
            'updated_at' => datetime_to_millis($row['updated_at']),
        ];
    }
}

$bookmarkUpserts = [];
$bookmarkDeletes = [];
foreach ($bookmarkRows as $row) {
    if ($row['deleted_at'] !== null) {
        $bookmarkDeletes[] = [
            'id' => $row['id'],
            'deleted_at' => datetime_to_millis($row['deleted_at']),
        ];
    } else {
        $bookmarkUpserts[] = [
            'id' => $row['id'],
            'url' => $row['url'],
            'original_url' => $row['original_url'],
            'title' => $row['title'],
            'description' => $row['description'],
            'site_name' => $row['site_name'],
            'thumbnail_url' => $row['thumbnail_url'],
            'thumbnail_width' => $row['thumbnail_width'] !== null ? (int) $row['thumbnail_width'] : null,
            'thumbnail_height' => $row['thumbnail_height'] !== null ? (int) $row['thumbnail_height'] : null,
            'accent_color' => $row['accent_color'] !== null ? (int) $row['accent_color'] : null,
            // JSON array on the wire (matching Android's `List<String>`),
            // even though storage is a newline-joined TEXT column -- see the
            // matching comment in push.php's validate_bookmark_payload.
            'image_candidates' => $row['image_candidates'] !== null
                ? array_values(array_filter(explode("\n", $row['image_candidates']), fn ($v) => $v !== ''))
                : [],
            'category_id' => $row['category_id'],
            'manual_fields' => (int) $row['manual_fields'],
            // See the is_default comment above -- same JSON-boolean requirement.
            'is_pinned' => (bool) $row['is_pinned'],
            'created_at' => datetime_to_millis($row['created_at']),
            'updated_at' => datetime_to_millis($row['updated_at']),
        ];
    }
}

$hasMore = (count($categoryRows) === $limit) || (count($bookmarkRows) === $limit);

$maxUpdatedMs = $since;
foreach ($categoryRows as $row) {
    $maxUpdatedMs = max($maxUpdatedMs, datetime_to_millis($row['updated_at']));
}
foreach ($bookmarkRows as $row) {
    $maxUpdatedMs = max($maxUpdatedMs, datetime_to_millis($row['updated_at']));
}

json_ok([
    'has_more' => $hasMore,
    'next_since' => $maxUpdatedMs,
    'categories' => [
        'upserts' => $categoryUpserts,
        'deletes' => $categoryDeletes,
    ],
    'bookmarks' => [
        'upserts' => $bookmarkUpserts,
        'deletes' => $bookmarkDeletes,
    ],
]);
