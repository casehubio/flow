import { rows, columns, dataTable, hostPanel, title } from "@casehubio/pages-ui";
import { lookup, col, groupBy } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-kpi-metric-row";
import "@casehubio/blocks-ui-approval-gate";

registerPanel("approval-gate", "blocks-approval-gate");

export const operationsView = rows(
  hostPanel("kpi-metric-row", { endpoint: "/api/applications/health" }),
  columns([4, 8],
    [dataTable({
      lookup: lookup("applications", groupBy(null,
        col("id"), col("name"), col("status"),
        col("version"), col("lastDeployed"),
      )),
      sortable: true,
      selectionTopic: "selected-app",
    })],
    [rows(
      title("Deployments", "h3"),
      dataTable({
        lookup: lookup("applications", groupBy(null,
          col("deploymentId"), col("version"),
          col("status"), col("timestamp"),
        )),
      }),
      hostPanel("approval-gate", {
        endpoint: "/api/approvals",
      }),
    )],
  ),
);
