package com.tms.gateway.core.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * Builds a Laravel-compatible paginated response with data, links, meta.
 * Matches Laravel's LengthAwarePaginator JSON output exactly.
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * return ResponseEntity.ok(PagedResponse.from(page, "/transactions", Map.of("filters", filterOptions)));
 * </pre>
 */
public class PagedResponse {

    private PagedResponse() {
        // Utility class
    }

    /**
     * Build paged response from Spring Data Page with extra data.
     *
     * @param page
     *            Spring Data Page result
     * @param path
     *            Base path for pagination links (e.g., "/transactions")
     * @param extra
     *            Additional data to include in response (e.g., filters)
     */
    public static <T> Map<String, Object> from(Page<T> page, String path, Map<String, Object> extra) {
        Map<String, Object> response = new LinkedHashMap<>();

        int currentPage = page.getNumber() + 1; // Convert 0-indexed to 1-indexed
        int lastPage = Math.max(page.getTotalPages(), 1);
        long total = page.getTotalElements();

        response.put("data", page.getContent());

        // Links
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("first", path + "?page=1");
        links.put("last", path + "?page=" + lastPage);
        links.put("prev", currentPage > 1 ? path + "?page=" + (currentPage - 1) : null);
        links.put("next", currentPage < lastPage ? path + "?page=" + (currentPage + 1) : null);
        response.put("links", links);

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current_page", currentPage);
        meta.put("from", total > 0 ? page.getNumber() * page.getSize() + 1 : null);
        meta.put("last_page", lastPage);
        meta.put("links", buildMetaLinks(currentPage, lastPage, path));
        meta.put("path", path);
        meta.put("per_page", page.getSize());
        meta.put("to", total > 0 ? Math.min((long) currentPage * page.getSize(), total) : null);
        meta.put("total", total);
        response.put("meta", meta);

        // Extra data (filters, stats, etc.)
        if (extra != null) {
            response.putAll(extra);
        }

        return response;
    }

    /**
     * Build paged response from Spring Data Page.
     */
    public static <T> Map<String, Object> from(Page<T> page, String path) {
        return from(page, path, null);
    }

    /**
     * Build paged response from Spring Data Page with default path.
     */
    public static <T> Map<String, Object> from(Page<T> page) {
        return from(page, "", null);
    }

    /**
     * Build paged response from a list with explicit pagination params. Used when
     * data is fetched from an external service (e.g., gRPC).
     *
     * @param data
     *            List of items for current page
     * @param total
     *            Total number of items across all pages
     * @param currentPage
     *            Current page number (1-indexed)
     * @param perPage
     *            Items per page
     * @param path
     *            Base path for pagination links
     * @param extra
     *            Additional data to include in response
     */
    public static Map<String, Object> from(List<?> data, long total, int currentPage, int perPage, String path,
            Map<String, Object> extra) {
        Map<String, Object> response = new LinkedHashMap<>();

        int lastPage = Math.max((int) Math.ceil((double) total / perPage), 1);

        response.put("data", data);

        // Links
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("first", path + "?page=1");
        links.put("last", path + "?page=" + lastPage);
        links.put("prev", currentPage > 1 ? path + "?page=" + (currentPage - 1) : null);
        links.put("next", currentPage < lastPage ? path + "?page=" + (currentPage + 1) : null);
        response.put("links", links);

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current_page", currentPage);
        meta.put("from", total > 0 ? (currentPage - 1) * perPage + 1 : null);
        meta.put("last_page", lastPage);
        meta.put("links", buildMetaLinks(currentPage, lastPage, path));
        meta.put("path", path);
        meta.put("per_page", perPage);
        meta.put("to", total > 0 ? Math.min((long) currentPage * perPage, total) : null);
        meta.put("total", total);
        response.put("meta", meta);

        if (extra != null) {
            response.putAll(extra);
        }

        return response;
    }

    /**
     * Build paged response from a list without extra data.
     */
    public static Map<String, Object> from(List<?> data, long total, int currentPage, int perPage, String path) {
        return from(data, total, currentPage, perPage, path, null);
    }

    /**
     * Empty paged response.
     */
    public static Map<String, Object> empty(String path) {
        return from(List.of(), 0, 1, 10, path, null);
    }

    /**
     * Build the meta.links array matching Laravel's LengthAwarePaginator.
     */
    private static List<Map<String, Object>> buildMetaLinks(int currentPage, int lastPage, String path) {
        List<Map<String, Object>> metaLinks = new ArrayList<>();

        // Previous link
        Map<String, Object> prev = new LinkedHashMap<>();
        prev.put("url", currentPage > 1 ? path + "?page=" + (currentPage - 1) : null);
        prev.put("label", "&laquo; Previous");
        prev.put("active", false);
        metaLinks.add(prev);

        // Page number links
        for (int i = 1; i <= lastPage; i++) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("url", path + "?page=" + i);
            link.put("label", String.valueOf(i));
            link.put("active", i == currentPage);
            metaLinks.add(link);
        }

        // Next link
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("url", currentPage < lastPage ? path + "?page=" + (currentPage + 1) : null);
        next.put("label", "Next &raquo;");
        next.put("active", false);
        metaLinks.add(next);

        return metaLinks;
    }
}
