// The welcome page. The IDE puts its data in `window.__welcome` before this runs, and calls
// `window.__welcomeRender(data)` when the theme, language, keymap or settings change.
(function () {
  "use strict";

  const root = document.documentElement;
  const app = document.getElementById("app");
  let shownSignature = null;
  /** The IDE colours set on :root, replaced as a whole on every theme change. */
  let appliedTokens = [];

  const ICONS = {
    info: '<svg viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="6.25" stroke="currentColor" stroke-width="1.2"/><path d="M8 7.2v3.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><circle cx="8" cy="5.2" r=".8" fill="currentColor"/></svg>',
    check: '<svg viewBox="0 0 12 12" fill="none"><path d="M2.6 6.3l2.2 2.2 4.6-4.9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    play: '<svg viewBox="0 0 14 14" fill="none"><path d="M4.5 3.2v7.6l6-3.8-6-3.8z" fill="currentColor"/></svg>',
    gear: '<svg viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="2.2" stroke="currentColor" stroke-width="1.2"/><path d="M8 1.8v1.6M8 12.6v1.6M14.2 8h-1.6M3.4 8H1.8M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1M12.4 12.4l-1.1-1.1M4.7 4.7L3.6 3.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg>',
    keyboard: '<svg viewBox="0 0 16 16" fill="none"><rect x="1.8" y="4" width="12.4" height="8" rx="1.6" stroke="currentColor" stroke-width="1.2"/><path d="M4.4 6.6h.01M6.8 6.6h.01M9.2 6.6h.01M11.6 6.6h.01M5.2 9.4h5.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
  };

  function post(message) {
    if (typeof window.__studioPost === "function") window.__studioPost(JSON.stringify(message));
  }

  /** An element: `h("div", { class: "x", onclick }, child, "text")`. */
  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props || {})) {
      if (v == null || v === false) continue;
      if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
      else if (k === "html") el.innerHTML = v;
      else if (k === "style") el.style.cssText = v;
      else el.setAttribute(k, v === true ? "" : String(v));
    }
    for (const c of children.flat()) if (c != null && c !== false) el.append(c);
    return el;
  }

  const bar = (cls, width) => h("span", { class: "bar " + cls, style: `width:${width}%` });

  function docPane(withCaret) {
    return h(
      "div",
      { class: "pane doc" },
      bar("h", 52),
      bar("", 94),
      withCaret ? h("div", { class: "caret-row" }, bar("mark", 14), bar("", 46), h("span", { class: "caret" })) : bar("", 78),
      bar("", 64),
      h("div", { class: "block" }),
      bar("h sub", 34),
      bar("", 90),
      bar("", 72),
      bar("", 84),
    );
  }

  function srcPane() {
    const rows = [
      [["kw", 14], ["", 36]],
      [["", 58]],
      [["str", 30], ["", 22]],
      [["", 44]],
      [["kw", 18], ["", 48]],
      [["", 30]],
      [["", 52]],
      [["str", 24], ["", 30]],
      [["kw", 12], ["", 40]],
      [["", 46]],
    ];
    return h(
      "div",
      { class: "pane src" },
      rows.map((tokens) =>
        h("div", { class: "row" }, h("span", { class: "num" }), tokens.map(([cls, w]) => h("span", { class: "tok " + cls, style: `width:${w}%` }))),
      ),
    );
  }

  function miniature(id) {
    switch (id) {
      case "LIVE":
        return h("div", { class: "mini" }, docPane(true));
      case "SOURCE":
        return h("div", { class: "mini" }, srcPane());
      case "SPLIT":
        return h("div", { class: "mini" }, srcPane(), docPane(false));
      default:
        return h("div", { class: "mini" }, docPane(false));
    }
  }

  function applyTheme(d) {
    root.dataset.theme = d.scheme === "dark" ? "dark" : "light";
    // A token the new theme leaves out (the IDE omits its interface colours when the interface and
    // the editor scheme differ in brightness) falls back to the page's light or dark default.
    const tokens = Object.entries(d.tokens || {}).filter(([name]) => name.startsWith("--"));
    for (const name of appliedTokens) if (!(d.tokens || {})[name]) root.style.removeProperty(name);
    for (const [name, value] of tokens) root.style.setProperty(name, value);
    appliedTokens = tokens.map(([name]) => name);
    root.lang = d.locale || "en";
    document.title = d.text.title;
  }

  function select(id) {
    for (const el of app.querySelectorAll(".layout")) el.setAttribute("aria-pressed", String(el.dataset.id === id));
  }

  function build(d, animate) {
    let i = 0;
    const reveal = (el) => {
      if (animate) {
        el.classList.add("reveal");
        el.style.setProperty("--i", String(i++));
      }
      return el;
    };
    const t = d.text;

    const hero = reveal(
      h(
        "header",
        { class: "hero" },
        h("div", { class: "logo", "aria-hidden": "true", html: d.logo }),
        h(
          "div",
          {},
          h("h1", { class: "title" }, t.title, d.version ? h("span", { class: "version" }, d.version) : null),
          h("p", { class: "tagline" }, t.tagline),
          h(
            "div",
            { class: "actions" },
            h("button", { class: "btn primary", onclick: () => post({ type: "tour" }) }, h("span", { html: ICONS.play }), t.tour),
            h("button", { class: "btn", onclick: () => post({ type: "settings" }) }, h("span", { html: ICONS.gear }), t.settings),
          ),
        ),
      ),
    );

    const layoutsHead = reveal(
      h(
        "div",
        { class: "section-head" },
        h("h2", {}, t.layouts),
        h("button", { class: "tip", "data-tip": t["layouts.default"], "aria-label": t["layouts.default"], html: ICONS.info }),
      ),
    );
    const layouts = h(
      "div",
      { class: "layouts", role: "group", "aria-label": t.layouts },
      d.layouts.map((l) =>
        reveal(
          h(
            "button",
            {
              class: "layout",
              "data-id": l.id,
              "aria-pressed": String(l.id === d.layout),
              onclick: () => {
                select(l.id);
                post({ type: "layout", id: l.id });
              },
            },
            miniature(l.id),
            h("span", { class: "check", html: ICONS.check }),
            h("span", { class: "layout-name" }, l.title),
            h("span", { class: "layout-desc" }, l.body),
          ),
        ),
      ),
    );

    const features = h(
      "div",
      { class: "features" },
      d.features.map((f) =>
        reveal(h("article", { class: "feature" }, h("div", { class: "feature-icon", html: f.icon }), h("h3", {}, f.title), h("p", {}, f.body))),
      ),
    );

    const keys = reveal(
      h(
        "div",
        { class: "keys" },
        d.keys.map((k) => h("div", { class: "key" }, h("span", { class: "key-label" }, k.label), h("kbd", {}, k.keys))),
      ),
    );

    const footer = reveal(
      h(
        "footer",
        { class: "footer" },
        h("button", { class: "link", onclick: () => post({ type: "keymap" }) }, h("span", { html: ICONS.keyboard }), t.keymap),
      ),
    );

    return [
      hero,
      h("section", { class: "section" }, layoutsHead, layouts),
      h("section", { class: "section" }, reveal(h("div", { class: "section-head" }, h("h2", {}, t.features))), features),
      h("section", { class: "section" }, reveal(h("div", { class: "section-head" }, h("h2", {}, t.keys))), keys),
      footer,
    ];
  }

  function render(d) {
    if (!d) return;
    applyTheme(d);
    // Same language and keys: keep the page (and its hover and focus) and update the selection only.
    const signature = [d.locale, d.version, d.keys.map((k) => k.label + k.keys).join("|")].join("#");
    if (signature === shownSignature) {
      select(d.layout);
      return;
    }
    const first = shownSignature === null;
    shownSignature = signature;
    app.replaceChildren(...build(d, first));
  }

  window.__welcomeRender = render;
  render(window.__welcome);
})();
