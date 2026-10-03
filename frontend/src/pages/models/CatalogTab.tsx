import { useSearchParams } from "react-router";
import type { CatalogModel, SlotKind } from "../../api/models";
import { useModelCatalog } from "../../api/models";
import { Button } from "../../components/Button/Button";
import { Cost } from "../../components/Cost/Cost";
import { type Column, DataTable } from "../../components/DataTable/DataTable";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { FilterBar } from "../../components/FilterBar/FilterBar";
import { Icon } from "../../components/Icon/Icon";
import { LoadState } from "../../components/LoadState/LoadState";
import { messages } from "../../messages";
import { UnavailablePage } from "../system/UnavailablePage";
import { fullDateTime, relativeTime, shortDateTime } from "./format";

const m = messages.models;
const slots: SlotKind[] = ["text", "vision", "image"];

function Price({ model }: { model: CatalogModel }) {
  const { inputPricePerMillionTokens: input, outputPricePerMillionTokens: output } = model;
  if (model.pricePerImage !== null) {
    return (
      <>
        <Cost amount={model.pricePerImage} precision />{" "}
        <span className="text-secondary">{m.perImage}</span>
      </>
    );
  }
  if (input === null && output === null) return <>{m.priceUnknown}</>;
  if (Number(input ?? 1) === 0 && Number(output ?? 1) === 0) return <>{m.free}</>;
  const side = (amount: string | null, label: string) => (
    <>
      {amount === null ? m.priceUnknown : <Cost amount={amount} precision />} {label}
    </>
  );
  return (
    <>
      {side(input, m.priceIn)} · {side(output, m.priceOut)}
      <div className="text-secondary small">{m.perMillion}</div>
    </>
  );
}

const modalities = (values: string[]) =>
  values
    .map((v, i) =>
      i === 0
        ? m.modality[v as "text" | "images"]
        : m.modality[v as "text" | "images"].toLowerCase(),
    )
    .join(", ");

const columns: Column<CatalogModel>[] = [
  {
    key: "model",
    header: m.columns.model,
    render: (r) => (
      <>
        <span className="fw-medium">{r.name}</span>{" "}
        <span className="text-secondary">{r.provider}</span>
      </>
    ),
  },
  { key: "takes", header: m.columns.takes, render: (r) => modalities(r.takes) },
  { key: "produces", header: m.columns.produces, render: (r) => modalities(r.produces) },
  { key: "price", header: m.columns.price, render: (r) => <Price model={r} /> },
  {
    key: "context",
    header: m.columns.context,
    render: (r) =>
      r.contextLength === null ? m.noContext : r.contextLength.toLocaleString("en-US"),
  },
];

/** SCR-66, Model catalog tab: search and the slot filter run in the browser over the one loaded list. */
export function CatalogTab({ onGoToProfiles }: { onGoToProfiles: () => void }) {
  const catalog = useModelCatalog();
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const slotParam = params.get("slot");
  const slot = slots.find((s) => s === slotParam);

  function update(name: string, value: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value) next.set(name, value);
        else next.delete(name);
        return next;
      },
      { replace: true },
    );
  }
  function reset() {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete("q");
        next.delete("slot");
        return next;
      },
      { replace: true },
    );
  }

  if (catalog.isError) return <UnavailablePage onRetry={() => catalog.refetch()} />;
  if (!catalog.data) return <LoadState state="loading" rows={6} />;

  const { state, lastRefreshedAt, models } = catalog.data;

  if (state === "not-configured") {
    return (
      <>
        <div className="alert alert-warning d-flex align-items-center gap-2" role="alert">
          <Icon name="alert-triangle" size={20} />
          {m.notConfiguredAlert}
        </div>
        <EmptyState
          kind="blocked"
          icon="ban"
          headingLevel={2}
          action={<Button onClick={onGoToProfiles}>{m.goToProfiles}</Button>}
        >
          {m.notConfigured}
        </EmptyState>
      </>
    );
  }
  if (state === "not-loaded") {
    return (
      <EmptyState
        kind="blocked"
        icon="clock"
        headingLevel={2}
        action={
          <Button busy={catalog.isFetching} onClick={() => void catalog.refetch()}>
            {m.checkAgain}
          </Button>
        }
      >
        {m.notLoaded}
      </EmptyState>
    );
  }

  const needle = q.trim().toLowerCase();
  const shown = [...models]
    .sort((a, b) => a.name.localeCompare(b.name))
    .filter(
      (model) =>
        (!needle || model.name.toLowerCase().includes(needle)) &&
        (!slot || model.slots.includes(slot)),
    );

  return (
    <>
      {state === "update-failed" && lastRefreshedAt ? (
        <div className="alert alert-warning d-flex align-items-center gap-2" role="alert">
          <Icon name="alert-triangle" size={20} />
          {m.updateFailed(shortDateTime(lastRefreshedAt))}
        </div>
      ) : null}
      <FilterBar
        search={q}
        onSearch={(v) => update("q", v)}
        searchLabel={m.search}
        filterLabel={m.slot}
        filterValue={slot ?? "any"}
        filterOptions={[
          { value: "any", label: m.slotOptions.any },
          ...slots.map((s) => ({ value: s, label: m.slotOptions[s] })),
        ]}
        onFilter={(v) => update("slot", v === "any" ? "" : v)}
        active={q !== "" || slot !== undefined}
        onReset={reset}
        resetLabel={m.resetAll}
      />
      {lastRefreshedAt ? (
        <p className="text-secondary small mb-2" title={fullDateTime(lastRefreshedAt)}>
          {m.updated(relativeTime(lastRefreshedAt))}
        </p>
      ) : null}
      {shown.length === 0 ? (
        <EmptyState
          kind="none"
          icon="search"
          headingLevel={2}
          action={
            <Button className="btn-secondary" onClick={reset}>
              {m.resetAll}
            </Button>
          }
        >
          {m.noMatch}
        </EmptyState>
      ) : (
        <DataTable columns={columns} rows={shown} rowKey={(r) => r.modelId} />
      )}
    </>
  );
}
