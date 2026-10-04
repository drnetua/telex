import { useId, useLayoutEffect, useRef, useState } from "react";
import { messages } from "../../messages";
import { Badge } from "../Badge/Badge";
import { Button } from "../Button/Button";
import { Icon } from "../Icon/Icon";

const m = messages.models;
export const MAX_CHAIN = 3;

export interface ChainItem {
  modelId: string;
  /** Catalog name; null when the model isn't known, then the id is shown. */
  name: string | null;
  /** The model left the catalog: kept and marked, never refused on its own. */
  missing: boolean;
}

interface ChainEditorProps {
  /** Slot name: the group's accessible name. */
  label: string;
  items: ChainItem[];
  onChange: (items: ChainItem[]) => void;
  onAdd: () => void;
  /** Text shown when the chain is empty ("Add at least one model." or "Not used"). */
  emptyText: string;
  /** Error for the whole slot, shown under the chain. */
  error?: string;
  /** Errors for single rows, by index. */
  rowErrors?: Record<number, string>;
  readOnly?: boolean;
}

type Focus = { modelId: string; act: "up" | "down" | "remove" } | "add";
const display = (i: ChainItem) => i.name ?? i.modelId;
const position = (index: number) => (index === 0 ? m.editor.main : m.editor.backup(index));

/** One slot's Fallback Chain: ordered rows with Main / Backup labels, reordering, removal and an Add model button. */
export function ChainEditor({
  label,
  items,
  onChange,
  onAdd,
  emptyText,
  error,
  rowErrors = {},
  readOnly = false,
}: ChainEditorProps) {
  const root = useRef<HTMLFieldSetElement>(null);
  const addRef = useRef<HTMLButtonElement>(null);
  const pending = useRef<Focus | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const errorId = useId();
  const full = items.length >= MAX_CHAIN;

  // After a change, put focus back on the moved (or neighbouring) row; never on <body>.
  useLayoutEffect(() => {
    const target = pending.current;
    pending.current = null;
    if (!target) return;
    if (target === "add") {
      addRef.current?.focus();
      return;
    }
    const row = Array.from(
      root.current?.querySelectorAll<HTMLElement>("li[data-model]") ?? [],
    ).find((li) => li.dataset.model === target.modelId);
    const enabled = (act: string) =>
      row?.querySelector<HTMLButtonElement>(`button[data-act="${act}"]:not(:disabled)`);
    const opposite = target.act === "up" ? "down" : "up";
    (enabled(target.act) ?? enabled(opposite) ?? addRef.current)?.focus();
  });

  function move(index: number, by: -1 | 1) {
    const next = [...items];
    const [moved] = next.splice(index, 1);
    if (!moved) return;
    next.splice(index + by, 0, moved);
    pending.current = { modelId: moved.modelId, act: by === -1 ? "up" : "down" };
    setAnnouncement(m.editor.nowAt(display(moved), position(index + by)));
    onChange(next);
  }

  function remove(index: number) {
    const gone = items[index];
    const neighbour = items[index + 1] ?? items[index - 1];
    pending.current = neighbour ? { modelId: neighbour.modelId, act: "remove" } : "add";
    if (gone) setAnnouncement(m.editor.removedAnnounce(display(gone)));
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <fieldset
      ref={root}
      className="mb-4"
      tabIndex={-1}
      aria-describedby={error ? errorId : undefined}
      data-slot={label}
    >
      <legend className="form-label fs-4 mb-2">{label}</legend>
      {items.length === 0 ? <p className="text-secondary mb-2">{emptyText}</p> : null}
      <ul className="list-group mb-2">
        {items.map((item, i) => (
          <li
            key={item.modelId}
            data-model={item.modelId}
            tabIndex={-1}
            className="list-group-item"
          >
            <div className="d-flex flex-wrap align-items-center gap-2">
              <span className="badge bg-secondary-subtle text-secondary-emphasis">
                {position(i)}
              </span>
              <span className="me-auto">{display(item)}</span>
              {item.missing ? <Badge icon="circle-off">{m.notInCatalog}</Badge> : null}
              <Button
                className="btn-ghost-secondary btn-icon"
                icon="arrow-up"
                aria-label={m.editor.moveUp}
                data-act="up"
                disabled={readOnly || i === 0}
                onClick={() => move(i, -1)}
              />
              <Button
                className="btn-ghost-secondary btn-icon"
                icon="arrow-down"
                aria-label={m.editor.moveDown}
                data-act="down"
                disabled={readOnly || i === items.length - 1}
                onClick={() => move(i, 1)}
              />
              <Button
                className="btn-ghost-secondary btn-icon"
                icon="x"
                aria-label={m.editor.remove(display(item))}
                data-act="remove"
                disabled={readOnly}
                onClick={() => remove(i)}
              />
            </div>
            {rowErrors[i] ? (
              <div className="invalid-feedback d-flex align-items-center gap-1">
                <Icon name="alert-circle" size={14} />
                {rowErrors[i]}
              </div>
            ) : null}
          </li>
        ))}
      </ul>
      <div className="d-flex flex-wrap align-items-center gap-2">
        <button
          ref={addRef}
          data-add
          type="button"
          className="btn btn-outline-primary"
          disabled={readOnly || full}
          onClick={onAdd}
        >
          <Icon name="plus" size={18} />
          {m.editor.addModel}
        </button>
        {full ? <span className="text-secondary">{m.editor.slotFull}</span> : null}
      </div>
      {error ? (
        <div id={errorId} className="invalid-feedback d-flex align-items-center gap-1">
          <Icon name="alert-circle" size={14} />
          {error}
        </div>
      ) : null}
      <div className="visually-hidden" aria-live="polite">
        {announcement}
      </div>
    </fieldset>
  );
}
