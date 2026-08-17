import { rows, dataTable, hostPanel } from "@casehubio/pages-ui";
import { lookup, col, groupBy } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-kpi-metric-row";

registerPanel("kpi-metric-row", "blocks-kpi-metric-row");

export const queuesView = rows(
  hostPanel("kpi-metric-row", { endpoint: "/queues/health" }),
  dataTable({
    lookup: lookup("queues", groupBy(null,
      col("id"), col("name"), col("labelPattern"),
      col("scope"),
    )),
    sortable: true,
    filter: { enabled: true },
  }),
);
