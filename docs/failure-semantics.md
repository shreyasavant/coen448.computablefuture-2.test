# Asynchronous Failure Handling Strategies

In concurrent systems, handling failures across multiple asynchronous operations is critical. This document outlines the three policies implemented in the `AsyncProcessor` system.

## Comparison of Policies

| Policy | Behavior on Failure | Result Type | Best For |
| :--- | :--- | :--- | :--- |
| **Fail-Fast** | Aborts immediately | Exception | All-or-nothing operations |
| **Fail-Partial** | Discards failed results | Reduced List | Non-critical data aggregation |
| **Fail-Soft** | Returns fallback value | Complete List | Layout-sensitive displays |

---

## 1. Fail-Fast
### Description
The operation stops as soon as any single task fails. The final `CompletableFuture` completes exceptionally.
### Suitability
- **Transactional Consistency**: When partial success is invalid (e.g., booking a flight and a hotel together).
- **Latency Optimization**: No point waiting for other tasks if the overall result is already doomed.
### Example
If you are validating security credentials across three different auth providers, and the first one fails, the entire request should fail immediately for safety.

## 2. Fail-Partial
### Description
Failures are suppressed, and the system returns only the results that succeeded. The list size may be smaller than the list of input tasks.
### Suitability
- **Search & Discovery**: When returning "top results" from multiple providers (e.g., searching for hotels across Expedia, Kayak, and Booking). If one is down, the user still sees the others.
- **Optional Features**: Secondary data like "Recommended for you" blocks.
### Risks
- **Silent Degradation**: Critical services might be failing, but the UI looks "fine" even if half the data is missing.

## 3. Fail-Soft (Fallback)
### Description
Failures are caught and replaced with a predetermined "fallback" or "default" value. The output list always maintains the same size as the input.
### Suitability
- **Static Layouts**: Dashboards where a specific number of widgets must be rendered.
- **Graceful Degradation**: Providing a generic "Hello User" if the profile-specific service fails.
### Risks
- **Data Integrity**: If a fallback value (like `0` for a bank balance) is used, it could be misinterpreted as accurate data.

---

## Risks of Hiding Failures in Concurrent Systems
Hiding failures (Partial/Soft) instead of failing fast introduces several risks:

1.  **Zombie Systems**: A system may appear "Green" on health dashboards while its internal components are failing at a high rate, delaying necessary maintenance.
2.  **Downstream Poisoning**: If a Fail-Soft policy returns a `null` or empty string that wasn't expected, it might cause a `NullPointerException` or logical error in a distant downstream service that assumes data is validated.
3.  **Resource Masking**: If a service fails slowly (e.g., due to a timeout), Fail-Partial/Soft might mask the fact that threads are being blocked for long periods, leading to eventual resource exhaustion.

### Example: The Financial Reporting Bug
Imagine an investment dashboard that aggregates balances from `Savings`, `Checking`, and `Stocks`.
- If **Fail-Soft** is used and the `Stocks` service fails, it returns `$0.00`.
- The user sees a total balance that is significantly lower than reality but receives **no error message**.
- **The Result**: The user panics and sells their assets, all because a network glitch was "gracefully" handled by hiding the failure.
