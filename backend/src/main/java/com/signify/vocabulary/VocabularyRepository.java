package com.signify.vocabulary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VocabularyRepository {

    private static final String COLUMNS = "id, term, normalized_term, category, media_path, media_type, source, "
            + "license, attribution, verification_status, notes";

    /** Words and phrases before letters and digits. */
    private static final String CATEGORY_ORDER =
            "CASE category WHEN 'WORD' THEN 0 WHEN 'PHRASE' THEN 1 WHEN 'LETTER' THEN 2 ELSE 3 END";

    private final JdbcClient jdbc;

    public VocabularyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SignEntry> findByNormalizedTerms(Collection<String> terms) {
        if (terms.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM sign_entry WHERE normalized_term IN (:terms)")
                .param("terms", terms)
                .query(VocabularyRepository::map)
                .list();
    }

    public Optional<SignEntry> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sign_entry WHERE id = :id")
                .param("id", id)
                .query(VocabularyRepository::map)
                .optional();
    }

    public Optional<SignEntry> findByCategoryAndTerm(SignCategory category, String term) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sign_entry WHERE category = :category AND normalized_term = :term")
                .param("category", category.name())
                .param("term", term)
                .query(VocabularyRepository::map)
                .optional();
    }

    public List<SignEntry> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sign_entry ORDER BY " + CATEGORY_ORDER + ", normalized_term")
                .query(VocabularyRepository::map)
                .list();
    }

    /** Page of entries filtered by an optional category and an optional case-insensitive substring. */
    public List<SignEntry> findPage(SignCategory category, String query, int offset, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sign_entry" + where(category, query)
                        + " ORDER BY CASE WHEN normalized_term = :exact THEN 0 ELSE 1 END, " + CATEGORY_ORDER + ", normalized_term"
                        + " LIMIT :limit OFFSET :offset")
                .params(filterParams(category, query))
                .param("exact", query == null ? "" : query)
                .param("limit", limit)
                .param("offset", offset)
                .query(VocabularyRepository::map)
                .list();
    }

    public long count(SignCategory category, String query) {
        return jdbc.sql("SELECT COUNT(*) FROM sign_entry" + where(category, query))
                .params(filterParams(category, query))
                .query(Long.class)
                .single();
    }

    public long countAll() {
        return jdbc.sql("SELECT COUNT(*) FROM sign_entry").query(Long.class).single();
    }

    public void insert(SignEntry e) {
        jdbc.sql("INSERT INTO sign_entry (term, normalized_term, category, media_path, media_type, source, license, "
                        + "attribution, verification_status, notes) VALUES (:term, :normalized, :category, :mediaPath, "
                        + ":mediaType, :source, :license, :attribution, :verification, :notes)")
                .params(writeParams(e))
                .update();
    }

    public void update(SignEntry e) {
        jdbc.sql("UPDATE sign_entry SET term = :term, category = :category, media_path = :mediaPath, "
                        + "media_type = :mediaType, source = :source, license = :license, attribution = :attribution, "
                        + "verification_status = :verification, notes = :notes, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE category = :category AND normalized_term = :normalized "
                        + "AND (term IS DISTINCT FROM :term OR media_path IS DISTINCT FROM :mediaPath "
                        + "OR media_type IS DISTINCT FROM :mediaType OR source IS DISTINCT FROM :source "
                        + "OR license IS DISTINCT FROM :license OR attribution IS DISTINCT FROM :attribution "
                        + "OR verification_status IS DISTINCT FROM :verification OR notes IS DISTINCT FROM :notes)")
                .params(writeParams(e))
                .update();
    }

    private static String where(SignCategory category, String query) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (category != null) {
            sql.append(" AND category = :category");
        }
        if (query != null && !query.isEmpty()) {
            sql.append(" AND (normalized_term LIKE :pattern OR LOWER(term) LIKE :pattern)");
        }
        return sql.toString();
    }

    private static Map<String, Object> filterParams(SignCategory category, String query) {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        if (category != null) {
            params.put("category", category.name());
        }
        if (query != null && !query.isEmpty()) {
            params.put("pattern", "%" + escapeLike(query) + "%");
        }
        return params;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Map<String, Object> writeParams(SignEntry e) {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        params.put("term", e.term());
        params.put("normalized", e.normalizedTerm());
        params.put("category", e.category().name());
        params.put("mediaPath", e.mediaPath());
        params.put("mediaType", e.mediaType() == null ? null : e.mediaType().name());
        params.put("source", e.source());
        params.put("license", e.license());
        params.put("attribution", e.attribution());
        params.put("verification", e.verificationStatus().name());
        params.put("notes", e.notes());
        return params;
    }

    private static SignEntry map(ResultSet rs, int row) throws SQLException {
        String mediaType = rs.getString("media_type");
        return new SignEntry(
                rs.getLong("id"),
                rs.getString("term"),
                rs.getString("normalized_term"),
                SignCategory.valueOf(rs.getString("category")),
                rs.getString("media_path"),
                mediaType == null ? null : MediaType.valueOf(mediaType),
                rs.getString("source"),
                rs.getString("license"),
                rs.getString("attribution"),
                VerificationStatus.valueOf(rs.getString("verification_status")),
                rs.getString("notes"));
    }
}
