package io.casehub.flow;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.worker.api.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Milestone;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import java.util.Map;

public class TestCaseDefinitions {

  public static class DocumentApprovalV1CaseHub extends CaseHub {

    private final Capability approveCapability =
        Capability.builder()
            .name("approveDocument")
            .description("Approve a document")
            .inputSchema("{ documentId: .documentId, status: .status }")
            .outputSchema("{ approved: true, status: .status }")
            .build();

    private final Milestone submittedMilestone =
        Milestone.builder()
            .name("documentSubmitted")
            .description("Document has been submitted for approval")
            .entryCriteria(".status == \"submitted\"")
            .completionCriteria(".status == \"approved\"")
            .build();

    private final Goal approvalGoal =
        Goal.builder()
            .name("documentApproved")
            .description("Document is approved")
            .condition(".status == \"approved\"")
            .kind(GoalKind.SUCCESS)
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Document Approval")
          .version("1.0.0")
          .capabilities(approveCapability)
          .workers(
              Worker.builder()
                  .name("approver-worker")
                  .description("Approves documents")
                  .capabilityName("approveDocument")
                  .function(input -> WorkerResult.of(Map.of("approved", true, "status", "approved")))
                  .build())
          .bindings(
              Binding.builder()
                  .name("approve-on-submit")
                  .capability(approveCapability)
                  .on(new ContextChangeTrigger(".status == \"submitted\""))
                  .build())
          .milestones(submittedMilestone)
          .goals(approvalGoal)
          .completion(GoalExpression.allOf(approvalGoal))
          .build();
    }
  }

  public static class DocumentApprovalV2CaseHub extends CaseHub {

    private final Capability approveCapability =
        Capability.builder()
            .name("approveDocument")
            .inputSchema("{ documentId: .documentId, status: .status }")
            .outputSchema("{ approved: true, status: .status }")
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Document Approval")
          .version("2.0.0")
          .capabilities(approveCapability)
          .workers(
              Worker.builder()
                  .name("approver-worker-v2")
                  .capabilityName("approveDocument")
                  .function(input -> WorkerResult.of(Map.of("approved", true, "status", "approved")))
                  .build())
          .bindings(
              Binding.builder()
                  .name("approve-on-submit")
                  .capability(approveCapability)
                  .on(new ContextChangeTrigger(".status == \"submitted\""))
                  .build())
          .build();
    }
  }

  public static class InvoiceProcessingCaseHub extends CaseHub {

    private final Capability processCapability =
        Capability.builder()
            .name("processInvoice")
            .inputSchema("{ invoiceId: .invoiceId, amount: .amount }")
            .outputSchema("{ processed: true }")
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Invoice Processing")
          .version("1.0.0")
          .capabilities(processCapability)
          .workers(
              Worker.builder()
                  .name("invoice-processor")
                  .capabilityName("processInvoice")
                  .function(input -> WorkerResult.of(Map.of("processed", true)))
                  .build())
          .bindings(
              Binding.builder()
                  .name("process-on-receive")
                  .capability(processCapability)
                  .on(new ContextChangeTrigger(".status == \"received\""))
                  .build())
          .build();
    }
  }
}
