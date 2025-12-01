package com.neovation.repository;

import com.neovation.model.RequestNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequestNoteRepository extends JpaRepository<RequestNote, Long> {
    List<RequestNote> findByServiceRequestId(String requestId);
}