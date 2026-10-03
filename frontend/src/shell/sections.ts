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

export function currentSection(pathname: string): Section | undefined {
  return sections.find((s) => pathname === s.path || pathname.startsWith(`${s.path}/`));
}
