package com.neovation.service;

import com.neovation.dto.RequestNoteDto;
import com.neovation.model.RequestNote;
import com.neovation.model.Role;
import com.neovation.model.ServiceRequest;
import com.neovation.model.User;
import com.neovation.repository.RequestNoteRepository;
import com.neovation.repository.ServiceRequestRepository;
import com.neovation.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RequestNoteService {
    private static final Logger log = LoggerFactory.getLogger(RequestNoteService.class);

    private final RequestNoteRepository noteRepository;
    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;

    public RequestNoteService(RequestNoteRepository noteRepository, ServiceRequestRepository requestRepository, UserRepository userRepository) {
        this.noteRepository = noteRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("User not found or not authenticated."));
    }

    private ServiceRequest findServiceRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + requestId));
    }

    private void checkStaffOrAuthorAccess(User currentUser, RequestNote note, Long noteId) {
        Role role = currentUser.getRole();
        boolean isAuthorized = role == Role.ADMIN || role == Role.STAFF || role == Role.MANAGER || note.getAuthor().getId().equals(currentUser.getId());

        if (!isAuthorized) {
            log.warn("Access Denied: User {} attempted to access note {} not authorized.", currentUser.getEmail(), noteId);
            throw new AccessDeniedException("You do not have permission to modify this note.");
        }
    }

    /**
     * POST /api/requests/{requestId}/notes - Create a note
     */
    public RequestNoteDto createNote(Long requestId, RequestNoteDto dto) { // <-- CHANGED RETURN TYPE
        User currentUser = getCurrentUser();

        // Security Check: Only ADMIN, STAFF, MANAGER can create notes
        Role role = currentUser.getRole();
        if (role != Role.ADMIN && role != Role.STAFF && role != Role.MANAGER) {
            throw new AccessDeniedException("You do not have permission to create notes.");
        }

        ServiceRequest request = findServiceRequest(requestId);

        RequestNote note = new RequestNote();
        note.setContent(dto.getContent());
        note.setServiceRequest(request);
        note.setAuthor(currentUser);

        RequestNote savedNote = noteRepository.save(note);
        log.info("Creating new note for request ID: {} by staff user ID: {}", requestId, currentUser.getId());

        return mapToDto(savedNote); // <-- Map entity to DTO
    }

    /**
     * GET /api/requests/{requestId}/notes - Get all notes for a request
     */
    public List<RequestNoteDto> getAllNotesByRequestId(Long requestId) { // <-- CHANGED RETURN TYPE
        User currentUser = getCurrentUser();

        // Security Check: Only ADMIN, STAFF, MANAGER can view all notes
        Role role = currentUser.getRole();
        if (role != Role.ADMIN && role != Role.STAFF && role != Role.MANAGER) {
            throw new AccessDeniedException("You do not have permission to view notes.");
        }

        findServiceRequest(requestId);

        List<RequestNote> notes = noteRepository.findByServiceRequestId(requestId);

        return notes.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList()); // <-- Map entities to DTOs
    }

    /**
     * PUT /api/requests/{requestId}/notes/{id} - Update a note
     */
    public RequestNoteDto updateNote(Long requestId, Long noteId, RequestNoteDto dto) { // <-- CHANGED RETURN TYPE
        User currentUser = getCurrentUser();
        findServiceRequest(requestId);

        RequestNote existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("RequestNote not found with id: " + noteId));

        // Validation: Ensure the note belongs to the correct request
        if (!existingNote.getServiceRequest().getId().equals(requestId)) {
            throw new EntityNotFoundException("Note not found for the given request.");
        }

        // Security Check: Must be the author OR an ADMIN/STAFF/MANAGER
        checkStaffOrAuthorAccess(currentUser, existingNote, noteId);

        existingNote.setContent(dto.getContent());

        RequestNote updatedNote = noteRepository.save(existingNote);
        log.info("Updating note ID: {} for request ID: {}", noteId, requestId);

        return mapToDto(updatedNote); // <-- Map entity to DTO
    }

    /**
     * DELETE /api/requests/{requestId}/notes/{id} - Delete a note
     */
    public void deleteNote(Long requestId, Long noteId) {
        User currentUser = getCurrentUser();
        ServiceRequest request = findServiceRequest(requestId);

        RequestNote existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("RequestNote not found with id: " + noteId));

        // Validation: Ensure the note belongs to the correct request
        if (!existingNote.getServiceRequest().getId().equals(requestId)) {
            throw new EntityNotFoundException("Note not found for the given request.");
        }

        // Security Check: Must be the author OR an ADMIN/STAFF/MANAGER
        checkStaffOrAuthorAccess(currentUser, existingNote, noteId);

        noteRepository.delete(existingNote);
        log.info("Deleted note ID: {} for request ID: {}", noteId, requestId);
    }

    private RequestNoteDto mapToDto(RequestNote note) {
        RequestNoteDto dto = new RequestNoteDto();
        dto.setId(note.getId());
        dto.setRequestId(note.getServiceRequest().getId());
        dto.setContent(note.getContent());
        dto.setCreatedAt(note.getCreatedAt());
        dto.setUpdatedAt(note.getUpdatedAt());

        // Populate author details
        dto.setAuthorName(note.getAuthor().getFirstName() + " " + note.getAuthor().getLastName());
        dto.setAuthorRole(note.getAuthor().getRole());

        return dto;
    }
}