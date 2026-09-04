package com.fashion.product;

import com.fashion.dto.ProductQueryDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, user-facing product query identity. Its canonical form is a cache
 * contract: do not change it without a new key version.
 */
public final class NormalizedProductQuery {

    private static final Set<String> SORTS = new HashSet<>(Arrays.asList(
            "createTime", "price_asc", "price_desc", "sales"));

    private final int page;
    private final int pageSize;
    private final Long categoryId;
    private final String sortBy;
    private final String keyword;
    private final String tag;
    private final boolean sale;
    private final byte[] canonicalBytes;
    private final String querySha256;

    private NormalizedProductQuery(int page, int pageSize, Long categoryId, String sortBy,
                                   String keyword, String tag, boolean sale) {
        this.page = page;
        this.pageSize = pageSize;
        this.categoryId = categoryId;
        this.sortBy = sortBy;
        this.keyword = keyword;
        this.tag = tag;
        this.sale = sale;
        String canonical = "v1|" + page + "|" + pageSize + "|"
                + (categoryId == null ? "-" : categoryId) + "|" + sortBy + "|"
                + utf8Length(keyword) + ":" + keyword + "|"
                + utf8Length(tag) + ":" + tag + "|sale=" + (sale ? "1" : "0");
        this.canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        this.querySha256 = sha256Hex(canonicalBytes);
    }

    public static NormalizedProductQuery forUser(ProductQueryDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("product query is required");
        }
        if (source.getPage() < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (source.getPageSize() < 1 || source.getPageSize() > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (source.getCategoryId() != null && source.getCategoryId() <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        String sort = normalizeSort(source.getSortBy());
        return new NormalizedProductQuery(source.getPage(), source.getPageSize(), source.getCategoryId(),
                sort, trimEdgeWhitespace(source.getKeyword()), trimEdgeWhitespace(source.getTag()), true);
    }

    private static String normalizeSort(String source) {
        String value = trimEdgeWhitespace(source);
        if (value.isEmpty() || "default".equals(value)) {
            return "createTime";
        }
        if (!SORTS.contains(value)) {
            throw new IllegalArgumentException("unsupported product sort");
        }
        return value;
    }

    static String trimEdgeWhitespace(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = source.length();
        while (start < end) {
            int codePoint = source.codePointAt(start);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = source.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return source.substring(start, end);
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public ProductQueryDTO toQueryDto() {
        ProductQueryDTO result = new ProductQueryDTO();
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setCategoryId(categoryId);
        result.setSortBy(sortBy);
        result.setKeyword(keyword);
        result.setTag(tag);
        result.setIsSale(true);
        return result;
    }

    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    public String querySha256() {
        return querySha256;
    }

    public boolean isSale() {
        return sale;
    }
}
