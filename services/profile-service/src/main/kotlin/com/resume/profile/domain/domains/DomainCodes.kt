package com.resume.profile.domain.domains

/** Feature 04 stable codes (BA 05; TL 03 code catalog) - never renamed. */

/** 422 `hostname_invalid`: wildcard, length, normalization failures. */
class HostnameInvalidException(message: String) :
    RuntimeException(message.ifBlank { "Enter a hostname like example.com or blog.example.com (no wildcards)." })

/** 422 `domain_denied`: denylist hit at add or recheck; never a plan fact. */
class DomainDeniedException :
    RuntimeException("This domain can't be added.")

/** 409 `domain_taken`: one host, one profile (BR-03); dispute path offered. */
class DomainTakenException :
    RuntimeException("This domain is already connected. If it's yours, contact support with DNS proof.")

/** 403 `domain_forbidden`: the fail-closed pro gate (BR-06, snapshot miss denies). */
class DomainForbiddenException :
    RuntimeException("Custom domains are a Pro feature.")

/** 422 `domain_quota_reached`: NFR-12 removal guidance, never silent eviction. */
class DomainQuotaReachedException :
    RuntimeException("You've reached the custom-domain limit. Remove a domain before adding another.")

/** 404 `domain_not_found`: unknown hostname on an owner path (BR-11). */
class DomainNotFoundException :
    RuntimeException("That hostname isn't connected to your account.")

/** 403 `domain_forbidden` on a foreign-owned host (BR-11/S-6; one 403 code in the catalog). */
class ForeignDomainException :
    RuntimeException("That hostname is connected to a different account.")
