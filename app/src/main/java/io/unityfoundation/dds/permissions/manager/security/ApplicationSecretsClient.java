package io.unityfoundation.dds.permissions.manager.security;

import com.google.cloud.secretmanager.v1.*;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

@Singleton
public class ApplicationSecretsClient {

    @Property(name = "gcp.project-id")
    protected String project;
    @Property(name = "gcp.credentials.enabled")
    protected Boolean enabled;
    private String identityCACert;
    private String identityCAKey;
    private String permissionsCACert;
    private String permissionsCAKey;
    private String governanceFile;

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationSecretsClient.class);

    public ApplicationSecretsClient() {
    }

    @EventListener
    public void onStartup(StartupEvent event) throws IOException {

        if (project != null && enabled != null && enabled) {
            try {
                SecretManagerServiceClient client = SecretManagerServiceClient.create();
                this.identityCACert =  getLatestSecret(client, project, "identity_ca_pem");
                this.identityCAKey =  getLatestSecret(client, project, "identity_ca_key_pem");
                this.permissionsCACert = getLatestSecret(client, project, "permissions_ca_pem");
                this.permissionsCAKey = getLatestSecret(client, project, "permissions_ca_key_pem");
                this.governanceFile = getLatestSecret(client, project, "governance_xml_p7s");
            } catch (Exception e) {
                LOG.error("Could not get secrets from GCP: " + e.getMessage());
                // all or nothing
                this.identityCACert = null;
                this.identityCAKey = null;
                this.permissionsCACert = null;
                this.permissionsCAKey = null;
                this.governanceFile = null;
            }
        }
    }

    private String getLatestSecret(SecretManagerServiceClient client, String project, String file) {
        AccessSecretVersionResponse response = client.accessSecretVersion(AccessSecretVersionRequest
                .newBuilder()
                .setName(SecretVersionName.of(project, file, "latest").toString())
                .build());
        return response.getPayload().getData().toStringUtf8();
    }

    public Optional<String> getIdentityCACert() {
        return Optional.ofNullable(identityCACert);
    }

    public Optional<String> getPermissionsCACert() {
        return Optional.ofNullable(permissionsCACert);
    }

    public Optional<String> getGovernanceFile() {
        return Optional.ofNullable(governanceFile);
    }

    public Optional<String> getIdentityCAKey() {
        return Optional.ofNullable(identityCAKey);
    }

    public Optional<String> getPermissionsCAKey() {
        return Optional.ofNullable(permissionsCAKey);
    }
}