import { useRef, useState } from "react";
import { useMe } from "../../api/account";
import { useChangeTimeZone, useListTimeZones } from "../../api/preferences";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { Toast } from "../../components/Toast/Toast";
import { TimeZonePicker } from "../../components/TimeZonePicker/TimeZonePicker";
import { cityOf } from "../../components/TimeZonePicker/zoneNames";
import { messages } from "../../messages";

const m = messages.timeZone;

/** "UTC+03:00" for the zone right now, read from `Intl`. */
function offsetOf(zone: string): string {
  try {
    const part = new Intl.DateTimeFormat("en", { timeZone: zone, timeZoneName: "longOffset" })
      .formatToParts(new Date())
      .find((p) => p.type === "timeZoneName")?.value;
    if (!part) return "UTC+00:00";
    return part === "GMT" ? "UTC+00:00" : part.replace("GMT", "UTC");
  } catch {
    return "UTC+00:00";
  }
}

type Outcome = "saved" | "failed" | null;

export function TimeZoneCard() {
  const me = useMe().data;
  const change = useChangeTimeZone();
  const [picking, setPicking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [refused, setRefused] = useState(false);
  const [outcome, setOutcome] = useState<Outcome>(null);
  const lastPick = useRef<string | null>(null);
  const list = useListTimeZones(picking);

  const zone = me?.timeZone ?? "UTC";

  async function save(next: string) {
    lastPick.current = next;
    setPicking(false);
    setSaving(true);
    setRefused(false);
    setOutcome(null);
    const result = await change(next);
    setSaving(false);
    if (result === "refused") setRefused(true);
    else setOutcome(result);
  }

  function open() {
    setRefused(false);
    setOutcome(null);
    setPicking(true);
  }

  return (
    <section className="card mb-4" aria-labelledby="time-zone-title">
      <div className="card-header">
        <h2 id="time-zone-title" className="card-title">
          {m.title}
        </h2>
      </div>
      <div className="card-body">
        {me?.timeZoneIsFallback ? (
          <div className="alert alert-info d-flex align-items-center gap-2 mb-3" role="status">
            <Icon name="info-circle" size={18} />
            <span>{m.fallbackHint}</span>
            <button type="button" className="btn btn-link ms-auto" onClick={open}>
              {m.chooseYours}
            </button>
          </div>
        ) : null}
        <div className="d-flex justify-content-between align-items-center gap-2">
          <div>
            <h4 className="mb-0">{cityOf(zone)}</h4>
            <small className="d-block text-secondary">{m.detail(zone, offsetOf(zone))}</small>
          </div>
          <Button className="btn-secondary" busy={saving} onClick={open}>
            {saving ? m.saving : m.change}
          </Button>
        </div>
        {refused ? (
          <div className="invalid-feedback d-flex align-items-center gap-1 mt-2">
            <Icon name="alert-circle" size={16} />
            {m.refused}
          </div>
        ) : null}
        <small className="d-block text-secondary mt-2">{m.hint}</small>
      </div>
      {picking ? (
        <TimeZonePicker
          zones={list.data}
          current={me?.timeZone ?? null}
          loading={list.isPending}
          failed={list.isError}
          onRetry={() => void list.refetch()}
          onPick={(z) => void save(z)}
          onCancel={() => setPicking(false)}
        />
      ) : null}
      {outcome === "saved" ? (
        <Toast message={m.saved} dismissLabel={m.dismiss} onDismiss={() => setOutcome(null)} />
      ) : null}
      {outcome === "failed" ? (
        <Toast
          tone="error"
          message={m.saveFailed}
          dismissLabel={m.dismiss}
          action={{
            label: m.tryAgain,
            onClick: () => lastPick.current && void save(lastPick.current),
          }}
          onDismiss={() => setOutcome(null)}
        />
      ) : null}
    </section>
  );
}
