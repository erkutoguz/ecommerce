#!/usr/bin/env bash

total=0

for dir in */; do
    count=$(
        find "$dir" \
            -type f \
            -path '*/src/main/java/*' \
            -name '*.java' \
            -exec cat {} + 2>/dev/null |
        wc -l |
        tr -d ' '
    )

    if [ "$count" -gt 0 ]; then
        printf "%-30s %8d satır\n" "${dir%/}" "$count"
        total=$((total + count))
    fi
done

echo "----------------------------------------"
printf "%-30s %8d satır\n" "TOPLAM" "$total"
