param(
    [switch]$Recreate,
    [int]$EmbeddingDimension = 384
)

$ErrorActionPreference = "Stop"

$OpenSearchUrl = "http://localhost:9200"
$IndexName = "news_article"
$IndexUrl = "$OpenSearchUrl/$IndexName"

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

$Body = $Body.Replace("EMBEDDING_DIMENSION_PLACEHOLDER", $EmbeddingDimension.ToString())

Write-Host "Creating index '$IndexName'..."
Invoke-RestMethod -Uri $IndexUrl -Method Put -ContentType "application/json" -Body $Body | Out-Null
Write-Host "Index '$IndexName' created with k-NN enabled and $EmbeddingDimension-dimensional embeddings."
