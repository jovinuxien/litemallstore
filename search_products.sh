#!/bin/bash

# Configuration
INDEX="ocs-1-litemall_index-en"
BASE_URL="http://localhost:8534/search-api/v1/search"
DEFAULT_SIZE=12

# Function to perform search
search_products() {
    local query="${1:-*}"
    local page="${2:-1}"
    local offset=$(( (page - 1) * DEFAULT_SIZE ))
    
    local url="$BASE_URL/$INDEX?\
q=${query}&\
facets=category,brand,price,discount,rating,color,size&\
size=${DEFAULT_SIZE}&\
offset=${offset}&\
sort=relevance:desc&\
filter=in_stock:true&\
highlight=true&\
spellcheck=true"
    
    echo "Searching: $query (Page $page)"
    echo "URL: ${url//&/\\&}"
    echo "="$(printf '=%.0s' {1..60})
    
    curl -s "$url" | jq '{
        status: "success",
        query: "'"$query"'",
        pagination: {
            current_page: '"$page"',
            page_size: '"$DEFAULT_SIZE"',
            total_results: .slices[0].matchCount,
            total_pages: (if .slices[0].matchCount > 0 then ((.slices[0].matchCount + '"$DEFAULT_SIZE"' - 1) / '"$DEFAULT_SIZE"' | floor) else 0 end),
            has_next: (.slices[0].nextOffset > 0)
        },
        products: .slices[0].hits | map({
            id: .id,
            title: .title,
            price: .price,
            original_price: .original_price,
            discount_percent: (if .price and .original_price and .original_price > 0 then ((1 - (.price / .original_price)) * 100 | floor) else null end),
            image: .image_url,
            brand: .brand,
            rating: .rating,
            categories: .categories,
            highlight: .highlight
        }),
        filters: .slices[0].facets | to_entries | map({
            type: .key,
            name: (if .key == "price" then "Price Range" 
                  elif .key == "category" then "Category" 
                  elif .key == "brand" then "Brand" 
                  else .key end),
            values: .value.buckets | map({
                value: .key,
                count: .docCount,
                selected: false
            })
        }),
        suggestions: .suggestions,
        spellcheck: .spellcheck
    }'
}
