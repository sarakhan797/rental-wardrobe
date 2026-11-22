package com.rentalwardrobe;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.math.BigDecimal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@Route("items")
@PageTitle("Items | Rental Wardrobe")
public class ItemsView extends VerticalLayout {
    private record Item(String code, String name, String category,
                        String size, BigDecimal rate) {}

    public ItemsView(JdbcTemplate database) {
        TextField code = new TextField("Item code");
        code.setMaxLength(30);

        TextField name = new TextField("Item name");
        name.setMaxLength(100);

        Select<String> category = new Select<>();
        category.setLabel("Category");
        category.setItems("Clothing", "Jewellery");
        category.setValue("Clothing");

        TextField size = new TextField("Size (optional)");
        size.setMaxLength(30);
        BigDecimalField rate = new BigDecimalField("Daily rent (₹)");

        Grid<Item> items = new Grid<>();
        items.addColumn(Item::code).setHeader("Item code");
        items.addColumn(Item::name).setHeader("Item name");
        items.addColumn(Item::category).setHeader("Category");
        items.addColumn(Item::size).setHeader("Size");
        items.addColumn(Item::rate).setHeader("Daily rent (₹)");

        Button save = new Button("Save item", event -> {
            BigDecimal amount = rate.getValue();
            if (code.getValue().isBlank() || name.getValue().isBlank()
                    || amount == null || amount.signum() <= 0
                    || amount.stripTrailingZeros().scale() > 2
                    || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
                Notification.show("Enter a code, a name, and a positive rent with up to two decimal places.");
                return;
            }

            try {
                database.update("""
                    INSERT INTO items (item_code, item_name, category, item_size, daily_rate)
                    VALUES (?, ?, ?, NULLIF(?, ''), ?)
                    """, code.getValue().strip(), name.getValue().strip(),
                    category.getValue(), size.getValue().strip(), amount);
            } catch (DuplicateKeyException exception) {
                Notification.show("That item code is already in use.");
                return;
            }

            refreshItems(database, items);
            code.clear();
            name.clear();
            size.clear();
            rate.clear();
            Notification.show("Item saved.");
        });

        add(new H1("Items"), new FormLayout(code, name, category, size, rate), save, items);
        refreshItems(database, items);
    }

    private void refreshItems(JdbcTemplate database, Grid<Item> items) {
        items.setItems(database.query("""
            SELECT item_code, item_name, category, COALESCE(item_size, ''), daily_rate
            FROM items
            ORDER BY item_name, item_code
            """, (row, rowNumber) -> new Item(row.getString(1), row.getString(2),
                row.getString(3), row.getString(4), row.getBigDecimal(5))));
    }
}
