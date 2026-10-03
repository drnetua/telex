import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { isBackground } from "./account";
import { apiFetch } from "./client";

export const SYSTEM_KEYS: string[] = ["fast", "balanced", "careful"];
export type SystemProfileKey = "fast" | "balanced" | "careful";
export type ProfileRef = { kind: "system"; key: SystemProfileKey } | { kind: "custom"; id: string };
export type Modality = "text" | "image";
export type SlotKind = "text" | "vision" | "image";

export type CatalogState = "current" | "update-failed" | "not-loaded" | "not-configured";

export interface CatalogModel {
  modelId: string;
  name: string;
  provider: string;
  takes: Modality[];
  produces: Modality[];
  slots: SlotKind[];
  /** Decimal strings (US dollars), null when unknown. */
  inputPricePerMillionTokens: string | null;
  outputPricePerMillionTokens: string | null;
  pricePerImage: string | null;
  contextLength: number | null;
}

export interface ModelCatalog {
  state: CatalogState;
  lastRefreshedAt: string | null;
  lastFailedAt: string | null;
  models: CatalogModel[];
}

export interface ChainModel {
  modelId: string;
  name: string | null;
  availability: "available" | "not-in-catalog" | "not-capable";
}

export interface Slot {
  state: "main-model" | "fallback" | "no-model-available" | "not-used";
  currentModelId: string | null;
  chain: ChainModel[];
}

export interface ProfileSlots {
  text: Slot;
  vision: Slot;
  image: Slot;
}

export interface PricePer100Runs {
  state: "estimate" | "under-one-cent" | "free" | "unknown" | "no-text-model";
  amount: string | null;
}

export interface ModelProfile {
  ref: ProfileRef;
  name: string;
  slots: ProfileSlots;
  pricePer100Runs: PricePer100Runs;
  choosable: boolean;
}

export interface ModelProfileList {
  aiConfigured: boolean;
  defaultProfile: ProfileRef;
  customProfileLimit: number;
  items: ModelProfile[];
}

export interface ModelProfileDraft {
  name: string;
  duplicatedFrom: ProfileRef | null;
  slots: ProfileSlots;
  pricePer100Runs: PricePer100Runs;
}

export interface ChainInput {
  text: string[];
  vision: string[];
  image: string[];
}

export interface ModelProfileUpdate {
  name: string;
  slots: ChainInput;
}

export interface ModelProfileCreate extends ModelProfileUpdate {
  duplicatedFrom?: ProfileRef | null;
}

export interface ModelProfileDeletion {
  defaultProfile: ProfileRef;
  defaultReset: boolean;
}

export const modelCatalogKey = ["models", "catalog"] as const;
export const modelProfilesKey = ["models", "profiles"] as const;

/** A profile in a URL: a system key or a custom profile id. */
export const profileKey = (ref: ProfileRef) => (ref.kind === "system" ? ref.key : ref.id);

const base = "/api/v1/models";

export function useModelCatalog() {
  return useQuery({
    queryKey: modelCatalogKey,
    queryFn: () => apiFetch<ModelCatalog>(`${base}/catalog`, { background: isBackground() }),
  });
}

export function useModelProfiles() {
  return useQuery({
    queryKey: modelProfilesKey,
    queryFn: () => apiFetch<ModelProfileList>(`${base}/profiles`, { background: isBackground() }),
  });
}

export function useModelProfile(key: string | undefined) {
  return useQuery({
    queryKey: [...modelProfilesKey, key],
    enabled: key !== undefined,
    // An open editor must not be reset by a background refetch (AC-218).
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    queryFn: () =>
      apiFetch<ModelProfile>(`${base}/profiles/${encodeURIComponent(key ?? "")}`, {
        background: isBackground(),
      }),
  });
}

const draftsKey = ["models", "profile-draft"] as const;
const draftKey = (from?: string) => [...draftsKey, from ?? null] as const;
const fetchDraft = (from?: string) =>
  apiFetch<ModelProfileDraft>(
    `${base}/profile-draft${from ? `?from=${encodeURIComponent(from)}` : ""}`,
    { background: isBackground() },
  );

/** A draft fetched this recently counts as asked-on-open: Create/Duplicate fetch it just before the modal mounts. */
const DRAFT_FRESH_MS = 5000;

export function useModelProfileDraft(from?: string, enabled = true) {
  // Ask on mount unless the card just did (the suggested name goes stale); never in the background while the form is open.
  const query = useQuery({
    queryKey: draftKey(from),
    queryFn: () => fetchDraft(from),
    enabled,
    staleTime: DRAFT_FRESH_MS,
    refetchOnWindowFocus: false,
    refetchOnMount: true,
  });
  const [freshAtMount] = useState(query.data !== undefined && !query.isStale);
  // Data counts only when it was fresh on open or arrived after the open (a failed refetch leaves old data behind).
  const usableNow =
    query.data !== undefined &&
    (freshAtMount || (query.isFetchedAfterMount && query.dataUpdatedAt > query.errorUpdatedAt));
  // Once the form has its data, a later failing background refetch must not take it away.
  const [latched, setLatched] = useState(false);
  if (usableNow && !latched) setLatched(true);
  return { ...query, usable: latched || usableNow };
}

/** On-demand draft fetch for "Create profile" / "Duplicate": always asks the server, caches under the hook's key. */
export function useLoadModelProfileDraft() {
  const client = useQueryClient();
  return (from?: string) =>
    client.fetchQuery({ queryKey: draftKey(from), queryFn: () => fetchDraft(from), staleTime: 0 });
}

const json = (method: string, body: unknown) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

function useProfileMutation<V, R>(fn: (variables: V) => Promise<R>, dropsDrafts = false) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSettled: () => {
      // A new or deleted profile changes the suggested name/limit: a cached draft must not be reused on the next open.
      if (dropsDrafts) client.removeQueries({ queryKey: draftsKey });
      return client.invalidateQueries({ queryKey: modelProfilesKey });
    },
  });
}

export function useCreateModelProfile() {
  return useProfileMutation(
    (body: ModelProfileCreate) => apiFetch<ModelProfile>(`${base}/profiles`, json("POST", body)),
    true,
  );
}

export function useUpdateModelProfile() {
  return useProfileMutation(({ id, body }: { id: string; body: ModelProfileUpdate }) =>
    apiFetch<ModelProfile>(`${base}/profiles/${encodeURIComponent(id)}`, json("PUT", body)),
  );
}

export function useDeleteModelProfile() {
  return useProfileMutation(
    (id: string) =>
      apiFetch<ModelProfileDeletion>(`${base}/profiles/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    true,
  );
}

export function useSetDefaultModelProfile() {
  return useProfileMutation((profile: ProfileRef) =>
    apiFetch<{ profile: ProfileRef }>(`${base}/default-profile`, json("PUT", { profile })),
  );
}
