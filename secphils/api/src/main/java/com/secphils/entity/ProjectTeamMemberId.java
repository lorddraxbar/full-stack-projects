package com.secphils.entity;

import java.io.Serializable;
import java.util.Objects;

public class ProjectTeamMemberId implements Serializable {

    private Project project;
    private User user;

    public ProjectTeamMemberId() {
    }

    public ProjectTeamMemberId(Project project, User user) {
        this.project = project;
        this.user = user;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectTeamMemberId that = (ProjectTeamMemberId) o;
        return Objects.equals(project != null ? project.getId() : null,
                              that.project != null ? that.project.getId() : null)
            && Objects.equals(user != null ? user.getId() : null,
                              that.user != null ? that.user.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            project != null ? project.getId() : null,
            user != null ? user.getId() : null);
    }
}
