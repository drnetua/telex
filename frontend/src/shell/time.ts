import { useEffect, useRef } from "react";
import type { Me } from "../api/account";
import { useSaveDetectedTimeZoneRequest } from "../api/preferences";

/** The device's named region, or null when it can't be read. */
export function deviceTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

const DATE_OPTIONS: Intl.DateTimeFormatOptions = {
  day: "numeric",
  month: "short",
  year: "numeric",
};

/** Every date shown goes through here: the Owner's saved zone, else the device's. */
export function formatInstant(
  iso: string,
  timeZone: string | null,
  options: Intl.DateTimeFormatOptions = DATE_OPTIONS,
): string {
  const date = new Date(iso);
  try {
    return new Intl.DateTimeFormat("en-GB", { ...options, timeZone: timeZone ?? undefined }).format(
      date,
    );
  } catch {
    return new Intl.DateTimeFormat("en-GB", options).format(date);
  }
}

/** Saves the device zone once per open while the Owner has none saved (the server writes only if unset). */
export function useSaveDetectedTimeZone(me: Me | undefined) {
  const save = useSaveDetectedTimeZoneRequest();
  const sent = useRef(false);
  const needed = me !== undefined && me.timeZone === null;
  useEffect(() => {
    if (!needed || sent.current) return;
    sent.current = true;
    save(deviceTimeZone());
  }, [needed, save]);
}
