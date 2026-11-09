/**
 * Web images (README badges, screenshots): loaded unless Settings | Tools | Markdown Studio
 * turns them off. The core's preview marks them as blocked when it renders; this lets them load.
 */
let allowed = true;

export function setRemoteImagesAllowed(value: boolean): void {
  allowed = value;
  if (value) document.querySelectorAll<HTMLImageElement>("img").forEach(unblock);
}

export function remoteImagesAllowed(): boolean {
  return allowed;
}

/** The preview stylesheet class for an image it did not load (stable name, see vite.config.ts). */
const BLOCKED = "Markdown_blocked";

function unblock(img: HTMLImageElement): void {
  const src = img.dataset.src ?? "";
  if (!allowed || !img.classList.contains(BLOCKED) || !/^https?:\/\//i.test(src)) return;
  img.classList.remove(BLOCKED);
  img.removeAttribute("title");
  img.src = src;
}

/** Watches the page for images the preview blocked. */
export function watchRemoteImages(root: Node = document.body): () => void {
  const observer = new MutationObserver((records) => {
    for (const r of records) if (r.target instanceof HTMLImageElement) unblock(r.target);
  });
  observer.observe(root, { subtree: true, attributes: true, attributeFilter: ["class"] });
  return () => observer.disconnect();
}
