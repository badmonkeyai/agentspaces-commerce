/*
 * Copyright 2026 Bad Monkey, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package ai.badmonkey.agentspaces.commerce;

import java.util.List;
import java.util.Objects;

/**
 * What a mandate is about: the items, the total in ISO 4217 minor units, the currency. An item's {@code sku} is the
 * UCP item id, {@code unitPrice} its price per unit in minor units.
 */
public record Cart(List<Item> items, long total, String currency) {

    public static final int MAX_ITEMS = 64;

    public record Item(String sku, String title, long quantity, long unitPrice) {
        public Item {
            Objects.requireNonNull(sku, "sku");
            title = title == null ? "" : title;
            if (quantity < 1) {
                throw new IllegalArgumentException("an item's quantity is at least one");
            }
            if (unitPrice < 0) {
                throw new IllegalArgumentException("an item's unit price is not negative");
            }
        }

        public long subtotal() {
            return quantity * unitPrice;
        }
    }

    public Cart {
        items = items == null ? List.of() : List.copyOf(items);
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("a cart carries at most " + MAX_ITEMS + " items");
        }
        if (total < 0) {
            throw new IllegalArgumentException("a cart's total is not negative");
        }
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("the currency is an ISO 4217 code: " + currency);
        }
    }

    /** The items' subtotals summed, for a check against {@code total}. */
    public long itemsTotal() {
        return items.stream().mapToLong(Item::subtotal).sum();
    }
}
