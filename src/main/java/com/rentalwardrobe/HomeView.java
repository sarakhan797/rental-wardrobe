package com.rentalwardrobe;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;

@Route("")
@PageTitle("Rental Wardrobe")
public class HomeView extends VerticalLayout {
    public HomeView(JdbcTemplate database) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        Long pickupsToday = database.queryForObject("""
            SELECT COUNT(*) FROM bookings WHERE pickup_date = ?
            """, Long.class, today);

        RentalCounts counts = database.queryForObject("""
            SELECT COUNT(CASE WHEN b.return_date = ? THEN 1 END) AS due_today,
                   COUNT(CASE WHEN b.return_date < ? THEN 1 END) AS overdue
            FROM booking_items bi
            JOIN bookings b ON b.id = bi.booking_id
            LEFT JOIN item_returns r
                ON r.booking_id = bi.booking_id AND r.item_id = bi.item_id
            WHERE r.booking_id IS NULL
            """, (row, index) -> new RentalCounts(
                row.getLong("due_today"), row.getLong("overdue")), today, today);

        FlexLayout navigation = new FlexLayout(
            new RouterLink("Items", ItemsView.class),
            new RouterLink("Customers", CustomersView.class),
            new RouterLink("Bookings", BookingsView.class),
            new RouterLink("Returns", ReturnsView.class)
        );
        navigation.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        navigation.getStyle().set("gap", "1rem");

        add(
            new H1("RENTAL WARDROBE"),
            navigation,
            new H2("Today's summary"),
            new Paragraph("Bookings scheduled for pickup today: " + pickupsToday),
            new Paragraph("Items still due today: " + counts.dueToday()),
            new Paragraph("Overdue items: " + counts.overdue())
        );
    }

    private record RentalCounts(long dueToday, long overdue) {}
}
