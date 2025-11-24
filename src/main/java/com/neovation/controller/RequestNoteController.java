package com.neovation.controller;

import com.neovation.dto.RequestNoteDto;
import com.neovation.model.RequestNote;
import com.neovation.service.RequestNoteService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/requests/{requestId}/notes")
public class RequestNoteController {
    private static final Logger log = LoggerFactory.getLogger(RequestNoteController.class);

    private final RequestNoteService noteService;

    public RequestNoteController(RequestNoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ResponseEntity<RequestNoteDto> createNote(
            @PathVariable Long requestId,
            @RequestBody @Valid RequestNoteDto dto) {

        log.info("Received POST to create note for request ID: {}", requestId);
        try {
            RequestNoteDto newNote = noteService.createNote(requestId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(newNote);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }

    @GetMapping
    public ResponseEntity<List<RequestNoteDto>> getAllNotes(@PathVariable Long requestId) {
        log.info("Received GET to fetch all notes for request ID: {}", requestId);
        try {
            List<RequestNoteDto> notes = noteService.getAllNotesByRequestId(requestId);
            return ResponseEntity.ok(notes);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RequestNoteDto> updateNote(
            @PathVariable Long requestId,
            @PathVariable Long id,
            @RequestBody @Valid RequestNoteDto dto) {

        log.info("Received PUT to update note ID: {} for request ID: {}", id, requestId);
        try {
            RequestNoteDto updatedNote = noteService.updateNote(requestId, id, dto);
            return ResponseEntity.ok(updatedNote);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNote(
            @PathVariable Long requestId,
            @PathVariable Long id) {

        log.info("Received DELETE to delete note ID: {} for request ID: {}", id, requestId);
        try {
            noteService.deleteNote(requestId, id);
            return ResponseEntity.noContent().build(); // 204
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build(); // 404
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
        }
    }
}