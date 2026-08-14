import { tabs, hostPanel } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-case-explorer";
import "@casehubio/blocks-ui-preferences-editor";
import "@casehubio/blocks-ui-kpi-metric-row";

registerPanel("case-definition-browser", "blocks-case-definition-browser");
registerPanel("preferences-editor", "blocks-preferences-editor");

export const systemView = tabs(
  ["Definitions", hostPanel("case-definition-browser", {
    endpoint: "/api/v1/case-definitions",
  })],
  ["Preferences", hostPanel("preferences-editor", {
    endpoint: "/api/preferences",
  })],
  ["Health", hostPanel("kpi-metric-row", {
    endpoint: "/q/health",
  })],
);
