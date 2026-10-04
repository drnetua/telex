import { useId } from "react";
import type { ModelProfile, ProfileRef } from "../../api/models";
import { profileKey } from "../../api/models";
import { messages } from "../../messages";
import { Badge } from "../Badge/Badge";
import { Icon } from "../Icon/Icon";
import { PriceLine, Warning } from "./profileDisplay";
import { fallbackWarning } from "./slotText";

const m = messages.models;

export interface ModelProfilePickerProps {
  profiles: ModelProfile[];
  value: ProfileRef;
  onChange: (ref: ProfileRef) => void;
  /** The option whose choice is in flight: it shows a spinner and the other radios are read-only. */
  busy?: ProfileRef | null;
}

const sameRef = (a: ProfileRef, b: ProfileRef) => profileKey(a) === profileKey(b);

/** C-22. One radio per profile: name, Default badge, price per 100 runs and the fallback warning. */
export function ModelProfilePicker({
  profiles,
  value,
  onChange,
  busy = null,
}: ModelProfilePickerProps) {
  const group = useId();
  return (
    <div role="radiogroup" aria-label={m.pickerLabel} tabIndex={-1} className="d-grid gap-2">
      {profiles.map((p) => {
        const key = profileKey(p.ref);
        const checked = sameRef(p.ref, value);
        const inFlight = busy !== null && sameRef(p.ref, busy);
        const warning = fallbackWarning(p.slots.text);
        return (
          <label key={key} className="form-check card card-body py-2 mb-0">
            <span className="d-flex align-items-start gap-2">
              <input
                type="radio"
                className="form-check-input mt-1 flex-shrink-0"
                name={group}
                value={key}
                checked={checked}
                disabled={busy !== null || !p.choosable}
                onChange={() => onChange(p.ref)}
              />
              <span className="d-flex flex-column gap-1">
                <span className="d-flex flex-wrap align-items-center gap-2">
                  <span className="fw-medium">{p.name}</span>
                  {checked ? (
                    <Badge tone="success" icon="check">
                      {m.defaultBadge}
                    </Badge>
                  ) : null}
                  {inFlight ? (
                    <span className="spinner-border spinner-border-sm" aria-hidden="true" />
                  ) : null}
                </span>
                {p.choosable ? (
                  <span className="text-secondary small">
                    <PriceLine price={p.pricePer100Runs} />
                  </span>
                ) : (
                  <span className="d-flex align-items-center gap-1 text-secondary small">
                    <Icon name="circle-off" size={14} />
                    <span>{m.noModelForText}</span>
                  </span>
                )}
                {warning ? <Warning>{warning}</Warning> : null}
              </span>
            </span>
          </label>
        );
      })}
    </div>
  );
}
