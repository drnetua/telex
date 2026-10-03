import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../../app/AppRoutes";
import { createAppQueryClient } from "../../app/queryClient";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const pending = () => new Promise<Response>(() => undefined);
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };

const model = (over: Record<string, unknown>) => ({
  modelId: "test/x",
  name: "X",
  provider: "test",
  takes: ["text"],
  produces: ["text"],
  slots: ["text"],
  inputPricePerMillionTokens: "1.00",
  outputPricePerMillionTokens: "2.00",
  pricePerImage: null,
  contextLength: 128000,
  ...over,
});
const models = [
  model({ modelId: "test/text-a", name: "Test text model A", provider: "alpha" }),
  model({
    modelId: "test/vision-b",
    name: "Test vision model B",
    provider: "test",
    takes: ["text", "image"],
    slots: ["text", "vision"],
    inputPricePerMillionTokens: "2.5",
    outputPricePerMillionTokens: "10",
    contextLength: 200000,
  }),
  model({
    modelId: "test/image-c",
    name: "Test image model C",
    provider: "pics",
    takes: ["text"],
    produces: ["image"],
    slots: ["image"],
    inputPricePerMillionTokens: "0.3",
    outputPricePerMillionTokens: "30",
    pricePerImage: "0.04",
    contextLength: null,
  }),
  model({
    modelId: "test/free-d",
    name: "Test free model D",
    inputPricePerMillionTokens: "0",
    outputPricePerMillionTokens: "0",
  }),
  model({
    modelId: "test/unknown-e",
    name: "Test unknown model E",
    inputPricePerMillionTokens: null,
    outputPricePerMillionTokens: null,
  }),
];
const catalog = (over: Record<string, unknown> = {}) => ({
  state: "current",
  lastRefreshedAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
  lastFailedAt: null,
  models,
  ...over,
});
const profilesBody = {
  aiConfigured: true,
  defaultProfile: { kind: "system", key: "balanced" },
  customProfileLimit: 20,
  items: [],
};

function stubApi(
  catalogResponse: () => Response | Promise<Response>,
  profiles: Record<string, unknown> = profilesBody,
) {
  const calls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      calls.push(url);
      if (url === "/api/v1/me") return Promise.resolve(json(200, me));
      if (url === "/api/v1/models/catalog") return Promise.resolve(catalogResponse());
      if (url.startsWith("/api/v1/models/profiles")) return Promise.resolve(json(200, profiles));
      return Promise.resolve(new Response(null, { status: 204 }));
    }),
  );
  return calls;
}

function Where() {
  const l = useLocation();
  return <div data-testid="where">{l.pathname + l.search}</div>;
}

function setup(entry = "/settings/models?tab=catalog") {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <MemoryRouter initialEntries={[entry]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const rowOf = (name: string) =>
  screen.getByText(name).closest("tr, [role=row], li, article") as HTMLElement;

afterEach(() => vi.unstubAllGlobals());

describe("SCR-66 route, tabs and navigation", () => {
  it("PageFrame has an interim Models link to /settings/models", async () => {
    stubApi(() => json(200, catalog()));
    setup("/inbox");
    const link = await screen.findByRole("link", { name: "Models" });
    expect(link).toHaveAttribute("href", "/settings/models");
  });

  it("/settings/models renders the Models page with Profiles and Model catalog tabs", async () => {
    stubApi(() => json(200, catalog()));
    setup("/settings/models");
    expect(await screen.findByRole("heading", { level: 1, name: "Models" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Profiles" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Model catalog" })).toBeInTheDocument();
  });

  it.each(["/settings/models?tab=bogus", "/settings/models"])(
    "%s falls back to the Profiles tab",
    async (entry) => {
      stubApi(() => json(200, catalog()));
      setup(entry);
      expect(
        await screen.findByRole("tab", { name: "Profiles", selected: true }),
      ).toBeInTheDocument();
    },
  );

  it("clicking Model catalog puts ?tab=catalog in the URL", async () => {
    stubApi(() => json(200, catalog()));
    setup("/settings/models");
    await userEvent.click(await screen.findByRole("tab", { name: "Model catalog" }));
    expect(screen.getByTestId("where")).toHaveTextContent("/settings/models?tab=catalog");
    expect(await screen.findByText("Test text model A")).toBeInTheDocument();
  });

  it.each(["/settings/models/profiles/new", "/settings/models/profiles/balanced"])(
    "editor route %s renders the page for now",
    async (entry) => {
      stubApi(() => json(200, catalog()));
      setup(entry);
      expect(await screen.findByRole("heading", { level: 1, name: "Models" })).toBeInTheDocument();
    },
  );
});

describe("SCR-66 Model catalog tab", () => {
  it("loading: a LoadState skeleton of 6 rows", async () => {
    stubApi(pending);
    setup();
    const status = await screen.findByRole("status");
    expect(status).toHaveAttribute("aria-busy", "true");
    expect(status.querySelectorAll(".placeholder")).toHaveLength(6);
  });

  it("AC-211 default: Updated line with absolute time tooltip, columns, rows ordered by name, no pagination", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    const updated = await screen.findByText(/^Updated 3 hours ago$/);
    expect(updated).toHaveAttribute("title");
    for (const h of ["Model", "Takes", "Produces", "Price", "Context"])
      expect(screen.getAllByText(h).length).toBeGreaterThan(0);
    const names = screen.getAllByText(/^Test .* model [A-E]$/).map((e) => e.textContent);
    expect(names).toEqual([...names].sort());
    expect(screen.queryByRole("navigation", { name: /pagination/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /csv|export/i })).not.toBeInTheDocument();
  });

  it("AC-211: a token model row shows provider, takes, produces, $ in/out per 1M tokens and context", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test vision model B");
    const row = within(rowOf("Test vision model B"));
    expect(row.getByText(/test/)).toBeInTheDocument();
    expect(row.getByText(/Text, images/)).toBeInTheDocument();
    expect(row.getByText("$2.50")).toHaveAttribute("title", "2.5");
    expect(row.getByText("$10.00")).toHaveAttribute("title", "10");
    expect(row.getByText(/per 1M tokens/)).toBeInTheDocument();
    expect(row.getByText("200,000")).toBeInTheDocument();
  });

  it("AC-211: an image-output model shows its per-1M-token price (never per image) and Context em dash", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test image model C");
    const row = within(rowOf("Test image model C"));
    expect(row.getByText("$0.30")).toBeInTheDocument();
    expect(row.getByText("$30.00")).toBeInTheDocument();
    expect(row.getByText(/per 1M tokens/)).toBeInTheDocument();
    expect(row.queryByText(/per image/)).not.toBeInTheDocument();
    expect(row.getByText("—")).toBeInTheDocument();
  });

  it("edge: null prices show 'Price unknown'; zero prices show 'Free'", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test unknown model E");
    expect(within(rowOf("Test unknown model E")).getByText("Price unknown")).toBeInTheDocument();
    expect(within(rowOf("Test free model D")).getByText("Free")).toBeInTheDocument();
  });

  it("AC-211 filtered: searching by name narrows the list, case-insensitively, in the browser", async () => {
    const calls = stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test text model A");
    await userEvent.type(screen.getByRole("searchbox"), "VISION");
    expect(screen.getByText("Test vision model B")).toBeInTheDocument();
    expect(screen.queryByText("Test text model A")).not.toBeInTheDocument();
    expect(calls.filter((u) => u === "/api/v1/models/catalog")).toHaveLength(1);
  });

  it("AC-211 filtered: Slot = Vision shows only vision-slot models and keeps the state in the URL", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test text model A");
    await userEvent.selectOptions(screen.getByLabelText("Slot"), "Vision");
    expect(screen.getByText("Test vision model B")).toBeInTheDocument();
    for (const gone of ["Test text model A", "Test image model C"])
      expect(screen.queryByText(gone)).not.toBeInTheDocument();
    expect(screen.getByTestId("where").textContent).toMatch(/slot=vision/);
  });

  it("AC-211 filtered: search and Slot combine; the Image slot shows image models", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test text model A");
    await userEvent.selectOptions(screen.getByLabelText("Slot"), "Image");
    expect(screen.getByText("Test image model C")).toBeInTheDocument();
    expect(screen.queryByText("Test vision model B")).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole("searchbox"), "zzz");
    expect(screen.queryByText("Test image model C")).not.toBeInTheDocument();
  });

  it("filtered empty: 'No models match your search.' and Reset all restores the list", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test text model A");
    await userEvent.type(screen.getByRole("searchbox"), "nothing-like-this");
    expect(screen.getByText("No models match your search.")).toBeInTheDocument();
    const empty = screen.getByText("No models match your search.").closest(".empty") as HTMLElement;
    expect(empty).toHaveAttribute("data-kind", "none");
    await userEvent.click(within(empty).getByRole("button", { name: "Reset all" }));
    expect(await screen.findByText("Test text model A")).toBeInTheDocument();
    expect(screen.getByRole("searchbox")).toHaveValue("");
  });

  it("filters are read from the URL on open (?slot=vision&q=...)", async () => {
    stubApi(() => json(200, catalog()));
    setup("/settings/models?tab=catalog&slot=vision");
    expect(await screen.findByText("Test vision model B")).toBeInTheDocument();
    expect(screen.queryByText("Test text model A")).not.toBeInTheDocument();
  });

  it("AC-212 update-failed: the last list plus a warning alert with icon and the time it is from", async () => {
    stubApi(() =>
      json(
        200,
        catalog({
          state: "update-failed",
          lastRefreshedAt: "2026-10-02T06:00:00Z",
          lastFailedAt: "2026-10-02T09:00:00Z",
        }),
      ),
    );
    setup();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      /The model list couldn't be updated\. It's from 2 Oct, \d\d:\d\d\./,
    );
    expect(alert.querySelector("svg, .icon, [data-icon]")).not.toBeNull();
    expect(screen.getByText("Test text model A")).toBeInTheDocument();
  });

  it("AC-212 not-loaded: blocked EmptyState with Check again, which refetches the catalog", async () => {
    let n = 0;
    const calls = stubApi(() => {
      n += 1;
      return json(
        200,
        n === 1 ? catalog({ state: "not-loaded", lastRefreshedAt: null, models: [] }) : catalog(),
      );
    });
    setup();
    const text = await screen.findByText(
      "The model list isn't available yet. teleX tries again every 5 minutes.",
    );
    expect(text.closest(".empty")).toHaveAttribute("data-kind", "blocked");
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Check again" }));
    expect(await screen.findByText("Test text model A")).toBeInTheDocument();
    expect(calls.filter((u) => u === "/api/v1/models/catalog")).toHaveLength(2);
  });

  it("AC-226 not-configured: page alert, blocked EmptyState, and Go to profiles switches the tab", async () => {
    stubApi(() =>
      json(200, catalog({ state: "not-configured", lastRefreshedAt: null, models: [] })),
    );
    setup();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "AI models aren't set up on this installation yet. Ask the person who runs teleX.",
    );
    const empty = screen.getByText("No models without AI set up.").closest(".empty") as HTMLElement;
    expect(empty).toHaveAttribute("data-kind", "blocked");
    await userEvent.click(within(empty).getByRole("button", { name: "Go to profiles" }));
    expect(screen.getByTestId("where").textContent).not.toMatch(/tab=catalog/);
    expect(screen.getByRole("tab", { name: "Profiles", selected: true })).toBeInTheDocument();
  });

  it("error: a 500 is routed to the failure screen, no table rendered", async () => {
    stubApi(() => json(500, { code: "internal-error" }));
    setup();
    expect(await screen.findByText(/teleX is unavailable/)).toBeInTheDocument();
    expect(screen.queryByText("Test text model A")).not.toBeInTheDocument();
  });

  it("each tab renders from its own query: both lists are requested when the page opens", async () => {
    const calls = stubApi(() => json(200, catalog()));
    setup("/settings/models");
    await screen.findByRole("heading", { name: "Models" });
    await vi.waitFor(() => {
      expect(calls).toContain("/api/v1/models/catalog");
      expect(calls.some((u) => u.startsWith("/api/v1/models/profiles"))).toBe(true);
    });
  });

  it("phone: search is a labelled control; rows are label/value cards (DataTable's own behaviour) with data-label cells", async () => {
    stubApi(() => json(200, catalog()));
    setup();
    await screen.findByText("Test vision model B");
    const cells = rowOf("Test vision model B").querySelectorAll("[data-label]");
    expect([...cells].map((c) => c.getAttribute("data-label"))).toEqual(
      expect.arrayContaining(["Takes", "Produces", "Price", "Context"]),
    );
  });

  const ALERT = "AI models aren't set up on this installation yet. Ask the person who runs teleX.";
  const above = (a: HTMLElement) =>
    a.compareDocumentPosition(screen.getByRole("tablist")) & Node.DOCUMENT_POSITION_FOLLOWING;

  it("F4: not-configured on the catalog tab shows one alert above the tabs", async () => {
    stubApi(() =>
      json(200, catalog({ state: "not-configured", lastRefreshedAt: null, models: [] })),
    );
    setup();
    await screen.findByText("No models without AI set up.");
    const alerts = screen.getAllByRole("alert");
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toHaveTextContent(ALERT);
    expect(above(alerts[0] as HTMLElement)).toBeTruthy();
  });

  it("F4: the alert above the tabs also shows on the catalog tab when only the profiles say AI is off", async () => {
    stubApi(() => json(200, catalog()), { ...profilesBody, aiConfigured: false });
    setup();
    await screen.findByText("Test text model A");
    const alerts = screen.getAllByRole("alert");
    expect(alerts).toHaveLength(1);
    expect(above(alerts[0] as HTMLElement)).toBeTruthy();
  });

  it("F7: tabs follow the ARIA tabs pattern: panel, labels, roving tabindex and arrow keys", async () => {
    stubApi(() => json(200, catalog()));
    setup("/settings/models");
    const profilesTab = await screen.findByRole("tab", { name: "Profiles" });
    const catalogTab = screen.getByRole("tab", { name: "Model catalog" });
    const panel = screen.getByRole("tabpanel");
    expect(profilesTab.getAttribute("aria-controls")).toBe(panel.id);
    expect(panel.getAttribute("aria-labelledby")).toBe(profilesTab.id);
    expect(profilesTab).toHaveAttribute("tabindex", "0");
    expect(catalogTab).toHaveAttribute("tabindex", "-1");
    profilesTab.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Model catalog", selected: true })).toHaveFocus();
    expect(screen.getByTestId("where").textContent).toMatch(/tab=catalog/);
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Profiles", selected: true })).toHaveFocus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(screen.getByRole("tab", { name: "Model catalog", selected: true })).toHaveFocus();
  });
});
