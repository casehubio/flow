import { bind, restSource } from "@casehubio/pages-ui";
import type { DataSetId } from "@casehubio/pages-data";

function rest(id: string, url: string, opts?: {
  dataPath?: string;
  expression?: string;
  refreshTime?: string;
}) {
  const binding = bind(id, restSource(url, id as DataSetId, opts));
  return opts?.refreshTime ? { ...binding, refreshTime: opts.refreshTime } : binding;
}

export function createDatasets(modules: string[]) {
  const datasets = [
    rest("cases", "/api/v1/cases", { dataPath: "items", refreshTime: "10second" }),
    rest("case-definitions", "/api/v1/case-definitions", { dataPath: "items" }),
    rest("workers", "/api/v1/workers", { refreshTime: "10second" }),
    rest("work-items", "/workitems", { refreshTime: "5second" }),
    rest("queues", "/queues", { refreshTime: "10second" }),
    rest("actors", "/workitems/actors", { refreshTime: "30second" }),
    rest("audit", "/audit"),
    rest("plan-items", "/api/v1/cases/#{row.caseId}/plan-items"),
    rest("goals", "/api/v1/cases/#{row.caseId}/goals"),
    rest("case-context", "/api/v1/cases/#{row.caseId}/context"),
  ];

  if (modules.includes("ops")) {
    datasets.push(
      rest("applications", "/api/applications", { refreshTime: "10second" }),
      rest("approvals", "/api/approvals", { refreshTime: "5second" }),
    );
  }

  if (modules.includes("iot")) {
    datasets.push(
      rest("devices", "/api/devices", { refreshTime: "5second" }),
      rest("situations", "/api/situations", { refreshTime: "10second" }),
      rest("providers", "/api/providers", { refreshTime: "30second" }),
    );
  }

  return datasets;
}
