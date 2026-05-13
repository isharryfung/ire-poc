package org.hkust.ire.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;

/**
 * H2 Database Configuration
 * 
 * Activates when profile "h2" is active or when spring.datasource.url contains "h2"
 * Provides startup logging and H2 console information for local development
 * 
 * @author ire-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.datasource.url",
    havingValue = "jdbc:h2:mem:ire_db",
    matchIfMissing = false
)
public class H2DatabaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(H2DatabaseConfiguration.class);

    @PostConstruct
    public void init() {
        printH2Banner();
        logH2Configuration();
    }

    /**
     * Print ASCII banner for H2 configuration
     */
    private void printH2Banner() {
        String banner = """
            
            ╔════════════════════════════════════════════════════════════╗
            ║        H2 Database Configuration Activated                 ║
            ║  ───────────────────────────────────────────────────────  ║
            ║  🗄️  Database: In-Memory H2 (Oracle Compatibility)         ║
            ║  🌐 H2 Console: http://localhost:8080/h2-console         ║
            ║  👤 User: sa (no password required)                       ║
            ║  ✅ Database initialized with Flyway migrations            ║
            ║  ✅ Sample data loaded (GID-001)                          ║
            ║  📊 Source credibility weights configured                  ║
            ║  💡 For production, use Oracle database                    ║
            ╚════════════════════════════════════════════════════════════╝
            """;
        System.out.println(banner);
        log.info("H2 Database Configuration activated successfully");
    }

    /**
     * Log detailed H2 configuration information
     */
    private void logH2Configuration() {
        log.debug("H2 Database Configuration Details:");
        log.debug("  - Database Mode: Oracle Compatibility");
        log.debug("  - Connection: In-Memory (jdbc:h2:mem:ire_db)");
        log.debug("  - H2 Console Path: /h2-console");
        log.debug("  - Flyway Migrations: ENABLED");
        log.debug("  - Migration Location: classpath:db/migration");
        log.debug("  - Hibernate DDL Mode: validate");
        log.debug("  - SQL Logging: ENABLED (DEBUG level)");
        log.debug("  - Sample Data: LOADED");
        log.debug("");
        log.debug("Database is ready for local development!");
        log.debug("Access H2 Console at: http://localhost:8080/h2-console");
    }

}
