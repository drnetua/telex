import type { Slot } from "../../api/models";
import { messages } from "../../messages";

const m = messages.models;

/** The catalog name of a model, or its id when it isn't in the catalog. */
export function currentModelLabel(slot: Slot): string {
  const current = slot.chain.find((c) => c.modelId === slot.currentModelId);
  return current?.name ?? slot.currentModelId ?? "";
}

/** The fallback warning for a slot, or null when its main model is in use. */
export function fallbackWarning(slot: Slot): string | null {
  return slot.state === "fallback" ? m.fallbackWarning(currentModelLabel(slot)) : null;
}
