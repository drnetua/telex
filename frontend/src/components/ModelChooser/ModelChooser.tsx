import { useEffect, useId, useRef, useState } from "react";
import type { CatalogModel, SlotKind } from "../../api/models";
import { messages } from "../../messages";
import { Cost } from "../Cost/Cost";
import { Icon } from "../Icon/Icon";

const m = messages.models;

interface ModelChooserProps {
  slot: SlotKind;
  models: CatalogModel[];
  /** Model ids already in the slot: disabled with "Already in this slot". */
  taken: string[];
  onChoose: (model: CatalogModel) => void;
  onClose: () => void;
}

const incapable: Record<SlotKind, string> = {
  vision: m.chooser.noImages,
  image: m.chooser.noImageOutput,
  text: m.chooser.noText,
};

/** Why a catalog row can't be added to the slot, or null when it can. */
function disabledReason(model: CatalogModel, slot: SlotKind, taken: string[]) {
  if (!model.slots.includes(slot)) return incapable[slot];
  if (taken.includes(model.modelId)) return m.chooser.alreadyIn;
  return null;
}

/** W-34b: "$2.50 / $10.00 per 1M", "Free" or "Price unknown", in catalog precision. */
function ChooserPrice({ model }: { model: CatalogModel }) {
  const { inputPricePerMillionTokens: input, outputPricePerMillionTokens: output } = model;
  if (input === null || output === null) return <>{m.priceUnknown}</>;
  if (Number(input) === 0 && Number(output) === 0) return <>{m.free}</>;
  return (
    <>
      <Cost amount={input} precision /> / <Cost amount={output} precision /> {m.chooser.perMillion}
    </>
  );
}

/** Search plus the whole catalog under a slot; rows that can't do the slot's job say why they are off. */
export function ModelChooser({ slot, models, taken, onChoose, onClose }: ModelChooserProps) {
  const [query, setQuery] = useState("");
  const search = useRef<HTMLInputElement>(null);
  const titleId = useId();
  useEffect(() => search.current?.focus(), []);
  const q = query.trim().toLowerCase();
  const shown = models.filter(
    (x) => !q || x.name.toLowerCase().includes(q) || x.modelId.toLowerCase().includes(q),
  );

  return (
    <section
      className="card mt-2"
      aria-labelledby={titleId}
      onKeyDown={(e) => {
        if (e.key !== "Escape") return;
        e.stopPropagation();
        e.nativeEvent.stopImmediatePropagation();
        onClose();
      }}
    >
      <div className="card-body">
        <div className="d-flex align-items-center gap-2 mb-2">
          <h3 id={titleId} className="h4 mb-0 me-auto">
            {m.chooser.title(m.slotLabels[slot])}
          </h3>
          <button type="button" className="btn btn-ghost-secondary" onClick={onClose}>
            {m.chooser.hide}
          </button>
        </div>
        <input
          ref={search}
          type="search"
          className="form-control mb-2"
          aria-label={m.chooser.search}
          placeholder={m.chooser.search}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") e.preventDefault();
          }}
        />
        {shown.length === 0 ? <p className="text-secondary mb-0">{m.chooser.empty}</p> : null}
        <ul className="list-group overflow-auto" style={{ maxHeight: "16rem" }}>
          {shown.map((model) => {
            const reason = disabledReason(model, slot, taken);
            return (
              <li key={model.modelId} className="list-group-item p-0">
                <button
                  type="button"
                  className="list-group-item-action w-100 border-0 bg-transparent text-start p-2"
                  aria-disabled={reason ? "true" : undefined}
                  onClick={() => (reason ? undefined : onChoose(model))}
                >
                  <span className={`d-block${reason ? " text-secondary" : ""}`}>{model.name}</span>
                  <span className="d-block small text-secondary">{model.provider}</span>
                  <span className="d-block small text-secondary">
                    <ChooserPrice model={model} />
                  </span>
                  {reason ? (
                    <span className="d-flex align-items-center gap-1 small text-secondary">
                      <Icon name="ban" size={14} />
                      {reason}
                    </span>
                  ) : null}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
