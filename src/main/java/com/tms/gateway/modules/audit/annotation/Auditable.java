package com.tms.gateway.modules.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the action recorded in the audit log for a state-changing endpoint.
 *
 * <p>
 * Annotating is optional: every POST/PUT/PATCH/DELETE handler is audited
 * automatically. Without this annotation the action falls back to the
 * endpoint's OpenAPI {@code @Operation} summary, then to the handler method
 * name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** Human-readable action, e.g. "Rotate API key". */
    String value();

    /** Module the action belongs to. Defaults to the first URL path segment. */
    String module() default "";
}
