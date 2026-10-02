import {
  IconAlertCircle,
  IconBan,
  IconBrandTelegram,
  IconClock,
  IconInfoCircle,
  IconLock,
  IconLogout,
  IconRefresh,
  IconSearch,
  IconUser,
  IconWifiOff,
  type TablerIcon,
} from "@tabler/icons-react";

const icons = {
  search: IconSearch,
  "alert-circle": IconAlertCircle,
  ban: IconBan,
  "brand-telegram": IconBrandTelegram,
  user: IconUser,
  clock: IconClock,
  "info-circle": IconInfoCircle,
  lock: IconLock,
  logout: IconLogout,
  "wifi-off": IconWifiOff,
  refresh: IconRefresh,
} satisfies Record<string, TablerIcon>;

export type IconName = keyof typeof icons;

export function Icon({ name, size = 24 }: { name: IconName; size?: number }) {
  const Component = icons[name];
  return <Component aria-hidden="true" size={size} stroke={1.5} />;
}
