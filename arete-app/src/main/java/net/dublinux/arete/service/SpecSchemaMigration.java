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
 * its own. Runs after the context is up (so Hibernate has already added the
 * new columns) and is idempotent — safe to run on every startup.
 *
 * <p>The job: uniqueness moved from {@code title} alone to
 * {@code (namespace, title)}. Hibernate's update never drops the old
 * title-only unique index, so this drops any single-column unique constraint
 * on {@code SPECS(TITLE)} and adds the composite if it is missing.
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
        dropTitleOnlyUnique();
        addCompositeUnique();
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
            jdbc.execute("alter table SPECS drop constraint if exists \"" + name + "\"");
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
        jdbc.execute("alter table SPECS add constraint " + COMPOSITE + " unique (NAMESPACE, TITLE)");
        log.info("Added composite unique constraint {} on SPECS(NAMESPACE, TITLE)", COMPOSITE);
    }
}
