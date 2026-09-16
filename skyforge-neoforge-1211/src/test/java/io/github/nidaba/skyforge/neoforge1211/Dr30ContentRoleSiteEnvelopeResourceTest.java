package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Dr30ContentRoleSiteEnvelopeResourceTest {
    @Test
    void exposesDeterministicContentContractForImplementation() throws IOException {
        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String contract = Files.readString(project.resolve(
                "../docs/agent-state/DR30_CONTENT_ROLE_SITE_ENVELOPE.json"));

        assertTrue(contract.contains("\"contract_id\": \"CONTENT-DR30-ROLE-SITE-001\""));
        assertTrue(contract.contains("\"id\": \"FREIGHT_TRANSFER_EDGE\""));
        assertTrue(contract.contains("AUTH-0096 physical surface support"));
        assertTrue(contract.contains("AUTH-0097 observed-open directional sample"));
        assertTrue(contract.contains("first eligible candidate in unchanged AUTH-0097 canonical anchor order"));
        assertTrue(contract.contains("\"no_eligible_candidate\": \"REPLAN_REQUIRED\""));
        assertTrue(contract.contains("FAIL_CLOSED_CONTENT_ROLE_REQUIRED"));
        assertTrue(contract.contains("\"thresholds\": []"));
        assertTrue(contract.contains("\"id\": \"C24\""));
        assertTrue(contract.contains("\"id\": \"AUTH-0096\""));
        assertTrue(contract.contains("\"id\": \"AUTH-0097\""));
    }
}
