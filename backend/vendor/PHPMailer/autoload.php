<?php
/**
 * Tiny manual "autoloader" for the 3 vendored PHPMailer files -- no
 * Composer available on shared hosting, so we just require them directly.
 */

require_once __DIR__ . '/src/Exception.php';
require_once __DIR__ . '/src/PHPMailer.php';
require_once __DIR__ . '/src/SMTP.php';
