package com.fleet;

import com.fleet.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FleetController {
    private final FleetService s;

    public FleetController(FleetService s) { this.s = s; }

    // master data
    @GetMapping("/fleet/vehicles") List<Vehicle> vehicles() { return s.all(Vehicle.class); }
    @PostMapping("/fleet/vehicles") Vehicle addVehicle(@RequestBody Vehicle v) { v.id = null; return s.save(v); }
    @GetMapping("/contacts") List<Contact> contacts() { return s.all(Contact.class); }
    @PostMapping("/contacts") Contact addContact(@RequestBody Contact c) { c.id = null; return s.save(c); }
    @GetMapping("/products") List<Product> products() { return s.all(Product.class); }

    // telemetry and maintenance
    @GetMapping("/fleet/telemetry") List<Telemetry> telemetry() { return s.all(Telemetry.class); }
    @PostMapping("/fleet/telemetry") Map<String, Object> ingest(@RequestBody Telemetry t) { t.id = null; return s.ingest(t); }
    @GetMapping("/fleet/maintenance-alerts") List<Ticket> alerts() { return s.openTickets(); }
    @PostMapping("/fleet/tickets/{id}/complete")
    Ticket complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        double cost = body.get("cost") == null ? 0 : ((Number) body.get("cost")).doubleValue();
        return s.complete(id, cost, Boolean.TRUE.equals(body.get("replaceTire")));
    }

    // procurement
    @GetMapping("/purchase-orders") List<PurchaseOrder> pos() { return s.all(PurchaseOrder.class); }
    @PostMapping("/purchase-orders") PurchaseOrder createPo(@RequestBody PurchaseOrder po) { po.id = null; return s.createPO(po); }
    @PostMapping("/purchase-orders/{id}/bill") PurchaseOrder bill(@PathVariable Long id) { return s.bill(id); }
    @PostMapping("/purchase-orders/{id}/pay") PurchaseOrder pay(@PathVariable Long id) { return s.payBill(id); }

    // invoices
    @GetMapping("/invoices") List<Invoice> invoices() { return s.all(Invoice.class); }
    @PostMapping("/invoices") Invoice createInvoice(@RequestBody Invoice i) { i.id = null; return s.createInvoice(i); }
    @PostMapping("/invoices/{id}/pay") Invoice payInvoice(@PathVariable Long id) { return s.payInvoice(id); }

    // budgets and reports
    @PostMapping("/budgets") Budget addBudget(@RequestBody Budget b) { b.id = null; return s.setBudget(b); }
    @GetMapping("/reports/balance-sheet") Map<String, Object> balanceSheet() { return s.balanceSheet(); }
    @GetMapping("/reports/profit-loss") Map<String, Object> profitLoss() { return s.profitLoss(); }
    @GetMapping("/reports/budget") List<Map<String, Object>> budget() { return s.budgetReport(); }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> bad(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
