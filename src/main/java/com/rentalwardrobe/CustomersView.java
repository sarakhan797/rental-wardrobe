package com.rentalwardrobe;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.jdbc.core.JdbcTemplate;

@Route("customers")
@PageTitle("Customers | Rental Wardrobe")
public class CustomersView extends VerticalLayout {
    private record Customer(String name, String phone) {}

    public CustomersView(JdbcTemplate database) {
        TextField name = new TextField("Customer name");
        name.setMaxLength(100);
        TextField phone = new TextField("Phone number");
        phone.setMaxLength(20);

        Grid<Customer> customers = new Grid<>();
        customers.addColumn(Customer::name).setHeader("Customer name");
        customers.addColumn(Customer::phone).setHeader("Phone number");

        Button save = new Button("Save customer", event -> {
            String customerName = name.getValue().strip();
            String phoneNumber = phone.getValue().strip();
            String digits = phoneNumber.replaceAll("[ ()-]", "");

            if (customerName.isEmpty() || !digits.matches("\\+?[0-9]{7,15}")) {
                Notification.show("Enter a name and a valid phone number.");
                return;
            }

            database.update(
                "INSERT INTO customers (customer_name, phone) VALUES (?, ?)",
                customerName, digits
            );
            refreshCustomers(database, customers);
            name.clear();
            phone.clear();
            Notification.show("Customer saved.");
        });

        add(new H1("Customers"), name, phone, save, customers);
        refreshCustomers(database, customers);
    }

    private void refreshCustomers(JdbcTemplate database, Grid<Customer> customers) {
        customers.setItems(database.query(
            "SELECT customer_name, phone FROM customers ORDER BY customer_name, id",
            (row, rowNumber) -> new Customer(row.getString(1), row.getString(2))
        ));
    }
}
