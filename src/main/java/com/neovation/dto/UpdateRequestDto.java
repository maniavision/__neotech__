package com.neovation.dto;

import com.neovation.model.RequestStatus;

public class UpdateRequestDto {
    private RequestStatus status;
    private String adminNotes;

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}
