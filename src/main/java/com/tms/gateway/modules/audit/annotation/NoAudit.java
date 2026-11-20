package com.tms.gateway.modules.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a write-method endpoint (or every endpoint of a controller) from the
 * audit log. Use it for endpoints that are fetches in practice even though they
 * use POST/PATCH, such as lookups or read receipts.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAudit {
}
