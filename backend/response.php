<?php
/**
 * response.php - JSON response helpers and epoch-millis <-> DATETIME(6) conversion.
 *
 * All timestamps cross the API boundary as epoch milliseconds (integers).
 * Internally MySQL stores DATETIME(6) values, always interpreted as UTC.
 */

/**
 * Send a successful JSON response and terminate the script.
 *
 * @param mixed $data
 */
function json_ok($data, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_SLASHES);
    exit;
}

/**
 * Send a JSON error response in the shape {"error":{"code":..,"message":..}}
 * and terminate the script.
 */
function json_error(string $code, string $message, int $status): void
{
    http_response_code($status);
    echo json_encode([
        'error' => [
            'code' => $code,
            'message' => $message,
        ],
    ], JSON_UNESCAPED_SLASHES);
    exit;
}

/**
 * Convert epoch milliseconds to a MySQL DATETIME(6) string (UTC).
 */
function millis_to_datetime(int $ms): string
{
    $seconds = intdiv($ms, 1000);
    $micros = ($ms % 1000) * 1000;
    if ($micros < 0) {
        $micros += 1000000;
    }
    $dt = new DateTime('@' . $seconds, new DateTimeZone('UTC'));
    return $dt->format('Y-m-d H:i:s') . '.' . str_pad((string) $micros, 6, '0', STR_PAD_LEFT);
}

/**
 * Convert a MySQL DATETIME(6) string (assumed UTC) to epoch milliseconds.
 */
function datetime_to_millis(string $dt): int
{
    // Format: "Y-m-d H:i:s.u" (microseconds may be absent).
    $parts = explode('.', $dt);
    $main = $parts[0];
    $micros = isset($parts[1]) ? str_pad(substr($parts[1], 0, 6), 6, '0') : '000000';

    $date = DateTime::createFromFormat('Y-m-d H:i:s', $main, new DateTimeZone('UTC'));
    if ($date === false) {
        throw new RuntimeException('Invalid datetime value: ' . $dt);
    }

    $seconds = $date->getTimestamp();
    $millis = intdiv((int) $micros, 1000);

    return ($seconds * 1000) + $millis;
}
