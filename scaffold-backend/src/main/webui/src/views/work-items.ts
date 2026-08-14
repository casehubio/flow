import { hostPanel } from "@casehubio/pages-ui";
import { registerPanel } from "@casehubio/pages-runtime";
import "@casehubio/blocks-ui-work-item-workbench";

registerPanel("work-item-workbench", "blocks-work-item-workbench");

export const workItemsView = hostPanel("work-item-workbench", {});
