const form = document.querySelector("#search-form");
const queryInput = document.querySelector("#query");
const sizeInput = document.querySelector("#size");
const minScoreInput = document.querySelector("#min-score");
const searchButton = document.querySelector("#search-button");
const statusEl = document.querySelector("#status");
const summaryEl = document.querySelector("#summary");
const resultsEl = document.querySelector("#results");
const quickQueryButtons = document.querySelectorAll("[data-query]");

form.addEventListener("submit", (event) => {
  event.preventDefault();
  runSearch();
});

quickQueryButtons.forEach((button) => {
  button.addEventListener("click", () => {
    queryInput.value = button.dataset.query;
    runSearch();
  });
});

async function runSearch() {
  const validationMessage = validateInputs();
  if (validationMessage) {
    setStatus(validationMessage, true);
    clearResults();
    return;
  }

  const params = new URLSearchParams({
    q: queryInput.value.trim(),
    size: sizeInput.value
  });

  const minScore = minScoreInput.value.trim();
  if (minScore !== "") {
    params.set("minScore", minScore);
  }

  setLoading(true);
  setStatus("Searching indexed news articles...", false);
  clearResults();

  try {
    const response = await fetch(`/search?${params.toString()}`, {
      headers: {
        "Accept": "application/json"
      }
    });

    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(payload.message || `Search failed with HTTP ${response.status}`);
    }

    renderResponse(payload);
  } catch (error) {
    setStatus(error.message || "Search failed. Check that search-service and OpenSearch are running.", true);
    clearResults();
  } finally {
    setLoading(false);
  }
}

function validateInputs() {
  const query = queryInput.value.trim();
  const size = Number(sizeInput.value);
  const minScoreValue = minScoreInput.value.trim();

  if (!query) {
    return "Query is required.";
  }

  if (!Number.isInteger(size) || size < 1 || size > 20) {
    return "Size must be a whole number between 1 and 20.";
  }

  if (minScoreValue !== "") {
    const minScore = Number(minScoreValue);
    if (!Number.isFinite(minScore) || minScore < 0) {
      return "Min score must be empty or greater than or equal to 0.";
    }
  }

  return "";
}

function renderResponse(payload) {
  const results = Array.isArray(payload.results) ? payload.results : [];
  const count = Number.isInteger(payload.resultCount) ? payload.resultCount : results.length;

  summaryEl.hidden = false;
  summaryEl.textContent = `${count} result${count === 1 ? "" : "s"}`;

  if (results.length === 0) {
    setStatus(`No results found for "${payload.query || queryInput.value.trim()}".`, false);
    return;
  }

  setStatus(`Showing results for "${payload.query || queryInput.value.trim()}".`, false);

  const fragment = document.createDocumentFragment();
  results.forEach((result) => {
    fragment.appendChild(createResultCard(result));
  });
  resultsEl.appendChild(fragment);
}

function createResultCard(result) {
  const card = document.createElement("article");
  card.className = "result-card";

  const header = document.createElement("div");
  header.className = "result-header";

  const title = document.createElement("h2");
  title.className = "result-title";

  if (result.url) {
    const link = document.createElement("a");
    link.href = result.url;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = result.title || "Untitled article";
    title.appendChild(link);
  } else {
    title.textContent = result.title || "Untitled article";
  }

  const score = document.createElement("div");
  score.className = "score";
  score.textContent = formatScore(result.score);

  header.append(title, score);

  const meta = document.createElement("div");
  meta.className = "meta-row";

  meta.appendChild(createMetaItem(result.source || "Unknown source"));
  meta.appendChild(createMetaItem(formatTimestamp(result.timestamp)));
  meta.appendChild(createSentimentBadge(result.sentiment));

  card.append(header, meta);
  return card;
}

function createMetaItem(text) {
  const item = document.createElement("span");
  item.className = "meta-item";
  item.textContent = text;
  return item;
}

function createSentimentBadge(sentiment) {
  const normalized = String(sentiment || "neutral").toLowerCase();
  const allowed = ["positive", "negative", "neutral"];
  const badge = document.createElement("span");
  badge.className = `sentiment ${allowed.includes(normalized) ? normalized : "neutral"}`;
  badge.textContent = allowed.includes(normalized) ? normalized : "neutral";
  return badge;
}

function formatTimestamp(timestamp) {
  if (!timestamp) {
    return "No timestamp";
  }

  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return timestamp;
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function formatScore(score) {
  const numericScore = Number(score);
  if (!Number.isFinite(numericScore)) {
    return "Score n/a";
  }

  return `Score ${numericScore.toFixed(3)}`;
}

function setStatus(message, isError) {
  statusEl.textContent = message;
  statusEl.classList.toggle("error", isError);
}

function setLoading(isLoading) {
  searchButton.disabled = isLoading;
  searchButton.textContent = isLoading ? "Searching" : "Search";
}

function clearResults() {
  resultsEl.replaceChildren();
  summaryEl.hidden = true;
  summaryEl.textContent = "";
}
