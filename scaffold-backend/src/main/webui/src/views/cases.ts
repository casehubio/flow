import { rows, columns, dataTable, title } from "@casehubio/pages-ui";
import { lookup, col, groupBy } from "@casehubio/pages-ui";

export const casesView = rows(
  dataTable({
    lookup: lookup("cases", groupBy(null,
      col("caseId"), col("name"), col("status"),
      col("namespace"), col("version"), col("createdAt"),
    )),
    sortable: true,
    filter: { enabled: true },
    selectionTopic: "case",
  }),
);
