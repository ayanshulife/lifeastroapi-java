// Code generator for the Java SDK.
//
// Reads the endpoint manifest of the Node.js SDK (scripts/endpoints_part1.json +
// endpoints_part2.json, resolved by scripts/manifest.mjs: per-path overrides, the `lang` rules,
// namespace and method names) and writes, under lifeastroapi-java/:
//
//   src/main/java/com/lifeastroapi/params/*Params.java      one parameter class (with builder) per method
//   src/main/java/com/lifeastroapi/resources/*.java          one class per resource group, blocking
//   src/main/java/com/lifeastroapi/resources/*Async.java     the same, returning CompletableFuture
//   src/main/java/com/lifeastroapi/LifeAstroResources.java   the base class of LifeAstro (group accessors)
//   src/main/java/com/lifeastroapi/LifeAstroAsyncResources.java  the same for LifeAstroAsync
//   src/test/java/com/lifeastroapi/Samples.java              a sample call of every method (tests, live sweep)
//   src/test/resources/com/lifeastroapi/manifest.json        the resolved manifest with the Java names
//
// and tool/param-docs.json (parameter docs from the website's examples.json, kept so the generator
// also runs without the website checkout).
//
// Importing manifest.mjs, rather than copying its rules, keeps the Node, Python, Dart, PHP and Java
// SDKs on the same endpoint list, method names and `lang` rules.
//
//   node tool/generate.mjs
//   node tool/generate.mjs --costs-url http://127.0.0.1:18080   # refresh tool/costs.json first
//
// Credit costs in the Javadoc come from tool/costs.json, a snapshot of GET /v1/meta/endpoints.
//
// Environment:
//   LIFEASTRO_NODE_SDK   path to the Node SDK repo (default ../lifeastroapi-node)
//   LIFEASTRO_EXAMPLES   path to the website's examples.json (default
//                        ../LifeAstroAPI/LifeAstroApiWebsite/lib/generated/examples.json)
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const NODE_SDK = resolve(process.env.LIFEASTRO_NODE_SDK ?? join(ROOT, "..", "lifeastroapi-node"));
const EXAMPLES = resolve(
  process.env.LIFEASTRO_EXAMPLES ??
    join(ROOT, "..", "LifeAstroAPI", "LifeAstroApiWebsite", "lib", "generated", "examples.json"),
);
const CORE = join(ROOT, "lifeastroapi-java");
const MAIN = join(CORE, "src", "main", "java", "com", "lifeastroapi");
const TEST = join(CORE, "src", "test", "java", "com", "lifeastroapi");
const TEST_RES = join(CORE, "src", "test", "resources", "com", "lifeastroapi");
const PARAMS_DIR = join(MAIN, "params");
const RES_DIR = join(MAIN, "resources");
const COSTS = join(ROOT, "tool", "costs.json");
const DOCS_FILE = join(ROOT, "tool", "param-docs.json");
const SAMPLES = JSON.parse(readFileSync(join(ROOT, "tool", "samples.json"), "utf8"));

const manifestPath = join(NODE_SDK, "scripts", "manifest.mjs");
if (!existsSync(manifestPath)) {
  console.error(`Node SDK manifest not found at ${manifestPath}. Set LIFEASTRO_NODE_SDK.`);
  process.exit(2);
}
const { entries } = await import(pathToFileURL(manifestPath).href);

// ---------------------------------------------------------------------------
// Credit costs (snapshot of GET /v1/meta/endpoints)
// ---------------------------------------------------------------------------
const argv = process.argv.slice(2);
const costsUrlAt = argv.indexOf("--costs-url");
if (costsUrlAt !== -1) {
  const base = String(argv[costsUrlAt + 1] ?? "").replace(/\/+$/, "");
  if (!base) {
    console.error("--costs-url needs a base URL, e.g. http://127.0.0.1:18080");
    process.exit(2);
  }
  const headers = { Accept: "application/json" };
  if (process.env.LIFEASTRO_API_KEY) headers.Authorization = `Bearer ${process.env.LIFEASTRO_API_KEY}`;
  const res = await fetch(`${base}/v1/meta/endpoints`, { headers });
  if (!res.ok) {
    console.error(`GET ${base}/v1/meta/endpoints answered ${res.status}`);
    process.exit(1);
  }
  const body = await res.json();
  const routes = {};
  for (const r of body.routes ?? []) {
    if ((r.method ?? "GET") === "GET" && String(r.path).startsWith("/v1/")) routes[r.path] = r.cost;
  }
  const sorted = Object.fromEntries(Object.entries(routes).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
  const snapshot = { source: `GET ${base}/v1/meta/endpoints`, fetched: new Date().toISOString().slice(0, 10), routes: sorted };
  writeFileSync(COSTS, JSON.stringify(snapshot, null, 1) + "\n");
  console.log(`Wrote tool/costs.json: ${Object.keys(sorted).length} routes from ${snapshot.source}`);
}
const COST_ROUTES = existsSync(COSTS) ? JSON.parse(readFileSync(COSTS, "utf8")).routes : {};

const norm = (path) => path.replace(/\{[^}]+\}/g, "{}");
const templateRegex = (path) =>
  new RegExp("^" + path.split(/\{[^}]+\}/).map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("[^/]+") + "$");

function costFor(path) {
  if (path in COST_ROUTES) return COST_ROUTES[path];
  const matches = Object.entries(COST_ROUTES)
    .filter(([route]) => norm(route) === norm(path) || templateRegex(route).test(path))
    .map(([route, cost]) => [(route.match(/\{/g) ?? []).length, cost])
    .sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  return matches.length ? matches[0][1] : null;
}

// ---------------------------------------------------------------------------
// Parameter docs (website examples.json, or the committed tool/param-docs.json)
// ---------------------------------------------------------------------------
let paramDocs = {};
if (existsSync(EXAMPLES)) {
  const raw = JSON.parse(readFileSync(EXAMPLES, "utf8"));
  for (const path of Object.keys(raw).sort()) {
    const docs = {};
    for (const d of raw[path].params_doc ?? []) {
      if (d.description) docs[d.name] = String(d.description).replace(/\s+/g, " ").trim();
    }
    paramDocs[path] = docs;
  }
  writeFileSync(DOCS_FILE, JSON.stringify(paramDocs, null, 1) + "\n");
} else if (existsSync(DOCS_FILE)) {
  console.log(`examples.json not found at ${EXAMPLES}; using tool/param-docs.json`);
  paramDocs = JSON.parse(readFileSync(DOCS_FILE, "utf8"));
} else {
  console.log(`examples.json not found at ${EXAMPLES}; parameter docs are left out.`);
}
const docsByTemplate = Object.fromEntries(Object.entries(paramDocs).map(([k, v]) => [norm(k), v]));
function docsFor(path) {
  if (paramDocs[path]) return paramDocs[path];
  if (docsByTemplate[norm(path)]) return docsByTemplate[norm(path)];
  for (const [template, d] of Object.entries(paramDocs)) {
    if (template.includes("{") && templateRegex(template).test(path)) return d;
  }
  return {};
}

// ---------------------------------------------------------------------------
// Java names
// ---------------------------------------------------------------------------
const JAVA_KEYWORDS = new Set(
  ("abstract assert boolean break byte case catch char class const continue default do double else enum " +
    "extends final finally float for goto if implements import instanceof int interface long native new " +
    "package private protected public return short static strictfp super switch synchronized this throw " +
    "throws transient try void volatile while true false null var record yield sealed permits").split(" "),
);
/** Node method names that are Java keywords. */
const JAVA_METHOD = { "dasha.char": "chara" };
/** Names a parameter cannot have: they are methods of every parameter class or its builder. */
const RESERVED_PARAM = new Set(["builder", "build", "toQuery", "toPath", "equals", "hashCode", "toString",
  "getClass", "notify", "notifyAll", "wait", "clone", "finalize",
  // local names of the generated code
  "query", "missing", "other", "o", "b", "s", "sep"]);

function camelOf(s) {
  const words = String(s).split(/[^a-zA-Z0-9]+/).filter(Boolean);
  return words.map((w, i) => (i === 0 ? w[0].toLowerCase() + w.slice(1) : w[0].toUpperCase() + w.slice(1))).join("");
}
const pascal = (s) => {
  const c = camelOf(s);
  return c[0].toUpperCase() + c.slice(1);
};

// ---------------------------------------------------------------------------
// Parameter model. `kind` is scalar, object (a BirthInput sent under a prefix) or path.
// Scalar `type`: Double, Integer, Boolean, String, Lang, Ayanamsa, HouseSystem (the last three
// stored as their wire string).
// ---------------------------------------------------------------------------
const scalar = (name, type, required, wire, more = {}) => ({ name, type, required, kind: "scalar", wires: [wire ?? name], ...more });
const obj = (name, required, prefix, more = {}) => ({ name, type: "BirthInput", required, kind: "object", prefix, place: false, window: false, ...more });
const AYANAMSA = () => scalar("ayanamsa", "Ayanamsa", false);
const HOUSE = () => scalar("houseSystem", "HouseSystem", false, "house_system");
const flatBirth = (prefix = "") => [
  scalar("lat", "Double", true, prefix + "lat"),
  scalar("lon", "Double", true, prefix + "lon"),
  scalar("date", "String", true, prefix + "date"),
  scalar("time", "String", true, prefix + "time"),
  scalar("tz", "String", true, prefix + "tz"),
];

// Base shapes, in the order of the Node SDK's src/params.ts builders (q* functions). `expr` is
// the Java value where it differs from the plain field (the defaults of params.ts).
const SHAPES = {
  moment: () => [
    scalar("lat", "Double", true),
    scalar("lon", "Double", true),
    scalar("tz", "String", true),
    scalar("date", "String", false, "date", { expr: "date != null ? date : Wire.todayIn(tz)", doc: "date_today" }),
    scalar("time", "String", false),
    AYANAMSA(),
  ],
  birth: () => [...flatBirth(), AYANAMSA(), HOUSE()],
  birthPrefixed: () => [...flatBirth("birth."), AYANAMSA()],
  boygirl: () => [obj("boy", true, "boy."), obj("girl", true, "girl."), AYANAMSA()],
  twoPerson: () => [obj("personA", true, "personA."), obj("personB", true, "personB.")],
  rashi: () => [
    scalar("rashi", "String", true, "rashi", { expr: "rashi == null ? null : rashi.trim().toLowerCase(Locale.ROOT)" }),
    scalar("date", "String", false),
    scalar("lang", "Lang", false),
  ],
  numerology: () => [scalar("name", "String", false), scalar("dob", "String", false), scalar("lang", "Lang", false)],
  dateRange: () => [scalar("startDate", "String", true, "start_date"), scalar("endDate", "String", true, "end_date")],
  westernNatal: () => [...flatBirth(), HOUSE()],
  westernMoment: () => [scalar("date", "String", true), scalar("time", "String", true), scalar("tz", "String", true)],
  muhurta: () => [
    scalar("lat", "Double", true),
    scalar("lon", "Double", true),
    scalar("tz", "String", true),
    scalar("startDate", "String", true, "start_date"),
    scalar("endDate", "String", true, "end_date"),
  ],
  natalTransit: () => [
    obj("birth", true, "birth."),
    scalar("date", "String", true),
    scalar("time", "String", true),
    scalar("tz", "String", true),
    AYANAMSA(),
  ],
  // Also sends the birth place unprefixed: the handler casts the natal lagna from it.
  natalTransitPlace: () => [
    obj("birth", true, "birth.", { place: true }),
    scalar("date", "String", true),
    scalar("time", "String", true),
    scalar("tz", "String", true),
    AYANAMSA(),
  ],
  natalWindow: () => [obj("birth", true, "birth.", { window: true }), scalar("from", "String", false), scalar("tz", "String", false), AYANAMSA()],
  natalWindowOptional: () => [obj("birth", false, "birth.", { window: true }), scalar("from", "String", false), scalar("tz", "String", false), AYANAMSA()],
  instant: () => [
    scalar("tz", "String", false, "tz", { expr: 'tz != null ? tz : "UTC"', doc: "tz_utc" }),
    scalar("date", "String", false, "date", { expr: 'date != null ? date : Wire.todayIn(tz != null ? tz : "UTC")', doc: "date_today" }),
    scalar("time", "String", false),
    AYANAMSA(),
  ],
  none: () => [],
  custom: () => [],
};

function extraType(x, name) {
  if (name === "lang" || name === "locale" || x.type === "locale") return "Lang";
  if (name === "ayanamsa") return "Ayanamsa";
  if (name === "houseSystem") return "HouseSystem";
  return { number: "Double", integer: "Integer", boolean: "Boolean" }[x.type] ?? "String";
}

function resolveEntry(e) {
  const { ns } = e._resolved;
  const nodeMethod = e._resolved.method;
  const method = JAVA_METHOD[`${ns}.${nodeMethod}`] ?? nodeMethod;
  if (JAVA_KEYWORDS.has(method)) throw new Error(`${ns}.${method} is a Java keyword; add it to JAVA_METHOD`);
  const shapeFn = SHAPES[e.shape];
  if (!shapeFn) throw new Error(`Unknown shape "${e.shape}" for ${e.path}`);
  const params = [];
  const byName = new Map();
  const ops = []; // [paramName, wire, expr]

  const add = (p) => {
    if (!/^[a-z][A-Za-z0-9]*$/.test(p.name) || JAVA_KEYWORDS.has(p.name) || RESERVED_PARAM.has(p.name)) {
      throw new Error(`${e.path}: parameter name "${p.name}" cannot be used in Java here`);
    }
    const existing = byName.get(p.name);
    if (!existing) {
      params.push(p);
      byName.set(p.name, p);
      return p;
    }
    if (existing.kind !== "scalar" || p.kind !== "scalar") throw new Error(`${e.path}: "${p.name}" is declared twice`);
    existing.required = existing.required || p.required;
    return existing;
  };

  for (const n of e.pathParams ?? []) {
    add({ name: n, type: "String", required: true, kind: "path", wires: [], numeric: n === "n" || n === "number" });
  }
  for (const p of shapeFn()) {
    const target = add(p);
    if (p.kind === "object") continue;
    for (const w of p.wires) ops.push([target.name, w, p.expr ?? target.name]);
  }
  for (const x of e.extras ?? []) {
    const name = x.name ?? camelOf(x.wire);
    const target = add(scalar(name, extraType(x, name), !!x.required, x.wire));
    // `lang` is sent as both `lang` and `locale` (the API reads either; some older routes read only
    // one), exactly as the Node SDK does.
    const wires = x.wire === "lang" ? ["lang", "locale"] : [x.wire];
    for (const w of wires) {
      ops.push([target.name, w, target.name]);
      if (!target.wires.includes(w)) target.wires.push(w);
    }
  }
  const className = pascal(ns.replace(/\./g, "-")) + pascal(method) + "Params";
  return { entry: e, ns, method, nodeMethod, params, ops, cost: costFor(e.path), className, conv: conveniences(e, params) };
}

/**
 * Convenience setters that fill several fields at once: `birth(BirthInput)` (or `boy` / `girl`)
 * for flat birth fields, `place(Place)` for lat / lon / tz.
 */
function conveniences(e, params) {
  const has = (n) => params.some((p) => p.name === n && p.kind === "scalar");
  const wire = (n) => params.find((p) => p.name === n)?.wires?.[0];
  const out = [];
  const birthShapes = ["birth", "birthPrefixed", "westernNatal"];
  if (["date", "time", "tz", "lat", "lon"].every(has) && (birthShapes.includes(e.shape) || (e.shape === "custom" && wire("date") === "birth.date"))) {
    out.push({ setter: "birth", map: { date: "date", time: "time", tz: "tz", lat: "lat", lon: "lon" } });
  }
  if (["birthDate", "birthTime", "birthTz"].every(has)) {
    const map = { date: "birthDate", time: "birthTime", tz: "birthTz" };
    if (has("lat") && has("lon")) Object.assign(map, { lat: "lat", lon: "lon" });
    out.push({ setter: "birth", map });
  }
  for (const who of ["boy", "girl"]) {
    if ([`${who}Date`, `${who}Time`, `${who}Tz`].every(has) && !params.some((p) => p.name === who)) {
      out.push({ setter: who, map: { date: `${who}Date`, time: `${who}Time`, tz: `${who}Tz` } });
    }
  }
  const setters = new Set(out.map((c) => c.setter));
  if (setters.size !== out.length) throw new Error(`${e.path}: two conveniences with one name`);
  for (const s of setters) if (params.some((p) => p.name === s)) throw new Error(`${e.path}: convenience ${s} clashes with a parameter`);
  if (!out.length && ["lat", "lon", "tz"].every(has) && !params.some((p) => p.name === "place")) {
    out.push({ setter: "place", map: { lat: "lat", lon: "lon", tz: "tz" } });
  }
  return out;
}

// ---------------------------------------------------------------------------
// Javadoc helpers
// ---------------------------------------------------------------------------
const escapeHtml = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
/** Plain text (with `code` spans) to Javadoc HTML. */
function html(text) {
  const parts = String(text).replace(/\s+/g, " ").trim().split("`");
  return parts
    .map((p, i) => (i % 2 === 1 ? `<code>${escapeHtml(p)}</code>` : escapeHtml(p)))
    .join("")
    .replace(/@/g, "&#64;")
    .replace(/\*\//g, "*&#47;")
    .replace(/\{/g, "&#123;")
    .replace(/\}/g, "&#125;");
}
function wrap(text, width) {
  const words = text.split(" ").filter(Boolean);
  const lines = [];
  let line = "";
  for (const w of words) {
    if (line && line.length + 1 + w.length > width) {
      lines.push(line);
      line = w;
    } else line = line ? `${line} ${w}` : w;
  }
  if (line) lines.push(line);
  return lines;
}
const WIDTH = 110;
function docBlock(indent, paragraphs, tags = []) {
  const pre = `${indent} * `;
  const out = [`${indent}/**`];
  paragraphs.forEach((para, i) => {
    if (i > 0) out.push(`${indent} *`);
    if (Array.isArray(para)) {
      for (const l of para) out.push(l === "" ? `${indent} *` : pre + l);
    } else {
      const lines = wrap(para, WIDTH - pre.length);
      if (i > 0 && lines.length && !para.startsWith("<pre>")) lines[0] = "<p>" + lines[0];
      for (const l of lines) out.push(pre + l);
    }
  });
  if (tags.length) {
    out.push(`${indent} *`);
    for (const t of tags) {
      const lines = wrap(t, WIDTH - pre.length);
      out.push(pre + lines[0]);
      for (const l of lines.slice(1)) out.push(`${pre}    ${l}`);
    }
  }
  out.push(`${indent} */`);
  return out;
}

const PARAM_DOC = {
  lat: "Latitude in decimal degrees, north positive.",
  lon: "Longitude in decimal degrees, east positive.",
  tz: "Time zone: IANA name (`Asia/Kolkata`) or UTC offset (`+05:30`).",
  tz_utc: "Time zone: IANA name (`Asia/Kolkata`) or UTC offset (`+05:30`). Defaults to `UTC`.",
  date: "Date, `YYYY-MM-DD`.",
  date_today: "Date, `YYYY-MM-DD`. Defaults to today in `tz`.",
  time: "Local time, 24-hour `HH:MM`.",
  lang: "Response language. Sent as `lang` and `locale`.",
  lang_shape: "Response language.",
  ayanamsa: "Sidereal ayanamsa; the API uses Lahiri when left out.",
  houseSystem: "House system, sent as `house_system`.",
  startDate: "First day of the window, `YYYY-MM-DD`.",
  endDate: "Last day of the window, `YYYY-MM-DD`.",
  rashi: "Moon sign, English or Hindi name in any case (`aries`, `Mesh`); sent lowercased.",
  name: "Full name as written on official documents.",
  dob: "Date of birth, `YYYY-MM-DD`.",
  from: "Start of the range, `YYYY-MM-DD` (local midnight in `tz`); defaults to now.",
  boy: "Groom's birth details, sent as `boy.date`, `boy.time`, `boy.tz`, `boy.lat`, `boy.lon`.",
  girl: "Bride's birth details, sent as `girl.date`, `girl.time`, `girl.tz`, `girl.lat`, `girl.lon`.",
  personA: "First person's birth details, sent as `personA.date`, `personA.time`, `personA.tz`, `personA.lat`, `personA.lon`.",
  personB: "Second person's birth details, sent as `personB.date`, `personB.time`, `personB.tz`, `personB.lat`, `personB.lon`.",
  birth: "Natal chart, sent as `birth.date`, `birth.time`, `birth.tz`, `birth.lat`, `birth.lon`.",
  birth_place: "Natal chart, sent as `birth.date`, `birth.time`, `birth.tz`, `birth.lat`, `birth.lon`; `lat` and `lon` are also sent unprefixed.",
  birth_window: "Natal chart, sent as `birth.*`: `date`, `time`, `tz`; `lat` and `lon` (optional) add the lagna-based fields.",
};

function paramDoc(m, p) {
  if (p.kind === "object") {
    if (p.place) return PARAM_DOC.birth_place;
    if (p.window) return PARAM_DOC.birth_window;
    return PARAM_DOC[p.name] ?? "";
  }
  if (p.name === "lang") return p.wires.includes("locale") ? PARAM_DOC.lang : PARAM_DOC.lang_shape;
  if (p.doc) return PARAM_DOC[p.doc];
  const docs = docsFor(m.entry.path);
  const fromExample = docs[p.wires[0]] ?? (p.kind === "path" ? docs[p.name] : undefined);
  if (fromExample) return fromExample;
  return PARAM_DOC[p.name] ?? "";
}

function wireNote(p) {
  if (p.kind === "path") return "Path parameter.";
  if (p.name === "lang" && p.wires.includes("locale")) return "";
  if (p.kind === "object") return "";
  const w = p.wires.filter((x) => x !== p.name);
  if (!w.length) return "";
  return `Wire name ${w.map((x) => "`" + x + "`").join(", ")}.`;
}

// ---------------------------------------------------------------------------
// Java literals for samples and examples
// ---------------------------------------------------------------------------
function javaString(s) {
  let out = '"';
  for (const ch of String(s)) {
    const c = ch.codePointAt(0);
    if (ch === '"') out += '\\"';
    else if (ch === "\\") out += "\\\\";
    else if (c < 0x20 || c > 0x7e) {
      for (const unit of ch.split("")) out += "\\u" + unit.charCodeAt(0).toString(16).padStart(4, "0");
    } else out += ch;
  }
  return out + '"';
}
function javaNumber(v, type, where) {
  const n = typeof v === "number" ? v : Number(v);
  if (!Number.isFinite(n)) throw new Error(`${where}: ${v} is not a number`);
  if (type === "Integer") {
    if (!Number.isInteger(n)) throw new Error(`${where}: ${v} is not an integer`);
    return String(n);
  }
  const s = String(n);
  return /[.e]/.test(s) ? s : s + ".0";
}
function birthLiteral(b) {
  if (b.lat === undefined) return `BirthInput.withoutPlace(${javaString(b.date)}, ${javaString(b.time)}, ${javaString(b.tz)})`;
  return `BirthInput.of(${javaString(b.date)}, ${javaString(b.time)}, ${javaString(b.tz)}, ${javaNumber(b.lat, "Double")}, ${javaNumber(b.lon, "Double")})`;
}
function literal(p, v, where) {
  if (p.kind === "object") return birthLiteral(v);
  if (p.kind === "path") {
    if (p.numeric && typeof v === "number") return String(v);
    return javaString(String(v));
  }
  switch (p.type) {
    case "Double":
    case "Integer":
      return javaNumber(v, p.type, where);
    case "Boolean":
      if (typeof v !== "boolean") throw new Error(`${where}: ${v} is not a boolean`);
      return String(v);
    case "Lang":
      return `Lang.fromCode(${javaString(v)})`;
    default:
      return javaString(String(v));
  }
}

/** The sample input of a method, keyed by parameter name: the same rule as tool/node-goldens.mjs. */
function sampleInput(m) {
  const input = structuredClone(SAMPLES.shapes[m.entry.shape] ?? {});
  for (const p of m.params) {
    if (p.kind === "path" && p.name in SAMPLES.path) input[p.name] = SAMPLES.path[p.name];
  }
  for (const x of m.entry.extras ?? []) {
    if (x.required && x.wire in SAMPLES.wire) input[x.name ?? camelOf(x.wire)] = SAMPLES.wire[x.wire];
  }
  Object.assign(input, SAMPLES.perPath[m.entry.path] ?? {});
  const names = new Set(m.params.map((p) => p.name));
  return Object.fromEntries(Object.entries(input).filter(([k]) => names.has(k)));
}

/** Builder calls for an input, using a convenience setter when it covers its fields. */
function builderCalls(m, input) {
  const calls = [];
  const used = new Set();
  for (const c of m.conv) {
    const fields = Object.entries(c.map);
    if (!fields.every(([, f]) => input[f] !== undefined)) continue;
    const v = Object.fromEntries(fields.map(([k, f]) => [k, input[f]]));
    if (c.setter === "place") {
      calls.push(`.place(Place.of(${javaNumber(v.lat, "Double")}, ${javaNumber(v.lon, "Double")}, ${javaString(v.tz)}))`);
    } else {
      calls.push(`.${c.setter}(${birthLiteral(v)})`);
    }
    for (const [, f] of fields) used.add(f);
  }
  for (const p of m.params) {
    if (used.has(p.name) || input[p.name] === undefined) continue;
    calls.push(`.${p.name}(${literal(p, input[p.name], `${m.entry.path} ${p.name}`)})`);
  }
  return calls;
}

// ---------------------------------------------------------------------------
// Parameter class source
// ---------------------------------------------------------------------------
const DATE_NAMES = new Set(["date", "startDate", "endDate", "dob", "birthDate", "onDate", "boyDate", "girlDate", "weekStart", "refDate"]);
const TIME_NAMES = new Set(["time", "birthTime", "boyTime", "girlTime"]);
const ZONE_NAMES = new Set(["tz", "birthTz", "boyTz", "girlTz"]);
const ENUM_TYPES = { Lang: "code", Ayanamsa: "value", HouseSystem: "value" };
const storage = (p) => (p.kind === "object" ? "BirthInput" : ENUM_TYPES[p.type] ? "String" : p.type);
const ordered = (params) => [...params.filter((p) => p.required), ...params.filter((p) => !p.required)];
const resourceClass = (ns) => (ns.startsWith("western.") ? "Western" + pascal(ns.split(".")[1]) : pascal(ns));
const accessorChain = (ns) => ns.split(".").map((s) => `${s}()`).join(".");

function setterSrc(m, p, I) {
  const out = [];
  const text = paramDoc(m, p);
  const note = wireNote(p);
  const docText = [text, note].filter(Boolean).join(" ");
  const doc = (paramType, argDoc, extra = "") =>
    docBlock(I, [html([docText, extra].filter(Boolean).join(" ")) || html(`Sets \`${p.name}\`.`)], [
      `@param ${p.name} ${argDoc}`,
      "@return this builder",
    ]);
  const body = (expr) => [`${I}    this.${p.name} = ${expr};`, `${I}    return this;`, `${I}}`];
  const st = storage(p);
  const req = p.required ? "required" : "or null to leave it out";
  if (p.kind === "object") {
    out.push(...doc("BirthInput", `the birth details, ${req}`), `${I}public Builder ${p.name}(BirthInput ${p.name}) {`, ...body(p.name));
    return out;
  }
  if (ENUM_TYPES[p.type]) {
    out.push(...doc(p.type, `the ${p.type === "Lang" ? "language" : "value"}, or null to leave it out`),
      `${I}public Builder ${p.name}(${p.type} ${p.name}) {`, ...body(`${p.name} == null ? null : ${p.name}.${ENUM_TYPES[p.type]}()`));
    out.push("");
    out.push(...doc("String", `its wire value, e.g. <code>"${{ Lang: "gu", Ayanamsa: "krishnamurti", HouseSystem: "placidus" }[p.type]}"</code>, or null`),
      `${I}public Builder ${p.name}(String ${p.name}) {`, ...body(p.name));
    return out;
  }
  const prim = { Double: "double", Integer: "int", Boolean: "boolean", String: "String" }[st];
  out.push(...doc(prim, p.kind === "path" ? "the value (required)" : st === "String" ? `the value, ${req}` : "the value"),
    `${I}public Builder ${p.name}(${prim} ${p.name}) {`, ...body(p.name));
  if (p.kind === "path" && p.numeric) {
    out.push("", ...doc("int", "the value"), `${I}public Builder ${p.name}(int ${p.name}) {`, ...body(`Integer.toString(${p.name})`));
  }
  if (st === "String" && p.kind === "scalar") {
    if (DATE_NAMES.has(p.name)) {
      out.push("", ...doc("LocalDate", "the date, sent as <code>YYYY-MM-DD</code>, or null"),
        `${I}public Builder ${p.name}(LocalDate ${p.name}) {`, ...body(`Wire.date(${p.name})`));
    }
    if (TIME_NAMES.has(p.name)) {
      out.push("", ...doc("LocalTime", "the time, sent as <code>HH:MM</code> (<code>HH:MM:SS</code> with seconds), or null"),
        `${I}public Builder ${p.name}(LocalTime ${p.name}) {`, ...body(`Wire.time(${p.name})`));
    }
    if (ZONE_NAMES.has(p.name)) {
      out.push("", ...doc("ZoneId", "the zone; its id is sent, or null"),
        `${I}public Builder ${p.name}(ZoneId ${p.name}) {`, ...body(`Wire.zone(${p.name})`));
    }
  }
  return out;
}

function convenienceSrc(m, c, I) {
  const fields = Object.entries(c.map);
  if (c.setter === "place") {
    return [
      ...docBlock(I, ["Sets <code>lat</code>, <code>lon</code> and <code>tz</code> from a place."], ["@param place the place", "@return this builder"]),
      `${I}public Builder place(Place place) {`,
      `${I}    Objects.requireNonNull(place, "place");`,
      `${I}    this.lat = place.lat();`,
      `${I}    this.lon = place.lon();`,
      `${I}    this.tz = place.tz();`,
      `${I}    return this;`,
      `${I}}`,
    ];
  }
  const list = fields.map(([, f]) => `<code>${f}</code>`);
  const listText = list.length > 1 ? list.slice(0, -1).join(", ") + " and " + list[list.length - 1] : list[0];
  const who = c.setter === "birth" ? "the birth details" : c.setter === "boy" ? "the groom's birth details" : "the bride's birth details";
  const lines = [
    ...docBlock(I, [`Sets ${listText} from ${who}.`], [`@param ${c.setter} ${who}`, "@return this builder"]),
    `${I}public Builder ${c.setter}(BirthInput ${c.setter}) {`,
    `${I}    Objects.requireNonNull(${c.setter}, "${c.setter}");`,
  ];
  for (const [k, f] of fields) lines.push(`${I}    this.${f} = ${c.setter}.${k}();`);
  lines.push(`${I}    return this;`, `${I}}`);
  return lines;
}

function paramsClassSrc(m) {
  const cls = m.className;
  const P = ordered(m.params);
  const I = "    ";
  const imports = new Set(["java.util.Map", "java.util.Objects", "java.util.TreeMap"]);
  const req = P.filter((p) => p.required);
  if (req.length) {
    imports.add("java.util.ArrayList");
    imports.add("java.util.List");
  }
  for (const p of P) {
    if (p.kind === "object") imports.add("com.lifeastroapi.BirthInput");
    if (ENUM_TYPES[p.type]) imports.add(`com.lifeastroapi.${p.type}`);
    if (storage(p) === "String" && p.kind === "scalar") {
      if (DATE_NAMES.has(p.name)) imports.add("java.time.LocalDate");
      if (TIME_NAMES.has(p.name)) imports.add("java.time.LocalTime");
      if (ZONE_NAMES.has(p.name)) imports.add("java.time.ZoneId");
    }
  }
  for (const c of m.conv) imports.add(c.setter === "place" ? "com.lifeastroapi.Place" : "com.lifeastroapi.BirthInput");
  if (m.ops.some(([, , expr]) => expr.includes("Locale.ROOT"))) imports.add("java.util.Locale");

  const res = resourceClass(m.ns);
  const desc = m.entry.desc || m.entry.path;
  const example = builderCalls(m, sampleInput(m));
  const exampleLines = [`${cls} params = ${cls}.builder()`, ...example.map((c) => `    ${c}`), "    .build();"];
  const javaCall = `client.${accessorChain(m.ns)}.${m.method}(params)`;
  const out = [];
  out.push(HEADER, "package com.lifeastroapi.params;", "");
  const sortedImports = [...imports].sort((a, b) => {
    const ga = a.startsWith("java.") ? 1 : 0;
    const gb = b.startsWith("java.") ? 1 : 0;
    return ga - gb || (a < b ? -1 : a > b ? 1 : 0);
  });
  let lastGroup = null;
  for (const i of sortedImports) {
    const g = i.startsWith("java.") ? 1 : 0;
    if (lastGroup !== null && g !== lastGroup) out.push("");
    out.push(`import ${i};`);
    lastGroup = g;
  }
  out.push("");
  out.push(
    ...docBlock("", [
      `Parameters of <code>GET ${escapeHtml(m.entry.path)}</code>, for {@link com.lifeastroapi.resources.${res}#${m.method}(${cls}) ${javaCall.replace("(params)", "()")}}.`,
      html(desc),
      ["<pre>{@code", ...exampleLines, `JsonNode result = ${javaCall};`, "}</pre>"],
      req.length
        ? `Required: ${req.map((p) => `<code>${p.name}</code>`).join(", ")}; {@link Builder#build()} throws {@link IllegalStateException} when one is missing. Immutable, with value equality, so it can be a cache key.`
        : "Every value is optional. Immutable, with value equality, so it can be a cache key.",
    ]),
  );
  out.push(`public final class ${cls} {`);
  for (const p of P) out.push(`${I}private final ${storage(p)} ${p.name};`);
  if (P.length) out.push("");
  out.push(`${I}private ${cls}(Builder b) {`);
  for (const p of P) out.push(`${I}${I}this.${p.name} = b.${p.name};`);
  out.push(`${I}}`, "");
  out.push(...docBlock(I, ["A new builder."], ["@return the builder"]));
  out.push(`${I}public static Builder builder() {`, `${I}${I}return new Builder();`, `${I}}`);
  for (const p of P) {
    out.push("");
    const t = paramDoc(m, p);
    const what = ENUM_TYPES[p.type] ? "the wire value" : "the value";
    out.push(...docBlock(I, [html(t || `The \`${p.name}\` value.`)], [`@return ${what}, or null when not set`]));
    out.push(`${I}public ${storage(p)} ${p.name}() {`, `${I}${I}return ${p.name};`, `${I}}`);
  }
  // toPath
  out.push("");
  const pathParams = P.filter((p) => p.kind === "path");
  out.push(...docBlock(I, ["The request path, with the path parameters filled in and percent-encoded."], ["@return the path"]));
  out.push(`${I}public String toPath() {`);
  if (pathParams.length) {
    const args = pathParams.map((p) => `"${p.name}", ${p.name}`).join(", ");
    out.push(`${I}${I}return Wire.path(${javaString(m.entry.path)}, ${args});`);
  } else {
    out.push(`${I}${I}return ${javaString(m.entry.path)};`);
  }
  out.push(`${I}}`, "");
  // toQuery
  out.push(
    ...docBlock(I, [
      "The query parameters this sends, by wire name, sorted. Unset values are left out" +
        (m.ops.some(([, , e]) => e.includes("todayIn")) ? "; <code>date</code> defaults to today." : "."),
    ], ["@return a new mutable map"]),
  );
  out.push(`${I}public Map<String, Object> toQuery() {`);
  out.push(`${I}${I}Map<String, Object> query = new TreeMap<>();`);
  for (const p of m.params.filter((x) => x.kind === "object")) {
    out.push(`${I}${I}Wire.putBirth(query, ${javaString(p.prefix)}, ${p.name});`);
    if (p.place) out.push(`${I}${I}Wire.putPlace(query, ${p.name});`);
  }
  for (const [, wire, expr] of m.ops) out.push(`${I}${I}Wire.put(query, ${javaString(wire)}, ${expr});`);
  out.push(`${I}${I}return query;`, `${I}}`, "");
  // equals / hashCode / toString
  out.push(`${I}@Override`, `${I}public boolean equals(Object o) {`);
  if (!P.length) {
    out.push(`${I}${I}return o instanceof ${cls};`);
  } else {
    out.push(`${I}${I}if (this == o) {`, `${I}${I}${I}return true;`, `${I}${I}}`);
    out.push(`${I}${I}if (!(o instanceof ${cls} other)) {`, `${I}${I}${I}return false;`, `${I}${I}}`);
    const cmp = P.map((p) => `Objects.equals(${p.name}, other.${p.name})`);
    out.push(`${I}${I}return ${cmp.join(`\n${I}${I}${I}${I}&& `)};`);
  }
  out.push(`${I}}`, "");
  out.push(`${I}@Override`, `${I}public int hashCode() {`);
  out.push(P.length ? `${I}${I}return Objects.hash(${P.map((p) => p.name).join(", ")});` : `${I}${I}return ${cls}.class.hashCode();`);
  out.push(`${I}}`, "");
  out.push(`${I}@Override`, `${I}public String toString() {`);
  out.push(`${I}${I}StringBuilder s = new StringBuilder("${cls}{");`);
  if (P.length) {
    out.push(`${I}${I}String sep = "";`);
    for (const p of P) {
      out.push(`${I}${I}if (${p.name} != null) {`);
      out.push(`${I}${I}${I}s.append(sep).append("${p.name}=").append(${p.name});`);
      out.push(`${I}${I}${I}sep = ", ";`);
      out.push(`${I}${I}}`);
    }
  }
  out.push(`${I}${I}return s.append('}').toString();`, `${I}}`, "");
  // Builder
  out.push(...docBlock(I, [`Builder for {@link ${cls}}. Not thread-safe; build one instance per request.`]));
  out.push(`${I}public static final class Builder {`);
  const J = I + I;
  for (const p of P) out.push(`${J}private ${storage(p)} ${p.name};`);
  if (P.length) out.push("");
  out.push(`${J}private Builder() {`, `${J}}`);
  for (const c of m.conv) {
    out.push("");
    out.push(...convenienceSrc(m, c, J));
  }
  for (const p of P) {
    out.push("");
    out.push(...setterSrc(m, p, J));
  }
  out.push("");
  out.push(...docBlock(J, [`The parameters.`], [
    `@return the immutable {@link ${cls}}`,
    ...(req.length ? ["@throws IllegalStateException when a required value is missing"] : []),
  ]));
  out.push(`${J}public ${cls} build() {`);
  if (req.length) {
    out.push(`${J}${I}List<String> missing = new ArrayList<>();`);
    for (const p of req) {
      if (p.kind === "object" && !p.window) out.push(`${J}${I}Wire.requirePlace(missing, "${p.name}", ${p.name});`);
      else if (p.kind === "path") out.push(`${J}${I}Wire.require(missing, "${p.name}", ${p.name} == null || ${p.name}.isEmpty() ? null : ${p.name});`);
      else out.push(`${J}${I}Wire.require(missing, "${p.name}", ${p.name});`);
    }
    out.push(`${J}${I}if (!missing.isEmpty()) {`, `${J}${I}${I}throw Wire.missing("${cls}", missing);`, `${J}${I}}`);
  }
  // A full birth object that is optional still needs its coordinates when given.
  for (const p of P.filter((x) => x.kind === "object" && !x.required && !x.window)) {
    out.push(`${J}${I}if (${p.name} != null && ${p.name}.lat() == null) {`);
    out.push(`${J}${I}${I}throw new IllegalStateException("${cls}: ${p.name} needs lat and lon");`, `${J}${I}}`);
  }
  out.push(`${J}${I}return new ${cls}(this);`, `${J}}`);
  out.push(`${I}}`, "}", "");
  return out.join("\n");
}

const HEADER = `// GENERATED by tool/generate.mjs from the endpoint manifest of the Node.js SDK.
// Do not edit by hand: run \`node tool/generate.mjs\`.`;

// ---------------------------------------------------------------------------
// Resource class source
// ---------------------------------------------------------------------------
const NS_DOC = {
  ashtakavarga: "Ashtakavarga: sarva, bhinna (per planet), kaksha and transit score.",
  calendar: "Hindu calendar: month, ritu, samvatsara, adhik maas, solstices.",
  chart: "Birth chart (kundli): planets, ascendant, houses, divisional charts, dignity, aspects, shadbala, KP, and the data bundles `kundliFacts`, `yogas`, `doshas`, `sensitivity`.",
  dasha: "Vimshottari (5 levels), Yogini and Chara dashas.",
  eclipses: "Solar and lunar eclipses in a date range, with local contact times and sutak.",
  festivals: "Hindu festivals and vrat dates by month, on a date, or by vrat family for a year.",
  geo: "Place search, reverse geocoding, time zone lookup. Answers in English.",
  horoscope: "Daily, weekly and monthly horoscope by rashi.",
  lalkitab: "Lal Kitab chart and dasha.",
  meta: "Free catalogs: endpoints with credit costs, ayanamsas, yogas, doshas, locales, version. Answers in English.",
  milan: "Kundli matching: Ashtakoota, doshas, navamsa and dasha compatibility, overlay, summary.",
  muhurta: "Muhurta: vivah, naamkaran, griha pravesh, vyapar, yatra, best time, chandra/tara bala.",
  narrative: "Authored Vedic readings: lagna, planets, yogas, doshas, dasha phal, transit phal, prashna, horoscopes and more.",
  numerology: "Numerology: driver, conductor, soul, personality, destiny, full, advanced, challenges, personal periods.",
  panchang: "Panchang: tithi, nakshatra, yoga, karana, vara, sunrise/sunset, muhurta windows, choghadiya, hora, monthly calendar.",
  planetMoments: "Planet moments: retrograde windows, sign ingress, combustion window, speed.",
  prashna: "Prashna (horary): answer, chart, arudha, significators.",
  remedies: "Remedies: by rule id, by graha (`forGraha`), mantra, pooja, vrat, daan, rudraksha, yantra, Lal Kitab.",
  reports: "Composed JSON reports: kundli (lite, detailed, brihad), match making, mangal dosha, sade sati, varshaphal, numerology, horoscopes.",
  tarot: "Tarot: cards, a single card, daily card, spreads, yes/no. Answers in English.",
  transit: "Transits: positions, sade sati, small panoti, tarabala, vedha, double transit, dated windows.",
  varshaphal: "Varshaphal (annual chart): chart, lord, muntha, mudda dasha, harsha bala, tajika yogas.",
  western: "Western (tropical) astrology. Sub-groups: `natal`, `synastry`, `transits`, `returns` and more. Answers in English (`narrative` also in Hindi and Marathi).",
};

function methodSrc(m, I, async) {
  const out = [];
  const cls = m.className;
  const noParams = m.params.length === 0;
  const optional = !noParams && !m.params.some((p) => p.required);
  const R = async ? "CompletableFuture<JsonNode>" : "JsonNode";
  const RT = async ? "CompletableFuture<T>" : "T";
  const target = async ? "client" : "client";
  const costLine = m.cost !== null && m.cost !== undefined ? `Cost: ${m.cost} credit${m.cost === 1 ? "" : "s"} per call. ` : "";
  const getLine = `${costLine}<code>GET ${escapeHtml(m.entry.path)}</code>`;
  const nodeNote = m.method !== m.nodeMethod ? ` (<code>${m.nodeMethod}</code> in the Node.js SDK, a Java keyword)` : "";
  const throwsTag = "@throws LifeAstroException on an API error or a network failure";
  const ret = async ? "@return the response JSON, when it arrives" : "@return the response JSON";
  const retT = async ? "@return the response mapped to <code>type</code>, when it arrives" : "@return the response mapped to <code>type</code>";
  const sig = noParams ? "" : `${cls} params`;
  const sigComma = noParams ? "" : `${cls} params, `;
  const ref = noParams ? `#${m.method}()` : `#${m.method}(${cls})`;
  const call = noParams ? `"${m.entry.path}", null` : "params.toPath(), params.toQuery()";
  const pTag = noParams ? [] : [`@param params the parameters, from {@link ${cls}#builder()}`];
  const asyncNote = async ? " Completes exceptionally with a {@link LifeAstroException} on an API error or a network failure." : "";

  out.push(...docBlock(I, [html(m.entry.desc || m.entry.path) + nodeNote, getLine + asyncNote], [...pTag, ret, ...(async ? [] : [throwsTag])]));
  const argComma = noParams ? "" : "params, ";
  out.push(`${I}public ${R} ${m.method}(${sig}) {`, `${I}    return ${m.method}(${argComma}JsonNode.class, null);`, `${I}}`, "");

  out.push(...docBlock(I, [`Like {@link ${ref}}, with per-call options.`], [...pTag, "@param options per-call timeout, raw body or <code>onResponse</code> hook, or null", ret, ...(async ? [] : [throwsTag])]));
  out.push(`${I}public ${R} ${m.method}(${sigComma}RequestOptions options) {`, `${I}    return ${m.method}(${argComma}JsonNode.class, options);`, `${I}}`, "");

  out.push(...docBlock(I, [`Like {@link ${ref}}, mapped to your own class (a DTO, a <code>Map</code>, ...).`], ["@param <T> the result type", ...pTag, "@param type the class to map the response to", retT, ...(async ? [] : ["@throws LifeAstroException on an API error, a network failure, or when the response does not map to <code>type</code>"])]));
  out.push(`${I}public <T> ${RT} ${m.method}(${sigComma}Class<T> type) {`, `${I}    return ${m.method}(${argComma}type, null);`, `${I}}`, "");

  out.push(...docBlock(I, [`Like {@link ${ref}}, mapped to your own class, with per-call options.`], ["@param <T> the result type", ...pTag, "@param type the class to map the response to", "@param options per-call timeout, raw body or <code>onResponse</code> hook, or null", retT, ...(async ? [] : ["@throws LifeAstroException on an API error, a network failure, or when the response does not map to <code>type</code>"])]));
  out.push(`${I}public <T> ${RT} ${m.method}(${sigComma}Class<T> type, RequestOptions options) {`);
  if (!noParams) out.push(`${I}    Objects.requireNonNull(params, "params");`);
  out.push(`${I}    return ${target}.request(${call}, type, options);`, `${I}}`);

  if (optional) {
    out.push("");
    out.push(...docBlock(I, [`Like {@link ${ref}} with no parameters set.`], [ret, ...(async ? [] : [throwsTag])]));
    out.push(`${I}public ${R} ${m.method}() {`, `${I}    return ${m.method}(${cls}.builder().build(), JsonNode.class, null);`, `${I}}`);
  }
  return out;
}

function resourceSrc(cls, doc, methods, children, async) {
  const I = "    ";
  const clientType = async ? "LifeAstroAsync" : "LifeAstro";
  const imports = new Set([`com.lifeastroapi.${clientType}`]);
  if (methods.length) {
    imports.add("com.fasterxml.jackson.databind.JsonNode");
    imports.add("com.lifeastroapi.RequestOptions");
    imports.add("com.lifeastroapi.errors.LifeAstroException");
    if (methods.some((m) => m.params.length)) imports.add("java.util.Objects");
    if (async) imports.add("java.util.concurrent.CompletableFuture");
    for (const m of methods) if (m.params.length) imports.add(`com.lifeastroapi.params.${m.className}`);
  }
  const groups = (i) => (i.startsWith("java.") ? 2 : i.startsWith("com.fasterxml") ? 0 : 1);
  const sorted = [...imports].sort((a, b) => groups(a) - groups(b) || (a < b ? -1 : a > b ? 1 : 0));
  const out = [HEADER, "package com.lifeastroapi.resources;", ""];
  let last = null;
  for (const i of sorted) {
    if (last !== null && groups(i) !== last) out.push("");
    out.push(`import ${i};`);
    last = groups(i);
  }
  out.push("");
  out.push(...docBlock("", [html(doc)]));
  out.push(`public final class ${cls} {`);
  if (methods.length) out.push(`${I}private final ${clientType} client;`);
  for (const [prop, child] of children) out.push(`${I}private final ${child} ${prop};`);
  out.push("");
  out.push(...docBlock(I, [`Used by {@link ${clientType}}; get this group from the client instead.`], ["@param client the client"]));
  out.push(`${I}public ${cls}(${clientType} client) {`);
  if (methods.length) out.push(`${I}${I}this.client = client;`);
  for (const [prop, child] of children) out.push(`${I}${I}this.${prop} = new ${child}(client);`);
  out.push(`${I}}`);
  for (const [prop, child, childDoc] of children) {
    out.push("");
    out.push(...docBlock(I, [html(childDoc)], ["@return the group"]));
    out.push(`${I}public ${child} ${prop}() {`, `${I}${I}return ${prop};`, `${I}}`);
  }
  for (const m of methods) {
    out.push("");
    out.push(...methodSrc(m, I, async));
  }
  out.push("}", "");
  return out.join("\n");
}

function resourcesBaseSrc(groups, async) {
  const I = "    ";
  const base = async ? "LifeAstroAsyncResources" : "LifeAstroResources";
  const client = async ? "LifeAstroAsync" : "LifeAstro";
  const out = [HEADER, "package com.lifeastroapi;", ""];
  for (const [, cls] of groups) out.push(`import com.lifeastroapi.resources.${cls}${async ? "Async" : ""};`);
  out.push("");
  out.push(
    ...docBlock("", [
      `The resource groups of the API, one accessor each: <code>${async ? "client.async()." : "client."}panchang()</code>, <code>chart()</code>, <code>western().natal()</code> and so on. {@link ${client}} extends this class.`,
    ]),
  );
  out.push(`public abstract sealed class ${base} permits ${client} {`);
  for (const [prop, cls] of groups) out.push(`${I}private final ${cls}${async ? "Async" : ""} ${prop};`);
  out.push("");
  out.push(`${I}${base}() {`, `${I}${I}${client} client = (${client}) this;`);
  for (const [prop, cls] of groups) out.push(`${I}${I}this.${prop} = new ${cls}${async ? "Async" : ""}(client);`);
  out.push(`${I}}`);
  for (const [prop, cls] of groups) {
    out.push("");
    out.push(...docBlock(I, [html(NS_DOC[prop] ?? prop)], ["@return the group"]));
    out.push(`${I}public final ${cls}${async ? "Async" : ""} ${prop}() {`, `${I}${I}return ${prop};`, `${I}}`);
  }
  out.push("}", "");
  return out.join("\n");
}

// ---------------------------------------------------------------------------
// Resolve, check, write
// ---------------------------------------------------------------------------
const methods = entries.map(resolveEntry);

const seenMethod = new Map();
const seenClass = new Map();
for (const m of methods) {
  const key = `${m.ns}#${m.method}`;
  if (seenMethod.has(key)) throw new Error(`Java method collision: ${m.ns}.${m.method} from ${seenMethod.get(key)} and ${m.entry.path}`);
  seenMethod.set(key, m.entry.path);
  if (seenClass.has(m.className)) throw new Error(`Parameter class collision: ${m.className} from ${seenClass.get(m.className)} and ${m.entry.path}`);
  seenClass.set(m.className, m.entry.path);
  if (m.cost === null) console.warn(`warning: no credit cost for ${m.entry.path} in tool/costs.json`);
  const said = /\((\d[\d,]*) credits?\)/.exec(m.entry.desc ?? "");
  if (said && m.cost !== null && Number(said[1].replace(/,/g, "")) !== m.cost) {
    console.warn(`warning: ${m.ns}.${m.method} description says ${said[1]} credits, tool/costs.json says ${m.cost}`);
  }
}

const byNs = new Map();
for (const m of methods) {
  if (!byNs.has(m.ns)) byNs.set(m.ns, []);
  byNs.get(m.ns).push(m);
}
for (const list of byNs.values()) list.sort((a, b) => (a.method < b.method ? -1 : a.method > b.method ? 1 : 0));

// Clean previously generated files.
for (const f of readdirSync(PARAMS_DIR)) if (f.endsWith("Params.java")) rmSync(join(PARAMS_DIR, f));
mkdirSync(RES_DIR, { recursive: true });
for (const f of readdirSync(RES_DIR)) if (f.endsWith(".java")) rmSync(join(RES_DIR, f));
mkdirSync(TEST_RES, { recursive: true });

for (const m of methods) {
  if (!m.params.length) continue;
  writeFileSync(join(PARAMS_DIR, `${m.className}.java`), paramsClassSrc(m));
}

const flatNs = [...byNs.keys()].filter((ns) => !ns.startsWith("western")).sort();
const westernChildNs = [...byNs.keys()].filter((ns) => ns.startsWith("western.")).sort();
const groups = []; // [prop, cls]
for (const async of [false, true]) {
  const sfx = async ? "Async" : "";
  for (const ns of flatNs) {
    const cls = pascal(ns);
    writeFileSync(join(RES_DIR, `${cls}${sfx}.java`), resourceSrc(cls + sfx, `${NS_DOC[ns] ?? ns} Use \`client.${async ? "async()." : ""}${ns}()\`.`, byNs.get(ns), [], async));
    if (!async) groups.push([ns, cls]);
  }
  const children = [];
  for (const ns of westernChildNs) {
    const sub = ns.split(".")[1];
    const cls = "Western" + pascal(sub);
    const lang = sub === "narrative" ? "Takes `lang` for `en`, `hi` and `mr`." : "Answers in English.";
    const doc = `Western \`${sub}\` endpoints: \`client.${async ? "async()." : ""}western().${sub}()\`. ${lang}`;
    writeFileSync(join(RES_DIR, `${cls}${sfx}.java`), resourceSrc(cls + sfx, doc, byNs.get(ns), [], async));
    children.push([sub, cls + sfx, doc]);
  }
  const direct = byNs.get("western") ?? [];
  for (const [sub] of children) {
    if (direct.some((m) => m.method === sub)) throw new Error(`western.${sub} is both a sub-group and a method`);
  }
  writeFileSync(join(RES_DIR, `Western${sfx}.java`), resourceSrc("Western" + sfx, NS_DOC.western, direct, children, async));
  if (!async) groups.push(["western", "Western"]);
}
groups.sort((a, b) => (a[0] < b[0] ? -1 : 1));
writeFileSync(
  join(RES_DIR, "package-info.java"),
  `${HEADER}

/**
 * The resource groups of the API (<code>client.panchang()</code>, <code>client.chart()</code>, ...),
 * blocking and, with the <code>Async</code> suffix, returning <code>CompletableFuture</code>. Get
 * them from {@link com.lifeastroapi.LifeAstro}; do not create them yourself.
 */
package com.lifeastroapi.resources;
`,
);
writeFileSync(join(MAIN, "LifeAstroResources.java"), resourcesBaseSrc(groups, false));
writeFileSync(join(MAIN, "LifeAstroAsyncResources.java"), resourcesBaseSrc(groups, true));

// ---------------------------------------------------------------------------
// Samples.java (tests and live sweep) and the resolved manifest
// ---------------------------------------------------------------------------
{
  const I = "    ";
  const out = [HEADER, "package com.lifeastroapi;", ""];
  const usedParams = new Set();
  const bodies = [];
  for (const ns of [...byNs.keys()].sort()) {
    const fn = "add" + pascal(ns.replace(/\./g, "-"));
    const lines = [`${I}private static void ${fn}(List<Sample> all) {`];
    for (const m of byNs.get(ns)) {
      const hasLang = m.params.some((p) => p.name === "lang");
      const chain = accessorChain(m.ns);
      let expr;
      if (!m.params.length) {
        expr = (c) => `${c}.${chain}.${m.method}()`;
      } else {
        usedParams.add(m.className);
        const calls = builderCalls(m, sampleInput(m));
        if (hasLang) calls.push(".lang(lang)");
        const built = `${m.className}.builder()${calls.join("")}.build()`;
        expr = (c) => `${c}.${chain}.${m.method}(${built})`;
      }
      lines.push(`${I}${I}all.add(new Sample(${javaString(`${m.ns}.${m.method}`)}, ${javaString(m.entry.path)}, ${javaString(m.entry.shape)}, ${hasLang},`);
      lines.push(`${I}${I}${I}${I}(c, lang) -> ${expr("c")},`);
      lines.push(`${I}${I}${I}${I}(c, lang) -> ${expr("c")}));`);
    }
    lines.push(`${I}}`);
    bodies.push({ fn, lines });
  }
  const imports = ["java.util.ArrayList", "java.util.List"];
  out.push(...[...usedParams].sort().map((c) => `import com.lifeastroapi.params.${c};`));
  out.push("", ...imports.map((i) => `import ${i};`), "");
  out.push(
    "/** A sample call of every method, with the inputs of tool/samples.json (as tool/node-goldens.mjs uses them). */",
    "final class Samples {",
    `${I}private Samples() {`,
    `${I}}`,
    "",
    `${I}/** Every method, sorted by key; {@code lang} is passed to the methods that take it (null for none). */`,
    `${I}static List<Sample> all() {`,
    `${I}${I}List<Sample> all = new ArrayList<>();`,
    ...bodies.map((b) => `${I}${I}${b.fn}(all);`),
    `${I}${I}return all;`,
    `${I}}`,
  );
  for (const b of bodies) out.push("", ...b.lines);
  out.push("}", "");
  writeFileSync(join(TEST, "Samples.java"), out.join("\n"));
}

const manifestOut = methods
  .map((m) => ({
    key: `${m.ns}.${m.method}`,
    nodeKey: `${m.ns}.${m.nodeMethod}`,
    path: m.entry.path,
    shape: m.entry.shape,
    cost: m.cost,
    params: m.params.length ? m.className : null,
    extras: (m.entry.extras ?? []).map((x) => ({ wire: x.wire, field: x.name ?? camelOf(x.wire), required: !!x.required })),
    fields: ordered(m.params).map((p) => ({
      name: p.name,
      type: p.type,
      required: p.required,
      kind: p.kind,
      ...(p.kind === "object" ? { prefix: p.prefix, place: p.place, window: p.window } : { wires: p.wires }),
    })),
  }))
  .sort((a, b) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
writeFileSync(join(TEST_RES, "manifest.json"), JSON.stringify({ node_entries: entries.length, methods: manifestOut }, null, 1) + "\n");

const paramClasses = methods.filter((m) => m.params.length).length;
console.log(
  `Generated ${groups.length} groups (+${westernChildNs.length} Western sub-groups), blocking and async: ` +
    `${methods.length} methods from ${entries.length} manifest entries, ${paramClasses} parameter classes.`,
);
