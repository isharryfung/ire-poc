package com.university.ire.repository;

import com.university.ire.entity.Identity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<Identity, Long> {
    Optional<Identity> findByHkid(String hkid);
    Optional<Identity> findByStudentId(String studentId);
    Optional<Identity> findByStaffId(String staffId);
    Optional<Identity> findByEmailIgnoreCase(String email);
    List<Identity> findAllByEmailIgnoreCase(String email);
}
