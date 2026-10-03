import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isBackground } from "./account";
import { apiFetch } from "./client";

export type SystemProfileKey = "fast" | "balanced" | "careful";
export type ProfileRef = { kind: "system"; key: SystemProfileKey } | { kind: "custom"; id: string };
export type Modality = "text" | "images";
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
    queryFn: () =>
      apiFetch<ModelProfile>(`${base}/profiles/${encodeURIComponent(key ?? "")}`, {
        background: isBackground(),
      }),
  });
}

const draftKey = (from?: string) => ["models", "profile-draft", from ?? null] as const;
const fetchDraft = (from?: string) =>
  apiFetch<ModelProfileDraft>(
    `${base}/profile-draft${from ? `?from=${encodeURIComponent(from)}` : ""}`,
    { background: isBackground() },
  );

export function useModelProfileDraft(from?: string) {
  return useQuery({ queryKey: draftKey(from), queryFn: () => fetchDraft(from) });
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

function useProfileMutation<V, R>(fn: (variables: V) => Promise<R>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSettled: () => client.invalidateQueries({ queryKey: modelProfilesKey }),
  });
}

export function useCreateModelProfile() {
  return useProfileMutation((body: ModelProfileCreate) =>
    apiFetch<ModelProfile>(`${base}/profiles`, json("POST", body)),
  );
}

export function useUpdateModelProfile() {
  return useProfileMutation(({ id, body }: { id: string; body: ModelProfileUpdate }) =>
    apiFetch<ModelProfile>(`${base}/profiles/${encodeURIComponent(id)}`, json("PUT", body)),
  );
}

export function useDeleteModelProfile() {
  return useProfileMutation((id: string) =>
    apiFetch<ModelProfileDeletion>(`${base}/profiles/${encodeURIComponent(id)}`, {
      method: "DELETE",
    }),
  );
}

export function useSetDefaultModelProfile() {
  return useProfileMutation((profile: ProfileRef) =>
    apiFetch<{ profile: ProfileRef }>(`${base}/default-profile`, json("PUT", { profile })),
  );
}
