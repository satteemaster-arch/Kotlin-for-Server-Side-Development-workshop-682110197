# Final Project — Ten REST APIs in One Ktor Server

Kotlin + Ktor + Exposed + H2, all ten briefs implemented in a single Gradle project.
Each API lives under its own route prefix so nothing collides.

## Run

```bash
./gradlew run          # server on http://localhost:8080
./gradlew test         # 120 tests
```

On this machine `JAVA_HOME` must point at a JDK 21 first, otherwise Gradle picks
JDK 24 and fails:

```bash
JAVA_HOME="C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.1.3/jbr" ./gradlew test
```

## Stack

| | |
|---|---|
| Kotlin | 2.4.10 |
| Ktor | 3.5.2 (Netty) |
| Exposed | 1.4.0 (`exposed-core`, `exposed-jdbc`) |
| Database | H2 2.4.240, in-memory (`DB_CLOSE_DELAY=-1`) |
| JSON | kotlinx.serialization |

Data is held in memory, so it resets every time the server restarts.

## Status codes

| Code | Meaning here |
|---|---|
| `200` | read or update succeeded |
| `201` | resource created |
| `204` | deleted, no body |
| `302` | short-code redirect |
| `400` | malformed JSON, non-numeric path id, or a value that makes no sense (`rating must be between 1 and 5`) |
| `404` | no such resource |
| `409` | request is well-formed but the current state forbids it (`book is already lent out`) |

The 400 / 409 split is deliberate: `BusinessRuleException.Kind` decides which one,
so "nonsense input" and "impossible right now" never share a status code.

## Endpoints

### 0. Personal Blog — `/blog`
| Method | Path |
|---|---|
| GET | `/blog/posts` |
| GET | `/blog/posts/{id}` |
| GET | `/blog/posts/{id}/full` — post with all its comments nested |
| POST | `/blog/posts` |
| PUT | `/blog/posts/{id}` |
| DELETE | `/blog/posts/{id}` |
| GET, POST | `/blog/posts/{id}/comments` |
| PUT, DELETE | `/blog/comments/{id}` |

`createdAt` never moves on update; only `updatedAt` does. Deleting a post cascades to its comments.

### 1. Inventory — `/inventory`
| Method | Path |
|---|---|
| CRUD | `/inventory/categories`, `/inventory/categories/{id}` |
| CRUD | `/inventory/products`, `/inventory/products/{id}` |
| POST | `/inventory/products/{id}/add-stock` — body `{"amount": 5}`, negative removes |

Stock can reach 0 but never below. The read-check-write runs inside one transaction.

### 2. URL Shortener
| Method | Path |
|---|---|
| POST | `/shorten` — body `{"longUrl": "https://..."}` |
| GET | `/s/{shortCode}` — **302** redirect, counts the click |
| GET | `/s/{shortCode}/stats` |
| DELETE | `/s/{shortCode}` |

The brief asks for `GET /{shortCode}` at the root. In a server shared with nine
other APIs a root wildcard would swallow their routes, so the redirect lives
under `/s/` instead. Codes are 7 characters, unique-indexed, and generation
retries on collision.

### 3. Recipe Book — `/recipes`
| Method | Path |
|---|---|
| CRUD | `/recipes`, `/recipes/{id}` |
| GET | `/recipes/{id}/full` — recipe with ingredients nested |
| GET | `/recipes/search?ingredient=chicken` — case-insensitive, partial match |
| GET, POST | `/recipes/{id}/ingredients` |
| PUT, DELETE | `/recipes/ingredients/{ingredientId}` |

`/search` is declared before `/{id}` so it is never parsed as an id.

### 4. Appointment Booking — `/booking`
| Method | Path |
|---|---|
| CRUD | `/booking/services`, `/booking/services/{id}` |
| CRUD | `/booking/appointments`, `/booking/appointments/{id}` |

Double booking is blocked per service: a new slot clashes when
`newStart < existingEnd && existingStart < newEnd`. Ranges that merely touch are
fine — a 10:00–11:00 booking leaves 11:00 free. Updating an appointment does not
clash with itself.

### 5. Expense Tracker — `/expenses`
| Method | Path |
|---|---|
| CRUD | `/expenses/categories`, `/expenses/categories/{id}` |
| CRUD | `/expenses/transactions`, `/expenses/transactions/{id}` |
| GET | `/expenses/reports/monthly?year=2026&month=8` |

The report uses `SUM` + `GROUP BY` in the database, not Kotlin-side folding.
`amount` is always positive; the sign lives in `type` (`INCOME` / `EXPENSE`).

### 6. Movie Library — `/movies`
| Method | Path |
|---|---|
| CRUD | `/movies`, `/movies/{id}` |
| GET | `/movies/{id}` — includes `averageRating` and `reviewCount` |
| GET | `/movies/search?title=...&director=...` |
| GET, POST | `/movies/{id}/reviews` |
| PUT, DELETE | `/movies/reviews/{reviewId}` |

`averageRating` is `null`, not `0.0`, when there are no reviews: "nobody rated it"
and "everyone rated it zero" are different facts. Ratings outside 1–5 are rejected.

### 7. Issue Tracker — `/issues`
| Method | Path |
|---|---|
| CRUD | `/issues`, `/issues/{id}` |
| GET | `/issues?status=OPEN&priority=HIGH` |
| PUT | `/issues/{id}/status` — body `{"status": "CLOSED"}` |

`status` and `priority` are Kotlin enums stored by **name**, so a renamed constant
fails loudly instead of silently shifting an ordinal. A `CLOSED` issue cannot be
reopened through the status endpoint.

### 8. Poll / Survey — `/polls`
| Method | Path |
|---|---|
| CRUD | `/polls`, `/polls/{id}` |
| GET | `/polls/{id}` — question, options, tallies and percentages |
| GET, POST | `/polls/{id}/options` |
| PUT, DELETE | `/polls/options/{optionId}` |
| POST | `/polls/options/{optionId}/vote` |

`voteCount` is never set by a client — only the vote action moves it. A poll with
zero votes reports 0% instead of dividing by zero.

### 9. Book Lending — `/library`
| Method | Path |
|---|---|
| CRUD | `/library/books`, `/library/books/{id}` |
| GET | `/library/books?available=true` |
| GET | `/library/books/{id}/history` |
| POST | `/library/books/{id}/checkout` — body `{"borrowerName": "..."}` |
| GET | `/library/lendings`, `/library/lendings/{id}` |
| POST | `/library/lendings/{id}/return` |

Checkout writes the lending record and flips `isAvailable` inside one transaction,
so the database can never hold an open loan for a book that still claims to be on
the shelf. Editing a book never touches that flag.

## Tests

120 tests, business logic and repository first, endpoints on top.

| Class | Tests | Focus |
|---|---|---|
| `BlogRepositoryTest` / `BlogApiTest` | 8 / 9 | one-to-many, cascade, timestamps |
| `InventoryTest` | 8 | stock never negative |
| `ShortenerTest` | 7 | unique codes, click counting |
| `RecipeTest` | 8 | ingredient search |
| `BookingTest` | 11 | overlap rules incl. touching ranges |
| `ExpenseTest` | 9 | SUM + GROUP BY report |
| `MovieTest` | 11 | null average, rating range |
| `IssueTest` | 10 | enum round-trip, state transitions |
| `PollTest` | 10 | vote increment, percentages |
| `LibraryTest` | 11 | checkout/return state pairs |
| `WorkshopTest` / `TaskApiTest` | 10 / 8 | Workshops 1–4, still green |

## Known limits

- **In-memory only.** Restarting the server empties every table.
- **Not thread-safe under load.** Reads and writes sit in one transaction each,
  but H2 in-memory with no connection pool is not built for concurrent traffic.
- **Dates are ISO-8601 strings**, not SQL date columns. They sort and compare
  correctly as text, and the monthly report matches on a `2026-08%` prefix.
- **No authentication.** None of the briefs asked for it.
