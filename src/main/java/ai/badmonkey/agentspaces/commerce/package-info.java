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


/**
 * The commerce records (SPEC §6.5a, §7.6): a {@link ai.badmonkey.agentspaces.commerce.Mandate}
 * is an Agent Payments Protocol (AP2) Checkout or Payment Mandate carried as an entry of a group's {@code commerce}
 * space, with its cart, its counterparty, its basis, and the credential as issued; an
 * {@link ai.badmonkey.agentspaces.commerce.Order} is the Universal Commerce Protocol (UCP) order a mandate led to.
 * {@link ai.badmonkey.agentspaces.commerce.Mandates#verify} checks a credential chain with no policy in it.
 */
package ai.badmonkey.agentspaces.commerce;
