import type { IconName } from "../components/Icon/Icon";
import type { messages } from "../messages";

export type SectionId = keyof typeof messages.shell.sections;

export interface Section {
  id: SectionId;
  path: string;
  icon: IconName;
  /** Where the section sits on a phone: in the bottom bar or under More. */
  phone: "bar" | "more";
  /** What the route renders; a real page replaces "coming-soon" when its epic lands. */
  page: "page" | "coming-soon";
}

/** Section registry (ADR-0006): the menu, the phone bar and the More sheet are all generated from it, in app-map order. */
export const sections: readonly Section[] = [
  {
    id: "overview",
    path: "/overview",
    icon: "layout-dashboard",
    phone: "more",
    page: "coming-soon",
  },
  { id: "inbox", path: "/inbox", icon: "inbox", phone: "bar", page: "page" },
  { id: "chats", path: "/chats", icon: "messages", phone: "bar", page: "coming-soon" },
  { id: "assistants", path: "/assistants", icon: "sparkles", phone: "bar", page: "coming-soon" },
  { id: "runs", path: "/runs", icon: "activity", phone: "more", page: "coming-soon" },
  { id: "tasks", path: "/tasks", icon: "checklist", phone: "bar", page: "coming-soon" },
  { id: "settings", path: "/settings", icon: "settings", phone: "more", page: "page" },
];

/** Addresses that sit under a section without being its own entry, e.g. Profile and security under Settings. */
const owned: Readonly<Record<string, SectionId>> = {
  "/profile": "settings",
  "/accounts": "settings",
};

export function currentSection(pathname: string): Section | undefined {
  const underPath = (base: string) => pathname === base || pathname.startsWith(`${base}/`);
  const owner = Object.keys(owned).find(underPath);
  if (owner) return sections.find((s) => s.id === owned[owner]);
  return sections.find((s) => underPath(s.path));
}
