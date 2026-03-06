package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final WorkspaceRepository workspaceRepository;
    private final FilterRepository filterRepository;
    private final EmailSubscriberRepository emailSubscriberRepository;

    public Workspace createWorkspace(WorkspaceDto dto) {
        Workspace workspace = Workspace.builder()
                .title(dto.getTitle())
                .sharedSpaceId(dto.getSharedSpaceId())
                .workspaceId(dto.getWorkspaceId())
                .build();
        return workspaceRepository.save(workspace);
    }

    public Workspace updateWorkspace(UUID id, WorkspaceDto dto) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found with ID: " + id));
        workspace.setTitle(dto.getTitle());
        workspace.setSharedSpaceId(dto.getSharedSpaceId());
        workspace.setWorkspaceId(dto.getWorkspaceId());
        return workspaceRepository.save(workspace);
    }

    public void deleteWorkspace(UUID id) {
        if (emailSubscriberRepository.existsByWorkspaceId(id)) {
            throw new IllegalStateException("Cannot delete workspace with active subscribers.");
        }
        workspaceRepository.deleteById(id);
    }

    public Filter createFilter(Filter dto) {
        Filter filter = Filter.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .query(dto.getQuery())
                .build();
        return filterRepository.save(filter);
    }

    public Filter updateFilter(UUID id, Filter dto) {
        Filter filter = filterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Filter not found with ID: " + id));
        filter.setTitle(dto.getTitle());
        filter.setDescription(dto.getDescription());
        filter.setQuery(dto.getQuery());
        return filterRepository.save(filter);
    }

    public void deleteFilter(UUID id) {
        if (emailSubscriberRepository.existsByFilterId(id)) {
            throw new IllegalStateException("Cannot delete filter with active subscribers.");
        }
        filterRepository.deleteById(id);
    }
}
