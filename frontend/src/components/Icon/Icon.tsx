import {
  IconAlertCircle,
  IconBan,
  IconClock,
  IconLogout,
  IconRefresh,
  IconSearch,
  IconWifiOff,
  type TablerIcon,
} from "@tabler/icons-react";

const icons = {
  search: IconSearch,
  "alert-circle": IconAlertCircle,
  ban: IconBan,
  clock: IconClock,
  logout: IconLogout,
  "wifi-off": IconWifiOff,
  refresh: IconRefresh,
} satisfies Record<string, TablerIcon>;

export type IconName = keyof typeof icons;

export function Icon({ name, size = 24 }: { name: IconName; size?: number }) {
  const Component = icons[name];
  return <Component aria-hidden="true" size={size} stroke={1.5} />;
}
