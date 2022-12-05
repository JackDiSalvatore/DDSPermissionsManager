package io.unityfoundation.dds.permissions.manager.model.applicationpermission;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Singleton
public class ApplicationPermissionService {

    private final ApplicationPermissionRepository applicationPermissionRepository;
    private final ApplicationRepository applicationRepository;
    private final TopicRepository topicRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public ApplicationPermissionService(ApplicationPermissionRepository applicationPermissionRepository, ApplicationRepository applicationRepository, TopicRepository topicRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.applicationPermissionRepository = applicationPermissionRepository;
        this.applicationRepository = applicationRepository;
        this.topicRepository = topicRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<AccessPermissionDTO> findAll(Long applicationId, Long topicId, Pageable pageable) {
        return getApplicationPermissionsPage(applicationId, topicId, pageable).map(applicationPermission -> new AccessPermissionDTO(
                applicationPermission.getId(), applicationPermission.getPermissionsTopic().getId(),
                applicationPermission.getPermissionsTopic().getName(), applicationPermission.getPermissionsApplication().getId(),
                applicationPermission.getPermissionsApplication().getName(),
                applicationPermission.getPermissionsApplication().getPermissionsGroup().getName(), applicationPermission.getAccessType()
        ));
    }

    private Page<ApplicationPermission> getApplicationPermissionsPage(Long applicationId, Long topicId, Pageable pageable) {
        if (applicationId == null && topicId == null) {
            return applicationPermissionRepository.findAll(pageable);
        } else if (applicationId != null && topicId == null) {
            return applicationPermissionRepository.findByPermissionsApplicationId(applicationId, pageable);
        } else if (topicId != null && applicationId == null) {
            return applicationPermissionRepository.findByPermissionsTopicId(topicId, pageable);
        } else {
            return applicationPermissionRepository.findByPermissionsApplicationIdAndPermissionsTopicId(
                    applicationId, topicId, pageable);
        }
    }

    public HttpResponse<AccessPermissionDTO> addAccess(Long applicationId, Long topicId, AccessType access) {
        final HttpResponse response;

        Optional<Application> applicationById = applicationRepository.findById(applicationId);
        if (applicationById.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Optional<Topic> topicById = topicRepository.findById(topicId);
            if (topicById.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
            } else {
                Topic topic = topicById.get();

                User user = securityUtil.getCurrentlyAuthenticatedUser().get();
                if (!securityUtil.isCurrentUserAdmin() &&
                        !groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId())) {
                    throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
                } else {
                    Application application = applicationById.get();
                    response = addAccess(application, topic, access);
                }
            }
        }

        return response;
    }

    public HttpResponse addAccess(Application application, Topic topic, AccessType access) {
        ApplicationPermission newPermission = saveNewPermission(application, topic, access);
        AccessPermissionDTO dto = createDTO(newPermission);
        return HttpResponse.created(dto);
    }

    public ApplicationPermission saveNewPermission(Application application, Topic topic, AccessType access) {
        ApplicationPermission applicationPermission = new ApplicationPermission(application, topic, access);
        return applicationPermissionRepository.save(applicationPermission);
    }

    public AccessPermissionDTO createDTO(ApplicationPermission applicationPermission) {
        Long topicId = applicationPermission.getPermissionsTopic().getId();
        String topicName = applicationPermission.getPermissionsTopic().getName();
        Long applicationId = applicationPermission.getPermissionsApplication().getId();
        String applicationName = applicationPermission.getPermissionsApplication().getName();
        String applicationGroupName = applicationPermission.getPermissionsApplication().getPermissionsGroup().getName();
        AccessType accessType = applicationPermission.getAccessType();
        return new AccessPermissionDTO(applicationPermission.getId(), topicId, topicName, applicationId, applicationName,
                applicationGroupName, accessType);
    }

    public HttpResponse deleteById(Long permissionId) {

        Optional<ApplicationPermission> applicationPermissionOptional = applicationPermissionRepository.findById(permissionId);

        if (applicationPermissionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Topic topic = applicationPermissionOptional.get().getPermissionsTopic();
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() && !groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        applicationPermissionRepository.deleteById(permissionId);
        return HttpResponse.noContent();
    }

    public HttpResponse<AccessPermissionDTO> updateAccess(Long permissionId, AccessType access) {
        if (Arrays.stream(AccessType.values()).noneMatch(accessType -> accessType.equals(access))) {
            return HttpResponse.badRequest();
        }

        Optional<ApplicationPermission> applicationPermissionOptional = applicationPermissionRepository.findById(permissionId);

        if (applicationPermissionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Topic topic = applicationPermissionOptional.get().getPermissionsTopic();
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() && !groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        ApplicationPermission applicationPermission = applicationPermissionOptional.get();
        applicationPermission.setAccessType(access);

        return HttpResponse.ok(createDTO(applicationPermissionRepository.update(applicationPermission)));
    }

    public void deleteAllByTopic(Topic topic) {
        applicationPermissionRepository.deleteByPermissionsTopicEquals(topic);
    }
    public void deleteAllByApplication(Application application) {
        applicationPermissionRepository.deleteByPermissionsApplicationEquals(application);
    }

    public List<ApplicationPermission> findAllByApplication(Application application) {
        return applicationPermissionRepository.findByPermissionsApplication(application);
    }
}
