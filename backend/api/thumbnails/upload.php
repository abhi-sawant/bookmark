<?php
/**
 * POST /api/thumbnails/upload.php (auth required, multipart/form-data)
 * Fields: bookmark_id (text), file (the image)
 */

require_once __DIR__ . '/../../bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_error('METHOD_NOT_ALLOWED', 'This endpoint only accepts POST.', 405);
}

$auth = require_auth();
$userId = $auth['userId'];

const MAX_THUMBNAIL_BYTES = 3 * 1024 * 1024; // 3MB

$bookmarkId = $_POST['bookmark_id'] ?? null;
if (!is_string($bookmarkId) || !preg_match('/^[A-Za-z0-9\-]{1,64}$/', $bookmarkId)) {
    json_error('VALIDATION_FAILED', 'bookmark_id must be a safe identifier.', 422);
}

if (!isset($_FILES['file']) || !is_array($_FILES['file'])) {
    json_error('VALIDATION_FAILED', 'A file upload named "file" is required.', 422);
}

$file = $_FILES['file'];

if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    if ($file['error'] === UPLOAD_ERR_INI_SIZE || $file['error'] === UPLOAD_ERR_FORM_SIZE) {
        json_error('PAYLOAD_TOO_LARGE', 'Uploaded file is too large.', 413);
    }
    json_error('VALIDATION_FAILED', 'File upload failed.', 422);
}

if (($file['size'] ?? 0) <= 0 || $file['size'] > MAX_THUMBNAIL_BYTES) {
    json_error('PAYLOAD_TOO_LARGE', 'Uploaded file must be a non-empty image up to 3MB.', 413);
}

$tmpPath = $file['tmp_name'] ?? '';
if ($tmpPath === '' || !is_uploaded_file($tmpPath)) {
    json_error('VALIDATION_FAILED', 'Invalid file upload.', 422);
}

// Verify the real content type from the file bytes, never trust the
// client-supplied MIME type or filename.
$finfo = finfo_open(FILEINFO_MIME_TYPE);
$realMime = finfo_file($finfo, $tmpPath);
finfo_close($finfo);

if ($realMime !== 'image/webp') {
    json_error('UNSUPPORTED_MEDIA_TYPE', 'Only image/webp uploads are accepted.', 415);
}

// Re-verify actual bytes on disk match the declared size and cap.
$actualSize = filesize($tmpPath);
if ($actualSize === false || $actualSize <= 0 || $actualSize > MAX_THUMBNAIL_BYTES) {
    json_error('PAYLOAD_TOO_LARGE', 'Uploaded file must be a non-empty image up to 3MB.', 413);
}

$userDir = __DIR__ . '/../../uploads/thumbnails/' . $userId;
if (!is_dir($userDir)) {
    if (!mkdir($userDir, 0755, true) && !is_dir($userDir)) {
        throw new RuntimeException('Failed to create upload directory.');
    }
}

// bookmark_id was already validated against a strict [A-Za-z0-9-]{1,64}
// pattern above, so it is safe to use directly in the destination path.
$destPath = $userDir . '/' . $bookmarkId . '.webp';

if (!move_uploaded_file($tmpPath, $destPath)) {
    throw new RuntimeException('Failed to store uploaded thumbnail.');
}
chmod($destPath, 0644);

$url = rtrim(APP_BASE_URL, '/') . '/uploads/thumbnails/' . $userId . '/' . $bookmarkId . '.webp';

json_ok(['url' => $url], 201);
