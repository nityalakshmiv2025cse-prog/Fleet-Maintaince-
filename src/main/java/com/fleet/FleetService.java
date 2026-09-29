package com.fleet;

import com.fleet.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class FleetService {
    public static final double PSI_THRESHOLD = 28.0;

    @PersistenceContext
    private EntityManager em;

    // ---------- generic helpers ----------
    public <T> T save(T entity) { em.persist(entity); return entity; }

    public <T> List<T> all(Class<T> type) {
        return em.createQuery("select e from " + type.getSimpleName() + " e order by e.id desc", type).getResultList();
    }

    private <T> T find(Class<T> type, Long id, String label) {
        T e = id == null ? null : em.find(type, id);
        if (e == null) throw new IllegalArgumentException(label + " not found");
        return e;
    }

    private void post(String account, String kind, double dr, double cr, String memo, String division) {
        Ledger l = new Ledger();
        l.account = account; l.kind = kind; l.debit = dr; l.credit = cr;
        l.memo = memo; l.division = division; l.postedAt = LocalDateTime.now();
        em.persist(l);
    }

    // ---------- telemetry ----------
    public Map<String, Object> ingest(Telemetry t) {
        Vehicle v = find(Vehicle.class, t.vehicleId, "Vehicle");
        t.readAt = LocalDateTime.now();
        em.persist(t);
        Ticket ticket = null;
        if (t.psi < PSI_THRESHOLD) {
            long open = em.createQuery("select count(k) from Ticket k where k.vehicleId=:v and k.tirePosition=:p and k.status='OPEN'", Long.class)
                    .setParameter("v", v.id).setParameter("p", t.tirePosition).getSingleResult();
            if (open == 0) {
                ticket = new Ticket();
                ticket.vehicleId = v.id; ticket.tirePosition = t.tirePosition;
                ticket.reason = "Low pressure: " + t.psi + " PSI (minimum " + PSI_THRESHOLD + ")";
                ticket.createdAt = LocalDateTime.now();
                em.persist(ticket);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("reading", t);
        out.put("ticketCreated", ticket != null);
        out.put("ticket", ticket);
        return out;
    }

    public List<Ticket> openTickets() {
        return em.createQuery("select k from Ticket k where k.status='OPEN' order by k.id desc", Ticket.class).getResultList();
    }

    private Product tire() {
        return em.createQuery("select p from Product p where p.kind='GOODS' order by p.id", Product.class)
                .setMaxResults(1).getSingleResult();
    }

    public Ticket complete(Long id, double labourCost, boolean replaceTire) {
        Ticket t = find(Ticket.class, id, "Ticket");
        if (!"OPEN".equals(t.status)) throw new IllegalStateException("Ticket is already completed");
        Vehicle v = find(Vehicle.class, t.vehicleId, "Vehicle");
        double total = labourCost;
        if (labourCost > 0) {
            post("Maintenance & Alignment Expenses", "EXPENSE", labourCost, 0, "Service ticket #" + id, v.division);
            post("Cash", "ASSET", 0, labourCost, "Service ticket #" + id, v.division);
        }
        if (replaceTire) {
            Product p = tire();
            if (p.stock <= 0) throw new IllegalStateException("No tires in stock. Raise a purchase order first.");
            p.stock--;
            total += p.price;
            post("Maintenance & Alignment Expenses", "EXPENSE", p.price, 0, "Tire replaced, ticket #" + id, v.division);
            post("Fleet Tires Inventory", "ASSET", 0, p.price, "Tire replaced, ticket #" + id, v.division);
        }
        t.cost = total;
        t.status = "DONE";
        return t;
    }

    // ---------- procurement ----------
    public PurchaseOrder createPO(PurchaseOrder po) {
        find(Contact.class, po.vendorId, "Vendor");
        find(Product.class, po.productId, "Product");
        if (po.qty <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        po.status = "ORDERED"; po.createdAt = LocalDateTime.now();
        return save(po);
    }

    public PurchaseOrder bill(Long id) {
        PurchaseOrder po = find(PurchaseOrder.class, id, "Purchase order");
        if (!"ORDERED".equals(po.status)) throw new IllegalStateException("Only ordered POs can be billed");
        Product p = find(Product.class, po.productId, "Product");
        p.stock += po.qty;
        double amt = po.qty * po.unitCost;
        post("Fleet Tires Inventory", "ASSET", amt, 0, "Vendor bill for PO #" + id, po.division);
        post("Tire Supplier Creditors", "LIABILITY", 0, amt, "Vendor bill for PO #" + id, po.division);
        po.status = "BILLED";
        return po;
    }

    public PurchaseOrder payBill(Long id) {
        PurchaseOrder po = find(PurchaseOrder.class, id, "Purchase order");
        if (!"BILLED".equals(po.status)) throw new IllegalStateException("Bill the PO before paying it");
        double amt = po.qty * po.unitCost;
        post("Tire Supplier Creditors", "LIABILITY", amt, 0, "Payment for PO #" + id, po.division);
        post("Cash", "ASSET", 0, amt, "Bank payment for PO #" + id, po.division);
        po.status = "PAID";
        return po;
    }

    // ---------- customer invoices ----------
    public Invoice createInvoice(Invoice inv) {
        find(Contact.class, inv.customerId, "Customer");
        if (inv.amount <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        inv.status = "UNPAID"; inv.createdAt = LocalDateTime.now();
        return save(inv);
    }

    public Invoice payInvoice(Long id) {
        Invoice inv = find(Invoice.class, id, "Invoice");
        if (!"UNPAID".equals(inv.status)) throw new IllegalStateException("Invoice is already paid");
        post("Cash", "ASSET", inv.amount, 0, "Bank receipt for invoice #" + id, inv.division);
        post("Internal Fleet Service Billing", "INCOME", 0, inv.amount, "Invoice #" + id, inv.division);
        inv.status = "PAID";
        return inv;
    }

    public Budget setBudget(Budget b) {
        List<Budget> existing = em.createQuery("select x from Budget x where x.division=:d", Budget.class)
                .setParameter("d", b.division).getResultList();
        if (existing.isEmpty()) return save(b);
        existing.get(0).allocated = b.allocated;
        return existing.get(0);
    }

    // ---------- reports ----------
    private Map<String, double[]> balances() {
        Map<String, double[]> m = new LinkedHashMap<>(); // account -> {net(debit-credit), kindIndex}
        List<Object[]> rows = em.createQuery(
                "select l.account, l.kind, coalesce(sum(l.debit),0), coalesce(sum(l.credit),0) from Ledger l group by l.account, l.kind order by l.account",
                Object[].class).getResultList();
        List<String> kinds = List.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE");
        for (Object[] r : rows) {
            m.put((String) r[0], new double[]{((Number) r[2]).doubleValue() - ((Number) r[3]).doubleValue(), kinds.indexOf((String) r[1])});
        }
        return m;
    }

    private List<Map<String, Object>> lines(Map<String, double[]> b, int kind, boolean creditNormal) {
        List<Map<String, Object>> out = new ArrayList<>();
        b.forEach((acc, v) -> {
            if ((int) v[1] == kind) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("account", acc);
                line.put("amount", creditNormal ? -v[0] : v[0]);
                out.add(line);
            }
        });
        return out;
    }

    private double sum(List<Map<String, Object>> l) {
        return l.stream().mapToDouble(x -> ((Number) x.get("amount")).doubleValue()).sum();
    }

    public Map<String, Object> profitLoss() {
        Map<String, double[]> b = balances();
        List<Map<String, Object>> income = lines(b, 3, true), expenses = lines(b, 4, false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("income", income); out.put("expenses", expenses);
        out.put("totalIncome", sum(income)); out.put("totalExpenses", sum(expenses));
        out.put("netProfit", sum(income) - sum(expenses));
        return out;
    }

    public Map<String, Object> balanceSheet() {
        Map<String, double[]> b = balances();
        List<Map<String, Object>> assets = lines(b, 0, false), liabilities = lines(b, 1, true), equity = lines(b, 2, true);
        double profit = (double) profitLoss().get("netProfit");
        Map<String, Object> re = new LinkedHashMap<>();
        re.put("account", "Current period profit"); re.put("amount", profit);
        equity.add(re);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assets", assets); out.put("liabilities", liabilities); out.put("equity", equity);
        out.put("totalAssets", sum(assets));
        out.put("totalLiabilitiesAndEquity", sum(liabilities) + sum(equity));
        return out;
    }

    public List<Map<String, Object>> budgetReport() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Budget b : all(Budget.class)) {
            double actual = em.createQuery("select coalesce(sum(l.debit),0) from Ledger l where l.kind='EXPENSE' and l.division=:d", Double.class)
                    .setParameter("d", b.division).getSingleResult();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("division", b.division); row.put("allocated", b.allocated);
            row.put("actual", actual); row.put("variance", b.allocated - actual);
            out.add(row);
        }
        return out;
    }

    // ---------- demo data ----------
    public void seed() {
        long n = em.createQuery("select count(v) from Vehicle v", Long.class).getSingleResult();
        if (n > 0) return;
        String div = "Heavy Transport Division 2";
        Contact vendor = new Contact(); vendor.name = "Apex Tyre Manufacturers"; vendor.kind = "VENDOR"; vendor.email = "orders@apextyre.example";
        Contact cust = new Contact(); cust.name = "Southern Logistics Dept"; cust.kind = "CUSTOMER"; cust.email = "billing@southern.example";
        save(vendor); save(cust);
        String[][] prods = {{"All-Terrain Fleet Tire", "GOODS", "18000", "8"}, {"Tire Alignment Service", "SERVICE", "2500", "0"}, {"Fleet Transit Service", "SERVICE", "12000", "0"}};
        for (String[] r : prods) {
            Product p = new Product(); p.name = r[0]; p.kind = r[1]; p.price = Double.parseDouble(r[2]); p.stock = Integer.parseInt(r[3]);
            save(p);
        }
        String[][] vs = {{"TN-72-AB-1001", "Tata Prima 4028"}, {"TN-72-CD-2002", "Ashok Leyland 2820"}};
        for (String[] r : vs) {
            Vehicle v = new Vehicle(); v.plateNo = r[0]; v.model = r[1]; v.division = div;
            save(v);
        }
        Budget b = new Budget(); b.division = div; b.allocated = 250000; save(b);
        post("Cash", "ASSET", 1000000, 0, "Opening balance", null);
        post("Fleet Tires Inventory", "ASSET", 144000, 0, "Opening stock: 8 tires", null);
        post("Owner Capital", "EQUITY", 0, 1144000, "Opening balance", null);
    }
}
