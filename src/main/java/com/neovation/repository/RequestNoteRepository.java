package com.neovation.repository;

import com.neovation.model.RequestNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestNoteRepository extends JpaRepository<RequestNote, Long> {
    List<RequestNote> findByServiceRequestId(Long requestId);
}