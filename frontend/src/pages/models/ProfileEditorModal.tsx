import {
  Fragment,
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from "react";
import { Navigate, useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import {
  useCreateModelProfile,
  useModelCatalog,
  useModelProfile,
  useModelProfileDraft,
  useModelProfiles,
  useUpdateModelProfile,
  type ChainInput,
  type ModelProfileDraft,
  type ProfileRef,
  type ProfileSlots,
  type SlotKind,
} from "../../api/models";
import { Button } from "../../components/Button/Button";
import { ChainEditor, type ChainItem } from "../../components/ChainEditor/ChainEditor";
import { ModelChooser } from "../../components/ModelChooser/ModelChooser";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { UnavailablePage } from "../system/UnavailablePage";

const m = messages.models;
const e = m.editor.errors;
const SLOTS: SlotKind[] = ["text", "vision", "image"];
const MODELS_PATH = "/settings/models";

export type EditorNotice = { tone: "info" | "error"; message: string };
type Chains = Record<SlotKind, ChainItem[]>;
type SlotErrors = Partial<Record<SlotKind, { slot?: string; rows: Record<number, string> }>>;

const toChains = (s: ProfileSlots | undefined): Chains => {
  const of = (k: SlotKind) =>
    s?.[k]?.chain.map((c) => ({
      modelId: c.modelId,
      name: c.name,
      missing: c.availability === "not-in-catalog",
    })) ?? [];
  return { text: of("text"), vision: of("vision"), image: of("image") };
};
const toInput = (c: Chains): ChainInput => ({
  text: c.text.map((i) => i.modelId),
  vision: c.vision.map((i) => i.modelId),
  image: c.image.map((i) => i.modelId),
});

interface Props {
  /** The custom profile id to edit; undefined creates (optionally duplicating `?from=`). */
  profileId?: string;
  from?: string;
  /** The profile (or the one it is copied from) doesn't exist for this Owner: render SCR-91. */
  onGone: () => void;
}

/** SCR-34: loads the draft or the profile, then mounts the form. A refused opening redirects with feedback. */
export function ProfileEditorModal({ profileId, from, onGone }: Props) {
  const draft = useModelProfileDraft(profileId ? undefined : from, !profileId);
  const profile = useModelProfile(profileId);
  const source = profileId ? profile : draft;
  const failure = source.error instanceof ApiFailure ? source.error : null;
  const code = failure?.code;
  const gone = code === "not-found";
  useEffect(() => {
    if (gone) onGone();
  }, [gone, onGone]);

  if (code === "profile-limit-reached" || code === "ai-not-configured") {
    const message = code === "ai-not-configured" ? m.notConfiguredAlert : m.limitReached;
    return <Navigate to={MODELS_PATH} replace state={{ notice: { tone: "error", message } }} />;
  }
  if (gone) return null;
  if (source.isError) return <UnavailablePage onRetry={() => void source.refetch()} />;
  const data: ModelProfileDraft | undefined = profileId
    ? profile.data && {
        name: profile.data.name,
        duplicatedFrom: null,
        slots: profile.data.slots,
        pricePer100Runs: profile.data.pricePer100Runs,
      }
    : draft.data;
  if (!data) return null;
  return <EditorForm profileId={profileId} initial={data} onGone={onGone} />;
}

function EditorForm({
  profileId,
  initial,
  onGone,
}: {
  profileId?: string;
  initial: ModelProfileDraft;
  onGone: () => void;
}) {
  const navigate = useNavigate();
  const catalog = useModelCatalog();
  const profiles = useModelProfiles();
  const create = useCreateModelProfile();
  const update = useUpdateModelProfile();
  const titleId = useId();
  const nameErrorId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState(initial.name ?? "");
  const [chains, setChains] = useState<Chains>(() => toChains(initial.slots));
  const [nameError, setNameError] = useState<string | null>(null);
  const [slotErrors, setSlotErrors] = useState<SlotErrors>({});
  const [choosing, setChoosing] = useState<SlotKind | null>(null);
  const [notice, setNotice] = useState<EditorNotice | null>(null);
  const [focusRequest, setFocusRequest] = useState<{
    target: "name" | SlotKind;
    row?: number;
    add?: boolean;
  } | null>(null);
  const saving = create.isPending || update.isPending;
  const title = profileId ? m.editor.editTitle : m.editor.createTitle;

  const close = () => void navigate(MODELS_PATH);
  const closeRef = useRef(close);
  useEffect(() => {
    closeRef.current = close;
  });

  // Focus the Name field on open and give focus back to the opener on close.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    nameRef.current?.focus();
    return () => {
      if (opener?.isConnected) opener.focus?.();
    };
  }, []);

  useEffect(() => {
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key === "Escape" && !saving) closeRef.current();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [saving]);

  // Focus the first field with an error once it has rendered.
  useEffect(() => {
    if (!focusRequest) return;
    const { target, row, add } = focusRequest;
    if (target === "name") nameRef.current?.focus();
    else {
      const group = dialogRef.current?.querySelector<HTMLElement>(
        `fieldset[data-slot="${m.slotLabels[target]}"]`,
      );
      if (add) {
        group?.querySelector<HTMLElement>("button[data-add]")?.focus();
        return;
      }
      const rows = group?.querySelectorAll<HTMLElement>("li[data-model]");
      (row === undefined ? group : (rows?.[row] ?? group))?.focus();
    }
  }, [focusRequest]);

  function trapTab(ev: ReactKeyboardEvent) {
    if (ev.key !== "Tab") return;
    const focusable = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>(
        "button:not(:disabled), input:not(:disabled), [tabindex='0']",
      ) ?? [],
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (ev.shiftKey && document.activeElement === first) {
      ev.preventDefault();
      last.focus();
    } else if (!ev.shiftKey && document.activeElement === last) {
      ev.preventDefault();
      first.focus();
    }
  }

  function closeChooser(slot: SlotKind) {
    setChoosing(null);
    setFocusRequest({ target: slot, add: true });
  }

  function setChain(slot: SlotKind, items: ChainItem[]) {
    setChains((c) => ({ ...c, [slot]: items }));
    setSlotErrors((s) => ({ ...s, [slot]: undefined }));
  }

  function clientChecks(): { name?: string; text?: string } {
    const trimmed = name.trim();
    const problems: { name?: string; text?: string } = {};
    const others = (profiles.data?.items ?? []).filter(
      (p) => !(p.ref.kind === "custom" && p.ref.id === profileId),
    );
    const same = others.find((p) => p.name.trim().toLowerCase() === trimmed.toLowerCase());
    if (!trimmed) problems.name = e.nameRequired;
    else if (trimmed.length > 40) problems.name = e.nameTooLong;
    else if (same?.ref.kind === "system") problems.name = e.nameReserved(same.name);
    else if (same) problems.name = e.nameTaken(same.name);
    if (chains.text.length === 0) problems.text = e.textSlotRequired;
    return problems;
  }

  function fail(failure: unknown) {
    if (!(failure instanceof ApiFailure)) return;
    if (failure.code === "not-found") return onGone();
    if (failure.code === "profile-limit-reached")
      return setNotice({ tone: "error", message: m.limitReached });
    if (failure.code === "ai-not-configured")
      return setNotice({ tone: "error", message: m.notConfiguredAlert });
    if (failure.status !== 400) return;
    let nameMessage: string | null = null;
    const slotsOut: SlotErrors = {};
    for (const item of failure.errors) {
      const text = serverMessage(item.code, item.message, name.trim());
      const at = /^slots\.(text|vision|image)(?:\[(\d+)\])?$/.exec(item.field);
      if (item.field === "name") nameMessage ??= text;
      else if (at) {
        const slot = at[1] as SlotKind;
        const entry = (slotsOut[slot] ??= { rows: {} });
        if (at[2] === undefined) entry.slot ??= text;
        else
          entry.rows[Number(at[2])] ??= modelMessage(item.code, chains[slot][Number(at[2])], text);
      }
    }
    setNameError(nameMessage);
    setSlotErrors(slotsOut);
    const firstSlot = SLOTS.find((s) => slotsOut[s]);
    const firstRow = firstSlot ? Object.keys(slotsOut[firstSlot]?.rows ?? {})[0] : undefined;
    if (nameMessage) setFocusRequest({ target: "name" });
    else if (firstSlot)
      setFocusRequest({
        target: firstSlot,
        row: !slotsOut[firstSlot]?.slot && firstRow !== undefined ? Number(firstRow) : undefined,
      });
    else setNotice({ tone: "error", message: e.saveFailed });
  }

  function save() {
    const problems = clientChecks();
    setNameError(problems.name ?? null);
    setSlotErrors(problems.text ? { text: { slot: problems.text, rows: {} } } : {});
    if (problems.name) return setFocusRequest({ target: "name" });
    if (problems.text) return setFocusRequest({ target: "text" });
    setNotice(null);
    const slots = toInput(chains);
    const done = {
      onSuccess: () =>
        void navigate(MODELS_PATH, {
          state: { notice: { tone: "info", message: m.editor.saved } },
        }),
      onError: fail,
    };
    if (profileId) update.mutate({ id: profileId, body: { name: name.trim(), slots } }, done);
    else
      create.mutate(
        { name: name.trim(), duplicatedFrom: initial.duplicatedFrom as ProfileRef | null, slots },
        done,
      );
  }

  const models = catalog.data?.models ?? [];
  return (
    <>
      <div className="modal modal-blur d-block">
        <div
          className="modal-dialog modal-lg modal-fullscreen-md-down modal-dialog-scrollable"
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          onKeyDown={trapTab}
        >
          <form
            className="modal-content"
            noValidate
            onSubmit={(ev) => {
              ev.preventDefault();
              save();
            }}
          >
            <div className="modal-header">
              <h2 id={titleId} className="modal-title h3">
                {title}
              </h2>
              <button
                type="button"
                className="btn-close"
                aria-label={m.editor.close}
                disabled={saving}
                onClick={close}
              />
            </div>
            <div className="modal-body">
              <div className="mb-4">
                <label className="form-label" htmlFor={`${titleId}-name`}>
                  {m.editor.name}
                </label>
                <input
                  id={`${titleId}-name`}
                  ref={nameRef}
                  className={`form-control${nameError ? " is-invalid" : ""}`}
                  value={name}
                  readOnly={saving}
                  aria-invalid={nameError ? "true" : undefined}
                  aria-describedby={nameError ? nameErrorId : undefined}
                  onChange={(ev) => {
                    setName(ev.target.value);
                    setNameError(null);
                  }}
                />
                {nameError ? (
                  <div id={nameErrorId} className="invalid-feedback">
                    {nameError}
                  </div>
                ) : null}
              </div>
              {SLOTS.map((slot) => (
                <Fragment key={slot}>
                  <ChainEditor
                    label={m.slotLabels[slot]}
                    items={chains[slot]}
                    readOnly={saving}
                    emptyText={slot === "text" ? m.editor.addAtLeastOne : m.notUsed}
                    error={slotErrors[slot]?.slot}
                    rowErrors={slotErrors[slot]?.rows}
                    onChange={(items) => setChain(slot, items)}
                    onAdd={() => setChoosing(slot)}
                  />
                  {choosing === slot ? (
                    <ModelChooser
                      slot={slot}
                      models={models}
                      taken={chains[slot].map((i) => i.modelId)}
                      onClose={() => closeChooser(slot)}
                      onChoose={(model) => {
                        setChain(slot, [
                          ...chains[slot],
                          { modelId: model.modelId, name: model.name, missing: false },
                        ]);
                        closeChooser(slot);
                      }}
                    />
                  ) : null}
                </Fragment>
              ))}
            </div>
            <div className="modal-footer">
              <Button className="btn-ghost-secondary" disabled={saving} onClick={close}>
                {m.editor.cancel}
              </Button>
              <Button type="submit" busy={saving}>
                {m.editor.save}
              </Button>
            </div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show" />
      {notice ? (
        <Toast
          tone={notice.tone}
          message={notice.message}
          dismissLabel={m.dismiss}
          onDismiss={() => setNotice(null)}
        />
      ) : null}
    </>
  );
}

function serverMessage(code: string, fallback: string, name: string) {
  switch (code) {
    case "name-required":
      return e.nameRequired;
    case "name-too-long":
      return e.nameTooLong;
    case "name-taken":
      return e.nameTaken(name);
    case "name-reserved":
      return e.nameReserved(name);
    case "text-slot-required":
      return e.textSlotRequired;
    case "model-not-capable":
      return e.modelNotCapable;
    case "slot-full":
      return e.slotFull;
    case "model-duplicate":
      return e.modelDuplicate;
    default:
      return fallback;
  }
}

function modelMessage(code: string, item: ChainItem | undefined, text: string) {
  return code === "model-left-catalog" && item
    ? e.modelLeftCatalog(item.name ?? item.modelId)
    : text;
}
