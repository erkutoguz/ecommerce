package dev.erkut.customerservice.mapper;

import dev.erkut.customerservice.dto.CustomerAddressResponse;
import dev.erkut.customerservice.dto.CustomerResponse;
import dev.erkut.customerservice.model.Customer;
import dev.erkut.customerservice.model.CustomerAddress;

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
