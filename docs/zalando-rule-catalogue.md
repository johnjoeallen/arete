# Zally Rule Catalogue

## Zalando

Core Zally `ZalandoRuleSet` IDs:

`101, 104, 105, 107, 110, 115, 116, 118, 120, 125, 129, 130, 132, 134, 136, 143, 145, 146, 147, 150, 151, 153, 154, 166, 171, 172, 174, 176, 183, 215, 218, 219, 224, 235, 240`

## Zalando Extended

Includes every Zalando rule plus: `M008, M009, M010, M011, S005, S006, S007, H001, H002, Z001`.

The detailed supplied rule descriptions are the authoritative source for future catalogue mapping and implementation.

## Zalando → Speculate rule ID mapping

Titles below are from Zally's `zally-ruleset-zalando` (`@Rule` annotations,
`zalando/zally` `main`). Speculate rule IDs are files under
`generic-policy-validation-plugin/src/main/resources/api-policy/rules/`.

Status key: **✅ implemented** · **🟡 partial** (a related rule exists but scope
or convention differs) · **🔲 planned** (open issue, no rule yet) · **⬜ gap**
(no rule and no rule issue — detector capability only).

Match column is a fuzzy description match, not a code-level equivalence: Speculate
rules are often heuristic candidates-for-review where the Zally rule is exact, and
Speculate's default conventions (camelCase properties) sometimes invert Zalando's.

| Zally ID | Zally rule (title) | Speculate rule(s) | Status | Issue |
|----------|--------------------|-------------------|--------|-------|
| 101 | Provide API Specification using OpenAPI | STANDARD010 | ✅ | #56 (closed) |
| 104 | Secure Endpoints | SECURITY001 | ✅ | #53 (closed) |
| 105 | Secure All Endpoints With Scopes | SECURITY002 | ✅ | #54 (closed) |
| 107 | Prefer Compatible Extensions | COMPAT001–006 (breaking-change detection), JSON007/JSON014 | ✅ | #47 (closed) |
| 110 | Response As JSON Object | JSON016 | ✅ | #49 (closed) |
| 115 | Do Not Use URI Versioning | VERSION001 | ✅ | — |
| 116 | Use Semantic Versioning | DOC006 (requires a semantic `info.version`) | 🟡 | #33 |
| 118 | Property Names Must be ASCII snake_case | CASE001 (configurable property case), JSON003 (unsupported chars) | ✅ | — |
| 120 | Array names should be pluralized | JSON004 | ✅ | — |
| 125 | Represent enumerations as strings | JSON009 (numeric enum flagged) | ✅ | #46 |
| 129 | Lowercase words with hyphens (path segments) | CASE005 | ✅ | — |
| 130 | Use snake_case for Query Parameters | CASE003 | ✅ | — |
| 132 | Hyphenated words for HTTP headers | CASE004 | ✅ | — |
| 134 | Pluralize Resource Names | REST002 (collection uses singular noun) | ✅ | — |
| 136 | Avoid Trailing Slashes | — | 🔲 planned | #37 |
| 143 | Resources identified via path segments | REST001 (verb in path), REST003 (RPC-style), REST004 (custom action) | ✅ | #42 |
| 145 | Consider Using (Non-) Nested URLs | UPDATE003 (update as sub-resource, related) | 🟡 | #41 |
| 146 | Limit number of resource types | — | 🔲 planned | #39 |
| 147 | Limit number of Sub-resources level | — | 🔲 planned | #40 |
| 150 | Use Standard HTTP Status Codes | STATUS003 (401 vs 403), STATUS005 (status/semantics conflict) | 🟡 | — |
| 151 | Specify Success and Error Responses | STATUS001/002/004/006 | ✅ | #50 (closed) |
| 153 | Use 429 With Header For Rate Limits | STATUS007 | ✅ | #55 (closed) |
| 154 | Form-Style Query Format for Collection Parameters | STANDARD009 | ✅ | #52 (closed) |
| 166 | Avoid Link in Header | — | 🔲 planned | #38 |
| 171 | Define Format for Type Number and Integer | — | 🔲 planned | #45 |
| 172 | Prefer standard media type names | CONTENT004 | ✅ | #24 (closed) |
| 174 | Use common field names | — | 🔲 planned | #43 |
| 176 | Use Problem JSON | STATUS006 | ✅ | #50 (closed) |
| 183 | Use Only the Specified Proprietary Headers | STANDARD008 | ✅ | #51 (closed) |
| 215 | Provide API Identifier (`x-api-id`) | DOC007 | ✅ | #34 (closed) |
| 218 | Contain API Meta Information | DOC006 | ✅ | #33 (closed) |
| 219 | Provide API Audience (`x-audience`) | DOC008 | ✅ | #35 (closed) |
| 224 | Follow Naming Convention for Hostnames | — | 🔲 planned | #36 |
| 235 | Name date/time properties conventionally | — | 🔲 planned | #44 |
| 240 | Declare enum values using UPPER_SNAKE_CASE | JSON015 | ✅ | #48 (closed) |

### Legacy "Zalando Extended" IDs

`M008–M011, S005–S007, H001–H002, Z001` are legacy Zally rule codes that predate
the numeric `ZalandoRuleSet` IDs and are largely superseded by the numeric rules
above (property/header/path casing, semantic versioning, standard headers,
hypermedia). No rule descriptions were supplied for them, so no distinct
Speculate mapping is recorded; revisit if authoritative descriptions arrive.

## Open Zally-derived rule issues (outstanding work)

All are "Add this Zally-derived capability" gaps in the Generic API Policy catalogue.

| Issue | Title | Zally ID |
|-------|-------|----------|
| ~~#33~~ | ~~Add API metadata completeness rule~~ | 218 / 116 — **done: DOC006, closed** |
| ~~#34~~ | ~~Add API identifier rule~~ | 215 — **done: DOC007, closed** |
| ~~#35~~ | ~~Add API audience rule~~ | 219 — **done: DOC008, closed** |
| #36 | Add functional server hostname rule | 224 |
| #37 | Add trailing slash path rule | 136 |
| #38 | Add Link header avoidance rule | 166 |
| #39 | Add resource count limit rule | 146 |
| #40 | Add sub-resource depth limit rule | 147 |
| #41 | Add nested resource root rule | 145 |
| #42 | Add path-segment resource identification rule | 143 |
| #43 | Add common field type convention rule | 174 |
| #44 | Add date-time property suffix rule | 235 |
| #45 | Add numeric format rule | 171 |
| #46 | Add enum value type consistency rule | 125 |
| ~~#47~~ | ~~Add extensible enum rule~~ | 107 — **done: JSON014, closed** |
| ~~#48~~ | ~~Add enum casing rule~~ | 240 — **done: JSON015, closed** |
| ~~#49~~ | ~~Add JSON object success response rule~~ | 110 — **done: JSON016, closed** |
| ~~#50~~ | ~~Add default Problem Details response rule~~ | 176 / 151 — **done: STATUS006, closed** |
| ~~#51~~ | ~~Add proprietary header rule~~ | 183 — **done: STANDARD008, closed** |
| ~~#52~~ | ~~Add query collection serialization rule~~ | 154 — **done: STANDARD009, closed** |
| ~~#53~~ | ~~Add endpoint security coverage rule~~ | 104 — **done: SECURITY001, closed** |
| ~~#54~~ | ~~Add endpoint security scope rule~~ | 105 — **done: SECURITY002, closed** |
| ~~#55~~ | ~~Add rate-limit response header rule~~ | 153 — **done: STATUS007, closed** |
| ~~#56~~ | ~~Add OpenAPI version conformance rule~~ | 101 — **done: STANDARD010, closed** |

## Zalando rules with no Speculate match (after fuzzy pass)

No currently catalogued Zalando rule remains without a Speculate rule or
policy-opt-in capability after the implementations above. The mappings are
fuzzy descriptions rather than claims of byte-for-byte Zally equivalence.

Media-type coverage is provided by the policy-opt-in CONTENT001–CONTENT004
rules from #24.

## Speculate rules with no Zally equivalent

These go beyond the Zalando catalogue: BULK001–003 (bulk operations),
CASE002 (path parameter case), DOC001–005/DOC009 (operation summary style),
HTTP001–006/HTTP008 (method semantics), JSON006 (null vs. absent),
REST005/REST006 (Request/Response schema suffixes), UPDATE001–002 (PUT/PATCH
partial update), VERSION002–004 (header/media-type/unversioned).
