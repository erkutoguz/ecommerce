package dev.erkut.customerservice.customer.api;

import dev.erkut.customerservice.customer.api.response.CustomerAddressResponse;
import dev.erkut.customerservice.customer.api.response.CustomerResponse;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerAddress;

import java.util.List;

public class CustomerMapper {
    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStatus(),
                toCustomerAddressResponse(customer.getAddresses()),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

    public static CustomerAddressResponse toResponse(CustomerAddress customerAddress) {
        return new CustomerAddressResponse(
                customerAddress.getId(),
                customerAddress.getFullAddress(),
                customerAddress.getCity(),
                customerAddress.getCountry());
    }

    private static List<CustomerAddressResponse> toCustomerAddressResponse(List<CustomerAddress> addresses) {
        return addresses.stream().map(CustomerMapper::toResponse).toList();
    }
}
