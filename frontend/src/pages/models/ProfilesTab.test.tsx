import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../../app/AppRoutes";
import { createAppQueryClient } from "../../app/queryClient";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };

type Ref = { kind: "system"; key: string } | { kind: "custom"; id: string };
const slot = (
  state = "main-model",
  id: string | null = "test/a",
  name: string | null = "Model A",
) => ({
  state,
  currentModelId: id,
  chain: id ? [{ modelId: id, name, availability: "available" }] : [],
});
const unused = () => slot("not-used", null);
const noModel = () => ({
  state: "no-model-available",
  currentModelId: null,
  chain: [{ modelId: "test/gone", name: null, availability: "not-in-catalog" }],
});
const price = (state: string, amount: string | null) => ({ state, amount });
const prof = (ref: Ref, name: string, over: Record<string, unknown> = {}) => ({
  ref,
  name,
  slots: { text: slot(), vision: unused(), image: unused() },
  pricePer100Runs: price("estimate", "0.30"),
  choosable: true,
  ...over,
});
const sys = (key: string, name: string, over: Record<string, unknown> = {}) =>
  prof({ kind: "system", key }, name, over);
const fast = () => sys("fast", "Fast and cheap", { pricePer100Runs: price("estimate", "0.05") });
const balanced = () => sys("balanced", "Balanced");
const careful = () => sys("careful", "Careful", { pricePer100Runs: price("estimate", "2.5") });
const cheapVision = (over: Record<string, unknown> = {}) =>
  prof({ kind: "custom", id: "c1" }, "Cheap vision", over);

interface Api {
  list: Record<string, unknown>;
  put?: () => Response | Promise<Response>;
  del?: () => Response | Promise<Response>;
  draft?: () => Response | Promise<Response>;
}
function stubApi(api: Api) {
  const calls: { url: string; method: string; body?: string }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      calls.push({ url, method, body: init?.body as string | undefined });
      if (url === "/api/v1/me") return Promise.resolve(json(200, me));
      if (url === "/api/v1/models/catalog")
        return Promise.resolve(
          json(200, { state: "current", lastRefreshedAt: null, lastFailedAt: null, models: [] }),
        );
      if (url === "/api/v1/models/profiles") return Promise.resolve(json(200, api.list));
      if (url === "/api/v1/models/default-profile" && method === "PUT")
        return Promise.resolve(api.put?.() ?? json(200, {}));
      if (url.startsWith("/api/v1/models/profiles/") && method === "DELETE")
        return Promise.resolve(
          api.del?.() ??
            json(200, { defaultProfile: { kind: "system", key: "balanced" }, defaultReset: false }),
        );
      if (url.startsWith("/api/v1/models/profile-draft"))
        return Promise.resolve(api.draft?.() ?? json(200, {}));
      return Promise.resolve(new Response(null, { status: 204 }));
    }),
  );
  return calls;
}
const list = (over: Record<string, unknown> = {}) => ({
  aiConfigured: true,
  defaultProfile: { kind: "system", key: "balanced" },
  customProfileLimit: 20,
  items: [fast(), balanced(), careful(), cheapVision()],
  ...over,
});

function Where() {
  const l = useLocation();
  return <div data-testid="where">{l.pathname + l.search}</div>;
}
function setup() {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <MemoryRouter initialEntries={["/settings/models"]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
const picker = () => screen.findByRole("radiogroup");
const radio = (name: string) =>
  within(screen.getByRole("radiogroup")).getByRole("radio", { name: new RegExp(name) });
const card = (name: string) =>
  screen
    .getAllByRole("heading")
    .find((h) => h.textContent?.startsWith(name))
    ?.closest(".card") as HTMLElement;

afterEach(() => vi.unstubAllGlobals());

describe("SCR-66 Profiles tab", () => {
  it("loading: a LoadState skeleton while the list is requested", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        url === "/api/v1/me"
          ? Promise.resolve(json(200, me))
          : new Promise<Response>(() => undefined),
      ),
    );
    setup();
    const status = await screen.findByRole("status");
    expect(status).toHaveAttribute("aria-busy", "true");
  });

  it("AC-51: picker lists the 3 system profiles and the custom one, each with a price per 100 runs, current one marked Default", async () => {
    stubApi({ list: list() });
    setup();
    await picker();
    expect(within(screen.getByRole("radiogroup")).getAllByRole("radio")).toHaveLength(4);
    expect(radio("Balanced")).toBeChecked();
    expect(within(screen.getByRole("radiogroup")).getByText("Default")).toBeInTheDocument();
    expect(
      within(screen.getByRole("radiogroup")).getByText("≈ $2.50 per 100 runs"),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole("radiogroup")).getByText("≈ $0.05 per 100 runs"),
    ).toBeInTheDocument();
  });

  it("order: picker first, then Fast and cheap, Balanced, Careful cards, then 'Your profiles' with Create profile", async () => {
    stubApi({ list: list() });
    setup();
    await picker();
    const headings = screen.getAllByRole("heading").map((h) => h.textContent);
    const idx = (s: string) => headings.findIndex((t) => t?.startsWith(s));
    expect(idx("Fast and cheap")).toBeLessThan(idx("Balanced"));
    expect(idx("Balanced")).toBeLessThan(idx("Careful"));
    expect(idx("Careful")).toBeLessThan(idx("Your profiles"));
    expect(idx("Your profiles")).toBeLessThan(idx("Cheap vision"));
    expect(screen.getByRole("button", { name: "Create profile" })).toBeEnabled();
  });

  it("AC-51: choosing Careful sends setDefaultModelProfile, moves Default and toasts 'Careful is now your default profile.'", async () => {
    const calls = stubApi({ list: list() });
    setup();
    await picker();
    await userEvent.click(radio("Careful"));
    const put = calls.find((c) => c.url === "/api/v1/models/default-profile");
    expect(put?.method).toBe("PUT");
    expect(JSON.parse(put?.body ?? "{}")).toEqual({ profile: { kind: "system", key: "careful" } });
    expect(await screen.findByText("Careful is now your default profile.")).toBeInTheDocument();
  });

  it("choosing: the option shows a spinner and the other radios are read-only until the call ends", async () => {
    stubApi({ list: list(), put: () => new Promise<Response>(() => undefined) });
    setup();
    await picker();
    await userEvent.click(radio("Careful"));
    expect(radio("Careful").closest("label")?.querySelector(".spinner-border")).not.toBeNull();
    expect(radio("Balanced")).toBeDisabled();
    expect(radio("Fast and cheap")).toBeDisabled();
  });

  it("choose refused 409 no-text-model: reverts, refetches the list, error toast", async () => {
    const calls = stubApi({
      list: list(),
      put: () =>
        json(409, { type: "urn:telex:error:no-text-model", code: "no-text-model", errors: [] }),
    });
    setup();
    await picker();
    await userEvent.click(radio("Careful"));
    expect(
      await screen.findByText("Careful can't be your default right now. Choose another profile."),
    ).toBeInTheDocument();
    expect(radio("Balanced")).toBeChecked();
    await vi.waitFor(() =>
      expect(calls.filter((c) => c.url === "/api/v1/models/profiles").length).toBeGreaterThan(1),
    );
  });

  it("choose refused 404 not-found: error toast 'This profile no longer exists.' and the list refetches", async () => {
    const calls = stubApi({
      list: list(),
      put: () => json(404, { type: "urn:telex:error:not-found", code: "not-found", errors: [] }),
    });
    setup();
    await picker();
    await userEvent.click(radio("Cheap vision"));
    expect(await screen.findByText("This profile no longer exists.")).toBeInTheDocument();
    expect(radio("Balanced")).toBeChecked();
    await vi.waitFor(() =>
      expect(calls.filter((c) => c.url === "/api/v1/models/profiles").length).toBeGreaterThan(1),
    );
  });

  it("AC-210: Price unknown, Free and '< $0.01' states show in the picker and the options stay choosable", async () => {
    stubApi({
      list: list({
        items: [
          fast(),
          balanced(),
          careful(),
          prof({ kind: "custom", id: "c1" }, "Mystery", {
            pricePer100Runs: price("unknown", null),
          }),
          prof({ kind: "custom", id: "c2" }, "Gratis", { pricePer100Runs: price("free", "0") }),
          prof({ kind: "custom", id: "c3" }, "Tiny", {
            pricePer100Runs: price("under-one-cent", "0.004"),
          }),
        ],
      }),
    });
    setup();
    await picker();
    const group = within(screen.getByRole("radiogroup"));
    expect(group.getByText("Price unknown")).toBeInTheDocument();
    expect(group.getByText("Free")).toBeInTheDocument();
    expect(group.getByText("< $0.01 per 100 runs")).toBeInTheDocument();
    expect(radio("Mystery")).toBeEnabled();
  });

  it("AC-10: a fallback slot warns on the card and in the picker; the price follows the model in use", async () => {
    const text = {
      state: "fallback",
      currentModelId: "test/b",
      chain: [
        { modelId: "test/a", name: null, availability: "not-in-catalog" },
        { modelId: "test/b", name: "Model B", availability: "available" },
      ],
    };
    stubApi({
      list: list({
        items: [
          fast(),
          balanced(),
          careful(),
          cheapVision({
            slots: { text, vision: unused(), image: unused() },
            pricePer100Runs: price("estimate", "0.12"),
          }),
        ],
      }),
    });
    setup();
    await picker();
    const warning = "Main model unavailable. Using Model B for now.";
    expect(within(screen.getByRole("radiogroup")).getByText(warning)).toBeInTheDocument();
    expect(within(card("Cheap vision")).getByText(warning)).toBeInTheDocument();
    expect(within(card("Cheap vision")).getByText("≈ $0.12 per 100 runs")).toBeInTheDocument();
  });

  it("AC-223: slot states on cards - Not used, 'Not in the catalog' badge, 'Pick another model' (custom) vs 'Choose another profile' (system)", async () => {
    const slots = { text: slot(), vision: noModel(), image: unused() };
    stubApi({
      list: list({
        items: [fast(), sys("balanced", "Balanced", { slots }), careful(), cheapVision({ slots })],
      }),
    });
    setup();
    await picker();
    expect(within(card("Careful")).getAllByText("Not used").length).toBeGreaterThan(0);
    expect(within(card("Cheap vision")).getByText("No model available")).toBeInTheDocument();
    expect(within(card("Cheap vision")).getByText("test/gone")).toBeInTheDocument();
    expect(within(card("Cheap vision")).getByText("Not in the catalog")).toBeInTheDocument();
    expect(within(card("Cheap vision")).getByText("Pick another model")).toBeInTheDocument();
    expect(within(card("Balanced")).getByText("Choose another profile")).toBeInTheDocument();
    expect(within(card("Balanced")).queryByText("Pick another model")).not.toBeInTheDocument();
  });

  it("AC-223: 'Choose another profile' on a system card focuses the picker", async () => {
    const slots = { text: slot(), vision: noModel(), image: unused() };
    stubApi({ list: list({ items: [fast(), sys("balanced", "Balanced", { slots }), careful()] }) });
    setup();
    await picker();
    await userEvent.click(within(card("Balanced")).getByText("Choose another profile"));
    expect(screen.getByRole("radiogroup").contains(document.activeElement)).toBe(true);
  });

  it("AC-223: a no-text-model profile shows 'No model available for text' with no price and a disabled radio", async () => {
    stubApi({
      list: list({
        items: [
          fast(),
          balanced(),
          careful(),
          cheapVision({
            choosable: false,
            pricePer100Runs: price("no-text-model", null),
            slots: { text: noModel(), vision: unused(), image: unused() },
          }),
        ],
      }),
    });
    setup();
    await picker();
    expect(radio("Cheap vision")).toBeDisabled();
    const option = radio("Cheap vision").closest("label") as HTMLElement;
    expect(within(option).getByText("No model available for text")).toBeInTheDocument();
    expect(within(option).queryByText(/per 100 runs/)).not.toBeInTheDocument();
    expect(
      screen.queryByText("Your default profile has no model for text. Choose another profile."),
    ).not.toBeInTheDocument();
  });

  it("AC-223: when the default has no text model a warning alert shows above the picker and the profile stays the default", async () => {
    stubApi({
      list: list({
        defaultProfile: { kind: "custom", id: "c1" },
        items: [
          fast(),
          balanced(),
          careful(),
          cheapVision({
            choosable: false,
            pricePer100Runs: price("no-text-model", null),
            slots: { text: noModel(), vision: unused(), image: unused() },
          }),
        ],
      }),
    });
    setup();
    await picker();
    const alert = screen
      .getByText("Your default profile has no model for text. Choose another profile.")
      .closest("[role=alert], .alert") as HTMLElement;
    expect(alert).not.toBeNull();
    expect(alert.querySelector("svg, .icon, [data-icon]")).not.toBeNull();
    expect(
      alert.compareDocumentPosition(screen.getByRole("radiogroup")) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(radio("Cheap vision")).toBeChecked();
  });

  it("AC-219: system cards show 'System', the explanation and Duplicate only; custom cards have Edit, Duplicate, Delete", async () => {
    stubApi({ list: list() });
    setup();
    await picker();
    const b = within(card("Balanced"));
    expect(b.getByText("System")).toBeInTheDocument();
    expect(
      b.getByText("System profiles can't be changed. Duplicate it to make your own."),
    ).toBeInTheDocument();
    expect(b.getByRole("button", { name: "Duplicate" })).toBeInTheDocument();
    expect(b.queryByRole("button", { name: /Edit|Delete/ })).not.toBeInTheDocument();
    const c = within(card("Cheap vision"));
    for (const n of ["Edit", "Duplicate", "Delete"])
      expect(c.getByRole("button", { name: n })).toBeEnabled();
  });

  it("empty: no custom profiles shows EmptyState first with a Create profile action", async () => {
    stubApi({ list: list({ items: [fast(), balanced(), careful()] }) });
    setup();
    await picker();
    const empty = screen
      .getByText("Make your own profile with the models you trust.")
      .closest(".empty") as HTMLElement;
    expect(empty).toHaveAttribute("data-kind", "first");
    expect(within(empty).getByRole("button", { name: "Create profile" })).toBeInTheDocument();
  });

  it("opening the editor: Create profile fetches the draft and opens the editor route", async () => {
    const calls = stubApi({ list: list() });
    setup();
    await picker();
    await userEvent.click(screen.getByRole("button", { name: "Create profile" }));
    await vi.waitFor(() =>
      expect(screen.getByTestId("where").textContent).toMatch(/^\/settings\/models\/profiles\/new/),
    );
    expect(calls.some((c) => c.url === "/api/v1/models/profile-draft")).toBe(true);
  });

  it("opening the editor: Duplicate on a system profile requests the draft ?from=balanced and the Duplicate button is busy meanwhile", async () => {
    const calls = stubApi({ list: list(), draft: () => new Promise<Response>(() => undefined) });
    setup();
    await picker();
    await userEvent.click(within(card("Balanced")).getByRole("button", { name: "Duplicate" }));
    expect(calls.some((c) => c.url === "/api/v1/models/profile-draft?from=balanced")).toBe(true);
    expect(
      within(card("Balanced"))
        .getByRole("button", { name: "Duplicate" })
        .querySelector(".spinner-border"),
    ).not.toBeNull();
    expect(screen.getByTestId("where").textContent).not.toMatch(/profiles\/new/);
  });

  it("AC-218: 409 profile-limit-reached on the draft - the editor doesn't open, error toast names the limit", async () => {
    stubApi({
      list: list(),
      draft: () =>
        json(409, {
          type: "urn:telex:error:profile-limit-reached",
          code: "profile-limit-reached",
          errors: [],
        }),
    });
    setup();
    await picker();
    await userEvent.click(screen.getByRole("button", { name: "Create profile" }));
    expect(
      await screen.findByText("You can have up to 20 custom profiles. Delete one to make room."),
    ).toBeInTheDocument();
    expect(screen.getByTestId("where").textContent).toBe("/settings/models");
  });

  it("delete: a danger confirm 'Delete Cheap vision?' with 'This can't be undone.' and Delete profile / Cancel; Cancel sends nothing", async () => {
    const calls = stubApi({ list: list() });
    setup();
    await picker();
    await userEvent.click(within(card("Cheap vision")).getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("dialog", { name: "Delete Cheap vision?" });
    expect(within(dialog).getByText("This can't be undone.")).toBeInTheDocument();
    expect(within(dialog).queryByText(/becomes the default/)).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(calls.some((c) => c.method === "DELETE")).toBe(false);
  });

  it("AC-220: deleting the default adds the reset notice, and on confirm toasts 'Cheap vision deleted. Balanced is now your default profile.'", async () => {
    let deleted = false;
    const calls = stubApi({
      list: list({ defaultProfile: { kind: "custom", id: "c1" } }),
      del: () => {
        deleted = true;
        return json(200, {
          defaultProfile: { kind: "system", key: "balanced" },
          defaultReset: true,
        });
      },
    });
    setup();
    await picker();
    await userEvent.click(within(card("Cheap vision")).getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("dialog", { name: "Delete Cheap vision?" });
    expect(
      within(dialog).getByText("It's your default profile, so Balanced becomes the default."),
    ).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Delete profile" }));
    expect(
      await screen.findByText("Cheap vision deleted. Balanced is now your default profile."),
    ).toBeInTheDocument();
    expect(calls.find((c) => c.method === "DELETE")?.url).toBe("/api/v1/models/profiles/c1");
    expect(deleted).toBe(true);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("deleted (not the default): toast '<profile> deleted.'", async () => {
    stubApi({ list: list() });
    setup();
    await picker();
    await userEvent.click(within(card("Cheap vision")).getByRole("button", { name: "Delete" }));
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "Delete profile" }),
    );
    expect(await screen.findByText("Cheap vision deleted.")).toBeInTheDocument();
  });

  it("delete refused 404: the list refetches and an error toast says 'This profile no longer exists.'", async () => {
    const calls = stubApi({
      list: list(),
      del: () => json(404, { type: "urn:telex:error:not-found", code: "not-found", errors: [] }),
    });
    setup();
    await picker();
    await userEvent.click(within(card("Cheap vision")).getByRole("button", { name: "Delete" }));
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "Delete profile" }),
    );
    expect(await screen.findByText("This profile no longer exists.")).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(calls.filter((c) => c.url === "/api/v1/models/profiles").length).toBeGreaterThan(1),
    );
  });

  it("AC-226: AI not set up - a warning alert above the tabs, system slots 'No model available', Create/Duplicate/Edit disabled, Delete enabled", async () => {
    const dead = { text: noModel(), vision: noModel(), image: noModel() };
    stubApi({
      list: list({
        aiConfigured: false,
        items: [
          sys("fast", "Fast and cheap", {
            slots: dead,
            choosable: false,
            pricePer100Runs: price("no-text-model", null),
          }),
          sys("balanced", "Balanced", {
            slots: dead,
            choosable: false,
            pricePer100Runs: price("no-text-model", null),
          }),
          sys("careful", "Careful", {
            slots: dead,
            choosable: false,
            pricePer100Runs: price("no-text-model", null),
          }),
          cheapVision(),
        ],
      }),
    });
    setup();
    await picker();
    const text = "AI models aren't set up on this installation yet. Ask the person who runs teleX.";
    const alert = screen.getByText(text).closest("[role=alert], .alert") as HTMLElement;
    expect(alert.querySelector("svg, .icon, [data-icon]")).not.toBeNull();
    expect(
      alert.compareDocumentPosition(screen.getByRole("tablist")) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(within(card("Balanced")).getAllByText("No model available")).toHaveLength(3);
    expect(screen.getByRole("button", { name: "Create profile" })).toBeDisabled();
    for (const b of within(card("Balanced")).getAllByRole("button", { name: "Duplicate" }))
      expect(b).toBeDisabled();
    expect(within(card("Cheap vision")).getByRole("button", { name: "Duplicate" })).toBeDisabled();
    expect(within(card("Cheap vision")).getByRole("button", { name: "Edit" })).toBeDisabled();
    expect(within(card("Cheap vision")).getByRole("button", { name: "Delete" })).toBeEnabled();
  });

  it("no AI alert when AI is configured", async () => {
    stubApi({ list: list() });
    setup();
    await picker();
    expect(screen.queryByText(/AI models aren't set up/)).not.toBeInTheDocument();
  });

  it("edge: a profile name with <script> is plain text on the card, in the picker and in the delete dialog", async () => {
    const evil = "<script>alert(1)</script>";
    stubApi({
      list: list({
        items: [fast(), balanced(), careful(), prof({ kind: "custom", id: "c9" }, evil)],
      }),
    });
    setup();
    await picker();
    expect(document.querySelector("script")).toBeNull();
    expect(screen.getAllByText(evil, { exact: false }).length).toBeGreaterThan(1);
    await userEvent.click(within(card(evil)).getByRole("button", { name: "Delete" }));
    expect(screen.getByRole("dialog", { name: `Delete ${evil}?` })).toBeInTheDocument();
  });

  it("error: a 500 on the list is routed to the failure screen", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        Promise.resolve(
          url === "/api/v1/me" ? json(200, me) : json(500, { code: "internal-error" }),
        ),
      ),
    );
    setup();
    expect(await screen.findByText(/teleX is unavailable/)).toBeInTheDocument();
  });
});
