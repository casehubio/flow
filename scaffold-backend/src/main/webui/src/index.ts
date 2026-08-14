import { loadSite } from "@casehubio/pages-runtime";
import { page, tabs } from "@casehubio/pages-ui";
import "@casehubio/pages-ui-tokens";
import { createDatasets } from "./datasets";
import { casesView } from "./views/cases";
import { workItemsView } from "./views/work-items";
import { queuesView } from "./views/queues";
import { orchestrationView } from "./views/orchestration";
import { trustAuditView } from "./views/trust-audit";
import { operationsView } from "./views/operations";
import { iotView } from "./views/iot";
import { notificationsView } from "./views/notifications";
import { sessionsView } from "./views/sessions";
import { systemView } from "./views/system";
import { setupNavigation } from "./navigation";

type TabEntry = [string, ...unknown[]];

async function start() {
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

  const { modules } = await fetch("/api/modules").then(r => r.json());

  const allTabs: TabEntry[] = [
    ["Cases", casesView],
    ["Work Items", workItemsView],
    ["Queues", queuesView],
    ["Orchestration", orchestrationView],
    ["Trust & Audit", trustAuditView],
    ...(modules.includes("ops") ? [["Operations", operationsView] as TabEntry] : []),
    ...(modules.includes("iot") ? [["IoT", iotView] as TabEntry] : []),
    ["Notifications", notificationsView],
    ["Sessions", sessionsView],
    ["System", systemView],
  ];

  const app = page("CaseHub Console",
    tabs(...allTabs),
    { settings: { mode: prefersDark ? "dark" : "light" }, datasets: createDatasets(modules) },
  );

  const container = document.getElementById("app");
  if (container) {
    const site = await loadSite(container, app);
    site.setTheme(prefersDark ? "dark" : "light");
    setupNavigation(site, container);

    if (!location.hash) {
      site.navigate("Cases");
    }

    const picker = document.createElement("pages-theme-picker");
    picker.setAttribute("compact", "");
    picker.target = container;
    container.prepend(picker);

    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
      site.setTheme(e.matches ? "dark" : "light");
    });
  }
}

start().catch(console.error);
