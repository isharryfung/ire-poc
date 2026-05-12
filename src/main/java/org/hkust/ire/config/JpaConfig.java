package org.hkust.ire.config;

import org.hkust.ire.db.CommonRepository;
import org.hkust.ire.db.CommonRepositoryImpl;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration - enables custom repository base class (CommonRepositoryImpl)
 * following the JSHM CommonRepository pattern.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Configuration
@EntityScan(basePackages = "org.hkust.ire.db.persistence.domain")
@EnableJpaRepositories(
        basePackages = "org.hkust.ire.db.persistence.repository",
        repositoryBaseClass = CommonRepositoryImpl.class
)
public class JpaConfig {
}
