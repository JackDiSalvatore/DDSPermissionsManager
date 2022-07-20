package io.unityfoundation.dds.permissions.manager;

import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.GroupService;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserService;

import javax.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Controller("/groups")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GroupController {

    private final GroupRepository groupRepository;
    private final UserService userService;
    private final GroupService groupService;

    public GroupController(GroupRepository groupRepository, UserService userService, GroupService groupService) {
        this.groupRepository = groupRepository;
        this.userService = userService;
        this.groupService = groupService;
    }

    @Get
    public HttpResponse index(@Valid Pageable pageable) {
        return HttpResponse.ok(groupRepository.findAll(pageable));
    }

    @Get("/create")
    public HttpResponse create() {
        return HttpResponse.ok();
    }

    @Post("/save")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse<?> save(@Body Group group) {
        groupRepository.save(group);
        return HttpResponse.seeOther(URI.create("/groups/"));
    }


    @Post("/delete/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse<?> delete(Long id) {
        groupRepository.deleteById(id);
        return HttpResponse.seeOther(URI.create("/groups"));
    }

    @Get("/{id}")
    HttpResponse show(Long id) {
        Optional<Group> groupOptional = groupRepository.findById(id);
        if (groupOptional.isPresent()) {
            Group group = groupOptional.get();
            Iterable<User> candidateUsers = userService.listUsersNotInGroup(group);
            return HttpResponse.ok(Map.of("group", group, "candidateUsers", candidateUsers));
        }
        return HttpResponse.notFound();
    }

    @Post("/remove_member/{groupId}/{memberId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse removeMember(Long groupId, Long memberId) {
        Optional<Group> byId = groupRepository.findById(groupId);
        if (byId.isEmpty()) {
            return HttpResponse.notFound();
        }
        Group group = byId.get();
        group.removeUser(memberId);
        groupRepository.update(group);
        return HttpResponse.seeOther(URI.create("/groups/" + groupId));
    }

    @Post("/add_member/{groupId}/{candidateId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse addMember(Long groupId, Long candidateId) {
        if (groupService.addMember(groupId, candidateId)) {
            return HttpResponse.seeOther(URI.create("/groups/" + groupId));
        }
        return HttpResponse.notFound();
    }

}
