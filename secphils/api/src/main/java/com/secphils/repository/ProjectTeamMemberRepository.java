package com.secphils.repository;

import com.secphils.entity.ProjectTeamMember;
import com.secphils.entity.ProjectTeamMemberId;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTeamMemberRepository extends JpaRepository<ProjectTeamMember, ProjectTeamMemberId> {

    List<ProjectTeamMember> findByProject(Project project);

    List<ProjectTeamMember> findByProjectId(Long projectId);

    List<ProjectTeamMember> findByUser(User user);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
}
