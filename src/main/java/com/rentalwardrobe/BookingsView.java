package com.rentalwardrobe;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Route("bookings")
@PageTitle("Bookings | Rental Wardrobe")
public class BookingsView extends VerticalLayout {
    private record Customer(long id, String label) {}
    private record Item(long id, String label) {}
    private record Booking(long id, String customer, LocalDate pickup,
                           LocalDate due, BigDecimal total) {}

    public BookingsView(JdbcTemplate database, PlatformTransactionManager manager) {
        TransactionTemplate transaction = new TransactionTemplate(manager);

        ComboBox<Customer> customer = new ComboBox<>("Customer");
        customer.setItemLabelGenerator(Customer::label);
        customer.setItems(database.query("""
            SELECT id, customer_name, phone FROM customers ORDER BY customer_name, id
            """, (row, index) -> new Customer(row.getLong(1),
                row.getString(2) + " · " + row.getString(3))));

        MultiSelectComboBox<Item> items = new MultiSelectComboBox<>("Items");
        items.setItemLabelGenerator(Item::label);
        items.setItems(database.query("""
            SELECT id, item_code, item_name FROM items ORDER BY item_name, item_code
            """, (row, index) -> new Item(row.getLong(1),
                row.getString(2) + " · " + row.getString(3))));

        DatePicker pickup = new DatePicker("Pickup date");
        DatePicker due = new DatePicker("Return date");

        Grid<Booking> bookings = new Grid<>();
        bookings.addColumn(Booking::id).setHeader("Booking");
        bookings.addColumn(Booking::customer).setHeader("Customer");
        bookings.addColumn(Booking::pickup).setHeader("Pickup");
        bookings.addColumn(Booking::due).setHeader("Return due");
        bookings.addColumn(Booking::total).setHeader("Total rent (₹)");

        Button save = new Button("Save booking", event -> {
            Customer selectedCustomer = customer.getValue();
            var selectedItems = items.getValue().stream()
                .sorted(Comparator.comparingLong(Item::id)).toList();
            LocalDate start = pickup.getValue();
            LocalDate end = due.getValue();

            if (selectedCustomer == null || selectedItems.isEmpty()
                    || start == null || end == null || end.isBefore(start)
                    || start.getYear() < 1000 || end.getYear() > 9999) {
                Notification.show("Choose a customer, at least one item, and valid rental dates.");
                return;
            }

            String unavailable = transaction.execute(status -> {
                // Lock items in ID order so concurrent bookings check them one at a time.
                for (Item item : selectedItems) {
                    database.queryForObject(
                        "SELECT id FROM items WHERE id = ? FOR UPDATE", Long.class, item.id());
                }

                for (Item item : selectedItems) {
                    boolean overlaps = !database.queryForList("""
                        SELECT b.id FROM bookings b
                        JOIN booking_items bi ON bi.booking_id = b.id
                        WHERE bi.item_id = ? AND b.pickup_date <= ? AND b.return_date >= ?
                        LIMIT 1 FOR UPDATE
                        """, Long.class, item.id(), end, start).isEmpty();
                    if (overlaps) {
                        return item.label();
                    }
                }

                GeneratedKeyHolder key = new GeneratedKeyHolder();
                database.update(connection -> {
                    var statement = connection.prepareStatement("""
                        INSERT INTO bookings (customer_id, pickup_date, return_date)
                        VALUES (?, ?, ?)
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, selectedCustomer.id());
                    statement.setObject(2, start);
                    statement.setObject(3, end);
                    return statement;
                }, key);

                long bookingId = key.getKey().longValue();
                for (Item item : selectedItems) {
                    database.update("""
                        INSERT INTO booking_items (booking_id, item_id, daily_rate)
                        SELECT ?, id, daily_rate FROM items WHERE id = ?
                        """, bookingId, item.id());
                }
                return null;
            });

            if (unavailable != null) {
                Notification.show("Already booked for those dates: " + unavailable);
                return;
            }

            refreshBookings(database, bookings);
            items.clear();
            Notification.show("Booking saved.");
        });

        add(new H1("Bookings"), new FormLayout(customer, items, pickup, due), save, bookings);
        refreshBookings(database, bookings);
    }

    private void refreshBookings(JdbcTemplate database, Grid<Booking> bookings) {
        bookings.setItems(database.query("""
            SELECT b.id, c.customer_name, b.pickup_date, b.return_date,
                   SUM(bi.daily_rate) * (DATEDIFF(b.return_date, b.pickup_date) + 1) AS total
            FROM bookings b
            JOIN customers c ON c.id = b.customer_id
            JOIN booking_items bi ON bi.booking_id = b.id
            GROUP BY b.id, c.customer_name, b.pickup_date, b.return_date
            ORDER BY b.id DESC
            """, (row, index) -> new Booking(row.getLong(1), row.getString(2),
                row.getObject(3, LocalDate.class), row.getObject(4, LocalDate.class),
                row.getBigDecimal(5))));
    }
}
