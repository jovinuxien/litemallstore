#!/bin/bash

# Find all mapper XML files
find src/main/resources/org/linlinjava/litemall/db/dao -name "*.xml" | while read file; do
    echo "Checking $file..."
    
    # Use awk to remove duplicate selectOneByExampleSelective blocks
    awk '
    /<select id="selectOneByExampleSelective"/ {
        if (seen[$0]++) {
            # Skip duplicate
            skip = 1
        }
    }
    skip && /<\/select>/ {
        skip = 0
        next
    }
    !skip { print }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"
done

echo "Done fixing duplicates"
