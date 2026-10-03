import {
  IconAlertCircle,
  IconBan,
  IconBrandTelegram,
  IconCheck,
  IconClock,
  IconDeviceDesktop,
  IconInfoCircle,
  IconLock,
  IconLogout,
  IconMoon,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconSun,
  IconTrash,
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
  plus: IconPlus,
  trash: IconTrash,
  check: IconCheck,
  sun: IconSun,
  moon: IconMoon,
  "device-desktop": IconDeviceDesktop,
} satisfies Record<string, TablerIcon>;

export type IconName = keyof typeof icons;

export function Icon({ name, size = 24 }: { name: IconName; size?: number }) {
  const Component = icons[name];
  return <Component aria-hidden="true" size={size} stroke={1.5} />;
}
