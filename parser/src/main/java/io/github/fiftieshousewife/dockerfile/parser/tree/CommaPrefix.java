/*
 * Copyright 2026 Dockerfile Inventory.
 * Added by Dockerfile Inventory, 2026: whitespace before a comma in a JSON array (issue 8).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.fiftieshousewife.dockerfile.parser.tree;

import lombok.Value;
import lombok.With;
import org.openrewrite.marker.Marker;

import java.util.UUID;

/**
 * The whitespace between an element of a JSON array and the comma after it, as in {@code ["a" , "b"]}.
 * It is carried on the element before the comma and printed after it.
 */
@Value
@With
public class CommaPrefix implements Marker {
    UUID id;
    Space space;
}
