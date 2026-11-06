package com.vibee.repository;

import com.vibee.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    // Fetch kèm organizer ngay trong query để không bị N+1 khi map ra response
    @Override
    @EntityGraph(attributePaths = "organizer")
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "organizer")
    Optional<Event> findById(Long id);

    @EntityGraph(attributePaths = "organizer")
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);
}
