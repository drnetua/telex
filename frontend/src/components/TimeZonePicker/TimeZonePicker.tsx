import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { messages } from "../../messages";
import { Button } from "../Button/Button";
import { Icon } from "../Icon/Icon";
import { LoadState } from "../LoadState/LoadState";
import { cityOf } from "./zoneNames";

interface TimeZonePickerProps {
  zones?: readonly string[];
  /** The Owner's current zone, marked in the list. */
  current: string | null;
  loading?: boolean;
  failed?: boolean;
  onRetry?: () => void;
  onPick: (zone: string) => void;
  onCancel: () => void;
}

const m = messages.timeZone;

function label(zone: string): string {
  const city = cityOf(zone);
  return city === zone ? zone : `${city} — ${zone}`;
}

/** Every word typed must appear in the name, with `_` and `/` read as spaces. */
function matches(zone: string, words: string[]): boolean {
  const haystack = zone.toLowerCase().replaceAll(/[_/]/g, " ");
  return words.every((w) => haystack.includes(w));
}

/**
 * Searchable single-choice list of time zones in a modal. There is no clear or empty option: the only way out
 * without a pick is Cancel, Escape or the close button, all of which keep the current zone (AC-186).
 */
export function TimeZonePicker({
  zones = [],
  current,
  loading = false,
  failed = false,
  onRetry,
  onPick,
  onCancel,
}: TimeZonePickerProps) {
  const titleId = useId();
  const listId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(-1);

  const shown = useMemo(() => {
    const words = query.toLowerCase().replaceAll("_", " ").split(/\s+/).filter(Boolean);
    return words.length === 0 ? zones : zones.filter((z) => matches(z, words));
  }, [zones, query]);

  // Focus the search on open and give focus back to the opener on close.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    searchRef.current?.focus();
    return () => {
      if (opener?.isConnected) opener.focus?.();
    };
  }, []);

  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCancel]);

  useEffect(() => {
    if (active >= 0)
      document.getElementById(`${listId}-${active}`)?.scrollIntoView?.({ block: "nearest" });
  }, [active, listId]);

  function onSearchKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((i) => Math.min(shown.length - 1, i + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const zone = shown[active];
      if (zone) onPick(zone);
    }
  }

  function trapTab(e: KeyboardEvent) {
    if (e.key !== "Tab") return;
    const focusable = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>("button:not(:disabled), input") ?? [],
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  const ready = !loading && !failed;
  return (
    <>
      <div
        className="modal modal-blur d-block"
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={trapTab}
      >
        <div className="modal-dialog modal-dialog-centered modal-fullscreen-md-down">
          <div className="modal-content">
            <div className="modal-header">
              <h2 id={titleId} className="modal-title h3">
                {m.pickerTitle}
              </h2>
              <button
                type="button"
                className="btn btn-ghost-secondary btn-icon"
                aria-label={m.close}
                onClick={onCancel}
              >
                <Icon name="x" size={18} />
              </button>
            </div>
            <div className="modal-body">
              <label htmlFor={`${listId}-search`} className="form-label">
                {m.searchLabel}
              </label>
              <div className="input-icon mb-3">
                <span className="input-icon-addon">
                  <Icon name="search" size={18} />
                </span>
                <input
                  id={`${listId}-search`}
                  ref={searchRef}
                  type="text"
                  className="form-control"
                  role="combobox"
                  aria-expanded={ready}
                  aria-controls={listId}
                  aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
                  aria-autocomplete="list"
                  autoComplete="off"
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setActive(-1);
                  }}
                  onKeyDown={onSearchKey}
                />
              </div>
              {loading ? <LoadState state="loading" rows={3} /> : null}
              {failed ? (
                <p className="text-secondary mb-0">
                  {m.listFailed}{" "}
                  <button
                    type="button"
                    className="btn btn-link p-0 align-baseline"
                    onClick={onRetry}
                  >
                    {m.tryAgain}
                  </button>
                </p>
              ) : null}
              {ready && shown.length === 0 ? (
                <small className="text-secondary d-inline-flex align-items-center gap-1">
                  <Icon name="search" size={16} />
                  {m.noMatch(query.trim())}
                </small>
              ) : null}
              {ready && shown.length > 0 ? (
                <div
                  id={listId}
                  role="listbox"
                  aria-label={m.pickerTitle}
                  className="list-group overflow-auto"
                  style={{ maxHeight: "50vh" }}
                >
                  {shown.map((zone, i) => (
                    <div
                      key={zone}
                      id={`${listId}-${i}`}
                      role="option"
                      aria-selected={zone === current}
                      className={`list-group-item list-group-item-action d-flex justify-content-between align-items-center${
                        i === active ? " active" : ""
                      }`}
                      onClick={() => onPick(zone)}
                    >
                      {label(zone)}
                      {zone === current ? <Icon name="check" size={18} /> : null}
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="modal-footer">
              <Button className="btn-ghost-secondary" onClick={onCancel}>
                {m.cancel}
              </Button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
