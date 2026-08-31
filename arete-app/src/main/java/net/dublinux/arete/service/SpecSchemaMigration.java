package net.dublinux.arete.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-off schema fixups that {@code hibernate.ddl-auto=update} cannot do on
 * its own. Runs after the context is up (so Hibernate has already touched the
 * schema) and is idempotent — safe on every startup.
 *
 * <ol>
 *   <li>Ensure {@code SPECS.NAMESPACE} / {@code SPECS.SUBMITTER} exist, are
 *       backfilled, defaulted, and {@code NOT NULL} — Hibernate cannot add a
 *       {@code NOT NULL} column to a non-empty table without a default, and
 *       older builds may have left the column half-configured.</li>
 *   <li>Move uniqueness from {@code title} alone to {@code (namespace, title)}:
 *       drop any single-column unique constraint on {@code SPECS(TITLE)} and
 *       add the composite if it is missing.</li>
 * </ol>
 */
@Component
@Order(0)
class SpecSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpecSchemaMigration.class);
    private static final String COMPOSITE = "UK_SPECS_NAMESPACE_TITLE";

    private final JdbcTemplate jdbc;

    SpecSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureLabelColumn("NAMESPACE", "default");
        ensureLabelColumn("SUBMITTER", "anonymous");
        dropTitleOnlyUnique();
        addCompositeUnique();
    }

    private void ensureLabelColumn(String column, String defaultValue) {
        boolean exists = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = 'SPECS' and column_name = ?
                """, Integer.class, column) > 0;
        if (!exists) {
            jdbc.execute("alter table SPECS add column " + column
                    + " varchar(64) default '" + defaultValue + "' not null");
            log.info("Added SPECS.{} column", column);
            return;
        }
        // Existing column: normalise it, tolerating any prior state.
        run("update SPECS set " + column + " = '" + defaultValue + "' where " + column + " is null");
        run("alter table SPECS alter column " + column + " set default '" + defaultValue + "'");
        run("alter table SPECS alter column " + column + " set not null");
    }

    /** Drops every UNIQUE constraint on SPECS whose column set is exactly {TITLE}. */
    private void dropTitleOnlyUnique() {
        List<String> names = jdbc.queryForList("""
                select tc.constraint_name
                  from information_schema.table_constraints tc
                 where tc.table_name = 'SPECS' and tc.constraint_type = 'UNIQUE'
                   and (select listagg(kcu.column_name, ',') within group (order by kcu.column_name)
                          from information_schema.key_column_usage kcu
                         where kcu.constraint_name = tc.constraint_name) = 'TITLE'
                """, String.class);
        for (String name : names) {
            if (COMPOSITE.equalsIgnoreCase(name)) {
                continue;
            }
            run("alter table SPECS drop constraint if exists \"" + name + "\"");
            log.info("Dropped legacy title-only unique constraint {} on SPECS", name);
        }
    }

    private void addCompositeUnique() {
        Integer existing = jdbc.queryForObject("""
                select count(*)
                  from information_schema.table_constraints tc
                 where tc.table_name = 'SPECS' and tc.constraint_type = 'UNIQUE'
                   and (select listagg(kcu.column_name, ',') within group (order by kcu.column_name)
                          from information_schema.key_column_usage kcu
                         where kcu.constraint_name = tc.constraint_name) = 'NAMESPACE,TITLE'
                """, Integer.class);
        if (existing != null && existing > 0) {
            return;
        }
        run("alter table SPECS add constraint " + COMPOSITE + " unique (NAMESPACE, TITLE)");
        log.info("Added composite unique constraint {} on SPECS(NAMESPACE, TITLE)", COMPOSITE);
    }

    /** Executes {@code sql}, logging and swallowing a failure so a partial prior state can't wedge startup. */
    private void run(String sql) {
        try {
            jdbc.execute(sql);
        } catch (RuntimeException e) {
            log.debug("Schema fixup skipped ({}): {}", sql, e.getMessage());
        }
    }
}
