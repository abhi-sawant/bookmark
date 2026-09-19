<?php
/**
 * validate.php - small validation helpers shared by API endpoints.
 *
 * Each helper either returns a cleaned value or calls json_error() (422) and
 * terminates the request, so callers can use these inline without extra
 * boilerplate.
 */

/**
 * Require $value to be a non-empty (after trim, unless $allowEmpty) string
 * within [minLen, maxLen]. Returns the trimmed string.
 */
function require_string($value, string $field, int $minLen = 1, int $maxLen = 255, bool $allowEmpty = false)
{
    if (!is_string($value)) {
        json_error('VALIDATION_FAILED', "Field '$field' must be a string.", 422);
    }
    $trimmed = trim($value);
    $len = strlen($trimmed);
    if (!$allowEmpty && $len < $minLen) {
        json_error('VALIDATION_FAILED', "Field '$field' is too short.", 422);
    }
    if ($len > $maxLen) {
        json_error('VALIDATION_FAILED', "Field '$field' is too long (max $maxLen).", 422);
    }
    return $trimmed;
}

/**
 * Optional string field: returns null if $value is null/missing, otherwise
 * validates like require_string.
 */
function optional_string($value, string $field, int $maxLen = 255)
{
    if ($value === null) {
        return null;
    }
    if (!is_string($value)) {
        json_error('VALIDATION_FAILED', "Field '$field' must be a string.", 422);
    }
    $trimmed = trim($value);
    if (strlen($trimmed) === 0) {
        return null;
    }
    if (strlen($trimmed) > $maxLen) {
        json_error('VALIDATION_FAILED', "Field '$field' is too long (max $maxLen).", 422);
    }
    return $trimmed;
}

/**
 * Validate and normalize (lowercase) an email address. Returns the
 * lowercased address.
 */
function require_email($value): string
{
    if (!is_string($value)) {
        json_error('VALIDATION_FAILED', 'Field \'email\' must be a string.', 422);
    }
    $email = strtolower(trim($value));
    if ($email === '' || strlen($email) > 255 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        json_error('VALIDATION_FAILED', 'A valid email address is required.', 422);
    }
    return $email;
}

/**
 * Require an integer (or numeric string) within [min, max].
 */
function require_in_range($value, string $field, int $min, int $max): int
{
    if (!is_int($value) && !(is_string($value) && ctype_digit(ltrim($value, '-')) )) {
        json_error('VALIDATION_FAILED', "Field '$field' must be an integer.", 422);
    }
    $intVal = (int) $value;
    if ($intVal < $min || $intVal > $max) {
        json_error('VALIDATION_FAILED', "Field '$field' must be between $min and $max.", 422);
    }
    return $intVal;
}

/**
 * Coerce a value to 0 or 1 (accepts bool, int, numeric string).
 */
function coerce_bool_int($value): int
{
    if (is_bool($value)) {
        return $value ? 1 : 0;
    }
    if (is_numeric($value)) {
        return ((int) $value) !== 0 ? 1 : 0;
    }
    return 0;
}

/**
 * Validate a #RRGGBB hex color string.
 */
function require_color_hex($value, string $field = 'color_hex'): string
{
    if (!is_string($value) || !preg_match('/^#[0-9A-Fa-f]{6}$/', $value)) {
        json_error('VALIDATION_FAILED', "Field '$field' must be a #RRGGBB hex color.", 422);
    }
    return $value;
}

/**
 * Require an array field (used for request payload sub-arrays).
 */
function require_array($value, string $field): array
{
    if (!is_array($value)) {
        json_error('VALIDATION_FAILED', "Field '$field' must be an array.", 422);
    }
    return $value;
}
