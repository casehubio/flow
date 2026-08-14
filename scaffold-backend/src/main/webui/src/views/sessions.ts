import { hostPanel } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-session-workbench";

registerPanel("session-workbench", "blocks-session-workbench");

export const sessionsView = hostPanel("session-workbench", {
  endpoint: "/api/sessions",
});
