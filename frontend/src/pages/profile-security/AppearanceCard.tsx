import { ThemeSwitch } from "../../components/ThemeSwitch/ThemeSwitch";
import { messages } from "../../messages";

export function AppearanceCard() {
  const m = messages.profileSecurity;
  return (
    <section className="card mb-4" aria-labelledby="appearance-title">
      <div className="card-header">
        <h2 id="appearance-title" className="card-title">
          {m.appearanceTitle}
        </h2>
      </div>
      <div className="card-body">
        <ThemeSwitch variant="segmented" />
        <small className="d-block text-secondary mt-2">{messages.theme.hint}</small>
      </div>
    </section>
  );
}
