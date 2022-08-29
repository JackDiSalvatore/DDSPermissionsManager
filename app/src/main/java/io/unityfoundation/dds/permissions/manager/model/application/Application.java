package io.unityfoundation.dds.permissions.manager.model.application;

import io.micronaut.core.annotation.NonNull;

import javax.persistence.*;

@Entity
@Table(name = "permissions_applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String name;

    @NonNull
    private Long permissionsGroup;

    public Application() {
    }

    public Application(@NonNull String name, @NonNull Long permissionsGroup) {
        this.name = name;
        this.permissionsGroup = permissionsGroup;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public Long getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(Long permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }
}
