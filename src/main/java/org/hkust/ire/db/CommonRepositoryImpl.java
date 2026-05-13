package org.hkust.ire.db;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityManager;
import java.io.Serializable;

/**
 * Base implementation of CommonRepository, following the JSHM CommonRepositoryImpl pattern.
 *
 * <p>Provides the default JPA implementation for all repositories.
 * Custom cross-cutting query logic can be added here.</p>
 *
 * @param <T>  domain type
 * @param <ID> id type
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class CommonRepositoryImpl<T, ID extends Serializable>
        extends SimpleJpaRepository<T, ID>
        implements CommonRepository<T, ID> {

    private final EntityManager entityManager;

    /**
     * Constructs a new CommonRepositoryImpl.
     *
     * @param entityInformation entity metadata
     * @param entityManager     JPA entity manager
     */
    public CommonRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    /**
     * Returns the EntityManager for custom native queries.
     *
     * @return the EntityManager
     */
    protected EntityManager getEntityManager() {
        return entityManager;
    }
}
