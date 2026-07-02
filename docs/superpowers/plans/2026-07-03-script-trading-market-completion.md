# Script Trading Market Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mock script market with a tenant-safe, recoverable transaction system covering listing review, free/normal/exclusive/buyout licenses, personal and enterprise wallet payment, entitlement delivery, settlement, and controlled refunds.

**Architecture:** Spring Boot on 8080 owns listing, order, entitlement, delivery, refund, and audit state. Go on 3001 owns personal/workspace wallets, immutable double-entry ledger rows, recharge, atomic purchase transfers, release, and reversal; the services coordinate through signed idempotent APIs, order-number lookup, and an 8080 outbox/reconciliation worker. Vue exposes separate market, listing, checkout, purchase, seller, enterprise approval, and admin exception views.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, H2/MySQL, Go 1.25, Gin, GORM, SQLite/MySQL/PostgreSQL tests, Vue 3, Vue Router, Element Plus, Node built-in test runner.

---

## Execution prerequisites

- The current workspace is dirty with unrelated content-project and generated-static changes. Before Task 1, use `superpowers:using-git-worktrees` and create an isolated worktree from the branch that contains all prerequisite account/workspace changes.
- Confirm that migration `V5__creative_bible_foundation.sql` has been committed on the execution base. This plan reserves `V6__script_trade_market.sql`; if the base branch has acquired a higher migration, rename only the migration number before implementation and keep its content unchanged.
- Do not commit `aicp-backend/src/main/resources/static/assets/**`. Build artifacts are deployment output, not source for this feature.
- Read the approved design before each phase: `docs/superpowers/specs/2026-07-02-script-trading-market-completion-design.md`.
- Money is always `long` cents in Java and `int64` cents in Go. Currency is `CNY`. Never use `double`/`float64` for ledger arithmetic.

## File responsibility map

### 8080 backend

- `aicp-backend/src/main/resources/db/migration/V6__script_trade_market.sql`: additive production migration for trade tables and constraints.
- `aicp-backend/src/main/resources/db/schema-h2.sql`, `schema-mysql.sql`, `schema.sql`: fresh-install equivalents of V6.
- `aicp-backend/src/main/java/com/aicp/module/trade/domain/TradeEnums.java`: all listing, license, order, entitlement, refund, and outbox enums.
- `aicp-backend/src/main/java/com/aicp/module/trade/entity/*`: persistence-only entities, one table per class.
- `aicp-backend/src/main/java/com/aicp/module/trade/mapper/*Mapper.java`: MyBatis-Plus mappers and conditional inventory updates.
- `aicp-backend/src/main/java/com/aicp/module/trade/dto/TradeRequests.java`: validated inbound request records.
- `aicp-backend/src/main/java/com/aicp/module/trade/dto/TradeViews.java`: stable public response records.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/ListingService.java`: seller draft, submit, review, publish, unlist, and public snapshot operations.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/MarketQueryService.java`: public search, detail, and preview only.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`: idempotent order creation, inventory reservation, payment state, and cancellation.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/DeliveryService.java`: entitlement and purchased-copy creation.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/PurchaseApprovalService.java`: enterprise request and approval state.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/RefundService.java`: refund eligibility and entitlement revocation.
- `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryService.java`: outbox dispatch, unknown-payment lookup, delivery retry, release, and reconciliation.
- `aicp-backend/src/main/java/com/aicp/module/trade/wallet/WalletClient.java`: 3001 port.
- `aicp-backend/src/main/java/com/aicp/module/trade/wallet/HttpWalletClient.java`: signed HTTP adapter.
- `aicp-backend/src/main/java/com/aicp/module/trade/controller/*`: thin market, seller, order, enterprise, refund, and admin controllers.

### 3001 wallet

- `new-api/model/wallet.go`: wallet accounts, transfers, immutable ledger entries, idempotency records, and transactional operations.
- `new-api/controller/aicp_wallet.go`: internal wallet request validation and responses.
- `new-api/middleware/aicp_service_auth.go`: HMAC service authentication with timestamp replay protection.
- `new-api/router/api-router.go`: service-authenticated wallet routes, separate from user JWT routes.
- `new-api/model/main.go`: wallet model migration registration.
- `new-api/controller/topup*.go` and `new-api/model/topup.go`: project successful recharge into the personal wallet while preserving legacy quota compatibility.

### Frontend

- `aicp-frontend/src/api/trade.js`: complete trade API only; no view state.
- `aicp-frontend/src/views/trade/tradeState.js`: pure status, price, route, and permission helpers.
- `aicp-frontend/src/views/trade/useTradeMarket.js`: market query and pagination state.
- `aicp-frontend/src/views/Market.vue`: market page shell.
- `aicp-frontend/src/views/trade/MarketFilterBar.vue`, `ScriptListingCard.vue`: reusable market components.
- `aicp-frontend/src/views/trade/ScriptMarketDetail.vue`: detail, preview, license disclosure, and checkout entry.
- `aicp-frontend/src/views/trade/TradeCheckout.vue`: buyer subject, wallet balance, agreement, pay and top-up entry.
- `aicp-frontend/src/views/trade/TradeResult.vue`: terminal and recoverable payment states.
- `aicp-frontend/src/views/trade/MyPurchases.vue`, `SellerTradeCenter.vue`, `ListingEditor.vue`, `EnterprisePurchaseCenter.vue`, `TradeAdminCenter.vue`: role-specific workflows.
- `aicp-frontend/src/router/index.js`: dedicated trade routes.

## Phase 1 — Persisted market and free delivery

### Task 1: Define trade contracts and schema

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/domain/TradeEnums.java`
- Create: `aicp-backend/src/main/resources/db/migration/V6__script_trade_market.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-h2.sql`
- Modify: `aicp-backend/src/main/resources/db/schema-mysql.sql`
- Modify: `aicp-backend/src/main/resources/db/schema.sql`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/schema/TradeMarketSchemaTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/domain/TradeEnumsTest.java`

- [ ] **Step 1: Write failing enum and schema tests**

```java
class TradeEnumsTest {
    @Test void exposesApprovedStates() {
        assertThat(TradeEnums.LicenseType.values())
                .extracting(Enum::name)
                .containsExactly("FREE", "NORMAL", "EXCLUSIVE", "BUYOUT");
        assertThat(TradeEnums.OrderStatus.PAYMENT_UNKNOWN.canTransitionTo(
                TradeEnums.OrderStatus.PAID_PENDING_DELIVERY)).isTrue();
        assertThat(TradeEnums.OrderStatus.FULFILLED.canTransitionTo(
                TradeEnums.OrderStatus.PENDING_PAYMENT)).isFalse();
    }
}
```

```java
@SpringBootTest
class TradeMarketSchemaTest {
    @Autowired DataSource dataSource;

    @Test void createsTradeTablesAndUniqueGuards() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(tableExists(c, "TRADE_ORDERS")).isTrue();
            assertThat(tableExists(c, "SCRIPT_ENTITLEMENTS")).isTrue();
            assertThat(uniqueColumns(c, "TRADE_ORDERS"))
                    .contains(List.of("BUYER_WORKSPACE_ID", "CREATE_IDEMPOTENCY_KEY"));
            assertThat(uniqueColumns(c, "SCRIPT_ENTITLEMENTS"))
                    .contains(List.of("ORDER_ITEM_ID"));
        }
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd aicp-backend && mvn -Dtest=TradeEnumsTest,TradeMarketSchemaTest test`

Expected: FAIL because `TradeEnums` and the new tables do not exist.

- [ ] **Step 3: Implement the enums with explicit transitions**

```java
public final class TradeEnums {
    private TradeEnums() {}
    public enum ListingStatus { DRAFT, UNDER_REVIEW, REJECTED, LISTED, EXCLUSIVE_RESERVED, EXCLUSIVE_SOLD, UNLISTED }
    public enum LicenseType { FREE, NORMAL, EXCLUSIVE, BUYOUT;
        public boolean isExclusive() { return this == EXCLUSIVE || this == BUYOUT; }
    }
    public enum OrderStatus {
        PENDING_APPROVAL, REJECTED, PENDING_PAYMENT, PAYING, PAYMENT_FAILED,
        PAYMENT_UNKNOWN, PAID_PENDING_DELIVERY, COMPENSATING, FULFILLED,
        REFUND_REQUESTED, REFUND_PROCESSING, REFUND_REJECTED, REFUNDED,
        CANCELLED, EXPIRED;
        private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.ofEntries(
            Map.entry(PENDING_APPROVAL, Set.of(REJECTED, PENDING_PAYMENT, CANCELLED)),
            Map.entry(PENDING_PAYMENT, Set.of(PAYING, CANCELLED, EXPIRED)),
            Map.entry(PAYING, Set.of(PAYMENT_FAILED, PAYMENT_UNKNOWN, PAID_PENDING_DELIVERY)),
            Map.entry(PAYMENT_UNKNOWN, Set.of(PAYMENT_FAILED, PAID_PENDING_DELIVERY)),
            Map.entry(PAID_PENDING_DELIVERY, Set.of(COMPENSATING, FULFILLED, REFUNDED)),
            Map.entry(COMPENSATING, Set.of(FULFILLED, REFUNDED)),
            Map.entry(FULFILLED, Set.of(REFUND_REQUESTED)),
            Map.entry(REFUND_REQUESTED, Set.of(REFUND_PROCESSING, REFUND_REJECTED)),
            Map.entry(REFUND_PROCESSING, Set.of(REFUNDED)),
            Map.entry(REFUND_REJECTED, Set.of(FULFILLED))
        );
        public boolean canTransitionTo(OrderStatus next) {
            return NEXT.getOrDefault(this, Set.of()).contains(next);
        }
    }
    public enum EntitlementStatus { ACTIVE, REFUND_LOCKED, REVOKED }
    public enum PurchaseRequestStatus { PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED }
    public enum RefundStatus { REQUESTED, PROCESSING, APPROVED, REJECTED, REFUNDED }
    public enum OutboxStatus { PENDING, PROCESSING, SUCCEEDED, FAILED }
}
```

- [ ] **Step 4: Add the additive schema**

Create tables `script_listings`, `listing_license_options`, `trade_orders`, `trade_order_items`, `script_entitlements`, `purchased_script_copies`, `purchase_requests`, `refund_requests`, `trade_outbox_events`, and `trade_audit_logs` exactly as Section 9 of the design specifies. Use `BIGINT` cents, `VARCHAR(3) DEFAULT 'CNY'`, `VARCHAR(64)` workspace IDs, JSON-as-text columns compatible with H2/MySQL, and optimistic `row_version INT NOT NULL DEFAULT 0`.

Required database guards:

```sql
UNIQUE (buyer_workspace_id, create_idempotency_key),
UNIQUE (order_no),
UNIQUE (order_id), -- trade_order_items: one item in phase 1
UNIQUE (order_item_id), -- script_entitlements
UNIQUE (order_item_id), -- purchased_script_copies
UNIQUE (aggregate_type, aggregate_id, event_type, idempotency_key)
```

Add indexes for listing status/type/update time, order buyer/seller/status/update time, outbox status/next-attempt time, and refund status/update time. Mirror the migration into all three fresh-install schema files without dropping existing rows.

- [ ] **Step 5: Re-run focused tests**

Run: `cd aicp-backend && mvn -Dtest=TradeEnumsTest,TradeMarketSchemaTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade/domain \
  aicp-backend/src/main/resources/db/migration/V6__script_trade_market.sql \
  aicp-backend/src/main/resources/db/schema-h2.sql \
  aicp-backend/src/main/resources/db/schema-mysql.sql \
  aicp-backend/src/main/resources/db/schema.sql \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): define marketplace schema and states"
```

### Task 2: Implement persisted listing review and public market queries

**Files:**
- Replace: `aicp-backend/src/main/java/com/aicp/module/trade/entity/ScriptListing.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/ListingLicenseOption.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/ScriptListingMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/ListingLicenseOptionMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/dto/TradeRequests.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/dto/TradeViews.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/ListingService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/MarketQueryService.java`
- Replace: `aicp-backend/src/main/java/com/aicp/module/trade/controller/TradeController.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/SellerListingController.java`
- Delete: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/ListingServiceTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/MarketQueryServiceTest.java`

- [ ] **Step 1: Write failing service tests**

```java
@Test void unapprovedListingNeverAppearsInPublicSearch() {
    long id = listingService.createDraft(sellerContext(), validDraft()).id();
    assertThat(marketQueryService.search(new MarketQuery(null, null, null, 1, 20))).isEmpty();
    listingService.submit(sellerContext(), id);
    listingService.approve(adminContext(), id, "内容与标签通过");
    assertThat(marketQueryService.search(new MarketQuery("替身", "言情", "latest", 1, 20)))
            .extracting(ListingCard::id).containsExactly(id);
}

@Test void previewReturnsOnlyApprovedSnapshotEpisodes() {
    ListingDetail detail = marketQueryService.detail(listedId);
    PreviewView preview = marketQueryService.preview(listedId);
    assertThat(preview.episodes()).hasSize(detail.previewEpisodeCount());
    assertThat(preview.episodes()).allMatch(e -> e.number() <= 3);
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd aicp-backend && mvn -Dtest=ListingServiceTest,MarketQueryServiceTest test`

Expected: FAIL because persisted listing services do not exist.

- [ ] **Step 3: Implement validated request and view records**

`TradeRequests` must define `CreateListing`, `UpdateListing`, `ReviewDecision`, and `MarketQuery`. Enforce title 1–120 chars, description 1–5000 chars, preview episodes 1–3, unique license types, nonnegative cents, and positive exclusive/buyout prices. `TradeViews` must define `ListingCard`, `ListingDetail`, `PreviewView`, `LicenseOptionView`, and `PageView<T>`; never expose the seller's private script row ID as a cross-tenant access path.

- [ ] **Step 4: Implement listing state and query services**

Use trusted `WorkspaceContext` for all seller mutations. On submit, copy title, synopsis, four-axis tags, characters, approved preview episodes, author display name, cover, episode count, and content hash into `public_snapshot`. `MarketQueryService` must query only `LISTED` and `EXCLUSIVE_RESERVED`, allow-list sort keys `latest`, `popular`, `sales`, `rating`, and escape keyword wildcards.

Keep `TradeController` only for public `/market/listings` reads. Put authenticated seller writes under `SellerListingController`. Delete every fixed `Map.of(...)` response and delete the unused legacy `TradeService` so no caller can accidentally bypass the new state model.

- [ ] **Step 5: Run tests and compile**

Run: `cd aicp-backend && mvn -Dtest=ListingServiceTest,MarketQueryServiceTest test`

Expected: PASS.

Run: `cd aicp-backend && mvn -DskipTests test-compile`

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): persist listing review and market queries"
```

### Task 3: Implement free orders, entitlement delivery, and warehouse copies

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/TradeOrder.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/TradeOrderItem.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/ScriptEntitlement.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/PurchasedScriptCopy.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/TradeAuditLog.java`
- Create: matching mapper files under `aicp-backend/src/main/java/com/aicp/module/trade/mapper/`
- Delete: `aicp-backend/src/main/java/com/aicp/module/trade/entity/Order.java`
- Delete: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/OrderMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/DeliveryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/OrderController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/FreeOrderDeliveryTest.java`

- [ ] **Step 1: Write a failing free-delivery idempotency test**

```java
@Test void freeClaimCreatesOneOrderEntitlementAndCopy() {
    CreateOrder request = new CreateOrder(listingId, "FREE", "claim-001");
    OrderView first = orderService.createAndFulfillFree(buyerContext(), request);
    OrderView retry = orderService.createAndFulfillFree(buyerContext(), request);

    assertThat(retry.orderNo()).isEqualTo(first.orderNo());
    assertThat(first.status()).isEqualTo("FULFILLED");
    assertThat(entitlementMapper.countByOrder(first.orderNo())).isEqualTo(1);
    assertThat(copyMapper.countByOrder(first.orderNo())).isEqualTo(1);
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=FreeOrderDeliveryTest test`

Expected: FAIL because order and delivery services do not exist.

- [ ] **Step 3: Implement order snapshot creation**

Within one transaction, look up the enabled license option from the database, ignore client price/seller fields, create `trade_orders` with buyer identity from `WorkspaceContext`, create one `trade_order_item` containing the listing/version/price/agreement snapshots, and append `ORDER_CREATED` audit. Resolve `(buyer_workspace_id, create_idempotency_key)` first and return the existing order on exact retry.

- [ ] **Step 4: Implement idempotent delivery**

`DeliveryService.deliver(orderNo)` must lock the order, require `PAID_PENDING_DELIVERY`, insert one `script_entitlements` row and one `purchased_script_copies` row using their unique order-item constraints, append `ENTITLEMENT_GRANTED` and `COPY_CREATED` audit rows, then set `FULFILLED`. For zero-price orders, `OrderService.createAndFulfillFree` transitions directly from `PENDING_PAYMENT` to `PAID_PENDING_DELIVERY` and invokes delivery in the same application command.

- [ ] **Step 5: Protect trade workspace routes**

Add `/api/v1/trade/orders/**`, `/api/v1/trade/entitlements/**`, `/api/v1/trade/seller/**`, `/api/v1/trade/purchase-requests/**`, and `/api/v1/trade/refund-requests/**` to `WorkspaceContextFilter.PROTECTED_PATTERNS`. Public GET listing/detail/preview routes remain accessible without Workspace context.

- [ ] **Step 6: Run tests**

Run: `cd aicp-backend && mvn -Dtest=FreeOrderDeliveryTest,WorkspaceAccessServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/main/java/com/aicp/common/workspace/WorkspaceContextFilter.java \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): deliver free script licenses"
```

### Task 4: Replace the mock market UI and add buyer free-flow pages

**Files:**
- Replace: `aicp-frontend/src/api/trade.js`
- Create: `aicp-frontend/src/views/trade/tradeState.js`
- Create: `aicp-frontend/src/views/trade/useTradeMarket.js`
- Replace: `aicp-frontend/src/views/Market.vue`
- Create: `aicp-frontend/src/views/trade/MarketFilterBar.vue`
- Create: `aicp-frontend/src/views/trade/ScriptListingCard.vue`
- Create: `aicp-frontend/src/views/trade/ScriptMarketDetail.vue`
- Create: `aicp-frontend/src/views/trade/TradeCheckout.vue`
- Create: `aicp-frontend/src/views/trade/TradeResult.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Create: `aicp-frontend/tests/trade-state.test.js`
- Modify: `aicp-frontend/tests/navigation-contract.test.js`

- [ ] **Step 1: Write failing pure-state and route tests**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { formatCents, orderRoute, canRetryPayment } from '../src/views/trade/tradeState.js'

test('trade helpers preserve integer cents and recoverable states', () => {
  assert.equal(formatCents(2990, 'CNY'), '¥29.90')
  assert.equal(orderRoute('ORD-1', 'FULFILLED'), '/trade/orders/ORD-1/result')
  assert.equal(canRetryPayment('PAYMENT_UNKNOWN'), false)
  assert.equal(canRetryPayment('PAYMENT_FAILED'), true)
})
```

Add route assertions for `/market/:listingId`, `/trade/checkout/:listingId`, and `/trade/orders/:orderNo/result`.

- [ ] **Step 2: Run and verify failure**

Run: `cd aicp-frontend && node --test tests/trade-state.test.js tests/navigation-contract.test.js`

Expected: FAIL because helpers and routes do not exist.

- [ ] **Step 3: Implement the API and pure state module**

`tradeApi` must expose `searchListings`, `getListing`, `getPreview`, `createOrder`, `getOrder`, `cancelOrder`, `payOrder`, `getEntitlements`, and `getPurchases`. Use only `/trade/...` paths because `request.js` already prefixes `/api/v1`. `tradeState.js` must format integer cents, map every order status to label/severity, block duplicate pay in `PAYING`/`PAYMENT_UNKNOWN`, and build routes without importing Vue.

- [ ] **Step 4: Build market/detail/checkout/result views**

Use URL query parameters for keyword, four axes, episode count, price, license, sort, page, and size. Render loading, empty, error, and populated states. Detail must show historical normal-license count and agreement summary. Checkout must derive amount from the server response, display active Workspace, require agreement acceptance, and for `FREE` create the order then route to the result page. Result must poll only `PAYING`, `PAYMENT_UNKNOWN`, `PAID_PENDING_DELIVERY`, and `COMPENSATING`, stopping on terminal states.

- [ ] **Step 5: Run tests and build**

Run: `cd aicp-frontend && node --test tests/trade-state.test.js tests/navigation-contract.test.js`

Expected: PASS.

Run: `cd aicp-frontend && npm run build`

Expected: Vite build completes without errors.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/api/trade.js aicp-frontend/src/views/Market.vue \
  aicp-frontend/src/views/trade aicp-frontend/src/router/index.js \
  aicp-frontend/tests/trade-state.test.js aicp-frontend/tests/navigation-contract.test.js
git commit -m "feat(trade): add persisted market buyer flow"
```

## Phase 2 — Unified 3001 wallet and paid licenses

### Task 5: Add service-authenticated 3001 wallet contracts

**Files:**
- Create: `new-api/middleware/aicp_service_auth.go`
- Create: `new-api/middleware/aicp_service_auth_test.go`
- Create: `new-api/controller/aicp_wallet.go`
- Create: `new-api/controller/aicp_wallet_contract_test.go`
- Modify: `new-api/router/api-router.go`

- [ ] **Step 1: Write failing HMAC and route contract tests**

```go
func TestAicpServiceAuthAcceptsCanonicalSignature(t *testing.T) {
    body := []byte(`{"business_order_no":"ORD-1","amount_cents":2990}`)
    req := signedRequest(http.MethodPost, "/api/aicp/internal/wallet-transfers/purchase", body, "idem-1", fixedTime)
    response := performServiceRequest(req)
    require.NotEqual(t, http.StatusUnauthorized, response.Code)
}

func TestAicpServiceAuthRejectsExpiredTimestamp(t *testing.T) {
    req := signedRequest(http.MethodPost, walletPath, validBody, "idem-1", time.Now().Add(-6*time.Minute))
    response := performServiceRequest(req)
    require.Equal(t, http.StatusUnauthorized, response.Code)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./middleware ./controller -run 'TestAicpServiceAuth|TestAicpWalletContract'`

Expected: FAIL because service auth and wallet handlers do not exist.

- [ ] **Step 3: Implement canonical service authentication**

Canonical string:

```text
METHOD\nPATH\nTIMESTAMP\nIDEMPOTENCY_KEY\nSHA256_HEX(BODY)
```

Verify `X-AICP-Service`, `X-AICP-Timestamp`, `X-AICP-Signature`, and `Idempotency-Key` against an environment/config secret using constant-time HMAC-SHA256 comparison. Accept timestamps within 300 seconds. Restore the request body after hashing. Store the verified service name in Gin context.

- [ ] **Step 4: Register isolated internal routes**

Create group `/api/aicp/internal` guarded only by `AicpServiceAuth`; do not use browser `UserAuth` for these routes. Register wallet balance, precheck, purchase, order lookup, release, and reverse endpoints. Controllers bind typed structs with `int64 AmountCents`, `string Currency`, owner types `USER|WORKSPACE|PLATFORM`, and return a stable `{success,data,message}` envelope.

- [ ] **Step 5: Run tests and commit**

Run: `cd new-api && go test ./middleware ./controller -run 'TestAicpServiceAuth|TestAicpWalletContract'`

Expected: PASS.

```bash
git add new-api/middleware/aicp_service_auth.go new-api/middleware/aicp_service_auth_test.go \
  new-api/controller/aicp_wallet.go new-api/controller/aicp_wallet_contract_test.go \
  new-api/router/api-router.go
git commit -m "feat(wallet): secure internal wallet contracts"
```

### Task 6: Implement double-entry wallets and idempotent purchase transfer

**Files:**
- Create: `new-api/model/wallet.go`
- Create: `new-api/model/wallet_test.go`
- Modify: `new-api/model/main.go`
- Modify: `new-api/controller/aicp_wallet.go`

- [ ] **Step 1: Write failing wallet invariants**

```go
func TestPurchaseTransferIsBalancedAndIdempotent(t *testing.T) {
    seedWallet(t, "USER", "9", 10000, 0)
    seedWallet(t, "USER", "7", 0, 0)
    req := PurchaseTransferRequest{BusinessOrderNo: "ORD-1", Buyer: Owner{"USER", "9"},
        Seller: Owner{"USER", "7"}, AmountCents: 2990, PlatformFeeCents: 299, Currency: "CNY", IdempotencyKey: "trade:ORD-1"}
    first, err := ExecutePurchaseTransfer(req)
    require.NoError(t, err)
    retry, err := ExecutePurchaseTransfer(req)
    require.NoError(t, err)
    require.Equal(t, first.TransferNo, retry.TransferNo)
    require.Equal(t, int64(0), ledgerDebitTotal(t, first.TransferNo)-ledgerCreditTotal(t, first.TransferNo))
    require.Equal(t, int64(7010), available(t, "USER", "9"))
    require.Equal(t, int64(2691), frozen(t, "USER", "7"))
}

func TestPurchaseTransferRejectsSameKeyWithDifferentAmount(t *testing.T) {
    _, _ = ExecutePurchaseTransfer(baseRequest(2990))
    _, err := ExecutePurchaseTransfer(baseRequest(3990))
    require.ErrorIs(t, err, ErrIdempotencyConflict)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./model -run 'TestPurchaseTransfer'`

Expected: FAIL because wallet models do not exist.

- [ ] **Step 3: Implement wallet models and migration registration**

Define `WalletAccount`, `WalletTransfer`, `WalletLedgerEntry`, and `WalletIdempotencyRecord`. Register them in both primary and secondary `AutoMigrate` lists in `model/main.go`. Create unique indexes for `(owner_type,owner_id,currency)`, `(business_type,business_order_no,transfer_type)`, and `(caller,idempotency_key)`.

- [ ] **Step 4: Implement atomic purchase transfer**

In one GORM transaction: lock/create idempotency, hash normalized request JSON, lock buyer/seller/platform accounts, reject insufficient available balance, create one transfer, append balanced immutable entries, decrement buyer available, increment seller frozen, increment platform available, and store the response snapshot. Seller frozen amount equals `amount_cents - platform_fee_cents`. Reject negative components, fee greater than amount, non-CNY currency, identical buyer/seller owner, disabled wallet, and unsupported owner type.

- [ ] **Step 5: Wire controllers and run package tests**

Run: `cd new-api && go test ./model ./controller -run 'TestPurchaseTransfer|TestAicpWallet'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add new-api/model/wallet.go new-api/model/wallet_test.go new-api/model/main.go \
  new-api/controller/aicp_wallet.go
git commit -m "feat(wallet): add idempotent purchase ledger"
```

### Task 7: Project recharge into the unified personal wallet

**Files:**
- Modify: `new-api/model/topup.go`
- Modify: `new-api/controller/topup.go`
- Modify: all successful callback implementations under `new-api/controller/topup_*.go`
- Create: `new-api/model/topup_wallet_test.go`

- [ ] **Step 1: Write a failing recharge projection test**

```go
func TestCompleteTopUpCreditsWalletAndLegacyQuotaOnce(t *testing.T) {
    topup := seedPendingTopup(t, 9, "PAY-1", 10000)
    require.NoError(t, CompleteTopUpToWallet(topup.TradeNo, PaymentProviderEpay))
    require.NoError(t, CompleteTopUpToWallet(topup.TradeNo, PaymentProviderEpay))
    require.Equal(t, int64(10000), available(t, "USER", "9"))
    require.Equal(t, expectedLegacyQuota(10000), userQuota(t, 9))
    require.Equal(t, int64(1), countWalletTransfers(t, "TOPUP", topup.TradeNo))
}
```

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./model -run TestCompleteTopUpCreditsWalletAndLegacyQuotaOnce`

Expected: FAIL because top-ups do not project into wallets.

- [ ] **Step 3: Centralize successful top-up completion**

Implement `CompleteTopUpToWallet(tradeNo, expectedProvider)` as the only completion path. In one database transaction, lock pending top-up, validate provider, write a `TOPUP` wallet transfer and balanced platform-clearing-to-user entries, increment personal wallet available cents, update legacy quota projection using the existing conversion formula, and mark top-up successful. A completed top-up returns success without a second credit.

- [ ] **Step 4: Route every provider callback through the common function**

Replace direct quota mutations in EPay, Stripe, Creem, Waffo, Waffo Pancake, and admin completion with the common completion method. Preserve provider signature validation and existing response bodies.

- [ ] **Step 5: Run payment regression tests**

Run: `cd new-api && go test ./model ./controller -run 'TopUp|Topup|Payment'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add new-api/model/topup.go new-api/model/topup_wallet_test.go new-api/controller/topup*.go
git commit -m "feat(wallet): credit unified wallet on recharge"
```

### Task 8: Implement release, reversal, and wallet authorization

**Files:**
- Modify: `new-api/model/wallet.go`
- Modify: `new-api/controller/aicp_wallet.go`
- Modify: `new-api/controller/aicp_workspace.go`
- Modify: `new-api/model/aicp_workspace.go`
- Create: `new-api/model/wallet_reversal_test.go`
- Create: `new-api/controller/aicp_wallet_authorization_test.go`

- [ ] **Step 1: Write failing release/reversal tests**

```go
func TestReleaseMovesSellerFrozenToAvailableOnce(t *testing.T) {
    transfer := completedPurchase(t, 2990, 299)
    first, err := ReleaseTransfer(transfer.TransferNo, "release:ORD-1")
    require.NoError(t, err)
    retry, err := ReleaseTransfer(transfer.TransferNo, "release:ORD-1")
    require.NoError(t, err)
    require.Equal(t, first.TransferNo, retry.TransferNo)
    require.Equal(t, int64(2691), available(t, "USER", "7"))
    require.Equal(t, int64(0), frozen(t, "USER", "7"))
}

func TestReverseRefundsBuyerAndNeverExceedsOriginal(t *testing.T) {
    transfer := completedPurchase(t, 2990, 299)
    _, err := ReverseTransfer(transfer.TransferNo, 2990, "refund:ORD-1")
    require.NoError(t, err)
    _, err = ReverseTransfer(transfer.TransferNo, 1, "refund:ORD-1-extra")
    require.ErrorIs(t, err, ErrReverseAmountExceeded)
}
```

- [ ] **Step 2: Verify failure**

Run: `cd new-api && go test ./model ./controller -run 'TestRelease|TestReverse|TestWorkspaceWalletAuthorization'`

Expected: FAIL because release/reversal and Workspace wallet checks do not exist.

- [ ] **Step 3: Implement release and reversal transactions**

Release debits seller frozen and credits seller available. Reversal uses the original transfer, refunds buyer available, removes seller frozen first or seller available after release, and reverses platform fee. Track cumulative reversed cents and reject over-refund. Every operation uses its own idempotency key and immutable ledger entries.

- [ ] **Step 4: Enforce wallet ownership**

For `USER` wallets, verified owner ID must match the authenticated business actor supplied in the signed request. For `WORKSPACE` wallets, load active membership and require `trade.purchase`; purchase precheck also requires `trade.purchase.approve` when the request references an approved enterprise purchase. Service callers cannot invent a Workspace owner without a matching active membership assertion.

Add `HasPermission(permission string) bool` to `MembershipResult` and define the trade permission catalog `trade.purchase`, `trade.purchase.request`, `trade.purchase.approve`, `trade.listing.manage`, `trade.listing.review`, `trade.refund.review`, and `trade.exception.manage`. Personal Workspace owners receive `trade.purchase` and `trade.listing.manage` during personal-workspace provisioning. Enterprise role assignment remains explicit: existing membership JSON is not silently broadened by a migration.

- [ ] **Step 5: Run tests and commit**

Run: `cd new-api && go test ./model ./controller -run 'TestRelease|TestReverse|TestWorkspaceWalletAuthorization'`

Expected: PASS.

```bash
git add new-api/model/wallet.go new-api/model/wallet_reversal_test.go \
  new-api/controller/aicp_wallet.go new-api/controller/aicp_wallet_authorization_test.go \
  new-api/controller/aicp_workspace.go new-api/model/aicp_workspace.go
git commit -m "feat(wallet): release and reverse marketplace funds"
```

### Task 9: Add the signed 8080 wallet client and contract tests

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/wallet/WalletClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/wallet/HttpWalletClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/wallet/WalletDtos.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/wallet/TopUpClient.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/TradeWalletController.java`
- Modify: `aicp-backend/src/main/resources/application.yml`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/wallet/HttpWalletClientContractTest.java`

- [ ] **Step 1: Write a failing canonical-signature contract test**

```java
@Test void signsPurchaseWithCanonicalBodyAndIdempotencyKey() {
    server.enqueue(json(200, successfulTransfer("WT-1")));
    client.purchase(purchaseRequest("ORD-1", 2990, 299), "trade:purchase:ORD-1");
    RecordedRequest sent = server.takeRequest();
    assertThat(sent.getHeader("Idempotency-Key")).isEqualTo("trade:purchase:ORD-1");
    assertThat(verifySignature(sent, sharedSecret)).isTrue();
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=HttpWalletClientContractTest test`

Expected: FAIL because wallet client classes do not exist.

- [ ] **Step 3: Implement the port and adapter**

`WalletClient` exposes `balance`, `precheck`, `purchase`, `findByBusinessOrder`, `release`, and `reverse`. `HttpWalletClient` serializes stable snake_case JSON, hashes the exact UTF-8 bytes it sends, signs the canonical string from Task 5, sets 3-second connect and 8-second read timeouts, maps 409 to `IDEMPOTENCY_CONFLICT`, 422 to business errors, and timeout/5xx to `WalletResult.UNKNOWN` without claiming failure.

`TopUpClient` forwards the authenticated user's original bearer token only to 3001 user endpoints `GET /api/user/topup/info`, `GET /api/user/topup/self`, and the configured provider payment creation endpoint. `TradeWalletController` exposes `GET /api/v1/trade/wallet/balance`, `GET /api/v1/trade/wallet/topup-info`, `GET /api/v1/trade/wallet/topups`, and `POST /api/v1/trade/wallet/topups`. The balance endpoint uses the signed internal client and derives owner from trusted Workspace context; top-up endpoints are personal-wallet only and return 422 for enterprise wallets. The controller allow-lists provider values returned by 3001 and never accepts a callback URL from the browser.

Add configuration:

```yaml
aicp:
  wallet:
    base-url: ${AICP_WALLET_BASE_URL:http://localhost:3001}
    service-name: ${AICP_WALLET_SERVICE_NAME:aicp-8080}
    service-secret: ${AICP_WALLET_SERVICE_SECRET:}
    connect-timeout-ms: 3000
    read-timeout-ms: 8000
```

Fail startup outside the dev profile when the secret is blank.

- [ ] **Step 4: Run contract tests and commit**

Run: `cd aicp-backend && mvn -Dtest=HttpWalletClientContractTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade/wallet \
  aicp-backend/src/main/java/com/aicp/module/trade/controller/TradeWalletController.java \
  aicp-backend/src/main/resources/application.yml \
  aicp-backend/src/test/java/com/aicp/module/trade/wallet
git commit -m "feat(trade): add signed wallet client"
```

### Task 10: Implement paid order orchestration and exclusive inventory

**Files:**
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/ScriptListingMapper.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/DeliveryService.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/controller/OrderController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/PaidOrderOrchestrationTest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/ExclusiveInventoryConcurrencyTest.java`

- [ ] **Step 1: Write failing paid and concurrent-exclusive tests**

```java
@Test void paidOrderUsesServerPriceAndDeliversAfterWalletSuccess() {
    when(wallet.purchase(any(), eq("trade:purchase:ORD-1"))).thenReturn(success("WT-1"));
    OrderView result = orderService.pay(buyerContext(), "ORD-1");
    assertThat(result.status()).isEqualTo("FULFILLED");
    verify(wallet).purchase(argThat(r -> r.amountCents() == 2990 && r.platformFeeCents() == 299), any());
}

@Test void onlyOneConcurrentExclusiveReservationSucceeds() throws Exception {
    List<Future<OrderView>> attempts = runConcurrently(2,
        () -> orderService.create(buyerContext(), exclusiveRequestWithUniqueKey()));
    assertThat(attempts.stream().filter(this::completedSuccessfully).count()).isEqualTo(1);
    assertThat(listingMapper.selectById(listingId).getStatus()).isEqualTo("EXCLUSIVE_RESERVED");
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=PaidOrderOrchestrationTest,ExclusiveInventoryConcurrencyTest test`

Expected: FAIL because paid orchestration and conditional reserve do not exist.

- [ ] **Step 3: Implement database-conditional reservation**

Add one mapper update equivalent to:

```sql
UPDATE script_listings
SET status='EXCLUSIVE_RESERVED', reserved_order_no=?, reservation_expires_at=?, row_version=row_version+1
WHERE id=? AND status='LISTED' AND reservation_expires_at IS NULL;
```

Require affected rows to equal one. On paid exclusive/buyout delivery, conditionally transition matching reserved order to `EXCLUSIVE_SOLD`; on cancellation/expiry/payment failure, clear only the matching reservation.

- [ ] **Step 4: Implement paid orchestration**

Transition `PENDING_PAYMENT → PAYING` before the wallet call. On wallet success, persist transfer number and `PAID_PENDING_DELIVERY`, then invoke delivery. On deterministic wallet rejection, transition to `PAYMENT_FAILED` and release reservation. On timeout/5xx, transition to `PAYMENT_UNKNOWN`, keep reservation, and prohibit another pay call. Platform fee must be calculated server-side from one configured basis-point value using integer division with an explicit round-down rule.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=PaidOrderOrchestrationTest,ExclusiveInventoryConcurrencyTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): orchestrate paid and exclusive orders"
```

### Task 11: Add top-up entry and paid checkout UI

**Files:**
- Modify: `aicp-frontend/src/api/trade.js`
- Create: `aicp-frontend/src/api/wallet.js`
- Modify: `aicp-frontend/src/views/trade/TradeCheckout.vue`
- Modify: `aicp-frontend/src/views/trade/TradeResult.vue`
- Create: `aicp-frontend/src/views/trade/WalletTopUp.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/views/trade/tradeState.js`
- Create: `aicp-frontend/tests/trade-checkout-state.test.js`

- [ ] **Step 1: Write failing checkout decision tests**

```javascript
test('checkout sends insufficient balance to a safe relative return path', () => {
  const decision = checkoutDecision({ balanceCents: 1000, amountCents: 2990, orderNo: 'ORD-1' })
  assert.deepEqual(decision, { type: 'TOP_UP', to: '/wallet/topup?return_to=%2Ftrade%2Fcheckout%2FORD-1' })
})

test('unknown payment cannot create a second charge', () => {
  assert.equal(checkoutDecision({ status: 'PAYMENT_UNKNOWN' }).type, 'POLL_ONLY')
})
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-frontend && node --test tests/trade-checkout-state.test.js`

Expected: FAIL because checkout decision helpers do not exist.

- [ ] **Step 3: Implement wallet/top-up APIs and UI**

`walletApi` calls 8080 wallet façade endpoints for balance and top-up info; top-up channel creation targets the existing 3001-compatible endpoints exposed by 8080 configuration. Validate `return_to` with `^/(trade|market|profile)(/|\?|$)` and fall back to `/market`. Never place service credentials in the browser.

Checkout displays current personal or enterprise Workspace, server price, balance, agreement checkbox, historical normal-license count, and one primary action. On insufficient balance, navigate to top-up; after recharge return, refresh balance and require a second explicit pay click.

- [ ] **Step 4: Run tests and build**

Run: `cd aicp-frontend && node --test tests/trade-state.test.js tests/trade-checkout-state.test.js`

Expected: PASS.

Run: `cd aicp-frontend && npm run build`

Expected: Vite build completes.

- [ ] **Step 5: Commit**

```bash
git add aicp-frontend/src/api aicp-frontend/src/views/trade \
  aicp-frontend/src/router/index.js aicp-frontend/tests/trade-checkout-state.test.js
git commit -m "feat(trade): add wallet checkout and top-up return"
```

## Phase 3 — Enterprise approval, recovery, settlement, and refunds

### Task 12: Implement enterprise purchase approval and Workspace-wallet payment

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/PurchaseRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/PurchaseRequestMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/PurchaseApprovalService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/PurchaseApprovalController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/OrderService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/EnterprisePurchaseApprovalTest.java`

- [ ] **Step 1: Write failing approval tests**

```java
@Test void approvalDoesNotChargeAndPaymentUsesWorkspaceWallet() {
    PurchaseRequestView request = service.submit(memberWith("trade.purchase.request"), validRequest());
    service.approve(memberWith("trade.purchase.approve"), request.id(), "预算通过");
    verifyNoInteractions(walletClient);

    orderService.pay(memberWith("trade.purchase"), request.orderNo());
    verify(walletClient).purchase(argThat(p -> p.buyer().type().equals("WORKSPACE")
        && p.buyer().id().equals("ent_100")), anyString());
}

@Test void memberCannotApproveOwnRequestWithoutApprovalPermission() {
    assertThatThrownBy(() -> service.approve(memberWith("trade.purchase.request"), id, ""))
        .isInstanceOf(BizException.class);
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=EnterprisePurchaseApprovalTest test`

Expected: FAIL because enterprise purchase service does not exist.

- [ ] **Step 3: Implement approval state**

Submit stores the server-derived listing/license/amount snapshot and creates an order in `PENDING_APPROVAL`. Approve requires `trade.purchase.approve`, records approver/comment, transitions request to `APPROVED` and order to `PENDING_PAYMENT`, and only then reserves exclusive inventory for 30 minutes. Reject/cancel records audit and never touches wallet funds.

- [ ] **Step 4: Require Workspace wallet on enterprise pay**

Use `buyer_workspace_id` and `buyer_workspace_type` stored at request creation. Reject a changed `X-Workspace-Id`. Call 3001 with `Owner{type=WORKSPACE,id=buyer_workspace_id}` and actor user ID; never fall back to the actor's personal wallet.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=EnterprisePurchaseApprovalTest,PaidOrderOrchestrationTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): add enterprise purchase approval"
```

### Task 13: Implement outbox recovery, expiration, and reconciliation

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/TradeOutboxEvent.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/TradeOutboxEventMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryScheduler.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/TradeAdminController.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/TradeRecoveryServiceTest.java`

- [ ] **Step 1: Write failing crash-recovery tests**

```java
@Test void unknownPaymentQueriesOriginalBusinessOrderAndDelivers() {
    seedOrder("ORD-1", PAYMENT_UNKNOWN);
    when(wallet.findByBusinessOrder("ORD-1")).thenReturn(success("WT-1"));
    recovery.reconcilePayment("ORD-1");
    assertThat(readOrder("ORD-1").getStatus()).isEqualTo(FULFILLED.name());
    verify(wallet, never()).purchase(any(), anyString());
}

@Test void exhaustedDeliveryRetriesReverseOriginalTransfer() {
    seedCompensatingOrder("ORD-2", "WT-2", MAX_ATTEMPTS);
    recovery.retryDelivery("ORD-2");
    verify(wallet).reverse("WT-2", orderAmount, "trade:reverse:ORD-2");
    assertThat(readOrder("ORD-2").getStatus()).isEqualTo(REFUNDED.name());
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=TradeRecoveryServiceTest test`

Expected: FAIL because recovery components do not exist.

- [ ] **Step 3: Implement durable outbox processing**

Write outbox rows in the same local transaction as order state. Claim due events with status/row-version conditional updates. Implement handlers for `QUERY_PAYMENT`, `DELIVER_ORDER`, `REVERSE_FAILED_DELIVERY`, `RELEASE_SETTLEMENT`, and `RECONCILE_ORDER`. Use exponential delays of 10s, 30s, 2m, 10m, then every 30m; after the configured maximum, leave `FAILED` for admin retry.

- [ ] **Step 4: Implement expiration and reconciliation**

Every minute expire `PENDING_PAYMENT` orders older than 30 minutes and release matching reservations. Do not expire `PAYING` or `PAYMENT_UNKNOWN`. Daily reconciliation compares local order amount/fee/seller amount and state with the 3001 transfer, entitlement, and purchased copy. Persist mismatch codes; never auto-edit money to hide a mismatch.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=TradeRecoveryServiceTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/test/java/com/aicp/module/trade/service/TradeRecoveryServiceTest.java
git commit -m "feat(trade): recover and reconcile transactions"
```

### Task 14: Implement settlement and controlled refunds

**Files:**
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/entity/RefundRequest.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/mapper/RefundRequestMapper.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/service/RefundService.java`
- Create: `aicp-backend/src/main/java/com/aicp/module/trade/controller/RefundController.java`
- Modify: `aicp-backend/src/main/java/com/aicp/module/trade/service/TradeRecoveryService.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/service/RefundAndSettlementTest.java`

- [ ] **Step 1: Write failing settlement/refund tests**

```java
@Test void sevenDaySettlementReleasesFundsOnce() {
    seedFulfilledOrder("ORD-1", completedAt.minusDays(8), "WT-1");
    recovery.releaseDueSettlements();
    recovery.releaseDueSettlements();
    verify(wallet, times(1)).release("WT-1", "trade:release:ORD-1");
    assertThat(readOrder("ORD-1").isSettled()).isTrue();
}

@Test void approvedExclusiveRefundRevokesThenRestoresInventoryAfterWalletReverse() {
    RefundView refund = refunds.request(buyerContext(), "ORD-X", reason());
    refunds.approve(adminWith("trade.refund.review"), refund.id(), "证据成立");
    assertThat(readEntitlement("ORD-X").getStatus()).isEqualTo(REVOKED.name());
    assertThat(readListing(listingId).getStatus()).isEqualTo(LISTED.name());
    verify(wallet).reverse("WT-X", orderAmount, "trade:refund:ORD-X");
}
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-backend && mvn -Dtest=RefundAndSettlementTest test`

Expected: FAIL because refund and settlement behavior does not exist.

- [ ] **Step 3: Implement seven-day release**

Select fulfilled, unsettled orders older than seven days with no active refund. Call `wallet.release` using a stable key, then mark settled only after success. A second scheduler pass must skip the settled order. Unknown responses create a query/reconciliation event and query the same release key before any retry.

- [ ] **Step 4: Implement controlled refund**

Unpaid orders use cancel, not refund. Paid-undelivered orders automatically reverse. Fulfilled orders create `REFUND_REQUESTED`, set entitlement `REFUND_LOCKED`, and require `trade.refund.review`. On approval, call reversal first; only confirmed success sets order `REFUNDED`, entitlement `REVOKED`, purchased copy `REVOKED`, and restores exclusive inventory. Rejection returns entitlement to `ACTIVE` and order to `FULFILLED`.

- [ ] **Step 5: Run tests and commit**

Run: `cd aicp-backend && mvn -Dtest=RefundAndSettlementTest,TradeRecoveryServiceTest test`

Expected: PASS.

```bash
git add aicp-backend/src/main/java/com/aicp/module/trade \
  aicp-backend/src/test/java/com/aicp/module/trade
git commit -m "feat(trade): settle sellers and control refunds"
```

### Task 15: Add seller, enterprise, purchase, and admin views

**Files:**
- Modify: `aicp-frontend/src/api/trade.js`
- Create: `aicp-frontend/src/views/trade/MyPurchases.vue`
- Create: `aicp-frontend/src/views/trade/SellerTradeCenter.vue`
- Create: `aicp-frontend/src/views/trade/ListingEditor.vue`
- Create: `aicp-frontend/src/views/trade/EnterprisePurchaseCenter.vue`
- Create: `aicp-frontend/src/views/trade/TradeAdminCenter.vue`
- Modify: `aicp-frontend/src/router/index.js`
- Modify: `aicp-frontend/src/components/Sidebar.vue`
- Create: `aicp-frontend/tests/trade-permissions.test.js`

- [ ] **Step 1: Write failing permission and action tests**

```javascript
test('enterprise approval and admin actions require explicit permissions', () => {
  assert.equal(canApprovePurchase(['trade.purchase.request']), false)
  assert.equal(canApprovePurchase(['trade.purchase.approve']), true)
  assert.equal(canReviewRefund(['trade.refund.review']), true)
  assert.equal(canReviewRefund(['trade.purchase.approve']), false)
})

test('listing actions match server state', () => {
  assert.deepEqual(listingActions('REJECTED'), ['EDIT', 'RESUBMIT'])
  assert.deepEqual(listingActions('EXCLUSIVE_SOLD'), ['VIEW_SALES'])
})
```

- [ ] **Step 2: Verify failure**

Run: `cd aicp-frontend && node --test tests/trade-permissions.test.js`

Expected: FAIL because permission helpers and views do not exist.

- [ ] **Step 3: Implement role-specific pages**

My Purchases shows order, entitlement, agreement hash, warehouse entry, and eligible refund action. Seller Center shows listing status, orders, frozen/available income from 3001-backed summaries, and refund deductions. Listing Editor validates preview 1–3, unique license types, integer-cent conversion, and required agreement fields. Enterprise Center separates submit, approve/reject, and pay actions. Admin Center lists listing reviews, refund reviews, failed outbox events, payment unknown orders, and reconciliation mismatches; every mutation requires a confirmation and reason.

- [ ] **Step 4: Register guarded routes and navigation**

Add `/trade/purchases`, `/trade/seller`, `/trade/seller/listings/new`, `/trade/seller/listings/:id`, `/trade/enterprise`, and `/trade/admin`. Route meta records required permissions; components also use server-returned capabilities because client guards are not security boundaries.

- [ ] **Step 5: Run tests and build**

Run: `cd aicp-frontend && node --test tests/trade-*.test.js tests/navigation-contract.test.js`

Expected: PASS.

Run: `cd aicp-frontend && npm run build`

Expected: Vite build completes.

- [ ] **Step 6: Commit**

```bash
git add aicp-frontend/src/api/trade.js aicp-frontend/src/views/trade \
  aicp-frontend/src/router/index.js aicp-frontend/src/components/Sidebar.vue \
  aicp-frontend/tests
git commit -m "feat(trade): add seller enterprise and admin workflows"
```

### Task 16: Complete cross-service E2E, security, and deployment verification

**Files:**
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/TradeLifecycleE2ETest.java`
- Create: `aicp-backend/src/test/java/com/aicp/module/trade/TradeSecurityIntegrationTest.java`
- Create: `new-api/controller/aicp_wallet_e2e_test.go`
- Create: `docs/02-derived/script-trade-wallet-api-contract.md`
- Modify: `aicp-backend/docker-compose.yml`
- Modify: `new-api/docker-compose.yml`

- [ ] **Step 1: Add E2E acceptance scenarios**

Implement named tests for all ten scenarios in design Section 19.4. The concurrency test must use two real transactions. Crash recovery must seed `PAYMENT_UNKNOWN` with a successful 3001 transfer and prove no second purchase transfer is created. Security tests must prove cross-Workspace order/entitlement reads return 404, client-supplied price is ignored, unsigned wallet calls return 401, replayed expired signatures return 401, and enterprise orders cannot use personal wallets.

- [ ] **Step 2: Document the exact cross-service contract**

The contract document must include canonical signing input, headers, JSON request/response examples, integer money units, owner types, transfer states, error codes, idempotency behavior, timeout semantics, and curl examples using a generated signature script that reads the secret from environment without printing it.

- [ ] **Step 3: Configure service connectivity**

Set 8080 `AICP_WALLET_BASE_URL` to the 3001 service name in Docker networks and inject the same wallet service secret into both services through environment variables. Do not commit real secrets. Add health checks that distinguish market database readiness from wallet availability; wallet failure must not mark public market reads unhealthy.

- [ ] **Step 4: Run complete verification**

Run: `cd new-api && go test ./...`

Expected: all Go tests PASS.

Run: `cd aicp-backend && mvn test`

Expected: BUILD SUCCESS with all Java tests passing.

Run: `cd aicp-frontend && node --test tests/*.test.js && npm run build`

Expected: all Node tests PASS and Vite build completes.

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 5: Perform manual smoke verification**

Start 3001, 8080, and the frontend. Verify public search while logged out; personal free claim; paid normal license after recharge; concurrent exclusive attempts in two sessions; enterprise request/approve/pay; seller frozen balance; simulated seven-day release; fulfilled-order refund review; and 3001 outage behavior. Capture order number, transfer number, entitlement ID, and reconciliation result for each paid scenario in the test run notes.

- [ ] **Step 6: Commit**

```bash
git add aicp-backend/src/test/java/com/aicp/module/trade \
  new-api/controller/aicp_wallet_e2e_test.go \
  docs/02-derived/script-trade-wallet-api-contract.md \
  aicp-backend/docker-compose.yml new-api/docker-compose.yml
git commit -m "test(trade): verify marketplace transaction lifecycle"
```

## Final acceptance checklist

- [ ] No hard-coded market items or fixed successful payment responses remain.
- [ ] All private queries are scoped by authenticated user and trusted Workspace.
- [ ] Free, normal, exclusive, and buyout produce order, entitlement, warehouse copy, and audit rows.
- [ ] Exclusive/buyout concurrent purchase yields at most one fulfilled order.
- [ ] 3001 wallet purchase, release, recharge, and reversal are balanced and idempotent.
- [ ] Payment timeout is queried by original business order and never retried with a new charge key.
- [ ] Paid delivery survives an 8080 crash or reverses automatically after exhausted retries.
- [ ] Enterprise approval never silently charges and can only use the approved Workspace wallet.
- [ ] Refund reversal completes before entitlement revocation and inventory restoration become final.
- [ ] Daily reconciliation reports order/transfer/entitlement differences without mutating money.
- [ ] Full Go, Java, Node, and Vite verification passes.
