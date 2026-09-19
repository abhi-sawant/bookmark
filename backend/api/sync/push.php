<?php
/**
 * POST /api/sync/push.php (auth required)
 *
 * See backend spec for the full request/response contract. Processes all
 * category upserts, then all bookmark upserts, then category deletions,
 * then bookmark deletions -- all inside a single DB transaction.
 */

require_once __DIR__ . '/../../bootstrap.php';

const PUSH_MAX_ARRAY_SIZE = 500;
const ID_PATTERN = '/^[A-Za-z0-9\-]{1,36}$/';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();
$userId = $auth['userId'];

$body = json_decode(file_get_contents('php://input'), true);
if (!is_array($body)) {
    json_error('VALIDATION_FAILED', 'Invalid JSON body.', 422);
}

$categoriesIn = is_array($body['categories'] ?? null) ? $body['categories'] : [];
$bookmarksIn = is_array($body['bookmarks'] ?? null) ? $body['bookmarks'] : [];
$deletedCategoryIdsIn = is_array($body['deleted_category_ids'] ?? null) ? $body['deleted_category_ids'] : [];
$deletedBookmarkIdsIn = is_array($body['deleted_bookmark_ids'] ?? null) ? $body['deleted_bookmark_ids'] : [];

foreach ([
    'categories' => $categoriesIn,
    'bookmarks' => $bookmarksIn,
    'deleted_category_ids' => $deletedCategoryIdsIn,
    'deleted_bookmark_ids' => $deletedBookmarkIdsIn,
] as $fieldName => $arr) {
    if (count($arr) > PUSH_MAX_ARRAY_SIZE) {
        json_error('PAYLOAD_TOO_LARGE', "Field '$fieldName' exceeds the max of " . PUSH_MAX_ARRAY_SIZE . " entries.", 413);
    }
}

// ---------------------------------------------------------------------
// Field validation helpers. Return a cleaned assoc array, or null on
// validation failure.
// ---------------------------------------------------------------------

function valid_id($value): bool
{
    return is_string($value) && preg_match(ID_PATTERN, $value) === 1;
}

function valid_ms($value): bool
{
    return (is_int($value) || (is_string($value) && ctype_digit($value))) && (int) $value >= 0;
}

function validate_category_payload($raw): ?array
{
    if (!is_array($raw)) {
        return null;
    }
    if (!valid_id($raw['id'] ?? null)) {
        return null;
    }
    if (!is_string($raw['name'] ?? null)) {
        return null;
    }
    $name = trim($raw['name']);
    if ($name === '' || strlen($name) > 40) {
        return null;
    }
    if (!is_string($raw['color_hex'] ?? null) || !preg_match('/^#[0-9A-Fa-f]{6}$/', $raw['color_hex'])) {
        return null;
    }
    $iconKey = $raw['icon_key'] ?? null;
    if ($iconKey !== null) {
        if (!is_string($iconKey) || strlen($iconKey) > 40) {
            return null;
        }
    }
    if (!valid_ms($raw['created_at'] ?? null) || !valid_ms($raw['updated_at'] ?? null)) {
        return null;
    }

    return [
        'id' => $raw['id'],
        'name' => $name,
        'color_hex' => $raw['color_hex'],
        'icon_key' => $iconKey,
        'is_default' => coerce_bool_int($raw['is_default'] ?? 0),
        'created_at' => (int) $raw['created_at'],
        'updated_at' => (int) $raw['updated_at'],
    ];
}

function validate_bookmark_payload($raw): ?array
{
    if (!is_array($raw)) {
        return null;
    }
    if (!valid_id($raw['id'] ?? null)) {
        return null;
    }
    if (!is_string($raw['url'] ?? null) || strlen($raw['url']) === 0 || strlen($raw['url']) > 2048) {
        return null;
    }
    if (!is_string($raw['original_url'] ?? null) || strlen($raw['original_url']) === 0 || strlen($raw['original_url']) > 2048) {
        return null;
    }
    if (!is_string($raw['title'] ?? null) || strlen($raw['title']) > 200) {
        return null;
    }

    $description = $raw['description'] ?? null;
    if ($description !== null && (!is_string($description) || strlen($description) > 500)) {
        return null;
    }
    $siteName = $raw['site_name'] ?? null;
    if ($siteName !== null && (!is_string($siteName) || strlen($siteName) > 60)) {
        return null;
    }
    $thumbnailUrl = $raw['thumbnail_url'] ?? null;
    if ($thumbnailUrl !== null && (!is_string($thumbnailUrl) || strlen($thumbnailUrl) > 500)) {
        return null;
    }
    $thumbnailWidth = $raw['thumbnail_width'] ?? null;
    if ($thumbnailWidth !== null && (!is_numeric($thumbnailWidth) || (int) $thumbnailWidth < 0 || (int) $thumbnailWidth > 65535)) {
        return null;
    }
    $thumbnailHeight = $raw['thumbnail_height'] ?? null;
    if ($thumbnailHeight !== null && (!is_numeric($thumbnailHeight) || (int) $thumbnailHeight < 0 || (int) $thumbnailHeight > 65535)) {
        return null;
    }
    $accentColor = $raw['accent_color'] ?? null;
    if ($accentColor !== null && (!is_numeric($accentColor) || (int) $accentColor < -2147483648 || (int) $accentColor > 2147483647)) {
        return null;
    }
    // The wire format is a JSON array of URL strings (matching the Android
    // client's `List<String>`), joined with newlines for TEXT storage --
    // URLs cannot contain a literal newline, so this round-trips cleanly.
    $imageCandidatesIn = $raw['image_candidates'] ?? [];
    if (!is_array($imageCandidatesIn)) {
        return null;
    }
    foreach ($imageCandidatesIn as $candidate) {
        if (!is_string($candidate)) {
            return null;
        }
    }
    $imageCandidatesJoined = implode("\n", $imageCandidatesIn);
    if (strlen($imageCandidatesJoined) > 20000) {
        return null;
    }
    $imageCandidates = $imageCandidatesJoined !== '' ? $imageCandidatesJoined : null;
    $categoryId = $raw['category_id'] ?? 'unsorted';
    if (!valid_id($categoryId)) {
        return null;
    }
    $manualFields = $raw['manual_fields'] ?? 0;
    if (!is_numeric($manualFields) || (int) $manualFields < 0 || (int) $manualFields > 255) {
        return null;
    }
    if (!valid_ms($raw['created_at'] ?? null) || !valid_ms($raw['updated_at'] ?? null)) {
        return null;
    }

    return [
        'id' => $raw['id'],
        'url' => $raw['url'],
        'original_url' => $raw['original_url'],
        'title' => $raw['title'],
        'description' => $description,
        'site_name' => $siteName,
        'thumbnail_url' => $thumbnailUrl,
        'thumbnail_width' => $thumbnailWidth !== null ? (int) $thumbnailWidth : null,
        'thumbnail_height' => $thumbnailHeight !== null ? (int) $thumbnailHeight : null,
        'accent_color' => $accentColor !== null ? (int) $accentColor : null,
        'image_candidates' => $imageCandidates,
        'category_id' => $categoryId,
        'manual_fields' => (int) $manualFields,
        'is_pinned' => coerce_bool_int($raw['is_pinned'] ?? 0),
        'created_at' => (int) $raw['created_at'],
        'updated_at' => (int) $raw['updated_at'],
    ];
}

// ---------------------------------------------------------------------
// Processing
// ---------------------------------------------------------------------

$pdo = db();
$pdo->beginTransaction();

try {
    $categoriesApplied = [];
    $categoriesRejected = [];
    $bookmarksApplied = [];
    $bookmarksRejected = [];

    // Every category id mentioned in this request's categories array,
    // regardless of whether its own upsert succeeded -- used below to
    // resolve bookmark category_id references within the same request.
    $categoryIdsInRequest = [];

    $selectCategory = $pdo->prepare('SELECT updated_at, deleted_at FROM categories WHERE user_id = :user_id AND id = :id LIMIT 1');
    $insertCategory = $pdo->prepare(
        'INSERT INTO categories (user_id, id, name, color_hex, icon_key, is_default, created_at, updated_at, deleted_at)
         VALUES (:user_id, :id, :name, :color_hex, :icon_key, :is_default, :created_at, :updated_at, NULL)'
    );
    $updateCategory = $pdo->prepare(
        'UPDATE categories SET name = :name, color_hex = :color_hex, icon_key = :icon_key,
            is_default = :is_default, updated_at = :updated_at
         WHERE user_id = :user_id AND id = :id'
    );

    // --- 1. Categories --------------------------------------------------
    foreach ($categoriesIn as $rawCategory) {
        $incomingId = is_array($rawCategory) ? ($rawCategory['id'] ?? null) : null;
        if (is_string($incomingId)) {
            $categoryIdsInRequest[$incomingId] = true;
        }

        $clean = validate_category_payload($rawCategory);
        if ($clean === null) {
            $categoriesRejected[] = ['id' => $incomingId, 'reason' => 'validation_failed'];
            continue;
        }

        $selectCategory->execute([':user_id' => $userId, ':id' => $clean['id']]);
        $existing = $selectCategory->fetch();

        if ($existing === false) {
            $insertCategory->execute([
                ':user_id' => $userId,
                ':id' => $clean['id'],
                ':name' => $clean['name'],
                ':color_hex' => $clean['color_hex'],
                ':icon_key' => $clean['icon_key'],
                ':is_default' => $clean['is_default'],
                ':created_at' => millis_to_datetime($clean['created_at']),
                ':updated_at' => millis_to_datetime($clean['updated_at']),
            ]);
            $categoriesApplied[] = $clean['id'];
            continue;
        }

        $existingUpdatedMs = datetime_to_millis($existing['updated_at']);
        if ($clean['updated_at'] > $existingUpdatedMs) {
            $updateCategory->execute([
                ':name' => $clean['name'],
                ':color_hex' => $clean['color_hex'],
                ':icon_key' => $clean['icon_key'],
                ':is_default' => $clean['is_default'],
                ':updated_at' => millis_to_datetime($clean['updated_at']),
                ':user_id' => $userId,
                ':id' => $clean['id'],
            ]);
            $categoriesApplied[] = $clean['id'];
        } else {
            $categoriesRejected[] = ['id' => $clean['id'], 'reason' => 'stale'];
        }
    }

    // --- 2. Bookmarks -----------------------------------------------------
    $selectBookmark = $pdo->prepare('SELECT updated_at, deleted_at FROM bookmarks WHERE user_id = :user_id AND id = :id LIMIT 1');
    $selectCategoryExists = $pdo->prepare(
        'SELECT 1 FROM categories WHERE user_id = :user_id AND id = :id AND deleted_at IS NULL LIMIT 1'
    );
    $insertBookmark = $pdo->prepare(
        'INSERT INTO bookmarks (
            user_id, id, url, original_url, title, description, site_name,
            thumbnail_url, thumbnail_width, thumbnail_height, accent_color,
            image_candidates, category_id, manual_fields, is_pinned,
            created_at, updated_at, deleted_at
         ) VALUES (
            :user_id, :id, :url, :original_url, :title, :description, :site_name,
            :thumbnail_url, :thumbnail_width, :thumbnail_height, :accent_color,
            :image_candidates, :category_id, :manual_fields, :is_pinned,
            :created_at, :updated_at, NULL
         )'
    );
    $updateBookmark = $pdo->prepare(
        'UPDATE bookmarks SET
            url = :url, original_url = :original_url, title = :title,
            description = :description, site_name = :site_name,
            thumbnail_url = :thumbnail_url, thumbnail_width = :thumbnail_width,
            thumbnail_height = :thumbnail_height, accent_color = :accent_color,
            image_candidates = :image_candidates, category_id = :category_id,
            manual_fields = :manual_fields, is_pinned = :is_pinned,
            updated_at = :updated_at
         WHERE user_id = :user_id AND id = :id'
    );

    foreach ($bookmarksIn as $rawBookmark) {
        $incomingId = is_array($rawBookmark) ? ($rawBookmark['id'] ?? null) : null;

        $clean = validate_bookmark_payload($rawBookmark);
        if ($clean === null) {
            $bookmarksRejected[] = ['id' => $incomingId, 'reason' => 'validation_failed'];
            continue;
        }

        // Resolve category_id: must exist in DB (not deleted) or have
        // appeared earlier in this same request's categories array.
        $categoryOk = isset($categoryIdsInRequest[$clean['category_id']]);
        if (!$categoryOk) {
            $selectCategoryExists->execute([':user_id' => $userId, ':id' => $clean['category_id']]);
            $categoryOk = $selectCategoryExists->fetch() !== false;
        }
        if (!$categoryOk) {
            $bookmarksRejected[] = ['id' => $clean['id'], 'reason' => 'unknown_category'];
            continue;
        }

        $selectBookmark->execute([':user_id' => $userId, ':id' => $clean['id']]);
        $existing = $selectBookmark->fetch();

        $params = [
            ':url' => $clean['url'],
            ':original_url' => $clean['original_url'],
            ':title' => $clean['title'],
            ':description' => $clean['description'],
            ':site_name' => $clean['site_name'],
            ':thumbnail_url' => $clean['thumbnail_url'],
            ':thumbnail_width' => $clean['thumbnail_width'],
            ':thumbnail_height' => $clean['thumbnail_height'],
            ':accent_color' => $clean['accent_color'],
            ':image_candidates' => $clean['image_candidates'],
            ':category_id' => $clean['category_id'],
            ':manual_fields' => $clean['manual_fields'],
            ':is_pinned' => $clean['is_pinned'],
        ];

        if ($existing === false) {
            $insertBookmark->execute($params + [
                ':user_id' => $userId,
                ':id' => $clean['id'],
                ':created_at' => millis_to_datetime($clean['created_at']),
                ':updated_at' => millis_to_datetime($clean['updated_at']),
            ]);
            $bookmarksApplied[] = $clean['id'];
            continue;
        }

        $existingUpdatedMs = datetime_to_millis($existing['updated_at']);
        if ($clean['updated_at'] > $existingUpdatedMs) {
            $updateBookmark->execute($params + [
                ':updated_at' => millis_to_datetime($clean['updated_at']),
                ':user_id' => $userId,
                ':id' => $clean['id'],
            ]);
            $bookmarksApplied[] = $clean['id'];
        } else {
            $bookmarksRejected[] = ['id' => $clean['id'], 'reason' => 'stale'];
        }
    }

    // --- 3. Deletions -------------------------------------------------
    function apply_deletion(PDO $pdo, string $table, int $userId, $entry, bool $protectUnsorted): array
    {
        $id = is_array($entry) ? ($entry['id'] ?? null) : null;
        $deletedAtMs = is_array($entry) ? ($entry['deleted_at'] ?? null) : null;

        if (!is_string($id) || !valid_ms($deletedAtMs)) {
            return ['id' => $id, 'status' => 'rejected', 'reason' => 'validation_failed'];
        }

        if ($protectUnsorted && $id === 'unsorted') {
            return ['id' => $id, 'status' => 'rejected', 'reason' => 'protected'];
        }

        $deletedAtDt = millis_to_datetime((int) $deletedAtMs);

        $select = $pdo->prepare("SELECT updated_at, deleted_at FROM `$table` WHERE user_id = :user_id AND id = :id LIMIT 1");
        $select->execute([':user_id' => $userId, ':id' => $id]);
        $existing = $select->fetch();

        if ($existing === false) {
            // Never existed -- nothing to do, treat as applied.
            return ['id' => $id, 'status' => 'applied'];
        }

        $update = $pdo->prepare(
            "UPDATE `$table` SET deleted_at = :deleted_at, updated_at = :updated_at
             WHERE user_id = :user_id AND id = :id AND updated_at < :deleted_at_cmp"
        );
        $update->execute([
            ':deleted_at' => $deletedAtDt,
            ':updated_at' => $deletedAtDt,
            ':user_id' => $userId,
            ':id' => $id,
            ':deleted_at_cmp' => $deletedAtDt,
        ]);

        if ($update->rowCount() > 0) {
            return ['id' => $id, 'status' => 'applied'];
        }

        if ($existing['deleted_at'] !== null) {
            // Already deleted (or a newer/equal delete already applied).
            return ['id' => $id, 'status' => 'applied'];
        }

        // Row exists, not yet deleted, but the timestamp check failed:
        // a newer server state supersedes this delete request.
        return ['id' => $id, 'status' => 'rejected', 'reason' => 'stale'];
    }

    foreach ($deletedCategoryIdsIn as $entry) {
        $result = apply_deletion($pdo, 'categories', $userId, $entry, true);
        if ($result['status'] === 'applied') {
            $categoriesApplied[] = $result['id'];
        } else {
            $categoriesRejected[] = ['id' => $result['id'], 'reason' => $result['reason']];
        }
    }

    foreach ($deletedBookmarkIdsIn as $entry) {
        $result = apply_deletion($pdo, 'bookmarks', $userId, $entry, false);
        if ($result['status'] === 'applied') {
            $bookmarksApplied[] = $result['id'];
        } else {
            $bookmarksRejected[] = ['id' => $result['id'], 'reason' => $result['reason']];
        }
    }

    $pdo->commit();
} catch (Throwable $e) {
    $pdo->rollBack();
    throw $e;
}

json_ok([
    'categories' => [
        'applied' => $categoriesApplied,
        'rejected' => $categoriesRejected,
    ],
    'bookmarks' => [
        'applied' => $bookmarksApplied,
        'rejected' => $bookmarksRejected,
    ],
]);
