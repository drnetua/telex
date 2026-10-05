import type { IconName } from "../../components/Icon/Icon";
import { messages } from "../../messages";

export interface SettingsEntry {
  id: string;
  path: string;
  icon: IconName;
  title: string;
  hint: string;
}

/** Settings registry: later epics add their row here without touching the shell (QG-3a). */
export const settingsEntries: readonly SettingsEntry[] = [
  {
    id: "accounts",
    path: "/accounts",
    icon: "brand-telegram",
    title: messages.settings.accounts.title,
    hint: messages.settings.accounts.hint,
  },
  {
    id: "profile",
    path: "/profile",
    icon: "user",
    title: messages.settings.profile.title,
    hint: messages.settings.profile.hint,
  },
  {
    id: "models",
    path: "/settings/models",
    icon: "cpu",
    title: messages.settings.models.title,
    hint: messages.settings.models.hint,
  },
];
