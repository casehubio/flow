import { emitPagesEvent } from "@casehubio/pages-data";
import type { LiveSite } from "@casehubio/pages-runtime";

interface NavigationState {
  tab: string;
  entityId?: string;
}

const TAB_TOPICS: Record<string, string> = {
  "cases": "case-explorer",
  "work-items": "work-item",
  "queues": "queue",
  "orchestration": "active-case",
  "trust-audit": "selected-actor",
  "operations": "selected-app",
  "iot": "selected-device",
  "notifications": "notification",
  "sessions": "session",
  "system": "system",
};

const SLUG_TO_LABEL: Record<string, string> = {
  "cases": "Cases",
  "work-items": "Work Items",
  "queues": "Queues",
  "orchestration": "Orchestration",
  "trust-audit": "Trust & Audit",
  "operations": "Operations",
  "iot": "IoT",
  "notifications": "Notifications",
  "sessions": "Sessions",
  "system": "System",
};

export function parseHash(hash: string): NavigationState | null {
  const match = hash.match(/^#\/([a-z-]+)(?:\/(.+))?$/);
  if (!match) return null;
  return { tab: match[1], entityId: match[2] };
}

export function buildHash(tab: string, entityId?: string): string {
  return entityId ? `#/${tab}/${entityId}` : `#/${tab}`;
}

export function setupNavigation(site: LiveSite, appContainer: HTMLElement) {
  function navigate() {
    const state = parseHash(location.hash);
    if (!state) return;
    const label = SLUG_TO_LABEL[state.tab];
    if (label) {
      site.navigate(label);
    }
    if (state.entityId) {
      const topic = TAB_TOPICS[state.tab];
      if (topic) {
        setTimeout(() => {
          emitPagesEvent(appContainer, `${topic}:selected`, { id: state.entityId });
        }, 100);
      }
    }
  }

  window.addEventListener("hashchange", navigate);
  if (location.hash) navigate();
}
