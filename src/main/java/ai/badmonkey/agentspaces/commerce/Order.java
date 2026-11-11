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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The UCP order a mandate led to: {@code mandate} the id of the checkout mandate, {@code status} one of the UCP
 * checkout statuses as published ({@code incomplete}, {@code requires_escalation}, {@code ready_for_complete},
 * {@code complete_in_progress}, {@code completed}, {@code canceled}), {@code fulfillment} the fulfillment as the
 * merchant reports it (a map, UCP's shape), {@code permalink} where the order is seen.
 */
public record Order(int schemaVersion, String id, String mandate, String status, Map<String, Object> fulfillment, String permalink, Instant at) {

    public static final int VERSION = 1;
    public static final List<String> STATUSES = List.of("incomplete", "requires_escalation", "ready_for_complete", "complete_in_progress", "completed", "canceled");

    public Order {
        Objects.requireNonNull(id, "id");
        mandate = mandate == null ? "" : mandate;
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be one of " + STATUSES + ": " + status);
        }
        fulfillment = fulfillment == null ? Map.of() : Map.copyOf(fulfillment);
        permalink = permalink == null ? "" : permalink;
        Objects.requireNonNull(at, "at");
    }
}
