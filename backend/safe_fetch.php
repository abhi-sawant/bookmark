<?php
/**
 * safe_fetch.php - SSRF-safe outbound HTTP for link previews and link checks.
 *
 * The server fetches addresses chosen by users, so every request here is
 * constrained:
 *   - http/https only, ports 80 and 443 only, no credentials in the URL
 *   - the host is resolved up front and every resolved address must be public
 *     (no loopback, private, link-local, carrier-grade NAT, metadata, etc.)
 *   - the vetted address is pinned for the connection (CURLOPT_RESOLVE), so a
 *     second DNS lookup cannot swap in a private address (DNS rebinding)
 *   - redirects are followed by hand, at most SAFE_FETCH_MAX_REDIRECTS, and
 *     every hop goes through the same checks
 *   - connect and total timeouts, and a hard cap on the bytes read
 *
 * Functions only; nothing runs when this file is included.
 */

const SAFE_FETCH_MAX_REDIRECTS = 3;
const SAFE_FETCH_CONNECT_TIMEOUT = 4;
const SAFE_FETCH_TIMEOUT = 8;
const SAFE_FETCH_USER_AGENT = 'Mozilla/5.0 (compatible; BookmarkPreviewBot/1.0)';

class SafeFetchException extends RuntimeException
{
}

/**
 * True when $ip is a globally routable address. Anything private, reserved,
 * loopback, link-local or otherwise not meant for the public internet is false.
 */
function safe_fetch_is_public_ip(string $ip): bool
{
    $packed = @inet_pton($ip);
    if ($packed === false) {
        return false;
    }

    if (strlen($packed) === 16) {
        // IPv4-mapped (::ffff:a.b.c.d) -- judge the embedded IPv4 address.
        if (substr($packed, 0, 12) === "\0\0\0\0\0\0\0\0\0\0\xff\xff") {
            return safe_fetch_is_public_ip(inet_ntop(substr($packed, 12)));
        }
        // NAT64 (64:ff9b::/96) and 6to4 (2002::/16) embed IPv4 addresses.
        if (substr($packed, 0, 12) === "\x00\x64\xff\x9b\0\0\0\0\0\0\0\0" || substr($packed, 0, 2) === "\x20\x02") {
            return false;
        }
    } else {
        $b = array_values(unpack('C4', $packed));
        // 100.64.0.0/10 carrier-grade NAT, 198.18.0.0/15 benchmarking,
        // 192.0.0.0/24 IETF protocol assignments.
        if ($b[0] === 100 && ($b[1] & 0xC0) === 64) {
            return false;
        }
        if ($b[0] === 198 && ($b[1] & 0xFE) === 18) {
            return false;
        }
        if ($b[0] === 192 && $b[1] === 0 && $b[2] === 0) {
            return false;
        }
    }

    return filter_var(
        $ip,
        FILTER_VALIDATE_IP,
        FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE
    ) !== false;
}

/**
 * Resolves $host to one public address, preferring IPv4. Throws if the name
 * does not resolve or ANY resolved address is non-public.
 */
function safe_fetch_resolve(string $host): string
{
    $bare = trim($host, '[]');
    if (filter_var($bare, FILTER_VALIDATE_IP) !== false) {
        $addresses = [$bare];
    } else {
        $addresses = [];
        $records = @dns_get_record($bare, DNS_A | DNS_AAAA);
        if (is_array($records)) {
            foreach ($records as $record) {
                if (isset($record['ip'])) {
                    $addresses[] = $record['ip'];
                } elseif (isset($record['ipv6'])) {
                    $addresses[] = $record['ipv6'];
                }
            }
        }
        if ($addresses === []) {
            $v4 = @gethostbynamel($bare);
            if (is_array($v4)) {
                $addresses = $v4;
            }
        }
    }

    if ($addresses === []) {
        throw new SafeFetchException('The host could not be resolved.');
    }
    foreach ($addresses as $address) {
        if (!safe_fetch_is_public_ip($address)) {
            throw new SafeFetchException('That address is not allowed.');
        }
    }
    foreach ($addresses as $address) {
        if (strpos($address, ':') === false) {
            return $address; // prefer IPv4
        }
    }
    return $addresses[0];
}

/**
 * Validates $url and returns [scheme, host, port, resolvedIp]. Throws
 * SafeFetchException when the URL may not be fetched.
 */
function safe_fetch_check_url(string $url): array
{
    if (strlen($url) > 2048) {
        throw new SafeFetchException('The URL is too long.');
    }
    $parts = parse_url($url);
    if ($parts === false || !isset($parts['scheme'], $parts['host'])) {
        throw new SafeFetchException('The URL is not valid.');
    }
    $scheme = strtolower($parts['scheme']);
    if ($scheme !== 'http' && $scheme !== 'https') {
        throw new SafeFetchException('Only http and https links can be fetched.');
    }
    if (isset($parts['user']) || isset($parts['pass'])) {
        throw new SafeFetchException('URLs with credentials are not allowed.');
    }
    $port = $parts['port'] ?? ($scheme === 'https' ? 443 : 80);
    if ($port !== 80 && $port !== 443) {
        throw new SafeFetchException('That port is not allowed.');
    }
    $host = strtolower($parts['host']);
    return [$scheme, $host, $port, safe_fetch_resolve($host)];
}

/** Resolves a Location header value against the URL it came from. */
function safe_fetch_absolute_url(string $base, string $location): string
{
    if (preg_match('#^[a-z][a-z0-9+.-]*://#i', $location)) {
        return $location;
    }
    $b = parse_url($base);
    $origin = $b['scheme'] . '://' . $b['host'] . (isset($b['port']) ? ':' . $b['port'] : '');
    if (strpos($location, '//') === 0) {
        return $b['scheme'] . ':' . $location;
    }
    if (strpos($location, '/') === 0) {
        return $origin . $location;
    }
    $path = $b['path'] ?? '/';
    $dir = substr($path, 0, (int) strrpos($path, '/') + 1);
    return $origin . $dir . $location;
}

/**
 * Performs one request, following redirects manually.
 *
 * @param string $method   'GET' or 'HEAD'
 * @param int    $maxBytes body cap; reading stops quietly once reached
 * @return array{status:int, headers:array<string,string>, body:string, final_url:string}
 * @throws SafeFetchException when the URL is refused or the request fails
 */
function safe_fetch(string $url, string $method = 'GET', int $maxBytes = 1048576): array
{
    $method = strtoupper($method) === 'HEAD' ? 'HEAD' : 'GET';
    $current = $url;

    for ($hop = 0; $hop <= SAFE_FETCH_MAX_REDIRECTS; $hop++) {
        [$scheme, $host, $port, $ip] = safe_fetch_check_url($current);

        $headers = [];
        $body = '';
        $truncated = false;

        $ch = curl_init($current);
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_NOBODY => $method === 'HEAD',
            CURLOPT_RESOLVE => [$host . ':' . $port . ':' . $ip],
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
            CURLOPT_CONNECTTIMEOUT => SAFE_FETCH_CONNECT_TIMEOUT,
            CURLOPT_TIMEOUT => SAFE_FETCH_TIMEOUT,
            CURLOPT_USERAGENT => SAFE_FETCH_USER_AGENT,
            CURLOPT_ACCEPT_ENCODING => '',
            CURLOPT_HTTPHEADER => ['Accept: text/html,application/xhtml+xml,image/*;q=0.8,*/*;q=0.5'],
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HEADERFUNCTION => static function ($curl, string $line) use (&$headers): int {
                $pos = strpos($line, ':');
                if ($pos !== false) {
                    $headers[strtolower(trim(substr($line, 0, $pos)))] = trim(substr($line, $pos + 1));
                }
                return strlen($line);
            },
            CURLOPT_WRITEFUNCTION => static function ($curl, string $chunk) use (&$body, &$truncated, $maxBytes): int {
                $room = $maxBytes - strlen($body);
                if ($room <= 0) {
                    $truncated = true;
                    return 0; // abort the transfer
                }
                if (strlen($chunk) > $room) {
                    $body .= substr($chunk, 0, $room);
                    $truncated = true;
                    return 0;
                }
                $body .= $chunk;
                return strlen($chunk);
            },
        ]);

        $ok = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $error = curl_error($ch);
        curl_close($ch);

        // Aborting at the byte cap is expected, not a failure.
        if ($ok === false && !($truncated && $status > 0)) {
            throw new SafeFetchException($error !== '' ? $error : 'The request failed.');
        }

        if (in_array($status, [301, 302, 303, 307, 308], true) && isset($headers['location'])) {
            $current = safe_fetch_absolute_url($current, $headers['location']);
            continue;
        }

        return ['status' => $status, 'headers' => $headers, 'body' => $body, 'final_url' => $current];
    }

    throw new SafeFetchException('Too many redirects.');
}
