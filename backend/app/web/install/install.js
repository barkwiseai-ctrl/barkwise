async function loadInstallerMetadata() {
  const latestMetaEl = document.getElementById("latestMeta");
  const listEl = document.getElementById("releaseList");

  try {
    const latestResponse = await fetch("/install/apk/latest.json", { cache: "no-store" });
    const latest = latestResponse.ok ? await latestResponse.json() : null;
    if (latest && latest.version && latest.published_at_utc) {
      latestMetaEl.textContent =
        `Latest: ${latest.version} · published ${latest.published_at_utc} UTC`;
    } else {
      latestMetaEl.textContent = "No release metadata available yet.";
    }
  } catch (_error) {
    latestMetaEl.textContent = "Could not load latest release metadata.";
  }

  try {
    const releasesResponse = await fetch("/install/apk/releases.json", { cache: "no-store" });
    const releases = releasesResponse.ok ? await releasesResponse.json() : [];
    if (!Array.isArray(releases) || releases.length === 0) {
      return;
    }
    listEl.innerHTML = releases
      .map((release) => {
        const version = release.version || "unknown";
        const publishedAt = release.published_at_utc || "unknown time";
        const url = release.file || "/install/apk/barkwise-staging-latest.apk";
        return `
          <article class="release">
            <a href="${url}">${version}</a>
            <div class="meta">${publishedAt} UTC</div>
          </article>
        `;
      })
      .join("");
  } catch (_error) {
    // Keep default copy if release history cannot be loaded.
  }
}

loadInstallerMetadata();
