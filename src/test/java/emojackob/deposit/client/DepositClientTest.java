package emojackob.deposit.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emojackob.deposit.model.AddressAllocation;
import emojackob.deposit.model.AllocateRequest;
import emojackob.deposit.model.AllocatedAddress;
import emojackob.deposit.model.Balance;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.model.Deposit;
import emojackob.deposit.model.Page;
import emojackob.deposit.model.WithdrawalNotify;
import emojackob.deposit.sign.Keys;
import emojackob.deposit.testutil.MockDepositServer;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 端到端：本地 HTTP 服务端按后端规则重建负载并验签，验证 SDK 签名与解析。 */
class DepositClientTest {

    static MockDepositServer server;
    static KeyPair kp;
    static final String USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";

    @BeforeAll
    static void start() throws Exception {
        kp = Keys.generateKeyPair();
        server = new MockDepositServer(kp);
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    DepositClient client() {
        return new DepositClient(DepositClientConfig.builder()
                .baseUrl(server.baseUrl())
                .apiKey("dnk_test")
                .privateKey(kp.getPrivate())
                .project("demo")
                .build());
    }

    @Test
    void allocateAddressParsesAndSigns() {
        try (DepositClient c = client()) {
            AddressAllocation r = c.allocateAddress(new AllocateRequest("user-1001", "primary", 1));
            assertEquals("user-1001", r.getUserBinding());
            assertEquals("primary", r.getLabel());
            assertEquals(1, r.getCount());
            List<AllocatedAddress> allocated = r.getAllocated();
            assertEquals(1, allocated.size());
            assertEquals("0xabc", allocated.get(0).getAddress());
            assertEquals(1, allocated.get(0).getIndex());
            assertEquals("user", allocated.get(0).getPurpose());
        }
    }

    @Test
    void allocateAddressRequiresFields() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () ->
                    c.allocateAddress(new AllocateRequest(null, "primary", 1)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.allocateAddress(new AllocateRequest("", "primary", 1)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.allocateAddress(new AllocateRequest("user-1001", "", 1)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.allocateAddress(new AllocateRequest("user-1001", "primary", 0)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.allocateAddress(new AllocateRequest("user-1001", "primary", 101)));
        }
    }

    @Test
    void getAddressAllocationByBinding() {
        try (DepositClient c = client()) {
            AddressAllocation r = c.getAddressAllocation("user-1001");
            assertEquals("user-1001", r.getUserBinding());
            assertEquals("0xabc", r.getAllocated().get(0).getAddress());
        }
    }

    @Test
    void getAddressAllocationByBindingRequiresUserBinding() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () -> c.getAddressAllocation(null));
            assertThrows(IllegalArgumentException.class, () -> c.getAddressAllocation(""));
        }
    }

    @Test
    void getAddressAllocationByAddress() {
        try (DepositClient c = client()) {
            AddressAllocation r = c.getAddressAllocationByAddress("0xabc");
            assertEquals("user-1001", r.getUserBinding());
            assertEquals("primary", r.getLabel());
        }
    }

    @Test
    void getBalanceParses() {
        try (DepositClient c = client()) {
            Balance b = c.getBalance("0xabc", USDT);
            assertEquals("USDT", b.getToken());
            assertEquals("1.5", b.getBalance());
            assertEquals("1500000", b.getBalanceRaw());
        }
    }

    @Test
    void getBalanceRequiresErc20Token() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () -> c.getBalance("0xabc", null));
            assertThrows(IllegalArgumentException.class, () -> c.getBalance("0xabc", ""));
            assertThrows(IllegalArgumentException.class, () -> c.getBalance("0xabc", "native"));
        }
    }

    @Test
    void listDepositsParses() {
        try (DepositClient c = client()) {
            Page<Deposit> page = c.listDeposits(Map.of("page", "1", "page_size", "50"));
            assertEquals(1, page.getTotal());
            assertEquals("0xabc", page.getItems().get(0).getToAddr());
        }
    }

    @Test
    void createWithdrawalRequiresOrderNo() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest(null, "pool", "0x1", "1", USDT, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("", "pool", "0x1", "1", USDT, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("  ", "pool", "0x1", "1", USDT, null)));
        }
    }

    @Test
    void createWithdrawalRequiresErc20Token() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("WD-1", "pool", "0x1", "1", null, null)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("WD-1", "pool", "0x1", "1", "", null)));
            assertThrows(IllegalArgumentException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("WD-1", "pool", "0x1", "1", "native", null)));
        }
    }

    @Test
    void createWithdrawalSucceeds() {
        try (DepositClient c = client()) {
            WithdrawalNotify wd = c.createWithdrawal(new CreateWithdrawalRequest(
                    "WD-NEW", "pool", "0x1", "1", USDT, null));
            assertEquals("WD-NEW", wd.getOrderNo());
            assertEquals("created", wd.getStatus());
            assertEquals("withdrawal:WD-NEW:created", wd.getEventKey());
        }
    }

    @Test
    void createWithdrawalIdempotentSameFingerprintReturnsCurrentSnapshot() {
        try (DepositClient c = client()) {
            CreateWithdrawalRequest req = new CreateWithdrawalRequest(
                    "WD-IDEM", "pool", "0x1", "1", USDT, null);
            WithdrawalNotify first = c.createWithdrawal(req);
            WithdrawalNotify replay = c.createWithdrawal(req);
            assertEquals("created", first.getStatus());
            assertEquals("WD-IDEM", replay.getOrderNo());
            assertEquals(first.getStatus(), replay.getStatus());
            assertEquals(first.getEventKey(), replay.getEventKey());
        }
    }

    @Test
    void createWithdrawalConflictDifferentFingerprint() {
        try (DepositClient c = client()) {
            c.createWithdrawal(new CreateWithdrawalRequest(
                    "WD-CONFLICT", "pool", "0x1", "1", USDT, null));
            ApiException e = assertThrows(ApiException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest(
                            "WD-CONFLICT", "pool", "0x1", "2", USDT, null)));
            assertEquals("conflict", e.getCode());
            assertEquals(409, e.getHttpStatus());
            assertEquals("order_no already exists with different parameters", e.getMessage());
        }
    }

    @Test
    void getWithdrawalParsesUnifiedPayload() {
        try (DepositClient c = client()) {
            WithdrawalNotify wd = c.getWithdrawal("WD-1");
            assertEquals("WD-1", wd.getOrderNo());
            assertEquals("sent", wd.getStatus());
            assertEquals("withdrawal:WD-1:sent", wd.getEventKey());
            assertEquals("withdrawal_status", wd.getEventType());
            assertEquals(137, wd.getChainId());
            assertEquals("0xabc", wd.getFrom());
            assertEquals("0x1", wd.getTo());
            assertEquals("2026-08-25T03:30:00.000Z", wd.getCreatedAt());
        }
    }

    @Test
    void listWithdrawalsParsesUnifiedPayload() {
        try (DepositClient c = client()) {
            List<WithdrawalNotify> items = c.listWithdrawals(List.of("WD-1"));
            assertEquals(1, items.size());
            assertEquals("WD-1", items.get(0).getOrderNo());
        }
    }

    @Test
    void listWithdrawalRecordsParsesPage() {
        try (DepositClient c = client()) {
            Page<WithdrawalNotify> page = c.listWithdrawalRecords(Map.of("page", "1", "page_size", "50"));
            assertEquals(1, page.getTotal());
            assertEquals(1, page.getPage());
            assertEquals(50, page.getPageSize());
            assertEquals("WD-1", page.getItems().get(0).getOrderNo());
            assertEquals("sent", page.getItems().get(0).getStatus());
        }
    }

    @Test
    void cancelWithdrawalParses() {
        try (DepositClient c = client()) {
            WithdrawalNotify wd = c.cancelWithdrawal("WD-NEW");
            assertEquals("WD-NEW", wd.getOrderNo());
            assertEquals("cancelled", wd.getStatus());
            assertEquals("withdrawal:WD-NEW:cancelled", wd.getEventKey());
        }
    }

    @Test
    void cancelWithdrawalRequiresOrderNo() {
        try (DepositClient c = client()) {
            assertThrows(IllegalArgumentException.class, () -> c.cancelWithdrawal(null));
            assertThrows(IllegalArgumentException.class, () -> c.cancelWithdrawal(""));
        }
    }
}
