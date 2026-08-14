import { rows, dataTable, hostPanel } from "@casehubio/pages-ui";
import { lookup, col, groupBy } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-orchestration-workbench";

registerPanel("orchestration-workbench", "blocks-orchestration-workbench");

export const orchestrationView = rows(
  dataTable({
    lookup: lookup("cases", groupBy(null,
      col("caseId"), col("name"), col("status"),
      col("namespace"), col("createdAt"),
    )),
    sortable: true,
    filter: { enabled: true },
    selectionTopic: "active-case",
  }),
  hostPanel("orchestration-workbench", {
    endpoint: "/api/v1",
    "case-id": "#{row.caseId}",
  }),
);
