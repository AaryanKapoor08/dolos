package com.dolos.casework.casecmd;

import com.dolos.casework.casecmd.command.AddEvidence;
import com.dolos.casework.casecmd.command.AssignCase;
import com.dolos.casework.casecmd.command.CloseCase;
import com.dolos.casework.casecmd.command.Escalate;
import com.dolos.casework.casecmd.command.FileReport;
import com.dolos.casework.casecmd.command.OpenCase;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

/**
 * The command-side API of {@code casecmd} (Phase 3B): a thin, typed facade over the Axon
 * {@link CommandGateway}. Other modules (the REST controller now, the BPMN workflow in 3D and the
 * Kafka integration in 3E) drive the {@code Case} aggregate through this service rather than building
 * raw command messages, which keeps the command records an internal detail of this module.
 *
 * <p>Each call uses {@code sendAndWait}, so a rejected command (e.g. acting on a closed case)
 * propagates its exception to the caller synchronously for mapping to an HTTP status.
 */
@Service
public class CaseCommandService {

    private final CommandGateway commandGateway;

    public CaseCommandService(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * Opens a new case, generating its id.
     *
     * @return the new case id
     */
    public UUID openCase(UUID alertId, String accountId, int score, String openedBy) {
        UUID caseId = UUID.randomUUID();
        commandGateway.sendAndWait(new OpenCase(caseId, alertId, accountId, score, openedBy));
        return caseId;
    }

    public void assign(UUID caseId, String assignee, String assignedBy) {
        commandGateway.sendAndWait(new AssignCase(caseId, assignee, assignedBy));
    }

    public void addEvidence(UUID caseId, String note, String addedBy) {
        commandGateway.sendAndWait(new AddEvidence(caseId, note, addedBy));
    }

    public void escalate(UUID caseId, String reason, String escalatedBy) {
        commandGateway.sendAndWait(new Escalate(caseId, reason, escalatedBy));
    }

    public void fileReport(UUID caseId, String reportReference, String filedBy) {
        commandGateway.sendAndWait(new FileReport(caseId, reportReference, filedBy));
    }

    public void closeCase(UUID caseId, String resolution, String closedBy) {
        commandGateway.sendAndWait(new CloseCase(caseId, resolution, closedBy));
    }
}
