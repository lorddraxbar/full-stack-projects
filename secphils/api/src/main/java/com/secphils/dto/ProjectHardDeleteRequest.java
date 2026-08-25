package com.secphils.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectHardDeleteRequest {
    // Account password of the requesting admin. Required to hard-delete
    // before the 7-day window; optional (but ignored) after the window.
    public String password = "";
}
