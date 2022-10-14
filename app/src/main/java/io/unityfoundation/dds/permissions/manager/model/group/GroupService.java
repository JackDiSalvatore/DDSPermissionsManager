package io.unityfoundation.dds.permissions.manager.model.group;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthenticationException;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class GroupService {

    private final GroupRepository groupRepository;
    private final ApplicationPermissionRepository applicationPermissionRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;


    public GroupService(GroupRepository groupRepository, ApplicationPermissionRepository applicationPermissionRepository, SecurityUtil securityUtil,
                        GroupUserService groupUserService) {
        this.groupRepository = groupRepository;
        this.applicationPermissionRepository = applicationPermissionRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<GroupDTO> findAll(Pageable pageable, String filter) {
        return getGroupPage(pageable, filter).map(group -> {
            GroupDTO groupsResponseDTO = new GroupDTO();
            groupsResponseDTO.setGroupFields(group);
            groupsResponseDTO.setTopics(group.getTopics().stream().map(Topic::getId).collect(Collectors.toSet()));
            groupsResponseDTO.setApplications(group.getApplications().stream().map(Application::getId).collect(Collectors.toSet()));
            groupsResponseDTO.setTopicCount(group.getTopics().size());
            groupsResponseDTO.setApplicationCount(group.getApplications().size());
            groupsResponseDTO.setMembershipCount(groupUserService.getMembershipCountByGroup(group));

            return groupsResponseDTO;
        });
    }

    private Page<Group> getGroupPage(Pageable pageable, String filter) {
        if (!pageable.isSorted()) {
            pageable = pageable.order(Sort.Order.asc("name"));
        }

        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                return groupRepository.findAll(pageable);
            }

            return groupRepository.findAllByNameContainsIgnoreCase(filter, pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groupsList = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());

            if (filter == null) {
                return groupRepository.findAllByIdIn(groupsList, pageable);
            }

            return groupRepository.findAllByIdInAndNameContainsIgnoreCase(groupsList, filter, pageable);
        }
    }

    public MutableHttpResponse<?> save(Group group) {
        if (!securityUtil.isCurrentUserAdmin()) {
            return HttpResponse.unauthorized();
        } else if (group != null && group.getName() == null) {
            return HttpResponse.badRequest("Group name cannot be null");
        }

        Optional<Group> searchGroupByName = groupRepository.findByName(group.getName().trim());

        if (group.getId() == null) {
            if (searchGroupByName.isPresent()) {
                return HttpResponseFactory.INSTANCE.status(HttpStatus.SEE_OTHER, searchGroupByName.get());
            }
            return HttpResponse.ok(groupRepository.save(group));
        } else {
            if (searchGroupByName.isPresent()) {
                return HttpResponse.badRequest("Group with same name already exists");
            }
            return HttpResponse.ok(groupRepository.update(group));
        }
    }

    public MutableHttpResponse<?> deleteById(Long id) {
        if (!securityUtil.isCurrentUserAdmin()) {
            return HttpResponse.unauthorized();
        }
        Optional<Group> groupOptional = groupRepository.findById(id);
        if (groupOptional.isEmpty()) {
            return HttpResponse.notFound("Group not found");
        }

        Group group = groupOptional.get();
        groupUserService.removeByGroup(group);
        applicationPermissionRepository.deleteByPermissionsApplicationIdIn(group.getApplications().stream().map(Application::getId).collect(Collectors.toList()));
        applicationPermissionRepository.deleteByPermissionsTopicIdIn(group.getTopics().stream().map(Topic::getId).collect(Collectors.toList()));
        groupRepository.deleteById(id);

        return HttpResponse.seeOther(URI.create("/api/groups"));
    }

    public Page<GroupSearchDTO> search(String filter, GroupAdminRole role, Pageable pageable) {

        return getGroupSearchPage(filter, role, pageable).map(group -> {
            GroupSearchDTO groupsResponseDTO = new GroupSearchDTO();
            groupsResponseDTO.setGroupFields(group);

            return groupsResponseDTO;
        });
    }

    private Page<Group> getGroupSearchPage(String filter, GroupAdminRole role, Pageable pageable) {

        if (securityUtil.isCurrentUserAdmin()) {
            if (!pageable.isSorted()) {
                pageable = pageable.order(Sort.Order.asc("name"));
            }

            return groupRepository.findAllByNameContainsIgnoreCase(filter, pageable);
        } else {
            if (!pageable.isSorted()) {
                pageable = pageable.order(Sort.Order.asc("permissionsGroup.name"));
            }

            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            return groupUserService.getAllGroupsUserIsAnAdminOf(user, filter, pageable, role);
        }
    }
}
