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

/**
 * What a shopper's mandate rests on: the id of the approval a person gave, or the id of the jury's decision; both
 * empty for a mandate whose basis is elsewhere (a merchant's, a payment party's). A reader may refuse a shopper's
 * mandate without one; the record does not.
 */
public record Basis(String approval, String decision) {

    public static final Basis NONE = new Basis("", "");

    public Basis {
        approval = approval == null ? "" : approval;
        decision = decision == null ? "" : decision;
    }

    /** True when neither an approval nor a decision is named (a bean-style name would leak onto the wire). */
    public boolean absent() {
        return approval.isEmpty() && decision.isEmpty();
    }
}
