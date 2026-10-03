import "@tabler/core/dist/css/tabler.min.css";
import "./styles.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { startThemeRuntime } from "./shell/theme";

const root = document.getElementById("root");
if (!root) throw new Error("Missing #root element");

startThemeRuntime();

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
