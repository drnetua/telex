import { useRef, useState } from "react";
import { useNavigate } from "react-router";
import type { ModelProfile, ProfileRef } from "../../api/models";
import {
  profileKey,
  useDeleteModelProfile,
  useLoadModelProfileDraft,
  useModelProfiles,
  useSetDefaultModelProfile,
} from "../../api/models";
import { ApiFailure } from "../../api/client";
import { Button } from "../../components/Button/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { Icon } from "../../components/Icon/Icon";
import { LoadState } from "../../components/LoadState/LoadState";
import { ModelProfileCard } from "../../components/ModelProfileCard/ModelProfileCard";
import { ModelProfilePicker } from "../../components/ModelProfilePicker/ModelProfilePicker";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { UnavailablePage } from "../system/UnavailablePage";

const m = messages.models;
const sameRef = (a: ProfileRef, b: ProfileRef) => profileKey(a) === profileKey(b);
type Notice = { tone: "info" | "error"; message: string };

/** SCR-66, Profiles tab: the picker, the system and custom profile cards, create, duplicate and delete. */
export function ProfilesTab() {
  const profiles = useModelProfiles();
  const setDefault = useSetDefaultModelProfile();
  const remove = useDeleteModelProfile();
  const loadDraft = useLoadModelProfileDraft();
  const navigate = useNavigate();
  const pickerRef = useRef<HTMLDivElement>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [opening, setOpening] = useState<string | null>(null);
  const [target, setTarget] = useState<ModelProfile | null>(null);

  if (profiles.isError) return <UnavailablePage onRetry={() => profiles.refetch()} />;
  if (!profiles.data) return <LoadState state="loading" rows={6} />;

  const { aiConfigured, defaultProfile, items } = profiles.data;
  const system = items.filter((p) => p.ref.kind === "system");
  const custom = items.filter((p) => p.ref.kind === "custom");
  const current = items.find((p) => sameRef(p.ref, defaultProfile));
  const error = (message: string) => setNotice({ tone: "error", message });
  const info = (message: string) => setNotice({ tone: "info", message });

  function choose(ref: ProfileRef) {
    const chosen = items.find((p) => sameRef(p.ref, ref));
    setDefault.mutate(ref, {
      onSuccess: () => info(m.nowDefault(chosen?.name ?? "")),
      onError: (e) =>
        error(
          e instanceof ApiFailure && e.code === "not-found"
            ? m.profileGone
            : m.cantBeDefault(chosen?.name ?? ""),
        ),
    });
  }

  async function openEditor(from?: ProfileRef) {
    const key = from ? profileKey(from) : undefined;
    setOpening(key ?? "new");
    try {
      await loadDraft(key);
      void navigate(
        `/settings/models/profiles/new${key ? `?from=${encodeURIComponent(key)}` : ""}`,
      );
    } catch (e) {
      const code = e instanceof ApiFailure ? e.code : "";
      if (code === "profile-limit-reached") error(m.limitReached);
      else if (code === "ai-not-configured") error(m.notConfiguredAlert);
      else if (code === "not-found") {
        error(m.profileGone);
        void profiles.refetch();
      }
    } finally {
      setOpening(null);
    }
  }

  function focusPicker() {
    const group = pickerRef.current?.querySelector<HTMLElement>("[role=radiogroup]");
    const radio = group?.querySelector<HTMLElement>("input:checked:not(:disabled)");
    (radio ?? group?.querySelector<HTMLElement>("input:not(:disabled)") ?? group)?.focus();
  }

  function confirmDelete(profile: ModelProfile) {
    if (profile.ref.kind !== "custom") return;
    remove.mutate(profile.ref.id, {
      onSuccess: (r) =>
        info(r.defaultReset ? m.deletedDefaultReset(profile.name) : m.deleted(profile.name)),
      onError: (e) => {
        if (e instanceof ApiFailure && e.code === "not-found") error(m.profileGone);
      },
      onSettled: () => setTarget(null),
    });
  }

  const card = (p: ModelProfile) => {
    const key = profileKey(p.ref);
    return (
      <div className="col-12 col-lg-6 col-xl-4" key={key}>
        <ModelProfileCard
          profile={p}
          aiConfigured={aiConfigured}
          isDefault={sameRef(p.ref, defaultProfile)}
          duplicating={opening === key}
          onDuplicate={() => void openEditor(p.ref)}
          onEdit={() => void navigate(`/settings/models/profiles/${encodeURIComponent(key)}`)}
          onPickAnotherModel={() =>
            void navigate(`/settings/models/profiles/${encodeURIComponent(key)}`)
          }
          onChooseAnotherProfile={focusPicker}
          onDelete={() => setTarget(p)}
        />
      </div>
    );
  };

  const createButton = (
    <Button
      icon="plus"
      busy={opening === "new"}
      disabled={!aiConfigured}
      onClick={() => void openEditor()}
    >
      {m.createProfile}
    </Button>
  );

  return (
    <>
      {current && !current.choosable ? (
        <div className="alert alert-warning d-flex align-items-center gap-2" role="alert">
          <Icon name="alert-triangle" size={20} />
          {m.defaultNoTextAlert}
        </div>
      ) : null}
      <div ref={pickerRef} className="mb-4">
        <ModelProfilePicker
          profiles={items}
          value={defaultProfile}
          busy={setDefault.isPending ? setDefault.variables : null}
          onChange={choose}
        />
      </div>
      <h2 className="visually-hidden">{m.systemProfiles}</h2>
      <div className="row g-3 mb-4">{system.map(card)}</div>
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <h2 className="h3 mb-0">{m.yourProfiles}</h2>
        {custom.length > 0 ? createButton : null}
      </div>
      {custom.length === 0 ? (
        <EmptyState kind="first" icon="plus" headingLevel={2} action={createButton}>
          {m.emptyCustom}
        </EmptyState>
      ) : (
        <div className="row g-3">{custom.map(card)}</div>
      )}
      {target ? (
        <ConfirmDialog
          title={m.deleteTitle(target.name)}
          confirmLabel={m.deleteConfirm}
          cancelLabel={m.deleteCancel}
          busy={remove.isPending}
          busyLabel={m.deleting}
          onCancel={() => setTarget(null)}
          onConfirm={() => confirmDelete(target)}
        >
          <span className="d-block">{m.deleteBody}</span>
          {sameRef(target.ref, defaultProfile) ? (
            <span className="d-block">{m.deleteDefaultNote}</span>
          ) : null}
        </ConfirmDialog>
      ) : null}
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
