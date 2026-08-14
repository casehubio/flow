import { tabs, hostPanel } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-notification-inbox";

registerPanel("notification-inbox", "blocks-notification-inbox");
registerPanel("notification-preferences", "blocks-notification-preferences");

export const notificationsView = tabs(
  ["Inbox", hostPanel("notification-inbox", {
    endpoint: "/notifications",
  })],
  ["Preferences", hostPanel("notification-preferences", {
    endpoint: "/subscriptions",
  })],
);
