import type { ModelProfile, SlotKind } from "../../api/models";
import { messages } from "../../messages";
import { Badge } from "../Badge/Badge";
import { Button } from "../Button/Button";
import { Icon } from "../Icon/Icon";
import { PriceLine, Warning } from "../ModelProfilePicker/profileDisplay";
import { fallbackWarning } from "../ModelProfilePicker/slotText";

const m = messages.models;
const slotKinds: SlotKind[] = ["text", "vision", "image"];

export interface ModelProfileCardProps {
  profile: ModelProfile;
  aiConfigured: boolean;
  isDefault: boolean;
  onDuplicate: () => void;
  onEdit?: () => void;
  onDelete?: () => void;
  /** Custom card, no-model slot: "Pick another model". */
  onPickAnotherModel?: () => void;
  /** System card, no-model slot: "Choose another profile". */
  onChooseAnotherProfile?: () => void;
  /** Duplicate is busy while the draft loads. */
  duplicating?: boolean;
}

function SlotRow({
  kind,
  profile,
  aiConfigured,
  onPickAnotherModel,
  onChooseAnotherProfile,
}: {
  kind: SlotKind;
  profile: ModelProfile;
  aiConfigured: boolean;
  onPickAnotherModel?: () => void;
  onChooseAnotherProfile?: () => void;
}) {
  const slot = profile.slots[kind];
  const warning = fallbackWarning(slot);
  return (
    <div className="d-flex flex-wrap gap-2 py-1">
      <span className="text-secondary" style={{ minWidth: "4.5rem" }}>
        {m.slotLabels[kind]}
      </span>
      <div className="d-flex flex-column gap-1">
        {slot.state === "not-used" ? <span className="text-secondary">{m.notUsed}</span> : null}
        {slot.state === "no-model-available" ? (
          <span className="d-flex align-items-center gap-1">
            <Icon name="circle-off" size={14} />
            {m.noModel}
          </span>
        ) : null}
        {slot.chain.map((model) => (
          <span key={model.modelId} className="d-flex flex-wrap align-items-center gap-2">
            <span>{model.name ?? model.modelId}</span>
            {model.availability === "available" ? null : (
              <Badge icon="circle-off">{m.notInCatalog}</Badge>
            )}
          </span>
        ))}
        {warning ? <Warning>{warning}</Warning> : null}
        {slot.state === "no-model-available" && profile.ref.kind === "custom" ? (
          <Button
            className="btn-link p-0 align-self-start"
            disabled={!aiConfigured}
            onClick={onPickAnotherModel}
          >
            {m.pickAnotherModel}
          </Button>
        ) : null}
        {slot.state === "no-model-available" && profile.ref.kind === "system" ? (
          <Button className="btn-link p-0 align-self-start" onClick={onChooseAnotherProfile}>
            {m.chooseAnotherProfile}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

/** One profile as a Tabler card: name, badges, price, slot rows and its actions. */
export function ModelProfileCard({
  profile,
  aiConfigured,
  isDefault,
  onDuplicate,
  onEdit,
  onDelete,
  onPickAnotherModel,
  onChooseAnotherProfile,
  duplicating = false,
}: ModelProfileCardProps) {
  const system = profile.ref.kind === "system";
  return (
    <div className="card">
      <div className="card-body">
        <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
          <h3 className="card-title mb-0">{profile.name}</h3>
          {system ? <Badge icon="lock">{m.systemBadge}</Badge> : null}
          {isDefault ? (
            <Badge tone="success" icon="check">
              {m.defaultBadge}
            </Badge>
          ) : null}
        </div>
        <p className="text-secondary mb-2">
          <PriceLine price={profile.pricePer100Runs} />
        </p>
        {slotKinds.map((kind) => (
          <SlotRow
            key={kind}
            kind={kind}
            profile={profile}
            aiConfigured={aiConfigured}
            onPickAnotherModel={onPickAnotherModel}
            onChooseAnotherProfile={onChooseAnotherProfile}
          />
        ))}
        {system ? <p className="text-secondary small mt-2 mb-0">{m.systemNote}</p> : null}
      </div>
      <div className="card-footer d-flex flex-wrap gap-2">
        {system ? null : (
          <Button className="btn-secondary" disabled={!aiConfigured} onClick={onEdit}>
            {m.edit}
          </Button>
        )}
        <Button
          className="btn-secondary"
          busy={duplicating}
          disabled={!aiConfigured}
          onClick={onDuplicate}
        >
          {m.duplicate}
        </Button>
        {system ? null : (
          <Button className="btn-outline-danger" icon="trash" onClick={onDelete}>
            {m.delete}
          </Button>
        )}
      </div>
    </div>
  );
}
