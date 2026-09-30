import { IconBrandTelegram } from "@tabler/icons-react";
import { messages } from "./messages";

export function App() {
  return (
    <div className="page">
      <header className="navbar navbar-expand-md d-print-none">
        <div className="container-xl">
          <span className="navbar-brand d-flex align-items-center gap-2">
            <IconBrandTelegram aria-hidden="true" size={24} stroke={1.5} />
            {messages.appName}
          </span>
        </div>
      </header>
      <div className="page-wrapper">
        <main className="page-body">
          <div className="container-xl">
            <div className="empty">
              <h1 className="empty-title">{messages.home.title}</h1>
              <p className="empty-subtitle text-secondary">{messages.home.body}</p>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
