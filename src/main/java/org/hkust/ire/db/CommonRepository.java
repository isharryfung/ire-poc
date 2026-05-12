package org.hkust.ire.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * Base repository interface for all IRE repositories, following the JSHM CommonRepository pattern.
 *
 * <p>All repositories in the IRE system extend this interface to ensure
 * consistent CRUD operations and type safety.</p>
 *
 * @param <T>  domain type the repository manages
 * @param <ID> type of the entity id
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@NoRepositoryBean
public interface CommonRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {
}
