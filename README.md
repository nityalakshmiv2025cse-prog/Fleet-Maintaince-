# Fleet Tire Maintenance System

Spring Boot 3 (Java 17+) backend with an H2 database and a browser frontend served from the same app.

## Run in VS Code
1. Install JDK 17 or newer and Apache Maven (check with `java -version` and `mvn -v`).
2. In VS Code install the "Extension Pack for Java" and "Spring Boot Extension Pack".
3. Open this folder (File > Open Folder).
4. In the terminal run: `mvn spring-boot:run`
5. Open http://localhost:8080

Data is stored in `./data/fleetdb` and demo data is created on first start. Delete the `data` folder to reset.
DB console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/fleetdb;AUTO_SERVER=TRUE`, user `sa`, empty password).

## API
| Method | Path | Purpose |
|---|---|---|
| POST/GET | /api/fleet/vehicles | Add or list vehicles |
| POST/GET | /api/fleet/telemetry | Post PSI reading (below 28 PSI auto-creates a ticket) |
| GET | /api/fleet/maintenance-alerts | Open service tickets |
| POST | /api/fleet/tickets/{id}/complete | Close ticket `{cost, replaceTire}` |
| POST/GET | /api/purchase-orders, /{id}/bill, /{id}/pay | PO, vendor bill, bank payment |
| POST/GET | /api/invoices, /{id}/pay | Customer invoice and receipt |
| POST | /api/budgets | Set division budget |
| GET | /api/reports/balance-sheet, profit-loss, budget | Reports |

Example telemetry call:
`curl -X POST localhost:8080/api/fleet/telemetry -H "Content-Type: application/json" -d '{"vehicleId":1,"tirePosition":"Front left","psi":24,"wearPercent":30}'`
