import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../../app/AppRoutes";
import { createAppQueryClient } from "../../app/queryClient";

// SCR-34: profile editor modal + ChainEditor + ModelChooser, exercised through the two child routes.
// ChainEditor and ModelChooser are covered here through their DOM contract (roles/names below), not prop APIs.

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const problem = (status: number, code: string, errors?: unknown[]) =>
  json(status, {
    type: `urn:telex:error:${code}`,
    title: "x",
    status,
    detail: "x",
    code,
    ...(errors ? { errors } : {}),
  });
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };

const cat = (modelId: string, name: string, slots: string[], over = {}) => ({
  modelId,
  name,
  provider: "Test",
  takes: slots.includes("vision") ? ["text", "image"] : ["text"],
  produces: slots.includes("image") ? ["image"] : ["text"],
  slots,
  inputPricePerMillionTokens: "1",
  outputPricePerMillionTokens: "2",
  pricePerImage: null,
  contextLength: 1000,
  ...over,
});
const catalog = {
  state: "current",
  lastRefreshedAt: null,
  lastFailedAt: null,
  models: [
    cat("test/a", "Test text model A", ["text"]),
    cat("test/b", "Test text model B", ["text"]),
    cat("test/c", "Test text model C", ["text"]),
    cat("test/d", "Test text model D", ["text"]),
    cat("test/v", "Test vision model", ["text", "vision"]),
    cat("test/img", "Test image model", ["image"], { takes: ["text"], produces: ["image"] }),
  ],
};

const link = (id: string, name: string | null, availability = "available") => ({
  modelId: id,
  name,
  availability,
});
const A = () => link("test/a", "Test text model A");
const B = () => link("test/b", "Test text model B");
const C = () => link("test/c", "Test text model C");
const GONE = () => link("test/gone", null, "not-in-catalog");
const slot = (chain: ReturnType<typeof link>[]) => ({
  state: chain.length ? "main-model" : "not-used",
  currentModelId: chain[0]?.modelId ?? null,
  chain,
});
const slots = (text: ReturnType<typeof link>[], vision = [] as ReturnType<typeof link>[]) => ({
  text: slot(text),
  vision: slot(vision),
  image: slot([]),
});
const price = { state: "estimate", amount: "0.30" };
const sysRef = { kind: "system", key: "balanced" };
const profile = (id: string, name: string, s = slots([A()])) => ({
  ref: { kind: "custom", id },
  name,
  slots: s,
  pricePer100Runs: price,
  choosable: true,
});
const listBody = {
  aiConfigured: true,
  defaultProfile: sysRef,
  customProfileLimit: 20,
  items: [
    {
      ref: sysRef,
      name: "Balanced",
      slots: slots([A()]),
      pricePer100Runs: price,
      choosable: true,
    },
    profile("c1", "Cheap vision"),
    profile("c2", "Night shift"),
  ],
};
const draft = (over: Record<string, unknown> = {}) => ({
  name: "",
  duplicatedFrom: null,
  slots: slots([]),
  pricePer100Runs: { state: "no-text-model", amount: null },
  ...over,
});

interface Api {
  draft?: (url: string) => Response;
  get?: (id: string) => Response;
  post?: () => Response;
  put?: () => Response;
}
type Call = { url: string; method: string; body?: unknown };
function stubApi(api: Api = {}) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      calls.push({ url, method, body: init?.body ? JSON.parse(init.body as string) : undefined });
      const r = (res: Response) => Promise.resolve(res);
      if (url === "/api/v1/me") return r(json(200, me));
      if (url === "/api/v1/models/catalog") return r(json(200, catalog));
      if (url === "/api/v1/models/profiles" && method === "GET") return r(json(200, listBody));
      if (url === "/api/v1/models/profiles" && method === "POST")
        return r(api.post?.() ?? json(201, profile("new1", "Saved")));
      if (url.startsWith("/api/v1/models/profile-draft"))
        return r(api.draft?.(url) ?? json(200, draft()));
      const one = url.match(/^\/api\/v1\/models\/profiles\/([^/?]+)$/);
      if (one && method === "GET")
        return r(api.get?.(one[1] ?? "") ?? json(200, profile("c1", "Cheap vision")));
      if (one && method === "PUT") return r(api.put?.() ?? json(200, profile("c1", "Saved")));
      return r(new Response(null, { status: 204 }));
    }),
  );
  return calls;
}
const writes = (calls: Call[]) => calls.filter((c) => c.method === "POST" || c.method === "PUT");

function Where() {
  const l = useLocation();
  return (
    <>
      <div data-testid="where">{l.pathname + l.search}</div>
      <div data-testid="state">{JSON.stringify(l.state)}</div>
    </>
  );
}
function open(path: string, seed?: (client: QueryClient) => void) {
  const client = createAppQueryClient();
  seed?.(client);
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
const where = () => screen.getByTestId("where").textContent;
const shell = (title = "Create profile") => screen.findByRole("dialog", { name: title });
/** The modal once its draft or profile has loaded (the frame alone shows while it is pending). */
const dialog = async (title = "Create profile") => {
  await screen.findByLabelText("Name");
  return shell(title);
};
const section = (d: HTMLElement, name: string) => within(d).getByRole("group", { name });
const rows = (s: HTMLElement) => within(s).queryAllByRole("listitem");
const rowNames = (s: HTMLElement) => rows(s).map((r) => r.textContent ?? "");
const off = (el: HTMLElement) =>
  (el as HTMLButtonElement).disabled || el.getAttribute("aria-disabled") === "true";
const chooserRow = (re: RegExp) =>
  within(chooser())
    .getAllByRole("listitem")
    .find((r) => re.test(r.textContent ?? "")) as HTMLElement;
const chooser = () => screen.getByRole("region", { name: /choose a model/i });
async function add(
  user: ReturnType<typeof userEvent.setup>,
  d: HTMLElement,
  slotName: string,
  model: string,
) {
  await user.click(within(section(d, slotName)).getByRole("button", { name: "Add model" }));
  await user.click(within(chooser()).getByRole("button", { name: new RegExp(model) }));
}
const save = (user: ReturnType<typeof userEvent.setup>, d: HTMLElement) =>
  user.click(within(d).getByRole("button", { name: "Save profile" }));

afterEach(() => vi.unstubAllGlobals());

describe("SCR-34 profile editor: opening", () => {
  it("new: titled Create profile, modal-lg + fullscreen below tablet, Name then Text/Vision/Image, Save profile and Cancel", async () => {
    stubApi();
    open("/settings/models/profiles/new");
    const d = await dialog();
    expect(d.closest(".modal-dialog")).toHaveClass("modal-lg", "modal-fullscreen-md-down");
    expect(within(d).getByLabelText("Name")).toHaveValue("");
    for (const s of ["Text", "Vision", "Image"]) expect(section(d, s)).toBeInTheDocument();
    expect(within(d).getByRole("button", { name: "Save profile" })).toBeInTheDocument();
    expect(within(d).getByRole("button", { name: "Cancel" })).toBeInTheDocument();
  });

  it("empty slots: Text says 'Add at least one model.', Vision and Image say 'Not used'", async () => {
    stubApi();
    open("/settings/models/profiles/new");
    const d = await dialog();
    expect(within(section(d, "Text")).getByText("Add at least one model.")).toBeInTheDocument();
    expect(within(section(d, "Vision")).getByText("Not used")).toBeInTheDocument();
    expect(within(section(d, "Image")).getByText("Not used")).toBeInTheDocument();
  });

  it("AC-213 duplicate: draft from ?from= fills 'Balanced copy 2', copies every model, missing ones marked", async () => {
    const calls = stubApi({
      draft: () =>
        json(
          200,
          draft({ name: "Balanced copy 2", duplicatedFrom: sysRef, slots: slots([A(), GONE()]) }),
        ),
    });
    open("/settings/models/profiles/new?from=balanced");
    const d = await dialog();
    expect(calls.some((c) => c.url === "/api/v1/models/profile-draft?from=balanced")).toBe(true);
    expect(within(d).getByLabelText("Name")).toHaveValue("Balanced copy 2");
    const rs = rows(section(d, "Text"));
    expect(rs).toHaveLength(2);
    expect(within(rs[0]!).getByText("Main")).toBeInTheDocument();
    expect(within(rs[1]!).getByText("Backup 1")).toBeInTheDocument();
    expect(within(rs[1]!).getByText("test/gone")).toBeInTheDocument();
    expect(within(rs[1]!).getByText("Not in the catalog")).toBeInTheDocument();
    expect(within(rs[0]!).queryByText("Not in the catalog")).toBeNull();
  });

  it("edit: titled Edit profile, loads GET profiles/:id and prefills name and chains", async () => {
    const calls = stubApi({
      get: () =>
        json(
          200,
          profile("c1", "Cheap vision", slots([A(), B()], [link("test/v", "Test vision model")])),
        ),
    });
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    expect(calls.some((c) => c.url === "/api/v1/models/profiles/c1")).toBe(true);
    expect(within(d).getByLabelText("Name")).toHaveValue("Cheap vision");
    expect(rowNames(section(d, "Text"))).toHaveLength(2);
    expect(rowNames(section(d, "Vision"))[0]).toContain("Test vision model");
  });

  it("system key in :id redirects to /settings/models without asking for the profile", async () => {
    const calls = stubApi();
    open("/settings/models/profiles/balanced");
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(calls.some((c) => c.url === "/api/v1/models/profiles/balanced")).toBe(false);
  });
});

describe("SCR-34 not found and opening refusals (AC-222)", () => {
  it("404 on getModelProfile: no modal, SCR-91 renders at the same URL", async () => {
    stubApi({ get: () => problem(404, "not-found") });
    open("/settings/models/profiles/someone-elses");
    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(where()).toBe("/settings/models/profiles/someone-elses");
  });

  it.each(["/settings/models/profiles/someone-elses", "/settings/models/profiles/new?from=ghost"])(
    "SCR-91 for %s renders in the bare system layout, without the app navigation",
    async (path) => {
      stubApi({
        get: () => problem(404, "not-found"),
        draft: () => problem(404, "not-found"),
      });
      open(path);
      expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
      expect(screen.queryByRole("navigation")).toBeNull();
      expect(screen.queryByRole("banner")).toBeNull();
    },
  );

  it("404 on the draft's from: SCR-91 at that URL", async () => {
    stubApi({ draft: () => problem(404, "not-found") });
    open("/settings/models/profiles/new?from=ghost");
    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("404 on save: SCR-91, same page as a missing id", async () => {
    stubApi({ put: () => problem(404, "not-found") });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    await save(user, await dialog("Edit profile"));
    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("409 profile-limit-reached on the draft (opened by URL): redirect to /settings/models with the limit feedback", async () => {
    stubApi({ draft: () => problem(409, "profile-limit-reached") });
    open("/settings/models/profiles/new");
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(
      await screen.findByText("You can have up to 20 custom profiles. Delete one to make room."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("SCR-34 chain editor (AC-213, AC-216, AC-217)", () => {
  it("Add model puts the chosen model at the end; labels Main, Backup 1, Backup 2; empty hint goes away", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await add(user, d, "Text", "Test text model A");
    await add(user, d, "Text", "Test text model B");
    await add(user, d, "Text", "Test text model C");
    const rs = rows(section(d, "Text"));
    expect(rs.map((r) => r.textContent)).toEqual([
      expect.stringContaining("Test text model A"),
      expect.stringContaining("Test text model B"),
      expect.stringContaining("Test text model C"),
    ]);
    expect(within(rs[0]!).getByText("Main")).toBeInTheDocument();
    expect(within(rs[1]!).getByText("Backup 1")).toBeInTheDocument();
    expect(within(rs[2]!).getByText("Backup 2")).toBeInTheDocument();
    expect(within(section(d, "Text")).queryByText("Add at least one model.")).toBeNull();
  });

  it("each row has Move up, Move down and 'Remove <model>' buttons; ends of the chain can't move out", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A(), B()]))) });
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    const [first, second] = rows(section(d, "Text"));
    expect(off(within(first!).getByRole("button", { name: "Move up" }))).toBe(true);
    expect(off(within(first!).getByRole("button", { name: "Move down" }))).toBe(false);
    expect(off(within(second!).getByRole("button", { name: "Move down" }))).toBe(true);
    expect(
      within(first!).getByRole("button", { name: "Remove Test text model A" }),
    ).toBeInTheDocument();
  });

  it("AC-213 Move up swaps with the neighbour, focus stays on the moved row, order announced politely", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A(), B()]))) });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await user.click(within(rows(section(d, "Text"))[1]!).getByRole("button", { name: "Move up" }));
    const rs = rows(section(d, "Text"));
    expect(rs[0]!.textContent).toContain("Test text model B");
    expect(rs[1]!.textContent).toContain("Test text model A");
    expect(rs[0]!.contains(document.activeElement)).toBe(true);
    expect(document.activeElement?.tagName).toBe("BUTTON");
    const live = screen.getByText("Test text model B is now Main");
    expect(live.closest("[aria-live]")).toHaveAttribute("aria-live", "polite");
  });

  it("keyboard only: Move down via Enter on the focused button keeps focus on the moved row; Remove via Space", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A(), B(), C()]))) });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    within(rows(section(d, "Text"))[0]!)
      .getByRole("button", { name: "Move down" })
      .focus();
    await user.keyboard("{Enter}");
    let rs = rows(section(d, "Text"));
    expect(rs[1]!.textContent).toContain("Test text model A");
    expect(rs[1]!.contains(document.activeElement)).toBe(true);
    within(rs[2]!).getByRole("button", { name: "Remove Test text model C" }).focus();
    await user.keyboard(" ");
    rs = rows(section(d, "Text"));
    expect(rs).toHaveLength(2);
    expect(document.activeElement).not.toBe(document.body);
  });

  it("Remove drops the model from the chain", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A(), B()]))) });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await user.click(within(d).getByRole("button", { name: "Remove Test text model A" }));
    const rs = rows(section(d, "Text"));
    expect(rs).toHaveLength(1);
    expect(rs[0]!.textContent).toContain("Test text model B");
    expect(within(rs[0]!).getByText("Main")).toBeInTheDocument();
  });

  it("AC-217 slot full: Add model disabled with 'A slot holds at most three models.' beside it", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A(), B(), C()]))) });
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    const text = section(d, "Text");
    expect(off(within(text).getByRole("button", { name: "Add model" }))).toBe(true);
    expect(within(text).getByText("A slot holds at most three models.")).toBeInTheDocument();
    expect(
      within(section(d, "Vision")).queryByText("A slot holds at most three models."),
    ).toBeNull();
  });
});

describe("SCR-34 model chooser (AC-216, AC-217)", () => {
  it("lists the whole catalog with a search that filters by name", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.click(within(section(d, "Text")).getByRole("button", { name: "Add model" }));
    expect(within(chooser()).getAllByRole("listitem")).toHaveLength(6);
    await user.type(within(chooser()).getByRole("searchbox"), "vision");
    const left = within(chooser()).getAllByRole("listitem");
    expect(left).toHaveLength(1);
    expect(left[0]!.textContent).toContain("Test vision model");
  });

  it("AC-216 vision slot: a text-only model is disabled with 'Can't understand images' and can't be added", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.click(within(section(d, "Vision")).getByRole("button", { name: "Add model" }));
    const row = chooserRow(/Test text model A/);
    expect(within(row).getByText("Can't understand images")).toBeInTheDocument();
    await user.click(within(row).getByRole("button"));
    expect(rows(section(d, "Vision"))).toHaveLength(0);
    expect(off(within(chooserRow(/Test vision model/)).getByRole("button"))).toBe(false);
  });

  it("AC-216 image slot: a model that can't create images says 'Can't create images'", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.click(within(section(d, "Image")).getByRole("button", { name: "Add model" }));
    const row = chooserRow(/Test text model A/);
    expect(within(row).getByText("Can't create images")).toBeInTheDocument();
    expect(off(within(row).getByRole("button"))).toBe(true);
    expect(off(within(chooserRow(/Test image model/)).getByRole("button"))).toBe(false);
  });

  it("AC-216 text slot: an image-only model says 'Doesn't take and produce text'", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.click(within(section(d, "Text")).getByRole("button", { name: "Add model" }));
    const row = chooserRow(/Test image model/);
    expect(within(row).getByText("Doesn't take and produce text")).toBeInTheDocument();
    expect(off(within(row).getByRole("button"))).toBe(true);
  });

  it("AC-217 a model already in the slot is disabled with 'Already in this slot'", async () => {
    stubApi({ get: () => json(200, profile("c1", "Cheap vision", slots([A()]))) });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await user.click(within(section(d, "Text")).getByRole("button", { name: "Add model" }));
    const row = chooserRow(/Test text model A/);
    expect(within(row).getByText("Already in this slot")).toBeInTheDocument();
    await user.click(within(row).getByRole("button"));
    expect(rows(section(d, "Text"))).toHaveLength(1);
  });
});

describe("SCR-34 saving (AC-213)", () => {
  it("AC-213: duplicate, rename, two text models with the second moved up, one vision model, save: POST body, 'Profile saved', back to /settings/models", async () => {
    const calls = stubApi({
      draft: () =>
        json(200, draft({ name: "Balanced copy", duplicatedFrom: sysRef, slots: slots([A()]) })),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/new?from=balanced");
    const d = await dialog();
    const name = within(d).getByLabelText("Name");
    await user.clear(name);
    await user.type(name, "Cheap vision copy");
    await add(user, d, "Text", "Test text model B");
    await user.click(within(rows(section(d, "Text"))[1]!).getByRole("button", { name: "Move up" }));
    await add(user, d, "Vision", "Test vision model");
    await save(user, d);
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(writes(calls)).toEqual([
      expect.objectContaining({
        url: "/api/v1/models/profiles",
        method: "POST",
        body: {
          name: "Cheap vision copy",
          duplicatedFrom: sysRef,
          slots: { text: ["test/b", "test/a"], vision: ["test/v"], image: [] },
        },
      }),
    ]);
    expect(await screen.findByText("Profile saved")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("missing older model is kept and sent on save: edit PUTs name and all chains (no duplicatedFrom)", async () => {
    const calls = stubApi({
      get: () => json(200, profile("c1", "Cheap vision", slots([A(), GONE()]))),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await save(user, d);
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(writes(calls)).toEqual([
      expect.objectContaining({
        url: "/api/v1/models/profiles/c1",
        method: "PUT",
        body: {
          name: "Cheap vision",
          slots: { text: ["test/a", "test/gone"], vision: [], image: [] },
        },
      }),
    ]);
    expect(await screen.findByText("Profile saved")).toBeInTheDocument();
  });

  it("Save profile is busy while the request is in flight and the form is read-only", async () => {
    let release: (r: Response) => void = () => undefined;
    stubApi({ put: () => new Promise<Response>((r) => (release = r)) as unknown as Response });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await save(user, d);
    const btn = within(d).getByRole("button", { name: "Save profile" });
    await waitFor(() => expect(btn).toHaveAttribute("aria-busy", "true"));
    expect(within(d).getByLabelText("Name")).toHaveAttribute("readonly");
    release(json(200, profile("c1", "Cheap vision")));
  });
});

describe("SCR-34 client-side checks run first with the same words (AC-214, AC-215)", () => {
  it("AC-214 name only spaces: 'Name the profile', no request", async () => {
    const calls = stubApi({ draft: () => json(200, draft({ slots: slots([A()]) })) });
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "   ");
    await save(user, d);
    expect(await within(d).findByText("Name the profile")).toBeInTheDocument();
    expect(writes(calls)).toHaveLength(0);
    expect(within(d).getByLabelText("Name")).toHaveFocus();
  });

  it("AC-214 name longer than 40 characters after trimming: 'Use up to 40 characters'", async () => {
    const calls = stubApi({ draft: () => json(200, draft({ slots: slots([A()]) })) });
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), `  ${"x".repeat(41)}  `);
    await save(user, d);
    expect(await within(d).findByText("Use up to 40 characters")).toBeInTheDocument();
    expect(writes(calls)).toHaveLength(0);
  });

  it("AC-214 name equal to another of the Owner's profiles ignoring case", async () => {
    const calls = stubApi({ draft: () => json(200, draft({ slots: slots([A()]) })) });
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "night SHIFT");
    await save(user, d);
    expect(await within(d).findByText(/You already have a profile called/)).toBeInTheDocument();
    expect(writes(calls)).toHaveLength(0);
  });

  it("AC-214 name equal to a system profile name", async () => {
    const calls = stubApi({ draft: () => json(200, draft({ slots: slots([A()]) })) });
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "balanced");
    await save(user, d);
    expect(await within(d).findByText(/is a system profile name/)).toBeInTheDocument();
    expect(writes(calls)).toHaveLength(0);
  });

  it("editing keeps its own name without a clash", async () => {
    const calls = stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    await save(user, await dialog("Edit profile"));
    await waitFor(() => expect(writes(calls)).toHaveLength(1));
  });

  it("AC-215 empty text slot: 'The text slot needs at least one model.', no request", async () => {
    const calls = stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "Fresh");
    await save(user, d);
    expect(
      await within(section(d, "Text")).findByText("The text slot needs at least one model."),
    ).toBeInTheDocument();
    expect(writes(calls)).toHaveLength(0);
  });
});

describe("SCR-34 server validation: 400 errors[] mapped under their fields (AC-214, AC-215, AC-221)", () => {
  it("name error goes under Name and the name field is focused (name-taken from a parallel save)", async () => {
    stubApi({
      put: () =>
        problem(400, "validation-failed", [
          { field: "name", code: "name-taken", message: "You already have a profile called Fresh" },
        ]),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await save(user, d);
    expect(await within(d).findByText(/You already have a profile called/)).toBeInTheDocument();
    expect(within(d).getByLabelText("Name")).toHaveFocus();
    expect(within(d).getByLabelText("Name")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("dialog", { name: "Edit profile" })).toBeInTheDocument();
  });

  it("slots.text error goes under the Text section", async () => {
    stubApi({
      put: () =>
        problem(400, "validation-failed", [
          {
            field: "slots.text",
            code: "text-slot-required",
            message: "The text slot needs at least one model.",
          },
        ]),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await save(user, d);
    expect(
      await within(section(d, "Text")).findByText("The text slot needs at least one model."),
    ).toBeInTheDocument();
  });

  it("AC-221 model-left-catalog on slots.text[2] sits under that row; the older missing model stays marked and kept", async () => {
    stubApi({
      get: () => json(200, profile("c1", "Cheap vision", slots([A(), GONE()]))),
      put: () =>
        problem(400, "validation-failed", [
          {
            field: "slots.text[2]",
            code: "model-left-catalog",
            message: "Test text model B is no longer available. Pick another model.",
          },
        ]),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await add(user, d, "Text", "Test text model B");
    await save(user, d);
    const rs = rows(section(d, "Text"));
    expect(
      await within(rs[2]!).findByText(
        "Test text model B is no longer available. Pick another model.",
      ),
    ).toBeInTheDocument();
    expect(within(rs[1]!).getByText("Not in the catalog")).toBeInTheDocument();
    expect(within(rs[1]!).queryByText(/no longer available/)).toBeNull();
    expect(rs).toHaveLength(3);
  });

  it("editing a field after the error clears that field's message", async () => {
    stubApi({
      put: () =>
        problem(400, "validation-failed", [
          { field: "name", code: "name-reserved", message: "Balanced is a system profile name" },
        ]),
    });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await save(user, d);
    await within(d).findByText(/is a system profile name/);
    await user.type(within(d).getByLabelText("Name"), "!");
    expect(within(d).queryByText(/is a system profile name/)).toBeNull();
  });
});

describe("SCR-34 save refused: 409 keeps the editor open with an error toast", () => {
  it("profile-limit-reached", async () => {
    stubApi({ put: () => problem(409, "profile-limit-reached") });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    await save(user, await dialog("Edit profile"));
    const toast = await screen.findByRole("alert");
    expect(toast).toHaveTextContent(
      "You can have up to 20 custom profiles. Delete one to make room.",
    );
    expect(screen.getByRole("dialog", { name: "Edit profile" })).toBeInTheDocument();
    expect(where()).toBe("/settings/models/profiles/c1");
  });

  it("ai-not-configured", async () => {
    stubApi({ put: () => problem(409, "ai-not-configured") });
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    await save(user, await dialog("Edit profile"));
    const toast = await screen.findAllByRole("alert");
    expect(toast.some((t) => /AI models aren't set up/.test(t.textContent ?? ""))).toBe(true);
    expect(screen.getByRole("dialog", { name: "Edit profile" })).toBeInTheDocument();
  });
});

describe("SCR-34 closing", () => {
  it("Cancel closes the modal back to /settings/models and saves nothing", async () => {
    const calls = stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await user.type(within(d).getByLabelText("Name"), " changed");
    await user.click(within(d).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(writes(calls)).toHaveLength(0);
  });

  it("Esc closes it", async () => {
    const calls = stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/c1");
    await dialog("Edit profile");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(where()).toBe("/settings/models"));
    expect(writes(calls)).toHaveLength(0);
  });

  it("the close button closes it", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.click(within(d).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(where()).toBe("/settings/models"));
  });
});

describe("SCR-34 loading, background refetches and result notices (review-2026-10-03 C1, F1, F3)", () => {
  it("C1 loading: the modal shell with a 3-row LoadState shows while the draft is pending", async () => {
    stubApi({ draft: () => new Response(null) });
    let release: (r: Response) => void = () => undefined;
    const pending = new Promise<Response>((res) => (release = res));
    const inner = globalThis.fetch as unknown as (u: string, i?: RequestInit) => Promise<Response>;
    vi.stubGlobal("fetch", (url: string, init?: RequestInit) =>
      url.startsWith("/api/v1/models/profile-draft") ? pending : inner(url, init),
    );
    open("/settings/models/profiles/new");
    const d = await shell();
    const status = within(d).getByRole("status");
    expect(status.querySelectorAll(".placeholder")).toHaveLength(3);
    expect(within(d).queryByLabelText("Name")).toBeNull();
    release(json(200, draft()));
    expect(await screen.findByLabelText("Name")).toBeInTheDocument();
  });

  it("C1 loading: editing shows the same loading state while the profile is pending", async () => {
    stubApi();
    let release: (r: Response) => void = () => undefined;
    const pending = new Promise<Response>((res) => (release = res));
    const inner = globalThis.fetch as unknown as (u: string, i?: RequestInit) => Promise<Response>;
    vi.stubGlobal("fetch", (url: string, init?: RequestInit) =>
      url === "/api/v1/models/profiles/c1" ? pending : inner(url, init),
    );
    open("/settings/models/profiles/c1");
    const d = await shell("Edit profile");
    expect(within(d).getByRole("status").querySelectorAll(".placeholder")).toHaveLength(3);
    release(json(200, profile("c1", "Cheap vision")));
    expect(await screen.findByLabelText("Name")).toHaveValue("Cheap vision");
  });

  it("F1: a failing draft refetch after load keeps the form and the typed name", async () => {
    let draftCalls = 0;
    stubApi({
      draft: () =>
        ++draftCalls === 1 ? json(200, draft()) : problem(409, "profile-limit-reached"),
    });
    const user = userEvent.setup();
    const client = open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "Typed name");
    await client.refetchQueries({ queryKey: ["models", "profile-draft"] });
    expect(draftCalls).toBe(2);
    expect(where()).toBe("/settings/models/profiles/new");
    expect(within(await dialog()).getByLabelText("Name")).toHaveValue("Typed name");
  });

  it("F1: a failing profile refetch after load keeps the form and the typed name", async () => {
    let gets = 0;
    stubApi({
      get: () => (++gets === 1 ? json(200, profile("c1", "Cheap vision")) : json(500, {})),
    });
    const user = userEvent.setup();
    const client = open("/settings/models/profiles/c1");
    const d = await dialog("Edit profile");
    await user.type(within(d).getByLabelText("Name"), " v2");
    await client.refetchQueries({ queryKey: ["models", "profiles", "c1"] });
    expect(gets).toBe(2);
    expect(within(await dialog("Edit profile")).getByLabelText("Name")).toHaveValue(
      "Cheap vision v2",
    );
  });

  it("T25: a cached old draft and an opening refetch that fails with 409 go back to the list with the limit notice", async () => {
    stubApi({ draft: () => problem(409, "profile-limit-reached") });
    open("/settings/models/profiles/new", (client) =>
      client.setQueryData(["models", "profile-draft", null], draft({ name: "Stale name" }), {
        updatedAt: Date.now() - 60_000,
      }),
    );
    expect(
      await screen.findByText("You can have up to 20 custom profiles. Delete one to make room."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).toBeNull();
    expect(where()).toBe("/settings/models");
  });

  it("T25: Create profile from the list makes one draft request, not two", async () => {
    const calls = stubApi();
    const user = userEvent.setup();
    open("/settings/models");
    await user.click(await screen.findByRole("button", { name: "Create profile" }));
    await dialog();
    expect(calls.filter((c) => c.url.startsWith("/api/v1/models/profile-draft"))).toHaveLength(1);
  });

  it("T25: a notice is dropped when the Owner navigates on inside the Models page", async () => {
    stubApi({ draft: () => problem(409, "profile-limit-reached") });
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    await screen.findByText("You can have up to 20 custom profiles. Delete one to make room.");
    await user.click(await screen.findByRole("tab", { name: "Model catalog" }));
    expect(
      screen.queryByText("You can have up to 20 custom profiles. Delete one to make room."),
    ).not.toBeInTheDocument();
  });

  it("F3: the saved notice shows once and is cleared from the history entry", async () => {
    stubApi();
    const user = userEvent.setup();
    open("/settings/models/profiles/new");
    const d = await dialog();
    await user.type(within(d).getByLabelText("Name"), "Fresh");
    await add(user, d, "Text", "Test text model A");
    await save(user, d);
    expect(await screen.findByText("Profile saved")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("null"));
    expect(screen.getByText("Profile saved")).toBeInTheDocument();
    expect(where()).toBe("/settings/models");
  });

  it("F3: a limit notice from a refused opening shows once and is cleared from the history entry", async () => {
    stubApi({ draft: () => problem(409, "profile-limit-reached") });
    open("/settings/models/profiles/new");
    expect(
      await screen.findByText("You can have up to 20 custom profiles. Delete one to make room."),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("null"));
  });
});
