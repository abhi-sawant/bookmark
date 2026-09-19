<?php
/**
 * config.php - Bookmark sync API configuration
 * ============================================
 *
 * EDIT THE VALUES BELOW BEFORE DEPLOYING. Everything marked "CHANGE ME" must
 * be filled in with your real cPanel / MilesWeb hosting details.
 *
 * This file is read by every API script (via bootstrap.php). It contains no
 * real secrets by default -- only placeholders -- so it is safe to upload as
 * a starting point, but you MUST replace the placeholders with your actual
 * database and mail credentials before the API will work.
 *
 * Do not rename this file and do not move it outside the backend/ folder --
 * the accompanying .htaccess blocks direct web access to it, but only for
 * the file named "config.php" at this path.
 */

// -----------------------------------------------------------------------
// Database (MySQL / MariaDB) - from cPanel > MySQL Databases
// -----------------------------------------------------------------------
define('DB_HOST', 'localhost');           // CHANGE ME - usually "localhost" on shared hosting
define('DB_NAME', 'changeme');            // CHANGE ME - e.g. "cpaneluser_bookmark"
define('DB_USER', 'changeme');            // CHANGE ME - e.g. "cpaneluser_bmuser"
define('DB_PASS', 'changeme');            // CHANGE ME - the database user's password

// -----------------------------------------------------------------------
// Application
// -----------------------------------------------------------------------
// Public base URL this API is served from (no trailing slash). Used to build
// links (password reset emails) and absolute thumbnail URLs.
define('APP_BASE_URL', 'https://api.bookmark.slowatcoding.com'); // CHANGE ME

// -----------------------------------------------------------------------
// SMTP (used for password reset emails, sent via vendored PHPMailer)
// -----------------------------------------------------------------------
define('SMTP_HOST', 'smtp.example.com');       // CHANGE ME
define('SMTP_PORT', 587);                      // CHANGE ME - 587 (STARTTLS) or 465 (SMTPS)
define('SMTP_SECURE', 'ssl');                  // CHANGE ME - 'tls' or 'ssl'
define('SMTP_USERNAME', 'changeme@example.com'); // CHANGE ME
define('SMTP_PASSWORD', 'changeme');           // CHANGE ME
define('SMTP_FROM_EMAIL', 'changeme@example.com'); // CHANGE ME
define('SMTP_FROM_NAME', 'Bookmark App');      // CHANGE ME (display name shown to recipients)
