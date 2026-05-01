param(
    [switch]$Recreate,
    [Nullable[int]]$EmbeddingDimension
)

$ErrorActionPreference = "Stop"

$OpenSearchUrl = "http://localhost:9200"
$IndexName = "news_article"
$IndexUrl = "$OpenSearchUrl/$IndexName"

function Find-RepoRoot {
    $current = (Get-Location).Path

    while ($current) {
        if ((Test-Path (Join-Path $current ".env")) -or
            (Test-Path (Join-Path $current "config\sources.yaml"))) {
            return $current
        }

        $parent = Split-Path -Parent $current
        if ($parent -eq $current) {
            break
        }

        $current = $parent
    }

    return (Get-Location).Path
}

function Get-DotenvValue {
    param([string]$Name)

    $envPath = Join-Path (Find-RepoRoot) ".env"
    if (-not (Test-Path $envPath)) {
        return $null
    }

    foreach ($line in Get-Content $envPath) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -le 0) {
            continue
        }

        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        if ($key -ne $Name) {
            continue
        }

        return $trimmed.Substring($separatorIndex + 1).Trim().Trim('"').Trim("'")
    }

    return $null
}

function Resolve-EmbeddingDimension {
    if ($PSBoundParameters.ContainsKey("EmbeddingDimension")) {
        return $EmbeddingDimension.Value
    }

    if ($env:EMBEDDING_DIMENSIONS -and -not [string]::IsNullOrWhiteSpace($env:EMBEDDING_DIMENSIONS)) {
        return [int]$env:EMBEDDING_DIMENSIONS
    }

    $dotenvDimension = Get-DotenvValue "EMBEDDING_DIMENSIONS"
    if ($dotenvDimension -and -not [string]::IsNullOrWhiteSpace($dotenvDimension)) {
        return [int]$dotenvDimension
    }

    return 384
}

function Test-OpenSearchReachable {
    try {
        Invoke-RestMethod -Uri $OpenSearchUrl -Method Get | Out-Null
        return $true
    }
    catch {
        return $false
    }
}

function Test-IndexExists {
    try {
        Invoke-RestMethod -Uri $IndexUrl -Method Get | Out-Null
        return $true
    }
    catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 404) {
            return $false
        }

        throw
    }
}

$ResolvedEmbeddingDimension = Resolve-EmbeddingDimension
if ($ResolvedEmbeddingDimension -le 0) {
    throw "Embedding dimension must be greater than zero."
}

if (-not (Test-OpenSearchReachable)) {
    Write-Host "OpenSearch is not reachable at $OpenSearchUrl."
    Write-Host "Start it with: docker compose up -d opensearch"
    exit 1
}

$indexExists = Test-IndexExists

if ($indexExists -and -not $Recreate) {
    Write-Host "Index '$IndexName' already exists. Nothing to do."
    Write-Host "Use -Recreate to delete and recreate it."
    exit 0
}

if ($indexExists -and $Recreate) {
    Write-Host "Deleting existing index '$IndexName'..."
    Invoke-RestMethod -Uri $IndexUrl -Method Delete | Out-Null
}

$Body = @'
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "knn": true
    }
  },
  "mappings": {
    "properties": {
      "title": {
        "type": "text"
      },
      "content": {
        "type": "text"
      },
      "source": {
        "type": "keyword"
      },
      "url": {
        "type": "keyword"
      },
      "timestamp": {
        "type": "date"
      },
      "sentiment": {
        "type": "keyword"
      },
      "embedding": {
        "type": "knn_vector",
        "dimension": EMBEDDING_DIMENSION_PLACEHOLDER
      }
    }
  }
}
'@

$Body = $Body.Replace("EMBEDDING_DIMENSION_PLACEHOLDER", $ResolvedEmbeddingDimension.ToString())

Write-Host "Creating index '$IndexName'..."
Invoke-RestMethod -Uri $IndexUrl -Method Put -ContentType "application/json" -Body $Body | Out-Null
Write-Host "Index '$IndexName' created with k-NN enabled and $ResolvedEmbeddingDimension-dimensional embeddings."
