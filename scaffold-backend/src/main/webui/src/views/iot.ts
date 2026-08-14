import { rows, columns, dataTable, title } from "@casehubio/pages-ui";
import { lookup, col, groupBy } from "@casehubio/pages-ui";

export const iotView = columns([4, 8],
  [dataTable({
    lookup: lookup("devices", groupBy(null,
      col("id"), col("name"), col("type"),
      col("status"), col("provider"),
    )),
    sortable: true,
    filter: { enabled: true },
    selectionTopic: "selected-device",
  })],
  [rows(
    title("Device State", "h3"),
    dataTable({
      lookup: lookup("devices", groupBy(null,
        col("property"), col("value"),
        col("lastUpdated"),
      )),
    }),
    title("Situations", "h3"),
    dataTable({
      lookup: lookup("situations", groupBy(null,
        col("id"), col("name"), col("severity"),
        col("status"), col("detectedAt"),
      )),
      sortable: true,
    }),
  )],
);
