package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.RecipientGroupCreateDto;
import com.anushibinj.veemailer.dto.RecipientGroupResponseDto;
import com.anushibinj.veemailer.dto.RecipientGroupUpdateDto;
import com.anushibinj.veemailer.model.RecipientGroup;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.RecipientGroupRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipientGroupService {

    private final RecipientGroupRepository recipientGroupRepository;
    private final WorkspaceRepository workspaceRepository;

    // ── Queries ──────────────────────────────────────────────────────────────

    public List<RecipientGroupResponseDto> getGroupsForWorkspace(UUID workspaceId) {
        return recipientGroupRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public RecipientGroupResponseDto getGroup(UUID workspaceId, UUID groupId) {
        RecipientGroup group = findGroupOrThrow(workspaceId, groupId);
        return toDto(group);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Transactional
    public RecipientGroupResponseDto createGroup(UUID workspaceId, RecipientGroupCreateDto dto, String createdBy) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        if (recipientGroupRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName().trim())) {
            throw new IllegalArgumentException("A group named '" + dto.getName().trim() + "' already exists in this workspace");
        }

        RecipientGroup group = RecipientGroup.builder()
                .workspace(workspace)
                .name(dto.getName().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .memberEmails(normalizeMemberEmails(dto.getMemberEmails()))
                .createdAt(LocalDateTime.now())
                .createdBy(createdBy)
                .build();

        return toDto(recipientGroupRepository.save(group));
    }

    @Transactional
    public RecipientGroupResponseDto updateGroup(UUID workspaceId, UUID groupId, RecipientGroupUpdateDto dto) {
        RecipientGroup group = findGroupOrThrow(workspaceId, groupId);

        // Check name uniqueness only if the name is actually changing
        if (!group.getName().equalsIgnoreCase(dto.getName().trim())) {
            if (recipientGroupRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, dto.getName().trim())) {
                throw new IllegalArgumentException("A group named '" + dto.getName().trim() + "' already exists in this workspace");
            }
        }

        group.setName(dto.getName().trim());
        group.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        group.setMemberEmails(normalizeMemberEmails(dto.getMemberEmails()));

        return toDto(recipientGroupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(UUID workspaceId, UUID groupId) {
        RecipientGroup group = findGroupOrThrow(workspaceId, groupId);
        recipientGroupRepository.delete(group);
    }

    @Transactional
    public RecipientGroupResponseDto addMember(UUID workspaceId, UUID groupId, String email) {
        RecipientGroup group = findGroupOrThrow(workspaceId, groupId);
        group.getMemberEmails().add(email.trim().toLowerCase());
        return toDto(recipientGroupRepository.save(group));
    }

    @Transactional
    public RecipientGroupResponseDto removeMember(UUID workspaceId, UUID groupId, String email) {
        RecipientGroup group = findGroupOrThrow(workspaceId, groupId);
        group.getMemberEmails().remove(email.trim().toLowerCase());
        return toDto(recipientGroupRepository.save(group));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RecipientGroup findGroupOrThrow(UUID workspaceId, UUID groupId) {
        return recipientGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient group not found"));
    }

    private LinkedHashSet<String> normalizeMemberEmails(java.util.Set<String> emails) {
        if (emails == null) return new LinkedHashSet<>();
        return emails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private RecipientGroupResponseDto toDto(RecipientGroup group) {
        return RecipientGroupResponseDto.builder()
                .id(group.getId())
                .workspaceId(group.getWorkspace().getId())
                .name(group.getName())
                .description(group.getDescription())
                .memberEmails(group.getMemberEmails())
                .memberCount(group.getMemberEmails() != null ? group.getMemberEmails().size() : 0)
                .createdAt(group.getCreatedAt())
                .createdBy(group.getCreatedBy())
                .build();
    }
}
