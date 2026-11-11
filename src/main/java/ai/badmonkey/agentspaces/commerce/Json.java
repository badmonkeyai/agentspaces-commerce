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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/**
 * The JSON form of the claim sets the mandates carry, as Jackson writes and reads it. Keys keep insertion
 * order on write (the claim sets are {@code LinkedHashMap}s); integral numbers read as {@code Long} and the rest as
 * {@code Double}, so a verifier compares amounts and times the same way whichever side wrote them. The hand-rolled
 * writer and reader that Jackson replaced before the first release wrote the same bytes for the shapes a mandate uses.
 */
final class Json {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };

    private Json() {
    }

    /** A value (a map, a list, a string, a number, a boolean, null) as compact JSON. */
    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("not writable as JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** A JSON object as a map in document order; anything else is refused. */
    static Map<String, Object> readObject(String text) {
        try {
            String trimmed = text == null ? "" : text.trim();
            if (!trimmed.startsWith("{")) {
                throw new IllegalArgumentException("not a JSON object");
            }
            return MAPPER.readValue(trimmed, OBJECT);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("bad JSON: " + e.getMessage(), e);
        }
    }
}
