package com.dolos.alert.repo;

import com.dolos.alert.domain.AlertView;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over the CQRS read model (Phase 2F). Serves the analyst queue; the default
 * risk-sorted scan is backed by the {@code (score DESC, raised_at DESC)} index on {@code alert_view}.
 */
public interface AlertViewRepository extends JpaRepository<AlertView, UUID> {

    /** Risk-sorted queue page (highest score first, newest first) — the fast default queue view. */
    Page<AlertView> findAllByOrderByScoreDescRaisedAtDesc(Pageable pageable);
}
