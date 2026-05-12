package com.university.ire.repository;

import com.university.ire.entity.IdentityGraph;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityGraphRepository extends JpaRepository<IdentityGraph, Long> {
    List<IdentityGraph> findByFromIdentityIdOrToIdentityId(Long fromIdentityId, Long toIdentityId);
}
