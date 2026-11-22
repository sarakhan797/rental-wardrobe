package com.rentalwardrobe;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@Route("returns")
@PageTitle("Returns | Rental Wardrobe")
public class ReturnsView extends VerticalLayout {
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private record Rental(long bookingId, long itemId, String customer,
                          String item, LocalDate pickup, LocalDate due,
                          LocalDate returnedOn) {
        String status(LocalDate today) {
            if (returnedOn != null) {
                return "Returned";
            }
            if (pickup.isAfter(today)) {
                return "Upcoming";
            }
            return due.isBefore(today) ? "Overdue" : "Pending";
        }
    }

    public ReturnsView(JdbcTemplate database) {
        Grid<Rental> rentals = new Grid<>();
        rentals.addColumn(Rental::bookingId).setHeader("Booking");
        rentals.addColumn(Rental::customer).setHeader("Customer");
        rentals.addColumn(Rental::item).setHeader("Item");
        rentals.addColumn(Rental::due).setHeader("Return due");
        rentals.addColumn(rental -> rental.status(LocalDate.now(SHOP_ZONE)))
            .setHeader("Status");
        rentals.addColumn(rental -> rental.returnedOn() == null
            ? "" : rental.returnedOn().toString()).setHeader("Returned on");

        Button markReturned = new Button("Mark selected item returned", event -> {
            Rental rental = rentals.asSingleSelect().getValue();
            LocalDate today = LocalDate.now(SHOP_ZONE);
            if (rental == null || rental.returnedOn() != null
                    || rental.pickup().isAfter(today)) {
                Notification.show("Select an unreturned item whose pickup date has arrived.");
                return;
            }

            try {
                database.update("""
                    INSERT INTO item_returns (booking_id, item_id, returned_on)
                    VALUES (?, ?, ?)
                    """, rental.bookingId(), rental.itemId(), today);
                Notification.show("Return recorded.");
            } catch (DuplicateKeyException exception) {
                Notification.show("This item was already marked returned.");
            }

            rentals.deselectAll();
            refreshRentals(database, rentals);
        });

        add(new H1("Returns"), markReturned, rentals);
        refreshRentals(database, rentals);
    }

    private void refreshRentals(JdbcTemplate database, Grid<Rental> rentals) {
        rentals.setItems(database.query("""
            SELECT b.id, bi.item_id, c.customer_name,
                   CONCAT(i.item_code, ' · ', i.item_name),
                   b.pickup_date, b.return_date, r.returned_on
            FROM bookings b
            JOIN customers c ON c.id = b.customer_id
            JOIN booking_items bi ON bi.booking_id = b.id
            JOIN items i ON i.id = bi.item_id
            LEFT JOIN item_returns r
                ON r.booking_id = bi.booking_id AND r.item_id = bi.item_id
            ORDER BY r.returned_on IS NOT NULL, b.return_date, b.id, i.item_code
            """, (row, index) -> new Rental(
                row.getLong(1), row.getLong(2), row.getString(3), row.getString(4),
                row.getObject(5, LocalDate.class), row.getObject(6, LocalDate.class),
                row.getObject(7, LocalDate.class))));
    }
}
